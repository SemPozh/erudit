package com.erudit.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(T data, ApiErrorResponse error, PageMetadataResponse pagination) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, null, null);
    }

    public static <T> ApiResponse<T> page(T data, PageMetadataResponse pagination) {
        return new ApiResponse<>(data, null, pagination);
    }

    public static ApiResponse<Void> failure(String code, String message) {
        return new ApiResponse<>(null, new ApiErrorResponse(code, message), null);
    }
}
