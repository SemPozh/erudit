CREATE TABLE users(id UUID PRIMARY KEY,email VARCHAR(255) UNIQUE NOT NULL,display_name VARCHAR(80) NOT NULL,verified BOOLEAN NOT NULL DEFAULT FALSE,verification_token VARCHAR(80),verification_expires_at TIMESTAMPTZ,created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE user_settings(user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,locale VARCHAR(16) NOT NULL DEFAULT 'ru',daily_goal INTEGER NOT NULL DEFAULT 10 CHECK(daily_goal BETWEEN 1 AND 100));
INSERT INTO users(id,email,display_name,verified) VALUES('00000000-0000-0000-0000-000000000001','demo@erudit.local','Demo User',TRUE);
INSERT INTO user_settings(user_id,locale,daily_goal) VALUES('00000000-0000-0000-0000-000000000001','ru',10);
