package com.example.company.forwarder.persistence.repository;

import com.example.company.forwarder.persistence.domain.ForwarderLog;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface ForwarderLogRepository extends ReactiveCrudRepository<ForwarderLog, Long> {
}
