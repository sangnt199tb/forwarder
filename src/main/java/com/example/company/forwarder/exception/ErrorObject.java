package com.example.company.forwarder.exception;

import lombok.Data;

@Data
public class ErrorObject {
    private String errorCode;
    private String errorDesc;
    private ErrorMessage messages;
}
