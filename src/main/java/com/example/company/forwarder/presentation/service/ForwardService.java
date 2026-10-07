package com.example.company.forwarder.presentation.service;

import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

/** Nhận request của đối tác, kiểm tra, chuyển vào eBank qua API Gateway và trả kết quả đã ký. */
public interface ForwardService {

    /**
     * Header và body gốc của request đối tác gửi.
     *
     * @param requestId X-Request-Id đã kiểm tra định dạng (hoặc Forwarder tự sinh)
     * @param body      đúng các byte đối tác gửi, chữ ký tính trên đây
     */
    record InboundRequest(String partnerId, String timestamp, String signature, String requestId, byte[] body) {
    }

    /** Body là ResponseEnvelope dạng JSON. Đối tác đã qua kiểm tra chữ ký thì response có X-Timestamp, X-Signature. */
    Mono<ResponseEntity<byte[]>> forward(InboundRequest request);
}
