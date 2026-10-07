package com.erudit.content;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
public class ContentModerationAuditRepository {
    private final JdbcTemplate jdbc;

    public ContentModerationAuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void recordReportResolution(UUID reportId, String adminId, ReportDecision decision, Instant occurredAt) {
        jdbc.update("""
                INSERT INTO admin_audit_log
                    (id, actor_id, action, target_type, target_id, occurred_at, metadata)
                VALUES (?, ?, 'CONTENT_REPORT_RESOLVED', 'CONTENT_REPORT', ?, ?, ?)
                """, UUID.randomUUID(), adminId, reportId.toString(), Timestamp.from(occurredAt),
                "{\"decision\":\"" + decision.name() + "\"}");
    }

    public long countReportResolutions(UUID reportId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_log "
                + "WHERE action = 'CONTENT_REPORT_RESOLVED' AND target_id = ?", Long.class, reportId.toString());
        return count == null ? 0 : count;
    }
}
