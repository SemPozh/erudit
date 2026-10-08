package com.erudit.quiz.model;

import java.time.Instant;
import java.util.UUID;

public record QuizAttempt(
        UUID id,
        UUID quizId,
        String userId,
        Instant startedAt,
        Instant submittedAt,
        Integer correctAnswers,
        Integer score,
        Integer experience
) {
    public QuizAttempt {
        if (id == null || quizId == null || userId == null || startedAt == null) {
            throw new IllegalArgumentException("Attempt identity and start time are required");
        }
    }

    public boolean submitted() {
        return submittedAt != null;
    }
}