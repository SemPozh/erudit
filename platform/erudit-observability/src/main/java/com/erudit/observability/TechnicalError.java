package com.erudit.observability;

import org.springframework.http.HttpStatus;

public record TechnicalError(Detail error) {
    public record Detail(String code, String message) { }
    public static TechnicalError of(int status, boolean malformed) {
        String code = malformed ? "MALFORMED_JSON" : switch (status) {
            case 400 -> "VALIDATION_ERROR";
            case 404 -> "NOT_FOUND";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 503, 504 -> "SERVICE_UNAVAILABLE";
            default -> status < 500 ? "INVALID_REQUEST" : "INTERNAL_ERROR";
        };
        HttpStatus httpStatus = HttpStatus.resolve(status);
        return new TechnicalError(new Detail(code, httpStatus == null ? "Request failed" : httpStatus.getReasonPhrase()));
    }
}
