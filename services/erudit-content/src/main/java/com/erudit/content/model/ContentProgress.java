package com.erudit.content.model;
import java.time.OffsetDateTime;
import java.util.UUID;
public record ContentProgress(UUID userId, UUID contentId, String status, OffsetDateTime viewedAt, OffsetDateTime completedAt) {}
