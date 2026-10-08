package com.erudit.events.dto;

import com.erudit.events.model.EventType;

public record EventPublication(EventType eventType, String userId, String sessionId, String payload) {
    public EventPublication {
        if (eventType == null) throw new IllegalArgumentException("eventType is required");
        if (sessionId == null || sessionId.isBlank()) throw new IllegalArgumentException("sessionId is required");
        if (payload == null || payload.isBlank()) throw new IllegalArgumentException("payload is required");
    }
}
