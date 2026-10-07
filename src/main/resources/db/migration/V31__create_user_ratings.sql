CREATE TABLE user_rating_profiles (
    user_id VARCHAR(255) PRIMARY KEY,
    points BIGINT NOT NULL DEFAULT 0 CHECK (points >= 0),
    er_score INTEGER NOT NULL DEFAULT 0 CHECK (er_score >= 0 AND er_score <= 2000),
    grade_code VARCHAR(40) NOT NULL REFERENCES assessment_grade_definitions(code),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE rating_events (
    id UUID PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    source_type VARCHAR(20) NOT NULL CHECK (source_type IN ('QUIZ', 'COMPETITION')),
    source_id UUID NOT NULL,
    category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    points INTEGER NOT NULL CHECK (points >= 0),
    er_delta INTEGER NOT NULL CHECK (er_delta >= 0),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_rating_event_source UNIQUE (user_id, source_type, source_id)
);

CREATE INDEX idx_rating_events_user_time ON rating_events(user_id, occurred_at DESC);
CREATE INDEX idx_rating_events_category_time ON rating_events(category_id, occurred_at DESC);
