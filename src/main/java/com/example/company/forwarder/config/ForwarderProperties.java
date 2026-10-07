package com.example.company.forwarder.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Cấu hình forwarder.* trong application.yml.
 *
 * @param partnerAuth  kiểm tra request của đối tác
 * @param gatewayToken token RS256 Forwarder ký để gọi API Gateway
 * @param http         timeout khi gọi API Gateway
 */
@ConfigurationProperties(prefix = "forwarder")
public record ForwarderProperties(PartnerAuth partnerAuth, GatewayToken gatewayToken, Http http) {

    /** @param maxClockSkew X-Timestamp được lệch tối đa bao nhiêu so với đồng hồ Forwarder */
    public record PartnerAuth(Duration maxClockSkew) {
    }

    /**
     * @param privateKey khoá bí mật RSA (PKCS#8, DER, Base64), nằm trong file secret ngoài repo
     * @param issuer     claim iss, gateway bắt buộc đúng giá trị này
     * @param audience   claim aud, gateway bắt buộc đúng giá trị này
     * @param ttl        thời gian sống của token
     */
    public record GatewayToken(String privateKey, String issuer, String audience, Duration ttl) {
    }

    public record Http(Duration connectTimeout, Duration responseTimeout) {
    }
}
