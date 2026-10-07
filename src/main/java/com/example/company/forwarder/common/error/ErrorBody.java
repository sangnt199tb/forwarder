package com.example.company.forwarder.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Body lỗi của Forwarder, cùng dạng với ErrorResponse của eBank (code, errorCode, message.vi/en, requestId), để đối tác
 * xử lý một kiểu cho cả lỗi của Forwarder lẫn lỗi nghiệp vụ của eBank.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorBody(String code, String errorCode, Message message, String requestId) {

    public record Message(String vi, String en) {
    }
}
