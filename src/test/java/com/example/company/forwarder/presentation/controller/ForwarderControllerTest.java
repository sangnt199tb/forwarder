package com.example.company.forwarder.presentation.controller;

import com.example.company.forwarder.common.error.ForwarderErrorCode;
import com.example.company.forwarder.presentation.service.ForwardService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lỗi xảy ra trước khi vào ForwardService (không đọc được body) cũng trả ResponseEnvelope, status FAILED. */
class ForwarderControllerTest {

    private static final byte[] FAILED_BODY = "{\"status\":\"FAILED\"}".getBytes(StandardCharsets.UTF_8);

    private final ForwardService service = mock(ForwardService.class);
    private final WebTestClient client = WebTestClient.bindToController(new ForwarderController(service)).build();

    @Test
    void oversizedBodyIsRejectedAsValidationError() {
        when(service.reject(eq(ForwarderErrorCode.VALIDATION_ERROR), eq("req-9")))
                .thenReturn(ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(FAILED_BODY));
        // Lớn hơn giới hạn 256 KB mặc định của WebFlux khi đọc body vào bộ nhớ
        byte[] huge = new byte[300 * 1024];

        client.post().uri("/api/v1/forward")
                .header("X-Request-Id", "req-9")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(huge)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.status").isEqualTo("FAILED");

        verify(service).reject(ForwarderErrorCode.VALIDATION_ERROR, "req-9");
        verify(service, never()).forward(any());
    }
}
