package com.example.company.forwarder.persistence.repository;

import com.example.company.forwarder.persistence.domain.Partner;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface PartnerRepository extends ReactiveCrudRepository<Partner, Long> {

    Mono<Partner> findByPartnerIdAndStatus(String partnerId, String status);
}
