package com.erudit.quiz;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class DailyQuizRepository {

    private final JdbcTemplate jdbc;

    public DailyQuizRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Quizzes whose content the user has completed, in a stable order. */
    public List<UUID> quizIdsForCompletedContent(String userId) {
        return jdbc.query("""
                SELECT q.id FROM quizzes q
                JOIN content_progress p ON p.content_id = q.content_id
                WHERE p.user_id = ? AND p.status = 'COMPLETED'
                ORDER BY q.id
                """,
                (rs, i) -> rs.getObject("id", UUID.class), userId);
    }

    public List<UUID> allQuizIds() {
        return jdbc.query("SELECT id FROM quizzes ORDER BY id",
                (rs, i) -> rs.getObject("id", UUID.class));
    }
}