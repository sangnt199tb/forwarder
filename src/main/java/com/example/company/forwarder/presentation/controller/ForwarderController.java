package com.example.company.forwarder.presentation.controller;

import com.example.company.forwarder.common.error.ForwarderErrorCode;
import com.example.company.forwarder.presentation.service.ForwardService;
import com.example.company.forwarder.presentation.service.ForwardService.InboundRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Cửa vào duy nhất cho hệ thống bên ngoài: POST /forwarder-service/api/v1/forward. Đặc tả cho đối tác:
 * docs/api/forwarder-api.md. Body nhận dạng byte gốc (không để Spring parse) vì chữ ký tính trên đúng các byte này.
 * <p>
 * Lỗi xảy ra trước khi vào ForwardService (không đọc được body, body quá lớn) cũng trả body ResponseEnvelope:
 * request sai thì HTTP 400 VALIDATION_ERROR, lỗi bất ngờ thì HTTP 200 SERVER_ERROR.
 */
@RestController
@RequestMapping("/api/v1")
public class ForwarderController {

    private static final Logger log = LoggerFactory.getLogger(ForwarderController.class);
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final ForwardService forwardService;

    public ForwarderController(ForwardService forwardService) {
        this.forwardService = forwardService;
    }

    @PostMapping(value = "/forward", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<byte[]>> forward(
            @RequestHeader(value = "X-Partner-Id", required = false) String partnerId,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody(required = false) byte[] body) {
        return forwardService.forward(new InboundRequest(partnerId, timestamp, signature, validRequestId(requestId),
                body == null ? new byte[0] : body));
    }

    /**
     * Spring từ chối request trước khi gọi forward(): không đọc được body (ServerWebInputException, 400), body quá
     * giới hạn đọc vào bộ nhớ (ContentTooLargeException, 413; spring.codec.max-in-memory-size, mặc định 256 KB)...
     * Lỗi 4xx: VALIDATION_ERROR (HTTP 400), còn lại SERVER_ERROR (HTTP 200).
     */
    @ExceptionHandler({ResponseStatusException.class, DataBufferLimitException.class})
    public ResponseEntity<byte[]> handleRejectedRequest(Exception e, ServerWebExchange exchange) {
        String requestId = requestId(exchange);
        boolean clientError = e instanceof DataBufferLimitException
                || (e instanceof ResponseStatusException rse && rse.getStatusCode().is4xxClientError());
        if (!clientError) {
            log.error("[{}] Request failed before ForwardService", requestId, e);
        }
        return forwardService.reject(
                clientError ? ForwarderErrorCode.VALIDATION_ERROR : ForwarderErrorCode.SERVER_ERROR, requestId);
    }

    /** Lỗi bất ngờ ngoài ForwardService: SERVER_ERROR, HTTP 200 (lỗi hệ thống). */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<byte[]> handleUnexpected(Exception e, ServerWebExchange exchange) {
        String requestId = requestId(exchange);
        log.error("[{}] Unexpected error outside ForwardService", requestId, e);
        return forwardService.reject(ForwarderErrorCode.SERVER_ERROR, requestId);
    }

    private static String requestId(ServerWebExchange exchange) {
        return validRequestId(exchange.getRequest().getHeaders().getFirst("X-Request-Id"));
    }

    /** X-Request-Id sai định dạng có thể là chuỗi giả để chèn dòng log: tự sinh mới. */
    private static String validRequestId(String requestId) {
        return requestId != null && VALID_REQUEST_ID.matcher(requestId).matches()
                ? requestId : UUID.randomUUID().toString();
    }
}
