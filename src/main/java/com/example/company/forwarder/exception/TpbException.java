package com.example.company.forwarder.exception;

import lombok.Getter;
import lombok.Setter;

public class TpbException extends RuntimeException{
    @Getter
    @Setter
    private Response response;

    @Getter
    @Setter
    private Object tagObject;

    @Getter
    @Setter
    private String additionErrorCode;

    @Getter
    @Setter
    private String additionInfo;

    public TpbException(){
        super();
        setResponse(new Response());
    }

    public TpbException(String errorCode){
        super(String.format("{%s, %s}", errorCode, ErrorMessageLoader.getMessage(errorCode)));
        System.out.println("loi loi: " + ErrorMessageLoader.getMessage(errorCode));
        setResponse(ErrorHelper.buildResponse(errorCode));
    }

    public TpbException(String errorCode, Object tag){
        this(errorCode);
        this.setTagObject(tag);
    }

    public TpbException(String errorCode, String additionErrorCode){
        this(errorCode);
        this.setAdditionErrorCode(additionErrorCode);
    }

    public TpbException(String errorCode, String additionErrorCode, String additionInfo){
        this(errorCode);
        this.setAdditionErrorCode(additionErrorCode);
        this.setAdditionInfo(additionInfo);
    }

}
