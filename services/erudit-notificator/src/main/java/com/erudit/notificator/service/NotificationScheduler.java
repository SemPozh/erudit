package com.erudit.notificator.service;

import com.erudit.integration.InternalContracts.AnalyticsEvent;
import com.erudit.notificator.client.AnalyticsClient;
import com.erudit.notificator.repository.DeliveryRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class NotificationScheduler {
    private final DeliveryRepository repository;
    private final MockNotificationSender sender;
    private final AnalyticsClient analytics;

    @Scheduled(cron = "${erudit.notifications.trigger-cron:0 */5 * * * *}")
    public void deliver() {
        repository.pending(100).forEach(n -> {
            var attempt = sender.send(n, OffsetDateTime.now(ZoneOffset.UTC));
            if ("MOCK_DELIVERED".equals(attempt.status())) publish(n.userId(), n.id());
        });
    }

    private void publish(UUID user, UUID notification) {
        var event = new AnalyticsEvent(UUID.randomUUID(), user, "NOTIFICATION_MOCK_DELIVERED",
                OffsetDateTime.now(ZoneOffset.UTC), Map.of("notificationId", notification));
        analytics.publish(event).onErrorResume(error -> Mono.empty()).subscribe();
    }
}