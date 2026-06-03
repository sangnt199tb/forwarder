package com.example.company.forwarder.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class HashUtil {
    public static String calculateSHA256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            // Chuyển mảng byte sang chuỗi Hex (hệ cơ số 16)
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi thuật toán mã hóa SHA-256", e);
        }
    }
}
