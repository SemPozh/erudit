CREATE TABLE event_ingestion_receipts (
    event_id UUID PRIMARY KEY,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL
);

