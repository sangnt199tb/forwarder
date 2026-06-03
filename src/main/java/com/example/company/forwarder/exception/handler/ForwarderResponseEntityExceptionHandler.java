package com.example.company.forwarder.exception.handler;

import com.example.company.forwarder.exception.ErrorHelper;
import com.example.company.forwarder.exception.ForwarderNotFoundException;
import com.example.company.forwarder.exception.Response;
import com.example.company.forwarder.exception.TpbException;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
@RestController
@Order(value = PriorityOrdered.HIGHEST_PRECEDENCE)
public class ForwarderResponseEntityExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(value = {Throwable.class})
    protected ResponseEntity<Response> handlerTpbExp(Throwable ex, WebRequest request){
        HttpHeaders headers = new HttpHeaders();

        if(ex instanceof ForwarderNotFoundException){
            ForwarderNotFoundException notFoundException = (ForwarderNotFoundException) ex;
            return new ResponseEntity<>(notFoundException.getResponse(), headers, HttpStatus.NOT_FOUND);
        }

        if(ex instanceof TpbException){
            TpbException tpbException = (TpbException) ex;
            String message = tpbException.getMessage() == null ? tpbException.getResponse().toString() : tpbException.getMessage();
            return new ResponseEntity<>(tpbException.getResponse(), headers, HttpStatus.BAD_GATEWAY);
        }

        return new ResponseEntity<>(ErrorHelper.buildInternalServerError(), headers, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
