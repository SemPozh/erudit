package com.erudit.quiz;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class QuizRepository {
    private final JdbcTemplate jdbc;

    public QuizRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void save(Quiz quiz) {
        validateLinkedCards(quiz);
        jdbc.update("INSERT INTO quizzes (id, content_id, title, created_at) VALUES (?, ?, ?, ?)",
                quiz.id(), quiz.contentId(), quiz.title(), Timestamp.from(quiz.createdAt()));
        for (QuizQuestion question : quiz.questions()) {
            jdbc.update("""
                    INSERT INTO quiz_questions (id, quiz_id, quiz_card_id, position, text)
                    VALUES (?, ?, ?, ?, ?)
                    """, question.id(), quiz.id(), question.quizCardId(), question.position(), question.text());
            for (QuizAnswerOption answer : question.answers()) {
                jdbc.update("""
                        INSERT INTO quiz_answer_options (id, question_id, position, text, correct)
                        VALUES (?, ?, ?, ?, ?)
                        """, answer.id(), question.id(), answer.position(), answer.text(), answer.correct());
            }
        }
    }

    public Optional<Quiz> findById(UUID id) {
        return jdbc.query("SELECT id, content_id, title, created_at FROM quizzes WHERE id = ?", (rs, row) ->
                new Quiz(rs.getObject("id", UUID.class), rs.getObject("content_id", UUID.class),
                        rs.getString("title"), rs.getTimestamp("created_at").toInstant(), findQuestions(id)), id)
                .stream().findFirst();
    }

    public List<Quiz> findByContentId(UUID contentId) {
        return jdbc.query("""
                SELECT id, content_id, title, created_at FROM quizzes
                WHERE content_id = ? ORDER BY created_at, id
                """, (rs, row) -> {
            UUID id = rs.getObject("id", UUID.class);
            return new Quiz(id, contentId, rs.getString("title"),
                    rs.getTimestamp("created_at").toInstant(), findQuestions(id));
        }, contentId);
    }

    private List<QuizQuestion> findQuestions(UUID quizId) {
        return jdbc.query("""
                SELECT id, quiz_card_id, position, text FROM quiz_questions
                WHERE quiz_id = ? ORDER BY position
                """, (rs, row) -> {
            UUID questionId = rs.getObject("id", UUID.class);
            return new QuizQuestion(questionId, rs.getInt("position"), rs.getString("text"),
                    rs.getObject("quiz_card_id", UUID.class), findAnswers(questionId));
        }, quizId);
    }

    private List<QuizAnswerOption> findAnswers(UUID questionId) {
        return jdbc.query("""
                SELECT id, position, text, correct FROM quiz_answer_options
                WHERE question_id = ? ORDER BY position
                """, (rs, row) -> new QuizAnswerOption(rs.getObject("id", UUID.class), rs.getInt("position"),
                rs.getString("text"), rs.getBoolean("correct")), questionId);
    }

    private void validateLinkedCards(Quiz quiz) {
        for (QuizQuestion question : quiz.questions()) {
            if (question.quizCardId() == null) continue;
            Integer count = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM quiz_cards WHERE id = ? AND content_id = ?
                    """, Integer.class, question.quizCardId(), quiz.contentId());
            if (count == null || count == 0) {
                throw new IllegalArgumentException("Quiz card must belong to the quiz content");
            }
        }
    }
}
