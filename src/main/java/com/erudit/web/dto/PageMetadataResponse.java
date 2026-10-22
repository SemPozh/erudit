package com.erudit.web.dto;

public record PageMetadataResponse(int page, int size, long totalElements, int totalPages) {
}
