package com.erudit.notificator;

import static org.mockito.Mockito.*;

import com.erudit.notificator.client.AnalyticsClient;
import com.erudit.notificator.model.DeliveryAttempt;
import com.erudit.notificator.model.NotificationRecord;
import com.erudit.notificator.repository.DeliveryRepository;
import com.erudit.notificator.service.MockNotificationSender;
import com.erudit.notificator.service.NotificationScheduler;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class NotificationSchedulerTest {
    @Test
    void publishesOnlySuccessfulDeliveriesAndIgnoresAnalyticsFailure() {
        var repository = mock(DeliveryRepository.class);
        var sender = mock(MockNotificationSender.class);
        var analytics = mock(AnalyticsClient.class);
        var delivered = notification();
        var skipped = notification();
        when(repository.pending(100)).thenReturn(List.of(delivered, skipped));
        when(sender.send(eq(delivered), any())).thenReturn(attempt(delivered, "MOCK_DELIVERED"));
        when(sender.send(eq(skipped), any())).thenReturn(attempt(skipped, "SKIPPED_DAILY_LIMIT"));
        when(analytics.publish(any())).thenReturn(Mono.error(new IllegalStateException("analytics unavailable")));

        new NotificationScheduler(repository, sender, analytics).deliver();

        verify(analytics).publish(argThat(event -> event.userId().equals(delivered.userId())
                && event.payload().get("notificationId").equals(delivered.id())));
        verifyNoMoreInteractions(analytics);
    }

    private static NotificationRecord notification() {
        return new NotificationRecord(UUID.randomUUID(), UUID.randomUUID(), "DEMO", "Title", "Body", false,
                OffsetDateTime.now());
    }

    private static DeliveryAttempt attempt(NotificationRecord notification, String status) {
        return new DeliveryAttempt(UUID.randomUUID(), notification.id(), 1, status, "preview", OffsetDateTime.now());
    }
}
