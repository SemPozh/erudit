package com.erudit.web.exception;

import org.springframework.http.HttpStatus;

public final class ForbiddenException extends ApiException {
    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
