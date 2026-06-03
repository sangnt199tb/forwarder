package com.example.company.forwarder.dto;

import lombok.Data;

@Data
public class RequestEnvelope {
    private String apiId;
    private String checkSum;
    private String transactionKey;
    private Object body;
}
