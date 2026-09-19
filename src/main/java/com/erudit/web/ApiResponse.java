package com.erudit.web;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(T data, ApiError error, PageMetadata pagination) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, null, null);
    }

    public static <T> ApiResponse<T> page(T data, PageMetadata pagination) {
        return new ApiResponse<>(data, null, pagination);
    }

    public static ApiResponse<Void> failure(String code, String message) {
        return new ApiResponse<>(null, new ApiError(code, message), null);
    }
}
