package com.erudit.notification;
import java.util.UUID;
public interface NotificationTransport {
    NotificationChannel channel();
    void send(UUID userId, String title, String body);
}
