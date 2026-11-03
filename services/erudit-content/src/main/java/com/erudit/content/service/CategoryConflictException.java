package com.erudit.content.service;

public class CategoryConflictException extends RuntimeException {
    public CategoryConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
