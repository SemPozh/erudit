package com.erudit.eventreceiver.model;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record EventCommand(UUID eventId, UUID userId, String eventType, OffsetDateTime occurredAt,
                           Map<String, Object> payload) {}
