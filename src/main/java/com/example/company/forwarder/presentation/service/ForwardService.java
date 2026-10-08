package com.example.company.forwarder.presentation.service;

import com.example.company.forwarder.common.error.ForwarderErrorCode;
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

    /**
     * Body là ResponseEnvelope dạng JSON. HTTP 400 khi Forwarder từ chối request (xác thực, phân quyền, request sai),
     * 200 trong mọi trường hợp còn lại (kết quả nằm trong body).
     * Đối tác đã qua kiểm tra chữ ký thì response có X-Timestamp, X-Signature.
     */
    Mono<ResponseEntity<byte[]>> forward(InboundRequest request);

    /**
     * Response lỗi cho request không vào được tới forward() (ví dụ không đọc được body): status FAILED, responseBody
     * là lỗi HYD-40 của mã này, HTTP theo {@link ForwarderErrorCode#partnerStatus()}. Không ký (chưa xác thực đối tác).
     */
    ResponseEntity<byte[]> reject(ForwarderErrorCode code, String requestId);
}
