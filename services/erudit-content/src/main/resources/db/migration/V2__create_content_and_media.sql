CREATE TABLE content (
    id UUID PRIMARY KEY,
    category_id UUID NOT NULL REFERENCES categories(id),
    type VARCHAR(30) NOT NULL,
    title VARCHAR(250) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    body TEXT NOT NULL,
    media_url VARCHAR(1000),
    difficulty VARCHAR(20) NOT NULL,
    estimated_minutes INTEGER NOT NULL CHECK (estimated_minutes BETWEEN 1 AND 1440),
    premium_locked BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_content_catalog ON content(status, category_id, created_at DESC);
CREATE TABLE content_tags (
    content_id UUID NOT NULL REFERENCES content(id) ON DELETE CASCADE,
    tag VARCHAR(100) NOT NULL,
    PRIMARY KEY (content_id, tag)
);
CREATE TABLE media_files (
    id UUID PRIMARY KEY,
    storage_key VARCHAR(100) NOT NULL UNIQUE,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
