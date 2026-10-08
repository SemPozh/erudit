package com.erudit.notification;
import com.erudit.user.service.DevicePushTokenService;
import org.slf4j.Logger; import org.slf4j.LoggerFactory; import org.springframework.stereotype.Component;
import java.util.UUID;
@Component
public class PushNotificationTransport implements NotificationTransport {
    private static final Logger log = LoggerFactory.getLogger(PushNotificationTransport.class);
    private final DevicePushTokenService tokens;
    public PushNotificationTransport(DevicePushTokenService tokens) { this.tokens = tokens; }
    public NotificationChannel channel() { return NotificationChannel.PUSH; }
    public void send(UUID userId, String title, String body) {
        var active = tokens.activeTokens(userId);
        if (active.isEmpty()) throw new IllegalStateException("No active push tokens");
        active.forEach(token -> log.info("Push notification sent to device {}", token.getId()));
    }
}
