package com.erudit.quiz;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class QuizAttemptRepository {

    private final JdbcTemplate jdbc;

    public QuizAttemptRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void create(QuizAttempt a) {
        jdbc.update("""
                INSERT INTO quiz_attempts (id, quiz_id, user_id, started_at)
                VALUES (?, ?, ?, ?)
                """,
                a.id(), a.quizId(), a.userId(), Timestamp.from(a.startedAt()));
    }

    public Optional<QuizAttempt> findById(UUID id) {
        return jdbc.query("""
                SELECT id, quiz_id, user_id, started_at, submitted_at,
                       correct_answers, score, experience
                FROM quiz_attempts WHERE id = ?
                """,
                (rs, i) -> new QuizAttempt(
                        rs.getObject("id", UUID.class),
                        rs.getObject("quiz_id", UUID.class),
                        rs.getString("user_id"),
                        rs.getTimestamp("started_at").toInstant(),
                        rs.getTimestamp("submitted_at") == null
                                ? null : rs.getTimestamp("submitted_at").toInstant(),
                        (Integer) rs.getObject("correct_answers"),
                        (Integer) rs.getObject("score"),
                        (Integer) rs.getObject("experience")),
                id).stream().findFirst();
    }

    /** Returns false if the attempt was already submitted (or does not exist). */
    public boolean submit(QuizAttempt a, List<QuizAttemptAnswer> answers) {
        int updated = jdbc.update("""
                UPDATE quiz_attempts
                SET submitted_at = ?, correct_answers = ?, score = ?, experience = ?
                WHERE id = ? AND submitted_at IS NULL
                """,
                Timestamp.from(a.submittedAt()), a.correctAnswers(), a.score(),
                a.experience(), a.id());
        if (updated == 0) {
            return false;
        }
        jdbc.batchUpdate("""
                INSERT INTO quiz_attempt_answers (attempt_id, question_id, answer_id, correct)
                VALUES (?, ?, ?, ?)
                """,
                answers, answers.size(),
                (ps, x) -> {
                    ps.setObject(1, x.attemptId());
                    ps.setObject(2, x.questionId());
                    ps.setObject(3, x.answerId());
                    ps.setBoolean(4, x.correct());
                });
        return true;
    }
}