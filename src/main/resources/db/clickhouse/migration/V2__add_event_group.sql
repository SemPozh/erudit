ALTER TABLE events
    ADD COLUMN IF NOT EXISTS event_group LowCardinality(String) AFTER event_type;
