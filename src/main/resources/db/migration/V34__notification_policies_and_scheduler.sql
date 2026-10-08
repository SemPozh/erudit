CREATE TABLE notification_dispatch_claims (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    notification_type VARCHAR(40) NOT NULL,
    deduplication_key VARCHAR(250) NOT NULL,
    claimed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (user_id, notification_type, deduplication_key)
);

CREATE INDEX idx_notification_claims_time ON notification_dispatch_claims(claimed_at);
CREATE INDEX idx_admin_audit_actor_action_time ON admin_audit_log(actor_id, action, occurred_at DESC);
