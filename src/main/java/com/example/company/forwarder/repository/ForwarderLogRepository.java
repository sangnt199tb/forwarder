package com.example.company.forwarder.repository;

import com.example.company.forwarder.model.ForwarderLog;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface ForwarderLogRepository extends ReactiveCrudRepository<ForwarderLog, Long> {
}
