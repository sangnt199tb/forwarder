package com.example.company.forwarder.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;

@Data
@Table("forwarder_log")
public class ForwarderLog {
    @Id
    private Long id;
    private String transactionKey;
    private String apiId;
    private LocalDateTime createdAt;
}
