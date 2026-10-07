package com.erudit.payment;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionMaintenanceServiceTest {
    @Test
    void expiresEndedSubscriptionsAndNotifiesUpcomingExpiryOnce() {
        Instant now = Instant.parse("2026-10-06T12:00:00Z");
        SubscriptionRepository repository = mock(SubscriptionRepository.class);
        SubscriptionExpiryNotifier notifier = mock(SubscriptionExpiryNotifier.class);
        Subscription expiring = new Subscription(UUID.randomUUID(), UUID.randomUUID(), "PREMIUM_MONTHLY",
                SubscriptionStatus.ACTIVE, now.minusSeconds(60), now.plusSeconds(3600));
        when(repository.findExpiringBetween(now, now.plusSeconds(3 * 24 * 3600L)))
                .thenReturn(List.of(expiring));
        when(repository.claimExpiryNotification(expiring.id(), now)).thenReturn(true);

        new SubscriptionMaintenanceService(repository, notifier,
                Clock.fixed(now, ZoneOffset.UTC)).maintain();

        verify(repository).expireEnded(now);
        verify(notifier).notifyExpiring(expiring);
    }
}
