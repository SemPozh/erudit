package com.erudit.content;

import java.util.List;

public record ContentReportPage(List<ContentReport> items, long total, int page, int size) {
    public int totalPages() {
        return (int) Math.ceil((double) total / size);
    }
}
