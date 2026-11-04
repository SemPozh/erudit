CREATE TABLE IF NOT EXISTS event_receipts (
    event_id UUID PRIMARY KEY,
    user_id UUID,
    event_type VARCHAR(100) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(1000),
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
