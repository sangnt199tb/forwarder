package com.example.company.forwarder.integration.ebank;

import com.example.company.forwarder.common.error.ForwarderErrorCode;
import com.example.company.forwarder.common.error.ForwarderException;
import com.example.company.forwarder.persistence.domain.GatewayRouteConfig;
import com.example.company.forwarder.security.GatewayTokenSigner;
import io.netty.handler.timeout.ReadTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * Gọi vào eBank qua API Gateway (route *-fwd) theo cấu hình gateway_route_config. Không gọi thẳng service: Forwarder
 * nằm ở DMZ, chỉ được đi qua gateway (rate limit, log, circuit breaker, chặn path lạ).
 * <ul>
 *   <li>URL: target_url, biến {ten} lấy từ trường cùng tên trong body của đối tác, mã hoá chặt (cả "/"), nên giá trị
 *       như "../customer-internal" không đổi được path.</li>
 *   <li>Header: Authorization = token RS256 của Forwarder cho đúng method + path này (GatewayTokenSigner),
 *       X-Request-Id để log của Forwarder, gateway, service nối được với nhau.</li>
 * </ul>
 */
@Component
public class EbankGatewayClient {

    private static final Set<HttpMethod> WITH_BODY = Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH);

    /** HTTP status và body gốc eBank trả về. */
    public record DownstreamResponse(int status, byte[] body) {
    }

    private final WebClient webClient;
    private final GatewayTokenSigner tokenSigner;
    private final JsonMapper jsonMapper;

    public EbankGatewayClient(WebClient webClient, GatewayTokenSigner tokenSigner, JsonMapper jsonMapper) {
        this.webClient = webClient;
        this.tokenSigner = tokenSigner;
        this.jsonMapper = jsonMapper;
    }

    public Mono<DownstreamResponse> call(GatewayRouteConfig route, String partnerId, JsonNode body, String requestId) {
        return Mono.defer(() -> {
            HttpMethod method = HttpMethod.valueOf(route.getHttpMethod().trim().toUpperCase());
            URI uri = buildUri(route.getTargetUrl(), body);
            WebClient.RequestBodySpec request = webClient.method(method)
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenSigner.sign(partnerId, method.name(), uri.getRawPath()))
                    .header("X-Request-Id", requestId)
                    .accept(MediaType.APPLICATION_JSON);
            WebClient.RequestHeadersSpec<?> spec = WITH_BODY.contains(method) && body != null
                    ? request.contentType(MediaType.APPLICATION_JSON).bodyValue(jsonMapper.writeValueAsBytes(body))
                    : request;
            return spec.exchangeToMono(response -> response.bodyToMono(byte[].class)
                    .defaultIfEmpty(new byte[0])
                    .map(bytes -> new DownstreamResponse(response.statusCode().value(), bytes)));
        }).onErrorMap(WebClientRequestException.class, EbankGatewayClient::toForwarderException);
    }

    /** target_url với biến {ten} thay bằng giá trị trong body. Thiếu biến: VALIDATION_ERROR. */
    static URI buildUri(String targetUrl, JsonNode body) {
        Map<String, String> variables = new HashMap<>();
        if (body != null && body.isObject()) {
            for (Map.Entry<String, JsonNode> field : body.properties()) {
                if (field.getValue().isValueNode() && !field.getValue().isNull()) {
                    variables.put(field.getKey(), field.getValue().asString());
                }
            }
        }
        try {
            // encode() trước buildAndExpand(): giá trị biến được mã hoá chặt, "/" thành %2F
            return UriComponentsBuilder.fromUriString(targetUrl.trim()).encode().buildAndExpand(variables).toUri();
        } catch (IllegalArgumentException e) {
            throw new ForwarderException(ForwarderErrorCode.VALIDATION_ERROR,
                    "cannot build URL from " + targetUrl + ": " + e.getMessage());
        }
    }

    private static ForwarderException toForwarderException(WebClientRequestException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ReadTimeoutException || cause instanceof TimeoutException) {
                return new ForwarderException(ForwarderErrorCode.GATEWAY_TIMEOUT, "API Gateway timed out: " + e.getUri());
            }
        }
        return new ForwarderException(ForwarderErrorCode.SERVICE_UNAVAILABLE,
                "cannot reach API Gateway " + e.getUri() + ": " + e.getMessage());
    }
}
