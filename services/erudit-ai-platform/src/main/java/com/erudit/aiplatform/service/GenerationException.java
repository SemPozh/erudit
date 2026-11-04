package com.erudit.aiplatform.service;

public class GenerationException extends RuntimeException {
    private final String code;

    public GenerationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() { return code; }
}
