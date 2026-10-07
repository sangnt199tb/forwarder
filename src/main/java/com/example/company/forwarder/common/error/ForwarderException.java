package com.example.company.forwarder.common.error;

/**
 * Lỗi Forwarder trả cho đối tác. ForwardServiceImpl chuyển thành ResponseEnvelope (status FAILED, responseBody là
 * ErrorBody) với HTTP status của mã lỗi. reason chỉ để ghi log, không trả cho đối tác.
 */
public class ForwarderException extends RuntimeException {

    private final ForwarderErrorCode errorCode;

    public ForwarderException(ForwarderErrorCode errorCode, String reason) {
        super(errorCode.name() + ": " + reason);
        this.errorCode = errorCode;
    }

    public ForwarderErrorCode getErrorCode() {
        return errorCode;
    }
}
