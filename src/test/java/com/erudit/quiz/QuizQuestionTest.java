package com.erudit.quiz;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuizQuestionTest {
    @Test
    void requiresExactlyOneCorrectAnswer() {
        assertThatThrownBy(() -> question(false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one");
        assertThatThrownBy(() -> question(true, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one");
    }

    @Test
    void requiresAtLeastTwoAnswers() {
        assertThatThrownBy(() -> new QuizQuestion(UUID.randomUUID(), 0, "Question", null,
                List.of(new QuizAnswerOption(UUID.randomUUID(), 0, "Only", true))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least two");
    }

    private QuizQuestion question(boolean firstCorrect, boolean secondCorrect) {
        return new QuizQuestion(UUID.randomUUID(), 0, "Question", null, List.of(
                new QuizAnswerOption(UUID.randomUUID(), 0, "First", firstCorrect),
                new QuizAnswerOption(UUID.randomUUID(), 1, "Second", secondCorrect)));
    }
}
