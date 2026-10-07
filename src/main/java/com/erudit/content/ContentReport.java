package com.erudit.content;

import java.time.Instant;
import java.util.UUID;

public record ContentReport(UUID id, UUID contentId, String reason, ContentReportStatus status,
                            Instant createdAt, ReportDecision decision, String resolutionComment,
                            Instant resolvedAt, String resolvedBy) {
    public ContentReport(UUID id, UUID contentId, String reason, Instant createdAt) {
        this(id, contentId, reason, ContentReportStatus.OPEN, createdAt, null, null, null, null);
    }
}
