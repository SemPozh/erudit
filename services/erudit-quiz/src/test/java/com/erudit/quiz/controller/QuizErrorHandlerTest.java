package com.erudit.quiz.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QuizErrorHandlerTest {
    private final QuizErrorHandler handler = new QuizErrorHandler();

    @Test
    void mapsValidationAndConflictErrors() {
        var bad = handler.bad(new IllegalArgumentException("invalid"));
        assertThat(bad.getStatusCode().value()).isEqualTo(400);
        assertThat(bad.getBody()).containsEntry("message", "invalid");

        var conflict = handler.conflict(new IllegalStateException("already completed"));
        assertThat(conflict.getStatusCode().value()).isEqualTo(409);
        assertThat(conflict.getBody()).containsEntry("message", "already completed");
    }
}
