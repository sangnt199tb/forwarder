package com.example.company.forwarder.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Chữ ký HMAC-SHA256 giữa đối tác và Forwarder, dùng cho cả request (đối tác ký) và response (Forwarder ký).
 * <pre>
 * chuỗi ký = partnerId + "\n" + timestamp + "\n" + hex(SHA-256(body gốc))
 * chữ ký   = hex(HMAC-SHA256(secret của đối tác, chuỗi ký))     (chữ thường)
 * </pre>
 * Ký trên body gốc (đúng các byte gửi đi) nên mọi trường trong body (apiId, transactionKey, dữ liệu nghiệp vụ) đều được
 * bảo vệ: sửa một byte là sai chữ ký. Không ký trên JSON đã parse vì mỗi bên có thể sắp xếp trường khác nhau.
 * timestamp (epoch giây) nằm trong chuỗi ký nên không sửa được để gửi lại request cũ.
 */
public final class PartnerSignature {

    private static final HexFormat HEX = HexFormat.of();

    private PartnerSignature() {
    }

    public static String sign(String secret, String partnerId, String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HEX.formatHex(mac.doFinal(stringToSign(partnerId, timestamp, body).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }

    /** So sánh hết chuỗi dù sai từ ký tự đầu, để không đoán được chữ ký qua thời gian phản hồi. */
    public static boolean matches(String secret, String partnerId, String timestamp, byte[] body, String signature) {
        if (signature == null) {
            return false;
        }
        String expected = sign(secret, partnerId, timestamp, body);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                signature.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }

    static String stringToSign(String partnerId, String timestamp, byte[] body) {
        return partnerId + "\n" + timestamp + "\n" + sha256Hex(body);
    }

    static String sha256Hex(byte[] body) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
