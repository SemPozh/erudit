package com.erudit.notification.dto;

import com.erudit.notification.model.NotificationTemplate;

import java.util.List;

public record NotificationTemplatePage(List<NotificationTemplate> items, long total, int page, int size) {
    public int totalPages() {
        return (int) Math.ceil((double) total / size);
    }
}
