package com.erudit.notification.service;

import com.erudit.notification.model.NotificationChannel;
import org.slf4j.Logger; import org.slf4j.LoggerFactory; import org.springframework.stereotype.Component;
import java.util.UUID;
@Component
public class EmailNotificationTransport implements NotificationTransport {
    private static final Logger log = LoggerFactory.getLogger(EmailNotificationTransport.class);
    public NotificationChannel channel() { return NotificationChannel.EMAIL; }
    public void send(UUID userId, String title, String body) { log.info("Email notification sent to user {}: {}", userId, title); }
}
