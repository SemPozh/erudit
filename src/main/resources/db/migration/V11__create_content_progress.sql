CREATE TABLE content_progress (
    user_id VARCHAR(255) NOT NULL,
    content_id UUID NOT NULL REFERENCES content(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('VIEWED', 'COMPLETED')),
    viewed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (user_id, content_id),
    CONSTRAINT chk_content_progress_completion CHECK (
        (status = 'VIEWED' AND completed_at IS NULL)
        OR (status = 'COMPLETED' AND completed_at IS NOT NULL)
    )
);

CREATE INDEX idx_content_progress_content_status
    ON content_progress(content_id, status);
