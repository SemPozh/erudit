package com.erudit.observability;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ServletTechnicalErrors extends ResponseEntityExceptionHandler {
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception error, Object body, HttpHeaders headers,
                                                              HttpStatusCode status, WebRequest request) {
        return new ResponseEntity<>(TechnicalError.of(status.value(), error instanceof HttpMessageNotReadableException),
                headers, status);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> unexpected(Exception error) {
        int status = error instanceof ConstraintViolationException ? 400
                : error instanceof ErrorResponse response ? response.getStatusCode().value() : 500;
        return ResponseEntity.status(status).body(TechnicalError.of(status, false));
    }
}
