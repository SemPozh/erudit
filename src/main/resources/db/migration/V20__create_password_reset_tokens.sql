CREATE TABLE password_reset_tokens (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP   NOT NULL,
    expires_at TIMESTAMP   NOT NULL,
    used_at    TIMESTAMP,

    CONSTRAINT chk_password_reset_expiry CHECK (expires_at > created_at)
);

CREATE INDEX idx_password_reset_tokens_user
    ON password_reset_tokens(user_id);
