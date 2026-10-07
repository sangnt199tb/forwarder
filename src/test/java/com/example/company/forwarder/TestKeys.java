package com.example.company.forwarder;

import com.example.company.forwarder.config.ForwarderProperties;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;

/** Cặp khoá RSA và cấu hình mẫu cho test. */
public final class TestKeys {

    public static final KeyPair KEYS = generate();
    public static final String PRIVATE_KEY = Base64.getEncoder().encodeToString(KEYS.getPrivate().getEncoded());

    private TestKeys() {
    }

    public static ForwarderProperties properties() {
        return properties(PRIVATE_KEY);
    }

    public static ForwarderProperties properties(String privateKey) {
        return new ForwarderProperties(
                new ForwarderProperties.PartnerAuth(Duration.ofSeconds(300)),
                new ForwarderProperties.GatewayToken(privateKey, "ebank-forwarder", "ebank-gateway",
                        Duration.ofSeconds(60)),
                new ForwarderProperties.Http(Duration.ofSeconds(5), Duration.ofSeconds(130)));
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
