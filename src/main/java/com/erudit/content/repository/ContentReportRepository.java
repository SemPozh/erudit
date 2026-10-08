package com.erudit.content.repository;

import com.erudit.content.model.ContentReport;
import com.erudit.content.model.ContentReportStatus;
import com.erudit.content.model.ReportDecision;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
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
        return jdbc.query("SELECT * FROM content_reports WHERE id = ?", this::map, id)
                .stream().findFirst();
    }

    public List<ContentReport> findOpen(int page, int size) {
        return jdbc.query("SELECT * FROM content_reports WHERE status = 'OPEN' "
                + "ORDER BY created_at, id LIMIT ? OFFSET ?", this::map, size, page * size);
    }

    public long countOpen() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM content_reports WHERE status = 'OPEN'", Long.class);
        return count == null ? 0 : count;
    }

    public boolean resolve(UUID id, ReportDecision decision, String comment, Instant resolvedAt, String adminId) {
        return jdbc.update("""
                UPDATE content_reports
                SET status = 'RESOLVED', decision = ?, resolution_comment = ?, resolved_at = ?, resolved_by = ?
                WHERE id = ? AND status = 'OPEN'
                """, decision.name(), comment, Timestamp.from(resolvedAt), adminId, id) == 1;
    }

    private ContentReport map(ResultSet rs, int row) throws SQLException {
        Timestamp resolvedAt = rs.getTimestamp("resolved_at");
        String decision = rs.getString("decision");
        return new ContentReport(rs.getObject("id", UUID.class), rs.getObject("content_id", UUID.class),
                rs.getString("reason"), ContentReportStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(),
                decision == null ? null : ReportDecision.valueOf(decision),
                rs.getString("resolution_comment"), resolvedAt == null ? null : resolvedAt.toInstant(),
                rs.getString("resolved_by"));
    }
}
