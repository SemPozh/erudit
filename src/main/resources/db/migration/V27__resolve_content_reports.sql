ALTER TABLE content_reports ADD COLUMN decision VARCHAR(20);
ALTER TABLE content_reports ADD COLUMN resolution_comment VARCHAR(2000);
ALTER TABLE content_reports ADD COLUMN resolved_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE content_reports ADD COLUMN resolved_by VARCHAR(100);

CREATE TABLE admin_audit_log (
    id UUID PRIMARY KEY,
    actor_id VARCHAR(100) NOT NULL,
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(100) NOT NULL,
    target_id VARCHAR(100) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    metadata TEXT NOT NULL
);

CREATE INDEX idx_admin_audit_occurred_at ON admin_audit_log(occurred_at);
