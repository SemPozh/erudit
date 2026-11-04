CREATE TABLE admin_audit (
 id UUID PRIMARY KEY, actor_id UUID NOT NULL, action VARCHAR(100) NOT NULL,
 target_type VARCHAR(100) NOT NULL, target_id VARCHAR(100) NOT NULL,
 status VARCHAR(20) NOT NULL CHECK(status IN ('RUNNING','SUCCESS','FAILED','PARTIAL','UNKNOWN')),
 request_id VARCHAR(128) NOT NULL, metadata TEXT NOT NULL,
 occurred_at TIMESTAMPTZ NOT NULL, completed_at TIMESTAMPTZ
);
CREATE INDEX admin_audit_time ON admin_audit(occurred_at DESC,id);
CREATE INDEX admin_audit_actor ON admin_audit(actor_id,occurred_at DESC);
