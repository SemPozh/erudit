package com.erudit.aiplatform.controller;

import com.erudit.aiplatform.openapi.model.ErrorResponse;
import com.erudit.aiplatform.service.GenerationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GenerationExceptionHandler {
    @ExceptionHandler(GenerationException.class)
    ResponseEntity<ErrorResponse> generation(GenerationException exception) {
        HttpStatus status = switch (exception.code()) {
            case "AI_RATE_LIMIT" -> HttpStatus.TOO_MANY_REQUESTS;
            case "AI_TIMEOUT" -> HttpStatus.GATEWAY_TIMEOUT;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ResponseEntity.status(status).body(new ErrorResponse(exception.code(), exception.getMessage()));
    }
}
