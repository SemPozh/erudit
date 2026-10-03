package com.erudit.payment;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionTest {
    private final Instant start = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void activeOnlyInsidePaidPeriod() {
        Subscription subscription = subscription(SubscriptionStatus.ACTIVE, start.plus(30, ChronoUnit.DAYS));

        assertThat(subscription.isActiveAt(start)).isTrue();
        assertThat(subscription.isActiveAt(start.plus(29, ChronoUnit.DAYS))).isTrue();
        assertThat(subscription.isActiveAt(start.plus(30, ChronoUnit.DAYS))).isFalse();
        assertThat(subscription.expire().isActiveAt(start.plus(1, ChronoUnit.DAYS))).isFalse();
    }

    @Test
    void rejectsEmptyOrReversedPeriod() {
        assertThatThrownBy(() -> subscription(SubscriptionStatus.ACTIVE, start))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("end date");
    }

    private Subscription subscription(SubscriptionStatus status, Instant end) {
        return new Subscription(UUID.randomUUID(), UUID.randomUUID(), "PREMIUM_MONTHLY", status, start, end);
    }
}
