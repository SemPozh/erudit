package com.erudit.events;

import java.time.Instant;
import java.util.UUID;

public record AnalyticsEvent(
        UUID eventId,
        String userId,
        String sessionId,
        String eventType,
        Instant occurredAt,
        String payload) {
}
