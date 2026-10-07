package com.example.company.forwarder.presentation.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Body response trả cho đối tác. Chữ ký nằm ở header X-Timestamp, X-Signature, tính trên đúng các byte của body này.
 *
 * @param status         SUCCESS khi eBank trả 2xx, còn lại FAILED (HTTP status cũng cho biết lỗi gì)
 * @param transactionKey transactionKey của request (null nếu không đọc được body request)
 * @param requestId      dùng khi cần tra log cùng eBank
 * @param responseBody   body eBank trả về nguyên văn (dữ liệu, hoặc lỗi nghiệp vụ HYD-xx-xxx của eBank),
 *                       hoặc ErrorBody (HYD-40-xxx) khi Forwarder tự trả lỗi
 */
@JsonPropertyOrder({"status", "transactionKey", "requestId", "responseBody"})
public record ResponseEnvelope(String status, String transactionKey, String requestId, Object responseBody) {

    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
}
