package com.example.company.forwarder.persistence.repository;

import com.example.company.forwarder.persistence.domain.GatewayRouteConfig;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface RouteConfigRepository extends ReactiveCrudRepository<GatewayRouteConfig, Long> {

    Mono<GatewayRouteConfig> findByApiIdAndIsActiveTrue(String apiId);
}
