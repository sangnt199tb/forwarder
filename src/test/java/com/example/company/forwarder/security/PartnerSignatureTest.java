package com.example.company.forwarder.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PartnerSignatureTest {

    private static final byte[] BODY =
            "{\"apiId\":\"CUSTOMER_DETAIL\",\"transactionKey\":\"FCC-0001\",\"body\":{\"cif\":\"CIF0000000001\"}}"
                    .getBytes(StandardCharsets.UTF_8);

    /** Tính độc lập bằng openssl, xem docs/api/forwarder-api.md mục "Ví dụ chữ ký". */
    private static final String EXPECTED = "19e957a984103efae793b9bfb611e855982938263759146dfbd5a84f4b075613";

    @Test
    void matchesIndependentlyComputedVector() {
        assertThat(PartnerSignature.sha256Hex(BODY))
                .isEqualTo("509630b3aa765008c587bf9b19e8a01f60ca26b073b323a68d3c2b2d5622e911");
        assertThat(PartnerSignature.sign("test-secret", "FCC", "1791400000", BODY)).isEqualTo(EXPECTED);
    }

    @Test
    void acceptsUpperCaseHexAndSurroundingSpaces() {
        assertThat(PartnerSignature.matches("test-secret", "FCC", "1791400000", BODY,
                " " + EXPECTED.toUpperCase() + " ")).isTrue();
    }

    @Test
    void rejectsChangedBodyTimestampPartnerOrSecret() {
        byte[] changed = new String(BODY, StandardCharsets.UTF_8).replace("CIF0000000001", "CIF0000000002")
                .getBytes(StandardCharsets.UTF_8);

        assertThat(PartnerSignature.matches("test-secret", "FCC", "1791400000", changed, EXPECTED)).isFalse();
        assertThat(PartnerSignature.matches("test-secret", "FCC", "1791400001", BODY, EXPECTED)).isFalse();
        assertThat(PartnerSignature.matches("test-secret", "AI", "1791400000", BODY, EXPECTED)).isFalse();
        assertThat(PartnerSignature.matches("other-secret", "FCC", "1791400000", BODY, EXPECTED)).isFalse();
        assertThat(PartnerSignature.matches("test-secret", "FCC", "1791400000", BODY, null)).isFalse();
    }
}
