package com.example.company.forwarder.security;

import com.example.company.forwarder.config.ForwarderProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ký token RS256 (JWT) gửi kèm mỗi request Forwarder gọi vào API Gateway ("Authorization: Bearer ...").
 * Gateway kiểm tra bằng khoá công khai tương ứng (gateway.forwarder.public-key) rồi gắn X-Partner-Id cho service.
 * <p>
 * Claims: iss, aud, sub = mã đối tác, typ = forwarder, htm/htu = method và path của chính request này (token lộ ra cũng
 * không dùng được cho API hay khách hàng khác), iat, exp (forwarder.gateway-token.ttl), jti.
 * <p>
 * Tự ký bằng java.security, không thêm thư viện JWT: chỉ cần ký, không cần parse.
 */
@Component
public class GatewayTokenSigner {

    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();
    private static final String HEADER = BASE64URL.encodeToString(
            "{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private final PrivateKey privateKey;
    private final String issuer;
    private final String audience;
    private final Duration ttl;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public GatewayTokenSigner(ForwarderProperties properties, JsonMapper jsonMapper, Clock clock) {
        ForwarderProperties.GatewayToken config = properties.gatewayToken();
        this.privateKey = parsePrivateKey(config.privateKey());
        this.issuer = config.issuer();
        this.audience = config.audience();
        this.ttl = config.ttl();
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    /**
     * @param method  method sẽ gọi (GET, POST...)
     * @param rawPath path gốc đã mã hoá của URL sẽ gọi, ví dụ /customer-fwd/v1/customers/CIF0000000001
     */
    public String sign(String partnerId, String method, String rawPath) {
        long now = Instant.now(clock).getEpochSecond();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", issuer);
        claims.put("aud", audience);
        claims.put("sub", partnerId);
        claims.put("typ", "forwarder");
        claims.put("htm", method);
        claims.put("htu", rawPath);
        claims.put("iat", now);
        claims.put("exp", now + ttl.toSeconds());
        claims.put("jti", UUID.randomUUID().toString());

        String signingInput = HEADER + "." + BASE64URL.encodeToString(jsonMapper.writeValueAsBytes(claims));
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + BASE64URL.encodeToString(signature.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot sign gateway token", e);
        }
    }

    private static PrivateKey parsePrivateKey(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException("forwarder.gateway-token.private-key is missing: create "
                    + "%USERPROFILE%\\.ebank\\forwarder-secrets.yaml (see docs/config/forwarder-secrets.example.yaml)");
        }
        try {
            byte[] der = Base64.getDecoder().decode(base64.trim());
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException(
                    "forwarder.gateway-token.private-key is not a valid Base64 PKCS#8 RSA private key", e);
        }
    }
}
