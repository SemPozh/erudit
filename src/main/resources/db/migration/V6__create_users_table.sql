CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name          VARCHAR(255) NOT NULL,
    avatar        VARCHAR(255),
    created_at    TIMESTAMP    NOT NULL,
    status        VARCHAR(30)  NOT NULL,

    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE INDEX idx_users_status ON users(status);