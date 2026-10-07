package com.example.company.forwarder.security;

import com.example.company.forwarder.TestKeys;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayTokenSignerTest {

    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final GatewayTokenSigner signer = new GatewayTokenSigner(TestKeys.properties(), jsonMapper,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void signsRs256TokenBoundToMethodAndPath() throws Exception {
        String token = signer.sign("FCC", "GET", "/customer-fwd/v1/customers/CIF0000000001");

        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);

        Signature rsa = Signature.getInstance("SHA256withRSA");
        rsa.initVerify(TestKeys.KEYS.getPublic());
        rsa.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(rsa.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();

        JsonNode header = jsonMapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
        assertThat(header.get("alg").asString()).isEqualTo("RS256");

        JsonNode claims = jsonMapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
        assertThat(claims.get("iss").asString()).isEqualTo("ebank-forwarder");
        assertThat(claims.get("aud").asString()).isEqualTo("ebank-gateway");
        assertThat(claims.get("sub").asString()).isEqualTo("FCC");
        assertThat(claims.get("typ").asString()).isEqualTo("forwarder");
        assertThat(claims.get("htm").asString()).isEqualTo("GET");
        assertThat(claims.get("htu").asString()).isEqualTo("/customer-fwd/v1/customers/CIF0000000001");
        assertThat(claims.get("iat").asLong()).isEqualTo(NOW.getEpochSecond());
        assertThat(claims.get("exp").asLong()).isEqualTo(NOW.getEpochSecond() + 60);
        assertThat(claims.get("jti").asString()).isNotBlank();
    }

    @Test
    void missingPrivateKeyStopsStartupWithHint() {
        assertThatThrownBy(() -> new GatewayTokenSigner(TestKeys.properties(null), jsonMapper, Clock.systemUTC()))
                .hasMessageContaining("forwarder-secrets.yaml");
    }
}
