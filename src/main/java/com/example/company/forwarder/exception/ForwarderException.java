package com.example.company.forwarder.exception;

public class ForwarderException extends TpbException{
    public ForwarderException(){
        super();
    }

    public ForwarderException(String errorCode){
        super(errorCode);
    }

    public ForwarderException(String errorCode, String additionErrorCode, String additionInfo){
        super(errorCode, additionErrorCode, additionInfo);
    }

    public ForwarderException(String errorCode, String replaceStr){
        super(errorCode);
        setResponse(ErrorHelper.buildResponseWithoutDesc(errorCode, replaceStr));
    }

    public ForwarderException(Response response){
        super(response.getErrorMessage().getErrorCode());
        setResponse(response);
    }
}
