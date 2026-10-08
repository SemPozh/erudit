package com.erudit.content.repository;

import com.erudit.content.model.ContentProgress;
import com.erudit.content.model.ContentProgressStatus;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ContentProgressRepository {
    private final JdbcTemplate jdbc;

    public ContentProgressRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ContentProgress> find(String userId, UUID contentId) {
        return jdbc.query("SELECT * FROM content_progress WHERE user_id = ? AND content_id = ?",
                this::map, userId, contentId).stream().findFirst();
    }

    public void insert(ContentProgress progress) {
        jdbc.update("""
                INSERT INTO content_progress (user_id, content_id, status, viewed_at, completed_at)
                VALUES (?, ?, ?, ?, ?)
                """, progress.userId(), progress.contentId(), progress.status().name(),
                Timestamp.from(progress.viewedAt()), timestamp(progress.completedAt()));
    }

    public void complete(String userId, UUID contentId, Instant completedAt) {
        jdbc.update("""
                UPDATE content_progress SET status = 'COMPLETED', completed_at = ?
                WHERE user_id = ? AND content_id = ?
                """, Timestamp.from(completedAt), userId, contentId);
    }

    private ContentProgress map(ResultSet rs, int row) throws SQLException {
        Timestamp completedAt = rs.getTimestamp("completed_at");
        return new ContentProgress(rs.getString("user_id"), rs.getObject("content_id", UUID.class),
                ContentProgressStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("viewed_at").toInstant(), completedAt == null ? null : completedAt.toInstant());
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
