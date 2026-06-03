package com.example.company.forwarder.repository;

import com.example.company.forwarder.model.GatewayRouteConfig;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface RouteConfigRepository extends ReactiveCrudRepository<GatewayRouteConfig, Long> {

    // Tìm cấu hình đang active dựa theo apiId
    Mono<GatewayRouteConfig> findByApiIdAndIsActiveTrue(String apiId);
}