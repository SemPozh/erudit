package com.erudit.rating;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RatingRepository {
    private final JdbcTemplate jdbc;

    public RatingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<RatingProfile> findProfile(String userId) {
        return jdbc.query("SELECT user_id, points, er_score, grade_code FROM user_rating_profiles WHERE user_id = ?",
                (rs, row) -> new RatingProfile(rs.getString("user_id"), rs.getLong("points"),
                        rs.getInt("er_score"), rs.getString("grade_code")), userId).stream().findFirst();
    }

    public int assessmentErScore(String userId) {
        return jdbc.query("SELECT er_score FROM assessment_profiles WHERE user_id = ?",
                (rs, row) -> rs.getInt(1), userId).stream().findFirst().orElse(0);
    }

    public boolean eventExists(String userId, String sourceType, UUID sourceId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM rating_events
                WHERE user_id = ? AND source_type = ? AND source_id = ?
                """, Integer.class, userId, sourceType, sourceId);
        return count != null && count > 0;
    }

    public UUID categoryForQuiz(UUID quizId) {
        return jdbc.query("""
                SELECT c.category_id FROM quizzes q JOIN content c ON c.id = q.content_id
                WHERE q.id = ?
                """, (rs, row) -> rs.getObject(1, UUID.class), quizId).stream().findFirst().orElse(null);
    }

    public void insertEvent(String userId, String sourceType, UUID sourceId, UUID categoryId,
                            int points, int erDelta, Instant occurredAt) {
        jdbc.update("""
                INSERT INTO rating_events
                    (id, user_id, source_type, source_id, category_id, points, er_delta, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), userId, sourceType, sourceId, categoryId, points, erDelta,
                Timestamp.from(occurredAt));
    }

    public void saveProfile(RatingProfile profile, Instant updatedAt) {
        int updated = jdbc.update("""
                UPDATE user_rating_profiles
                SET points = ?, er_score = ?, grade_code = ?, updated_at = ?
                WHERE user_id = ?
                """, profile.points(), profile.erScore(), profile.gradeCode(), Timestamp.from(updatedAt),
                profile.userId());
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO user_rating_profiles (user_id, points, er_score, grade_code, updated_at)
                    VALUES (?, ?, ?, ?, ?)
                    """, profile.userId(), profile.points(), profile.erScore(), profile.gradeCode(),
                    Timestamp.from(updatedAt));
        }
    }
}
