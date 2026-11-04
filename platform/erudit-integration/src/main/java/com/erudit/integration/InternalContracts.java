package com.erudit.integration;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public final class InternalContracts {
    private InternalContracts() {}
    public record UserLookup(UUID id, String displayName, boolean active) {}
    public record Moderation(UUID contentId, String status, String reason) {}
    public record Notification(UUID userId, String templateCode, Map<String, String> variables) {}
    public record Entitlement(UUID userId, boolean premium, OffsetDateTime expiresAt) {}
    public record ContentLookup(UUID id, String title, String status) {}
    public record AnalyticsEvent(UUID eventId, UUID userId, String type, OffsetDateTime occurredAt,
                                 Map<String, Object> payload) {}
}
