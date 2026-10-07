package com.erudit.quiz;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class QuizHintRepository {

    private final JdbcTemplate jdbc;

    public QuizHintRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> findFact(UUID quizCardId) {
        return jdbc.query("SELECT fact FROM quiz_cards WHERE id = ?",
                (rs, i) -> rs.getString("fact"), quizCardId).stream().findFirst();
    }

    /** Records that a hint was used. Asking again for the same question is a no-op. */
    public void record(UUID attemptId, UUID questionId, Instant usedAt) {
        try {
            jdbc.update("""
                    INSERT INTO quiz_attempt_hints (attempt_id, question_id, used_at)
                    VALUES (?, ?, ?)
                    """, attemptId, questionId, Timestamp.from(usedAt));
        } catch (DuplicateKeyException ignored) {
            // hint for this question was already used in this attempt
        }
    }

    public int countForAttempt(UUID attemptId) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM quiz_attempt_hints WHERE attempt_id = ?",
                Integer.class, attemptId);
        return n == null ? 0 : n;
    }
}