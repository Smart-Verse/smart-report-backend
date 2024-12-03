package com.smartverse.smartreportbackend.config.exceptions;


import com.potatotech.authorization.exception.ServiceException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDate;
import java.util.LinkedHashMap;

@ControllerAdvice
public class ExceptionHandlerImpl {

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<?> handlerException(ServiceException exception){
        var ret = new LinkedHashMap<>();
        ret.put("message",exception.getMessage());
        ret.put("status", exception.getStatus().value());
        ret.put("timestamp", LocalDate.now());
        return new ResponseEntity<>(ret, exception.getStatus());
    }
}
