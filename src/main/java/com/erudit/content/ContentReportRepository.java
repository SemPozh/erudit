package com.erudit.content;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ContentReportRepository {
    private final JdbcTemplate jdbc;

    public ContentReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(ContentReport report) {
        jdbc.update("""
                INSERT INTO content_reports (id, content_id, reason, status, created_at)
                VALUES (?, ?, ?, 'OPEN', ?)
                """, report.id(), report.contentId(), report.reason(), Timestamp.from(report.createdAt()));
    }

    public Optional<ContentReport> findById(UUID id) {
        return jdbc.query("SELECT id, content_id, reason, created_at FROM content_reports WHERE id = ?",
                (rs, row) -> new ContentReport(
                        rs.getObject("id", UUID.class),
                        rs.getObject("content_id", UUID.class),
                        rs.getString("reason"),
                        rs.getTimestamp("created_at").toInstant()), id)
                .stream().findFirst();
    }
}
