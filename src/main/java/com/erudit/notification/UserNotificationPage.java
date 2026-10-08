package com.erudit.notification;
import java.util.List;
public record UserNotificationPage(List<UserNotification> items, long total, int page, int size) {
    public int totalPages() { return (int) Math.ceil((double) total / size); }
}
