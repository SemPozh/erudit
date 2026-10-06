CREATE TABLE user_settings (
    user_id            UUID        PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    language           VARCHAR(20) NOT NULL DEFAULT 'ru',
    time_zone          VARCHAR(50) NOT NULL DEFAULT 'UTC',
    daily_goal_minutes INTEGER     NOT NULL DEFAULT 15,
    visible_in_search  BOOLEAN     NOT NULL DEFAULT TRUE,
    visible_in_rating  BOOLEAN     NOT NULL DEFAULT TRUE,
    analytics_consent  BOOLEAN     NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_daily_goal_minutes CHECK (daily_goal_minutes BETWEEN 1 AND 1440)
);

CREATE TABLE user_favorite_categories (
    user_id     UUID NOT NULL REFERENCES user_settings(user_id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, category_id)
);

CREATE INDEX idx_user_favorite_categories_category
    ON user_favorite_categories(category_id);
