package com.erudit.notification;

import java.util.Set;
import java.util.UUID;

public record NotificationTemplate(UUID id, NotificationType type, NotificationChannel channel,
                                   String title, String body, Set<String> parameters) {
}
