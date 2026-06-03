package com.example.company.forwarder.exception;

public class ErrorHelper {
    public static Response buildResponse(String errorCode){
        return buildResponse(errorCode, null);
    }

    public static Response buildResponse(String errorCode, String errorDesc){
        Response response = new Response();
        response.setErrorMessage(buildErrorObject(errorCode, errorDesc));
        System.out.println("response response: " + response);
        return response;
    }

    public static ErrorObject buildErrorObject(String errorCode, String errorDesc){
        ErrorObject error = new ErrorObject();
        error.setErrorCode(errorCode);
        error.setErrorDesc(errorDesc);
        error.setMessages(ErrorMessageLoader.getMessage(errorCode));
        return error;
    }

    public static Response buildInternalServerError(){
        return buildResponse(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    public static Response buildResponseWithoutDesc(String errorCode, String formatValue) {
        return buildResponse(errorCode, null, formatValue);
    }

    public static Response buildResponse(String errorCode, String errorDesc, String formatValue){
        ErrorMessage message = ErrorMessageLoader.getMessage(errorCode);
        ErrorMessage msg = new ErrorMessage();
        msg.setEn(String.format(message.getEn(), formatValue));
        msg.setVn(String.format(message.getVn(), formatValue));

        Response response = new Response();
        response.setErrorMessage(buildErrorObject(errorCode, errorDesc, msg));

        return response;
    }

    public static ErrorObject buildErrorObject(String errorCode, String errorDesc, ErrorMessage msg){
        ErrorObject errorObject = new ErrorObject();
        errorObject.setErrorCode(errorCode);
        errorObject.setErrorDesc(errorDesc);
        errorObject.setMessages(msg);
        return errorObject;
    }

}
