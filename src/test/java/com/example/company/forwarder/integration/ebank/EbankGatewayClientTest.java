package com.example.company.forwarder.integration.ebank;

import com.example.company.forwarder.common.error.ForwarderErrorCode;
import com.example.company.forwarder.common.error.ForwarderException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class EbankGatewayClientTest {

    private static final String TEMPLATE = "http://localhost:8081/customer-fwd/v1/customers/{cif}";
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private JsonNode json(String value) {
        return jsonMapper.readTree(value);
    }

    @Test
    void expandsVariableFromBody() {
        assertThat(EbankGatewayClient.buildUri(TEMPLATE, json("{\"cif\":\"CIF0000000001\",\"other\":1}")).toString())
                .isEqualTo("http://localhost:8081/customer-fwd/v1/customers/CIF0000000001");
    }

    @Test
    void encodesSlashSoPathCannotChange() {
        String path = EbankGatewayClient.buildUri(TEMPLATE,
                json("{\"cif\":\"../../customer-internal/v1/customers\"}")).getRawPath();

        assertThat(path).startsWith("/customer-fwd/v1/customers/").doesNotContain("/../").contains("%2F");
    }

    @Test
    void missingVariableIsValidationError() {
        ForwarderException ex = catchThrowableOfType(
                () -> EbankGatewayClient.buildUri(TEMPLATE, json("{\"id\":\"x\"}")), ForwarderException.class);

        assertThat(ex.getErrorCode()).isEqualTo(ForwarderErrorCode.VALIDATION_ERROR);
    }

    @Test
    void objectValuesAreNotUsedAsVariables() {
        ForwarderException ex = catchThrowableOfType(
                () -> EbankGatewayClient.buildUri(TEMPLATE, json("{\"cif\":{\"a\":1}}")), ForwarderException.class);

        assertThat(ex.getErrorCode()).isEqualTo(ForwarderErrorCode.VALIDATION_ERROR);
    }
}
