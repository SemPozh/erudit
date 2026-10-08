package com.erudit.competition.model;

import java.time.Instant;
import java.util.UUID;

public record Competition(
        UUID id,
        String title,
        UUID quizId,
        String creatorId,
        String rules,
        Instant startsAt,
        Instant endsAt,
        Instant createdAt
) {
    public Competition {
        if (id == null || quizId == null || creatorId == null
                || startsAt == null || endsAt == null || createdAt == null) {
            throw new IllegalArgumentException("Competition identity, quiz, creator and times are required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Competition title is required");
        }
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("Competition must end after it starts");
        }
    }
}