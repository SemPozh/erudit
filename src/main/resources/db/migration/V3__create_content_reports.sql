CREATE TABLE content_reports (
    id UUID PRIMARY KEY,
    content_id UUID NOT NULL REFERENCES content(id),
    reason TEXT,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_content_reports_content ON content_reports(content_id);
CREATE INDEX idx_content_reports_status ON content_reports(status);
