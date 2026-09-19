CREATE TABLE categories (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE
);

CREATE TABLE content (
    id UUID PRIMARY KEY,
    category_id UUID NOT NULL REFERENCES categories(id),
    type VARCHAR(30) NOT NULL,
    title VARCHAR(250) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    body TEXT NOT NULL,
    media_url VARCHAR(1000),
    difficulty VARCHAR(20) NOT NULL,
    estimated_minutes INTEGER NOT NULL,
    author_id VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_content_category_status ON content(category_id, status);

CREATE TABLE content_tags (
    content_id UUID NOT NULL REFERENCES content(id),
    tag VARCHAR(100) NOT NULL,
    PRIMARY KEY (content_id, tag)
);
