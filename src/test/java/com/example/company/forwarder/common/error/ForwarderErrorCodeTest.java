package com.example.company.forwarder.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

class ForwarderErrorCodeTest {

    /** Forwarder từ chối request (chưa tới eBank): đối tác nhận HTTP 400. */
    private static final EnumSet<ForwarderErrorCode> REJECTIONS = EnumSet.of(
            ForwarderErrorCode.VALIDATION_ERROR, ForwarderErrorCode.UNAUTHORIZED, ForwarderErrorCode.REQUEST_EXPIRED,
            ForwarderErrorCode.DUPLICATE_TRANSACTION, ForwarderErrorCode.API_NOT_FOUND,
            ForwarderErrorCode.API_NOT_ALLOWED);

    @Test
    void rejectionsAre400AndSystemErrorsAre200() {
        for (ForwarderErrorCode code : ForwarderErrorCode.values()) {
            HttpStatus expected = REJECTIONS.contains(code) ? HttpStatus.BAD_REQUEST : HttpStatus.OK;
            assertThat(code.partnerStatus()).as(code.name()).isEqualTo(expected);
        }
    }
}
