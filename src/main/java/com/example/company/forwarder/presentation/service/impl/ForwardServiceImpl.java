package com.example.company.forwarder.presentation.service.impl;

import com.example.company.forwarder.common.error.ErrorMessages;
import com.example.company.forwarder.common.error.ForwarderErrorCode;
import com.example.company.forwarder.common.error.ForwarderException;
import com.example.company.forwarder.config.ForwarderProperties;
import com.example.company.forwarder.integration.ebank.EbankGatewayClient;
import com.example.company.forwarder.integration.ebank.EbankGatewayClient.DownstreamResponse;
import com.example.company.forwarder.persistence.domain.ForwarderLog;
import com.example.company.forwarder.persistence.domain.GatewayRouteConfig;
import com.example.company.forwarder.persistence.domain.Partner;
import com.example.company.forwarder.persistence.repository.ForwarderLogRepository;
import com.example.company.forwarder.persistence.repository.PartnerApiPermissionRepository;
import com.example.company.forwarder.persistence.repository.PartnerRepository;
import com.example.company.forwarder.persistence.repository.RouteConfigRepository;
import com.example.company.forwarder.presentation.dto.RequestEnvelope;
import com.example.company.forwarder.presentation.dto.ResponseEnvelope;
import com.example.company.forwarder.presentation.service.ForwardService;
import com.example.company.forwarder.security.PartnerSignature;
import io.r2dbc.spi.R2dbcDataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Các bước, theo thứ tự (bước nào sai thì dừng):
 * <ol>
 *   <li>Đủ header X-Partner-Id, X-Timestamp, X-Signature; đối tác ACTIVE; chữ ký HMAC đúng ({@link PartnerSignature}).
 *       Sai: 401 UNAUTHORIZED, không nói rõ sai ở đâu, response không ký (chưa biết đối tác là ai).
 *       Từ đây mọi response đều được ký bằng secret của đối tác.</li>
 *   <li>X-Timestamp lệch không quá forwarder.partner-auth.max-clock-skew: REQUEST_EXPIRED.</li>
 *   <li>Body đúng dạng RequestEnvelope: VALIDATION_ERROR.</li>
 *   <li>Đối tác được gọi apiId (partner_api_permission): API_NOT_ALLOWED. apiId có và đang bật: API_NOT_FOUND.</li>
 *   <li>Ghi forwarder_log trạng thái PROCESSING. Trùng (partner_id, transaction_key): DUPLICATE_TRANSACTION
 *       (chống gửi lại request cũ, kể cả trong khoảng max-clock-skew).</li>
 *   <li>Gọi API Gateway ({@link EbankGatewayClient}), trả nguyên body của eBank trong responseBody, cập nhật log.</li>
 * </ol>
 * Không ghi body request, response vào log (có dữ liệu cá nhân).
 */
@Service
public class ForwardServiceImpl implements ForwardService {

    private static final Logger log = LoggerFactory.getLogger(ForwardServiceImpl.class);

    static final String PARTNER_ACTIVE = "ACTIVE";
    private static final Pattern PARTNER_ID = Pattern.compile("[A-Z0-9_]{1,50}");
    private static final Pattern TIMESTAMP = Pattern.compile("\\d{1,12}");
    private static final Pattern API_ID = Pattern.compile("[A-Z0-9_]{1,100}");
    private static final Pattern TRANSACTION_KEY = Pattern.compile("[A-Za-z0-9._-]{1,100}");
    /** Gateway trả các mã này là do Forwarder cấu hình sai (khoá, target_url), không trả nguyên cho đối tác. */
    private static final Set<Integer> REJECTED_BY_GATEWAY = Set.of(401, 403, 404, 405);

    private final PartnerRepository partners;
    private final PartnerApiPermissionRepository permissions;
    private final RouteConfigRepository routes;
    private final ForwarderLogRepository logs;
    private final EbankGatewayClient gatewayClient;
    private final ErrorMessages errorMessages;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final Duration maxClockSkew;

    public ForwardServiceImpl(PartnerRepository partners, PartnerApiPermissionRepository permissions,
                              RouteConfigRepository routes, ForwarderLogRepository logs,
                              EbankGatewayClient gatewayClient, ErrorMessages errorMessages, JsonMapper jsonMapper,
                              Clock clock, ForwarderProperties properties) {
        this.partners = partners;
        this.permissions = permissions;
        this.routes = routes;
        this.logs = logs;
        this.gatewayClient = gatewayClient;
        this.errorMessages = errorMessages;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.maxClockSkew = properties.partnerAuth().maxClockSkew();
    }

    /** Kết quả trả cho đối tác, trước khi chuyển thành JSON và ký. */
    /**
     * @param status        mã HTTP thật (của eBank, hoặc mã chi tiết của lỗi Forwarder), ghi vào forwarder_log
     * @param partnerStatus mã HTTP trả cho đối tác: 200, hoặc 400 khi Forwarder từ chối request
     */
    private record Outcome(HttpStatusCode status, HttpStatusCode partnerStatus, String result, Object responseBody,
                           String errorCode) {
    }

    /** Dữ liệu của một request đã qua kiểm tra chữ ký. */
    private static final class Context {
        final Partner partner;
        final String requestId;
        final long startNanos;
        RequestEnvelope envelope;

        Context(Partner partner, String requestId, long startNanos) {
            this.partner = partner;
            this.requestId = requestId;
            this.startNanos = startNanos;
        }

        String transactionKey() {
            return envelope == null ? null : envelope.transactionKey();
        }

        String apiId() {
            return envelope == null ? null : envelope.apiId();
        }

        long elapsedMs() {
            return (System.nanoTime() - startNanos) / 1_000_000;
        }
    }

    @Override
    public Mono<ResponseEntity<byte[]>> forward(InboundRequest in) {
        long startNanos = System.nanoTime();
        if (in.partnerId() == null || !PARTNER_ID.matcher(in.partnerId()).matches()
                || in.timestamp() == null || !TIMESTAMP.matcher(in.timestamp()).matches()
                || in.signature() == null || in.signature().isBlank()) {
            return Mono.just(rejectUnauthenticated(in, "missing or malformed X-Partner-Id, X-Timestamp, X-Signature"));
        }
        return partners.findByPartnerIdAndStatus(in.partnerId(), PARTNER_ACTIVE)
                .filter(partner -> PartnerSignature.matches(
                        partner.getSecretKey(), in.partnerId(), in.timestamp(), in.body(), in.signature()))
                .flatMap(partner -> authenticated(new Context(partner, in.requestId(), startNanos), in))
                .switchIfEmpty(Mono.fromSupplier(
                        () -> rejectUnauthenticated(in, "unknown or inactive partner, or wrong signature")))
                .onErrorResume(e -> {
                    // Lỗi trước khi biết đối tác (thường là DB): không ký được response
                    log.error("[{}] Cannot authenticate partner {}", in.requestId(), in.partnerId(), e);
                    return Mono.just(respond(null, in.requestId(), null,
                            errorOutcome(ForwarderErrorCode.SERVER_ERROR, in.requestId())));
                });
    }

    private Mono<ResponseEntity<byte[]>> authenticated(Context ctx, InboundRequest in) {
        return Mono.defer(() -> {
                    checkFresh(in.timestamp());
                    ctx.envelope = parseEnvelope(in.body());
                    return findRoute(ctx);
                })
                .flatMap(route -> startLog(ctx).flatMap(logRow -> callAndFinish(ctx, route, logRow)))
                .onErrorResume(e -> Mono.just(finish(ctx, toErrorOutcome(ctx, e))));
    }

    /** Lệch quá max-clock-skew so với đồng hồ Forwarder, về cả hai phía (request cũ, hoặc "từ tương lai"). */
    private void checkFresh(String timestamp) {
        long skew = Math.abs(Instant.now(clock).getEpochSecond() - Long.parseLong(timestamp));
        if (skew > maxClockSkew.toSeconds()) {
            throw new ForwarderException(ForwarderErrorCode.REQUEST_EXPIRED, "X-Timestamp off by " + skew + "s");
        }
    }

    private RequestEnvelope parseEnvelope(byte[] body) {
        RequestEnvelope envelope;
        try {
            envelope = jsonMapper.readValue(body, RequestEnvelope.class);
        } catch (JacksonException e) {
            throw new ForwarderException(ForwarderErrorCode.VALIDATION_ERROR, "body is not a valid envelope");
        }
        if (envelope == null || envelope.apiId() == null || !API_ID.matcher(envelope.apiId()).matches()) {
            throw new ForwarderException(ForwarderErrorCode.VALIDATION_ERROR, "missing or malformed apiId");
        }
        if (envelope.transactionKey() == null || !TRANSACTION_KEY.matcher(envelope.transactionKey()).matches()) {
            throw new ForwarderException(ForwarderErrorCode.VALIDATION_ERROR, "missing or malformed transactionKey");
        }
        return envelope;
    }

    private Mono<GatewayRouteConfig> findRoute(Context ctx) {
        String partnerId = ctx.partner.getPartnerId();
        return permissions.existsByPartnerIdAndApiId(partnerId, ctx.apiId())
                .flatMap(allowed -> allowed
                        ? routes.findByApiIdAndIsActiveTrue(ctx.apiId())
                        .switchIfEmpty(Mono.error(new ForwarderException(ForwarderErrorCode.API_NOT_FOUND,
                                "no active route for " + ctx.apiId())))
                        : Mono.error(new ForwarderException(ForwarderErrorCode.API_NOT_ALLOWED,
                        partnerId + " is not allowed to call " + ctx.apiId())));
    }

    private Mono<ForwarderLog> startLog(Context ctx) {
        LocalDateTime now = LocalDateTime.now(clock);
        ForwarderLog row = new ForwarderLog();
        row.setPartnerId(ctx.partner.getPartnerId());
        row.setTransactionKey(ctx.transactionKey());
        row.setApiId(ctx.apiId());
        row.setRequestId(ctx.requestId);
        row.setStatus("PROCESSING");
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        return logs.save(row).onErrorMap(
                e -> e instanceof DataIntegrityViolationException || e instanceof R2dbcDataIntegrityViolationException,
                e -> new ForwarderException(ForwarderErrorCode.DUPLICATE_TRANSACTION,
                        "transactionKey already used: " + ctx.transactionKey()));
    }

    private Mono<ResponseEntity<byte[]>> callAndFinish(Context ctx, GatewayRouteConfig route, ForwarderLog logRow) {
        return gatewayClient.call(route, ctx.partner.getPartnerId(), ctx.envelope.body(), ctx.requestId)
                .map(this::toOutcome)
                .onErrorResume(e -> Mono.just(toErrorOutcome(ctx, e)))
                .flatMap(outcome -> updateLog(ctx, logRow, outcome).thenReturn(finish(ctx, outcome)));
    }

    /** Trả nguyên body của eBank, trừ khi gateway từ chối chính Forwarder (cấu hình sai). */
    private Outcome toOutcome(DownstreamResponse response) {
        if (REJECTED_BY_GATEWAY.contains(response.status())) {
            throw new ForwarderException(ForwarderErrorCode.DOWNSTREAM_REJECTED,
                    "API Gateway returned " + response.status() + ", check forwarder key and target_url");
        }
        JsonNode body;
        try {
            body = response.body().length == 0 ? null : jsonMapper.readTree(response.body());
        } catch (JacksonException e) {
            throw new ForwarderException(ForwarderErrorCode.SERVER_ERROR,
                    "eBank returned non-JSON body with status " + response.status());
        }
        HttpStatusCode status = HttpStatusCode.valueOf(response.status());
        if (status.is2xxSuccessful()) {
            return new Outcome(status, HttpStatus.OK, ResponseEnvelope.SUCCESS, body, null);
        }
        JsonNode errorCode = body == null ? null : body.get("errorCode");
        // Lỗi nghiệp vụ do eBank trả: eBank đã xử lý xong request, đối tác nhận 200
        return new Outcome(status, HttpStatus.OK, ResponseEnvelope.FAILED, body,
                errorCode != null && errorCode.isValueNode() ? errorCode.asString() : null);
    }

    private Outcome toErrorOutcome(Context ctx, Throwable e) {
        if (e instanceof ForwarderException fe) {
            ForwarderErrorCode code = fe.getErrorCode();
            if (code.status().is5xxServerError()) {
                log.error("[{}] {} apiId={} txn={}: {}", ctx.requestId, ctx.partner.getPartnerId(), ctx.apiId(),
                        ctx.transactionKey(), fe.getMessage());
            } else {
                log.info("[{}] {} apiId={} txn={} rejected: {}", ctx.requestId, ctx.partner.getPartnerId(),
                        ctx.apiId(), ctx.transactionKey(), fe.getMessage());
            }
            return errorOutcome(code, ctx.requestId);
        }
        log.error("[{}] Unexpected error for {} apiId={} txn={}", ctx.requestId, ctx.partner.getPartnerId(),
                ctx.apiId(), ctx.transactionKey(), e);
        return errorOutcome(ForwarderErrorCode.SERVER_ERROR, ctx.requestId);
    }

    private Outcome errorOutcome(ForwarderErrorCode code, String requestId) {
        return new Outcome(code.status(), code.partnerStatus(), ResponseEnvelope.FAILED,
                errorMessages.body(code, requestId),
                code.errorCode());
    }

    /** Ghi log lỗi thì vẫn trả kết quả cho đối tác (eBank đã xử lý xong), chỉ ghi lỗi. */
    private Mono<Void> updateLog(Context ctx, ForwarderLog row, Outcome outcome) {
        row.setStatus(outcome.result());
        row.setHttpStatus(outcome.status().value());
        row.setErrorCode(outcome.errorCode());
        row.setDurationMs(ctx.elapsedMs());
        row.setUpdatedAt(LocalDateTime.now(clock));
        return logs.save(row)
                .doOnError(e -> log.error("[{}] Cannot update forwarder_log id={}", ctx.requestId, row.getId(), e))
                .onErrorResume(e -> Mono.empty())
                .then();
    }

    private ResponseEntity<byte[]> finish(Context ctx, Outcome outcome) {
        log.info("[{}] {} apiId={} txn={} -> {} {} {}ms", ctx.requestId, ctx.partner.getPartnerId(), ctx.apiId(),
                ctx.transactionKey(), outcome.status().value(), outcome.result(), ctx.elapsedMs());
        return respond(ctx.partner, ctx.requestId, ctx.transactionKey(), outcome);
    }

    private ResponseEntity<byte[]> rejectUnauthenticated(InboundRequest in, String reason) {
        String partnerId = in.partnerId() != null && PARTNER_ID.matcher(in.partnerId()).matches()
                ? in.partnerId() : "<invalid>";
        log.warn("[{}] Rejected request from partner {}: {}", in.requestId(), partnerId, reason);
        return respond(null, in.requestId(), null, errorOutcome(ForwarderErrorCode.UNAUTHORIZED, in.requestId()));
    }

    @Override
    public ResponseEntity<byte[]> reject(ForwarderErrorCode code, String requestId) {
        log.warn("[{}] Request rejected before processing: {}", requestId, code);
        return respond(null, requestId, null, errorOutcome(code, requestId));
    }

    /**
     * HTTP trả đối tác: 400 khi Forwarder từ chối request (xác thực, phân quyền, request sai, apiId không có);
     * 200 trong mọi trường hợp còn lại (eBank đã xử lý: thành công hoặc lỗi nghiệp vụ; lỗi hệ thống). Kết quả chi tiết
     * nằm trong body (status SUCCESS/FAILED, responseBody.code/errorCode). Mã HTTP thật chỉ ghi vào forwarder_log và log.
     * partner null: chưa xác thực được đối tác, response không ký.
     */
    private ResponseEntity<byte[]> respond(Partner partner, String requestId, String transactionKey, Outcome outcome) {
        byte[] body = jsonMapper.writeValueAsBytes(
                new ResponseEnvelope(outcome.result(), transactionKey, requestId, outcome.responseBody()));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Request-Id", requestId);
        if (partner != null) {
            String timestamp = String.valueOf(Instant.now(clock).getEpochSecond());
            headers.set("X-Timestamp", timestamp);
            headers.set("X-Signature",
                    PartnerSignature.sign(partner.getSecretKey(), partner.getPartnerId(), timestamp, body));
        }
        return ResponseEntity.status(outcome.partnerStatus()).headers(headers).body(body);
    }
}
