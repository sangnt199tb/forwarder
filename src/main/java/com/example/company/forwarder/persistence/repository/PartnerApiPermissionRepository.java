package com.example.company.forwarder.persistence.repository;

import com.example.company.forwarder.persistence.domain.PartnerApiPermission;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface PartnerApiPermissionRepository extends ReactiveCrudRepository<PartnerApiPermission, Long> {

    Mono<Boolean> existsByPartnerIdAndApiId(String partnerId, String apiId);
}
