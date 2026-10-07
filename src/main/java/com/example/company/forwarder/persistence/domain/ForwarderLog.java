package com.example.company.forwarder.persistence.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Một dòng cho mỗi giao dịch đã qua kiểm tra chữ ký. Khoá duy nhất (partner_id, transaction_key) chống gửi lại:
 * cùng transactionKey gửi lần hai thì không ghi được dòng mới, trả DUPLICATE_TRANSACTION.
 * Không lưu body request, response (có dữ liệu cá nhân).
 */
@Table("forwarder_log")
public class ForwarderLog {

    @Id
    private Long id;
    private String partnerId;
    private String transactionKey;
    private String apiId;
    /** X-Request-Id, trùng với log của gateway và service */
    private String requestId;
    /** PROCESSING, SUCCESS, FAILED */
    private String status;
    /** HTTP status trả cho đối tác */
    private Integer httpStatus;
    /** errorCode của lỗi (HYD-40-xxx của Forwarder, hoặc của service eBank) */
    private String errorCode;
    private Long durationMs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPartnerId() {
        return partnerId;
    }

    public void setPartnerId(String partnerId) {
        this.partnerId = partnerId;
    }

    public String getTransactionKey() {
        return transactionKey;
    }

    public void setTransactionKey(String transactionKey) {
        this.transactionKey = transactionKey;
    }

    public String getApiId() {
        return apiId;
    }

    public void setApiId(String apiId) {
        this.apiId = apiId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
