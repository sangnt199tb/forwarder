package com.example.company.forwarder.exception;

import lombok.Data;
import org.apache.commons.lang.builder.ToStringBuilder;

@Data
public class Response {
    private int responseCode;
    private int requestCode;
    private ErrorObject errorMessage;

    @Override
    public String toString(){
        return ToStringBuilder.reflectionToString(this);
    }

}
