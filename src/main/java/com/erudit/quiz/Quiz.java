package com.erudit.quiz;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Quiz(UUID id, UUID contentId, String title, Instant createdAt, List<QuizQuestion> questions) {
    public Quiz {
        if (id == null || contentId == null || createdAt == null) {
            throw new IllegalArgumentException("Quiz identity, content and creation time are required");
        }
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Quiz title is required");
        questions = List.copyOf(questions);
        if (questions.isEmpty()) throw new IllegalArgumentException("Quiz must contain at least one question");
    }
}
