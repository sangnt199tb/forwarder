package com.example.company.forwarder.controller;

import com.example.company.forwarder.dto.RequestEnvelope;
import com.example.company.forwarder.dto.ResponseEnvelope;
import com.example.company.forwarder.exception.ErrorCode;
import com.example.company.forwarder.exception.ForwarderException;
import com.example.company.forwarder.model.ForwarderLog;
import com.example.company.forwarder.model.GatewayRouteConfig;
import com.example.company.forwarder.repository.ForwarderLogRepository;
import com.example.company.forwarder.repository.RouteConfigRepository;
import com.example.company.forwarder.util.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1")
public class ForwarderController {

    Logger logger = LoggerFactory.getLogger(ForwarderController.class);

    private final String PARTNER_SECRET = "292cf2184dac080ff1f610fa62221496";

    private final RouteConfigRepository routeConfigRepository;
    private final WebClient webClient;
    private final ForwarderLogRepository logRepository;

    public ForwarderController(RouteConfigRepository routeConfigRepository, WebClient webClient, ForwarderLogRepository logRepository) {
        this.routeConfigRepository = routeConfigRepository;
        this.webClient = webClient;
        this.logRepository = logRepository;
    }

    @PostMapping("/forward")
    public Mono<ResponseEntity<Object>> handleForward(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody RequestEnvelope envelope) {

        // 1. VALIDATE CHECKSUM REQUEST
        String rawData = "OPENAPI" + envelope.getApiId() + envelope.getTransactionKey() + PARTNER_SECRET;
        String computedHash = HashUtil.calculateSHA256(rawData);

        if (!computedHash.equalsIgnoreCase(envelope.getCheckSum())) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(buildErrorEnvelope(envelope, "INVALID_CHECKSUM")));
        }

        // 2. SAVE LOG
        ForwarderLog log = new ForwarderLog();
        log.setTransactionKey(envelope.getTransactionKey());
        log.setApiId(envelope.getApiId());
        log.setCreatedAt(LocalDateTime.now());

        return logRepository.save(log)
                .then(routeConfigRepository.findByApiIdAndIsActiveTrue(envelope.getApiId()))
                .flatMap(routeConfig -> forwardToTarget(routeConfig, envelope, authHeader))
                .switchIfEmpty(Mono.defer(() -> {
                    return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(buildErrorEnvelope(envelope, "API_NOT_FOUND")));
                }));
    }

    private Mono<ResponseEntity<Object>> forwardToTarget(GatewayRouteConfig config, RequestEnvelope reqEnvelope, String authHeader) {
        var requestSpec = webClient.method(HttpMethod.valueOf(config.getHttpMethod()))
                .uri(config.getTargetUrl().trim())
                .header("X-Internal-Gateway-Secret", "Ebank@SecretKey2026")
                .bodyValue(reqEnvelope.getBody());

        if (authHeader != null && !authHeader.isEmpty()) {
            requestSpec.header("Authorization", authHeader);
        }

        return requestSpec.exchangeToMono(clientResponse ->
                clientResponse.toEntity(Object.class).map(responseEntity -> {

                    String status = responseEntity.getStatusCode().is2xxSuccessful() ? "SUCCESS" : "FAILED";
                    Object innerBody = responseEntity.getBody() != null ? responseEntity.getBody() : "{}";

                    ResponseEnvelope resEnvelope = new ResponseEnvelope();
                    resEnvelope.setTransactionKey(reqEnvelope.getTransactionKey());
                    resEnvelope.setStatus(status);
                    resEnvelope.setResponseBody(innerBody);

                    String rawResponseData = "RESPONSE" + reqEnvelope.getTransactionKey() + status + PARTNER_SECRET;
                    resEnvelope.setCheckSum(HashUtil.calculateSHA256(rawResponseData));

                    return ResponseEntity.status(responseEntity.getStatusCode()).body((Object) resEnvelope);
                })
        ).onErrorResume(error -> {
            logger.error("ForwarderController forwardToTarget with error detail: {}", error.getMessage());
            logger.error("ForwarderController forwardToTarget error call: {}", config.getTargetUrl());
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(buildErrorEnvelope(reqEnvelope, ErrorCode.INTERNAL_SERVER_ERROR.toString())));
        });
    }

    private ResponseEnvelope buildErrorEnvelope(RequestEnvelope reqEnvelope, String status) {
        ResponseEnvelope res = new ResponseEnvelope();
        res.setTransactionKey(reqEnvelope != null ? reqEnvelope.getTransactionKey() : "UNKNOWN");
        res.setStatus(status);
        res.setResponseBody(null);

        String raw = "RESPONSE" + res.getTransactionKey() + status + PARTNER_SECRET;
        res.setCheckSum(HashUtil.calculateSHA256(raw));
        return res;
    }
}
