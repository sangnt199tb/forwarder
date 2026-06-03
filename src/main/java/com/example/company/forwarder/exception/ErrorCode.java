package com.example.company.forwarder.exception;

public interface ErrorCode {
    String SUCCESS = "0";
    String ERROR = "1";
    String INTERNAL_SERVER_ERROR = "HYD-40-001";
    String FILE_NAME_ERROR = "HYD-40-002";
    String FILE_TYPE_ERROR = "HYD-40-003";
    String FILE_NOT_FOUND="HYD-40-004";
    String FACE_MATCH_FAILED = "HYD-40-005";
    String TIME_OUT = "HYD-40-006";
    String FILE_REQUEST_IN_VALID = "HYD-40-007";
}
