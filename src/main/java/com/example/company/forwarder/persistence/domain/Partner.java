package com.example.company.forwarder.persistence.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Hệ thống bên ngoài được gọi vào eBank qua Forwarder (FCC, AI...). secret_key là secret HMAC dùng chung với đối tác,
 * trao cho đối tác qua kênh riêng. Chỉ đối tác có status = ACTIVE mới gọi được.
 */
@Table("partner")
public class Partner {

    @Id
    private Long id;
    /** Mã đối tác, gửi trong header X-Partner-Id, ví dụ FCC */
    private String partnerId;
    private String partnerName;
    /** Secret HMAC-SHA256. Lưu nguyên văn vì Forwarder cần nó để tính lại chữ ký (giới hạn: chưa mã hoá khi lưu) */
    private String secretKey;
    /** ACTIVE hoặc INACTIVE */
    private String status;
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

    public String getPartnerName() {
        return partnerName;
    }

    public void setPartnerName(String partnerName) {
        this.partnerName = partnerName;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
