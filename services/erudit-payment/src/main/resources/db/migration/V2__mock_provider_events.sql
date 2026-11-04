ALTER TABLE payments ALTER COLUMN external_id TYPE VARCHAR(300);
ALTER TABLE subscriptions ADD COLUMN provider_event_at TIMESTAMPTZ;
CREATE TABLE payment_provider_events (
 provider VARCHAR(40) NOT NULL, event_id VARCHAR(255) NOT NULL,
 fingerprint VARCHAR(64) NOT NULL, received_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY(provider,event_id)
);
CREATE TABLE mobile_receipts (
 provider VARCHAR(40) NOT NULL, receipt_id VARCHAR(64) NOT NULL,
 user_id UUID NOT NULL REFERENCES subscription_owners(user_id),
 subscription_id UUID NOT NULL REFERENCES subscriptions(id),
 PRIMARY KEY(provider,receipt_id)
);
