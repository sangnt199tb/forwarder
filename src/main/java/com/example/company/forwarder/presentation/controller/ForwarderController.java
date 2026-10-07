package com.example.company.forwarder.presentation.controller;

import com.example.company.forwarder.presentation.service.ForwardService;
import com.example.company.forwarder.presentation.service.ForwardService.InboundRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Cửa vào duy nhất cho hệ thống bên ngoài: POST /forwarder-service/api/v1/forward. Đặc tả cho đối tác:
 * docs/api/forwarder-api.md. Body nhận dạng byte gốc (không để Spring parse) vì chữ ký tính trên đúng các byte này.
 */
@RestController
@RequestMapping("/api/v1")
public class ForwarderController {

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
        // X-Request-Id sai định dạng có thể là chuỗi giả để chèn dòng log: tự sinh mới
        String id = requestId != null && VALID_REQUEST_ID.matcher(requestId).matches()
                ? requestId : UUID.randomUUID().toString();
        return forwardService.forward(
                new InboundRequest(partnerId, timestamp, signature, id, body == null ? new byte[0] : body));
    }
}
