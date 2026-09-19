package com.erudit.content;

import java.time.Instant;
import java.util.UUID;

public record ContentReport(UUID id, UUID contentId, String reason, Instant createdAt) {
}
