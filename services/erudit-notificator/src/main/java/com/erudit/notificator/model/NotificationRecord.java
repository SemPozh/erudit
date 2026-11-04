package com.erudit.notificator.model;
import java.time.OffsetDateTime;
import java.util.UUID;
public record NotificationRecord(UUID id, UUID userId, String templateCode, String title, String body,
                                 boolean read, OffsetDateTime createdAt) {}
