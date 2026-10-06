package com.erudit.quiz;

import java.util.UUID;

public record QuizAnswerOption(UUID id, int position, String text, boolean correct) {
    public QuizAnswerOption {
        if (id == null) throw new IllegalArgumentException("Answer id is required");
        if (position < 0) throw new IllegalArgumentException("Answer position cannot be negative");
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Answer text is required");
    }
}
