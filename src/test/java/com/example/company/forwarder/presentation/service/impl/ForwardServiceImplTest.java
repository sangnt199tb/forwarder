package com.example.company.forwarder.presentation.service.impl;

import com.example.company.forwarder.TestKeys;
import com.example.company.forwarder.common.error.ErrorMessages;
import com.example.company.forwarder.common.error.ForwarderErrorCode;
import com.example.company.forwarder.common.error.ForwarderException;
import com.example.company.forwarder.integration.ebank.EbankGatewayClient;
import com.example.company.forwarder.integration.ebank.EbankGatewayClient.DownstreamResponse;
import com.example.company.forwarder.persistence.domain.ForwarderLog;
import com.example.company.forwarder.persistence.domain.GatewayRouteConfig;
import com.example.company.forwarder.persistence.domain.Partner;
import com.example.company.forwarder.persistence.repository.ForwarderLogRepository;
import com.example.company.forwarder.persistence.repository.PartnerApiPermissionRepository;
import com.example.company.forwarder.persistence.repository.PartnerRepository;
import com.example.company.forwarder.persistence.repository.RouteConfigRepository;
import com.example.company.forwarder.presentation.service.ForwardService.InboundRequest;
import com.example.company.forwarder.security.PartnerSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ForwardServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");
    private static final String TS = String.valueOf(NOW.getEpochSecond());
    private static final String SECRET = "fcc-secret";
    private static final String BODY =
            "{\"apiId\":\"CUSTOMER_DETAIL\",\"transactionKey\":\"FCC-0001\",\"body\":{\"cif\":\"CIF0000000001\"}}";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private PartnerRepository partners;
    private PartnerApiPermissionRepository permissions;
    private RouteConfigRepository routes;
    private ForwarderLogRepository logs;
    private EbankGatewayClient gatewayClient;
    private ForwardServiceImpl service;

    @BeforeEach
    void setUp() {
        partners = mock(PartnerRepository.class);
        permissions = mock(PartnerApiPermissionRepository.class);
        routes = mock(RouteConfigRepository.class);
        logs = mock(ForwarderLogRepository.class);
        gatewayClient = mock(EbankGatewayClient.class);

        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/errors");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);

        service = new ForwardServiceImpl(partners, permissions, routes, logs, gatewayClient,
                new ErrorMessages(messages), jsonMapper, Clock.fixed(NOW, ZoneOffset.UTC), TestKeys.properties());

        Partner fcc = new Partner();
        fcc.setPartnerId("FCC");
        fcc.setSecretKey(SECRET);
        fcc.setStatus("ACTIVE");
        when(partners.findByPartnerIdAndStatus(anyString(), eq("ACTIVE"))).thenReturn(Mono.empty());
        when(partners.findByPartnerIdAndStatus("FCC", "ACTIVE")).thenReturn(Mono.just(fcc));

        GatewayRouteConfig route = new GatewayRouteConfig();
        route.setApiId("CUSTOMER_DETAIL");
        route.setTargetUrl("http://localhost:8081/customer-fwd/v1/customers/{cif}");
        route.setHttpMethod("GET");
        route.setIsActive(true);
        when(permissions.existsByPartnerIdAndApiId(anyString(), anyString())).thenReturn(Mono.just(false));
        when(permissions.existsByPartnerIdAndApiId("FCC", "CUSTOMER_DETAIL")).thenReturn(Mono.just(true));
        when(routes.findByApiIdAndIsActiveTrue(anyString())).thenReturn(Mono.empty());
        when(routes.findByApiIdAndIsActiveTrue("CUSTOMER_DETAIL")).thenReturn(Mono.just(route));
        when(logs.save(any(ForwarderLog.class))).thenAnswer(inv -> {
            ForwarderLog row = inv.getArgument(0);
            if (row.getId() == null) {
                row.setId(1L);
            }
            return Mono.just(row);
        });
    }

    private ResponseEntity<byte[]> send(String partnerId, String timestamp, String secret, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String signature = PartnerSignature.sign(secret, partnerId, timestamp, bytes);
        return service.forward(new InboundRequest(partnerId, timestamp, signature, "req-1", bytes)).block();
    }

    private ResponseEntity<byte[]> send() {
        return send("FCC", TS, SECRET, BODY);
    }

    private JsonNode json(ResponseEntity<byte[]> response) {
        return jsonMapper.readTree(response.getBody());
    }

    private static boolean signedByFcc(ResponseEntity<byte[]> response) {
        String ts = response.getHeaders().getFirst("X-Timestamp");
        String sig = response.getHeaders().getFirst("X-Signature");
        return ts != null && PartnerSignature.matches(SECRET, "FCC", ts, response.getBody(), sig);
    }

    private void ebankReturns(int status, String body) {
        when(gatewayClient.call(any(), eq("FCC"), any(), eq("req-1")))
                .thenReturn(Mono.just(new DownstreamResponse(status, body.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void successPassesEbankBodyThroughAndSignsResponse() {
        ebankReturns(200, "{\"customer\":{\"cif\":\"CIF0000000001\"}}");

        ResponseEntity<byte[]> response = send();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode body = json(response);
        assertThat(body.get("status").asString()).isEqualTo("SUCCESS");
        assertThat(body.get("transactionKey").asString()).isEqualTo("FCC-0001");
        assertThat(body.get("requestId").asString()).isEqualTo("req-1");
        assertThat(body.get("responseBody").get("customer").get("cif").asString()).isEqualTo("CIF0000000001");
        assertThat(signedByFcc(response)).isTrue();

        ArgumentCaptor<ForwarderLog> saved = ArgumentCaptor.forClass(ForwarderLog.class);
        verify(logs, atLeastOnce()).save(saved.capture());
        ForwarderLog last = saved.getValue();
        assertThat(last.getPartnerId()).isEqualTo("FCC");
        assertThat(last.getTransactionKey()).isEqualTo("FCC-0001");
        assertThat(last.getStatus()).isEqualTo("SUCCESS");
        assertThat(last.getHttpStatus()).isEqualTo(200);
    }

    @Test
    void ebankBusinessErrorIsPassedThroughWithItsStatus() {
        ebankReturns(400, "{\"code\":\"CUSTOMER_NOT_FOUND\",\"errorCode\":\"HYD-37-005\"}");

        ResponseEntity<byte[]> response = send();

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(json(response).get("status").asString()).isEqualTo("FAILED");
        assertThat(json(response).get("responseBody").get("errorCode").asString()).isEqualTo("HYD-37-005");
        assertThat(signedByFcc(response)).isTrue();

        ArgumentCaptor<ForwarderLog> saved = ArgumentCaptor.forClass(ForwarderLog.class);
        verify(logs, atLeastOnce()).save(saved.capture());
        assertThat(saved.getValue().getErrorCode()).isEqualTo("HYD-37-005");
    }

    @Test
    void gatewayRejectingForwarderIsHiddenAsDownstreamRejected() {
        ebankReturns(401, "{\"code\":\"UNAUTHORIZED\",\"errorCode\":\"HYD-00-001\"}");

        ResponseEntity<byte[]> response = send();

        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(json(response).get("responseBody").get("errorCode").asString()).isEqualTo("HYD-40-010");
        assertThat(json(response).get("responseBody").get("code").asString()).isEqualTo("SERVER_ERROR");
    }

    @Test
    void gatewayUnreachableIsServiceUnavailable() {
        when(gatewayClient.call(any(), any(), any(), any())).thenReturn(Mono.error(
                new ForwarderException(ForwarderErrorCode.SERVICE_UNAVAILABLE, "connection refused")));

        ResponseEntity<byte[]> response = send();

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(json(response).get("responseBody").get("errorCode").asString()).isEqualTo("HYD-40-008");
        assertThat(signedByFcc(response)).isTrue();
    }

    @Test
    void wrongSignatureIsUnauthorizedAndNotSigned() {
        ResponseEntity<byte[]> response = send("FCC", TS, "wrong-secret", BODY);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(json(response).get("responseBody").get("errorCode").asString()).isEqualTo("HYD-40-002");
        assertThat(json(response).get("responseBody").get("message").get("vi").asString()).isNotBlank();
        assertThat(response.getHeaders().getFirst("X-Signature")).isNull();
        verify(gatewayClient, never()).call(any(), any(), any(), any());
    }

    @Test
    void unknownPartnerIsUnauthorized() {
        assertThat(send("AI", TS, SECRET, BODY).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void missingHeadersAreUnauthorized() {
        ResponseEntity<byte[]> response = service.forward(new InboundRequest(null, null, null, "req-1",
                BODY.getBytes(StandardCharsets.UTF_8))).block();

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void oldTimestampIsRequestExpired() {
        String old = String.valueOf(NOW.getEpochSecond() - 301);

        ResponseEntity<byte[]> response = send("FCC", old, SECRET, BODY);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(json(response).get("responseBody").get("code").asString()).isEqualTo("REQUEST_EXPIRED");
        assertThat(signedByFcc(response)).isTrue();
    }

    @Test
    void tamperedBodyIsUnauthorized() {
        byte[] signedBody = BODY.getBytes(StandardCharsets.UTF_8);
        String signature = PartnerSignature.sign(SECRET, "FCC", TS, signedBody);
        byte[] tampered = BODY.replace("CIF0000000001", "CIF0000000002").getBytes(StandardCharsets.UTF_8);

        ResponseEntity<byte[]> response = service.forward(
                new InboundRequest("FCC", TS, signature, "req-1", tampered)).block();

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void apiNotGrantedIsForbidden() {
        ResponseEntity<byte[]> response = send("FCC", TS, SECRET,
                BODY.replace("CUSTOMER_DETAIL", "TRANSFER_CREATE"));

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(json(response).get("responseBody").get("code").asString()).isEqualTo("API_NOT_ALLOWED");
    }

    @Test
    void grantedButInactiveApiIsNotFound() {
        when(permissions.existsByPartnerIdAndApiId("FCC", "OLD_API")).thenReturn(Mono.just(true));

        ResponseEntity<byte[]> response = send("FCC", TS, SECRET, BODY.replace("CUSTOMER_DETAIL", "OLD_API"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(json(response).get("responseBody").get("code").asString()).isEqualTo("API_NOT_FOUND");
    }

    @Test
    void malformedEnvelopeIsValidationError() {
        for (String body : List.of("not json", "{\"transactionKey\":\"FCC-0001\"}",
                "{\"apiId\":\"CUSTOMER_DETAIL\",\"transactionKey\":\"bad key with spaces\"}")) {
            ResponseEntity<byte[]> response = send("FCC", TS, SECRET, body);

            assertThat(response.getStatusCode().value()).as(body).isEqualTo(400);
            assertThat(json(response).get("responseBody").get("code").asString()).isEqualTo("VALIDATION_ERROR");
        }
    }

    @Test
    void reusedTransactionKeyIsDuplicate() {
        when(logs.save(any(ForwarderLog.class))).thenReturn(Mono.error(new DuplicateKeyException("uk_partner_txn")));

        ResponseEntity<byte[]> response = send();

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(json(response).get("responseBody").get("code").asString()).isEqualTo("DUPLICATE_TRANSACTION");
        verify(gatewayClient, never()).call(any(), any(), any(), any());
    }
}
