package com.erudit.content.model;

import java.time.Instant;
import java.util.UUID;

public record ContentProgress(String userId, UUID contentId, ContentProgressStatus status,
                              Instant viewedAt, Instant completedAt) {
    public ContentProgress {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId is required");
        if (contentId == null) throw new IllegalArgumentException("contentId is required");
        if (status == null || viewedAt == null) throw new IllegalArgumentException("status and viewedAt are required");
        if (status == ContentProgressStatus.COMPLETED && completedAt == null) {
            throw new IllegalArgumentException("completedAt is required for completed progress");
        }
    }
}
