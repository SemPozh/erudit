package com.erudit.notification.dto;

import com.erudit.notification.model.UserNotification;
import java.util.List;
public record UserNotificationPage(List<UserNotification> items, long total, int page, int size) {
    public int totalPages() { return (int) Math.ceil((double) total / size); }
}
