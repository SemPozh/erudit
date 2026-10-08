package com.erudit.web.exception;

import com.erudit.user.exception.EmailAlreadyExistsException;
import com.erudit.user.exception.ExpiredVerificationTokenException;
import com.erudit.user.exception.InvalidVerificationTokenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class UserExceptionHandler {

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> conflict(EmailAlreadyExistsException ex) {
        return body(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", ex.getMessage());
    }

    @ExceptionHandler(InvalidVerificationTokenException.class)
    public ResponseEntity<Map<String, Object>> notFound(InvalidVerificationTokenException ex) {
        return body(HttpStatus.NOT_FOUND, "INVALID_VERIFICATION_TOKEN", ex.getMessage());
    }

    @ExceptionHandler(ExpiredVerificationTokenException.class)
    public ResponseEntity<Map<String, Object>> gone(ExpiredVerificationTokenException ex) {
        return body(HttpStatus.GONE, "EXPIRED_VERIFICATION_TOKEN", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .orElse("Validation failed");
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(Map.of("error", Map.of("code", code, "message", message)));
    }
}