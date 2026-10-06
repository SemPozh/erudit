CREATE TABLE refresh_tokens (
    id             UUID        PRIMARY KEY,
    user_id        UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash     VARCHAR(64) NOT NULL UNIQUE,
    created_at     TIMESTAMP   NOT NULL,
    expires_at     TIMESTAMP   NOT NULL,
    revoked_at     TIMESTAMP,
    replaced_by_id UUID        REFERENCES refresh_tokens(id),

    CONSTRAINT chk_refresh_token_expiry CHECK (expires_at > created_at)
);

CREATE INDEX idx_refresh_tokens_user_expires_at
    ON refresh_tokens(user_id, expires_at);
