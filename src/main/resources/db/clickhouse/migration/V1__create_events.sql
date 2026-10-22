CREATE TABLE IF NOT EXISTS events (
    event_id UUID,
    user_id String,
    session_id String,
    event_type LowCardinality(String),
    occurred_at DateTime64(3, 'UTC'),
    payload String,
    INDEX idx_event_id event_id TYPE bloom_filter(0.01) GRANULARITY 4,
    INDEX idx_user_id user_id TYPE bloom_filter(0.01) GRANULARITY 4
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(occurred_at)
ORDER BY (event_type, occurred_at, event_id);
