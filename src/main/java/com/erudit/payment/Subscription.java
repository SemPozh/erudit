package com.erudit.payment;

import java.time.Instant;
import java.util.UUID;

public record Subscription(UUID id, UUID userId, String planCode, SubscriptionStatus status,
                           Instant startDate, Instant endDate) {
    public Subscription {
        if (id == null) throw new IllegalArgumentException("Subscription id is required");
        if (userId == null) throw new IllegalArgumentException("Subscription user is required");
        if (planCode == null || planCode.isBlank()) throw new IllegalArgumentException("Subscription plan is required");
        if (status == null) throw new IllegalArgumentException("Subscription status is required");
        if (startDate == null || endDate == null || !endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("Subscription end date must be after its start date");
        }
    }

    public boolean isActiveAt(Instant instant) {
        return status == SubscriptionStatus.ACTIVE
                && !instant.isBefore(startDate)
                && instant.isBefore(endDate);
    }

    public Subscription expire() {
        return new Subscription(id, userId, planCode, SubscriptionStatus.EXPIRED, startDate, endDate);
    }
}
