package com.erudit.quiz;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

final class QuizTestFixture {
    private QuizTestFixture() {}

    static StoredContent contentWithCard(JdbcTemplate jdbc) {
        UUID categoryId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        jdbc.update("INSERT INTO categories (id, name) VALUES (?, ?)", categoryId, "Quiz " + categoryId);
        jdbc.update("""
                INSERT INTO content (id, category_id, type, title, description, body, difficulty,
                    estimated_minutes, author_id, status, created_at, premium_locked)
                VALUES (?, ?, 'ARTICLE', 'Quiz content', '', '', 'BEGINNER', 5, 'author', 'PUBLISHED', ?, FALSE)
                """, contentId, categoryId, Timestamp.from(Instant.now()));
        jdbc.update("""
                INSERT INTO quiz_cards (id, content_id, position, fact, question, correct_answer)
                VALUES (?, ?, 0, 'Earth is round', 'What shape is Earth?', 'Round')
                """, cardId, contentId);
        return new StoredContent(contentId, cardId);
    }

    static Quiz quiz(StoredContent content) {
        return new Quiz(UUID.randomUUID(), content.contentId(), "Knowledge check",
                Instant.now().truncatedTo(ChronoUnit.MILLIS), List.of(
                new QuizQuestion(UUID.randomUUID(), 0, "What shape is Earth?", content.cardId(), List.of(
                        new QuizAnswerOption(UUID.randomUUID(), 0, "Round", true),
                        new QuizAnswerOption(UUID.randomUUID(), 1, "Square", false)))));
    }

    record StoredContent(UUID contentId, UUID cardId) {}
}
