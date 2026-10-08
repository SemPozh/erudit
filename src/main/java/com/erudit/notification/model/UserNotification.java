package com.erudit.notification.model;
import java.time.Instant;
import java.util.UUID;
public record UserNotification(UUID id, UUID userId, NotificationType type, String title, String body,
                               Instant readAt, Instant createdAt) {
    public boolean read() { return readAt != null; }
}
