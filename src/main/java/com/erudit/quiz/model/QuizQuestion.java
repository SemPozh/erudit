package com.erudit.quiz.model;

import java.util.List;
import java.util.UUID;

public record QuizQuestion(UUID id, int position, String text, UUID quizCardId,
                           List<QuizAnswerOption> answers) {
    public QuizQuestion {
        if (id == null) throw new IllegalArgumentException("Question id is required");
        if (position < 0) throw new IllegalArgumentException("Question position cannot be negative");
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Question text is required");
        answers = List.copyOf(answers);
        if (answers.size() < 2) throw new IllegalArgumentException("Question must contain at least two answers");
        if (answers.stream().filter(QuizAnswerOption::correct).count() != 1) {
            throw new IllegalArgumentException("Question must contain exactly one correct answer");
        }
    }
}
