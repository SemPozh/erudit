package com.erudit.notification.service;

import com.erudit.notification.model.NotificationChannel;
import java.util.UUID;
public interface NotificationTransport {
    NotificationChannel channel();
    void send(UUID userId, String title, String body);
}
