package com.erudit.web;

import org.springframework.http.HttpStatus;

public final class ValidationException extends ApiException {
    public ValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
