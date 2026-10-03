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
    public AnalyticsEvent {
        if (eventId == null) throw new IllegalArgumentException("eventId is required");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt is required");
        eventType = EventTypeCatalog.require(eventType).value();
    }

    public EventGroup eventGroup() {
        return EventTypeCatalog.require(eventType).group();
    }
}
