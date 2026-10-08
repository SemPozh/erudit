package com.erudit.content.dto;

import com.erudit.content.model.Content;

import java.util.List;

public record ContentPage(List<Content> items, long total, int page, int size) {
    public int totalPages() {
        return (int) Math.ceil((double) total / size);
    }
}
