package com.erudit.assessment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AssessmentRepository {
    private final JdbcTemplate jdbc;

    public AssessmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AssessmentQuestion> findActiveQuestions() {
        return questions("""
                SELECT q.id question_id, q.topic, q.text question_text,
                       o.id option_id, o.text option_text, o.correct
                FROM assessment_questions q
                JOIN assessment_answer_options o ON o.question_id = q.id
                WHERE q.active = TRUE
                ORDER BY q.topic, q.id, o.position
                """, new Object[0]);
    }

    public void createSession(UUID id, String userId, Instant createdAt, List<AssessmentQuestion> questions) {
        jdbc.update("INSERT INTO assessment_sessions (id, user_id, status, created_at) VALUES (?, ?, 'STARTED', ?)",
                id, userId, Timestamp.from(createdAt));
        for (int position = 0; position < questions.size(); position++) {
            jdbc.update("""
                    INSERT INTO assessment_session_questions (assessment_id, question_id, position)
                    VALUES (?, ?, ?)
                    """, id, questions.get(position).id(), position);
        }
    }

    public Optional<SessionState> findSession(UUID id) {
        return jdbc.query("SELECT user_id, status FROM assessment_sessions WHERE id = ?",
                (rs, row) -> new SessionState(rs.getString("user_id"), rs.getString("status")), id)
                .stream().findFirst();
    }

    public List<AssessmentQuestion> findSessionQuestions(UUID assessmentId) {
        return questions("""
                SELECT q.id question_id, q.topic, q.text question_text,
                       o.id option_id, o.text option_text, o.correct
                FROM assessment_session_questions sq
                JOIN assessment_questions q ON q.id = sq.question_id
                JOIN assessment_answer_options o ON o.question_id = q.id
                WHERE sq.assessment_id = ?
                ORDER BY sq.position, o.position
                """, assessmentId);
    }

    public void submit(UUID assessmentId, String userId, List<SubmittedAnswer> answers,
                       Map<UUID, AssessmentOption> selectedOptions, Instant submittedAt,
                       AssessmentSubmission result) {
        for (SubmittedAnswer answer : answers) {
            AssessmentOption selected = selectedOptions.get(answer.answerId());
            jdbc.update("""
                    INSERT INTO assessment_answers (assessment_id, question_id, answer_id, correct)
                    VALUES (?, ?, ?, ?)
                    """, assessmentId, answer.questionId(), answer.answerId(), selected.correct());
        }
        jdbc.update("UPDATE assessment_sessions SET status = 'SUBMITTED', submitted_at = ? WHERE id = ?",
                Timestamp.from(submittedAt), assessmentId);
        jdbc.update("""
                INSERT INTO assessment_results
                    (id, assessment_id, user_id, correct_answers, total_answers, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, result.id(), assessmentId, userId, result.correctAnswers(), result.totalAnswers(),
                Timestamp.from(submittedAt));
    }

    private List<AssessmentQuestion> questions(String sql, Object... args) {
        Map<UUID, QuestionBuilder> result = new LinkedHashMap<>();
        jdbc.query(sql, rs -> {
            UUID questionId = rs.getObject("question_id", UUID.class);
            String topic = rs.getString("topic");
            String questionText = rs.getString("question_text");
            QuestionBuilder question = result.computeIfAbsent(questionId, ignored ->
                    new QuestionBuilder(questionId, topic, questionText));
            question.options.add(new AssessmentOption(rs.getObject("option_id", UUID.class), questionId,
                    rs.getString("option_text"), rs.getBoolean("correct")));
        }, args);
        return result.values().stream().map(QuestionBuilder::build).toList();
    }

    public record SessionState(String userId, String status) {}

    private static final class QuestionBuilder {
        private final UUID id;
        private final String topic;
        private final String text;
        private final List<AssessmentOption> options = new ArrayList<>();

        private QuestionBuilder(UUID id, String topic, String text) {
            this.id = id;
            this.topic = topic;
            this.text = text;
        }

        private AssessmentQuestion build() {
            return new AssessmentQuestion(id, topic, text, List.copyOf(options));
        }
    }
}
