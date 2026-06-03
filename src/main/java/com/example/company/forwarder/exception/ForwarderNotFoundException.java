package com.example.company.forwarder.exception;

public class ForwarderNotFoundException extends TpbException{
    public ForwarderNotFoundException(String errorCode){
        super(errorCode);
    }
}
