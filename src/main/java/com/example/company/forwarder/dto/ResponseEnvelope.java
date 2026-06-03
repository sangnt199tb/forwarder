package com.example.company.forwarder.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

@Data
@JsonPropertyOrder({
        "status",
        "transactionKey",
        "checkSum",
        "responseBody"
})
public class ResponseEnvelope {
    private String transactionKey;
    private String status;
    private String checkSum;
    private Object responseBody;
}
