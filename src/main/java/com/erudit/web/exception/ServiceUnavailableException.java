package com.erudit.web.exception;

import org.springframework.http.HttpStatus;

public final class ServiceUnavailableException extends ApiException {
    public ServiceUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}

