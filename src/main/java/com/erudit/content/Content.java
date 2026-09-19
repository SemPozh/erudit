package com.erudit.content;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Content(
        UUID id,
        UUID categoryId,
        ContentType type,
        String title,
        String description,
        String body,
        String mediaUrl,
        Difficulty difficulty,
        int estimatedMinutes,
        String authorId,
        ContentStatus status,
        Instant createdAt,
        List<String> tags) {
}
