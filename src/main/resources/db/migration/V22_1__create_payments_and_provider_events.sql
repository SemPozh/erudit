CREATE TABLE payments (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subscription_id UUID REFERENCES subscriptions(id) ON DELETE SET NULL,
    amount DECIMAL(12, 2) NOT NULL CHECK (amount >= 0),
    currency CHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'REFUNDED')),
    provider VARCHAR(40) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    paid_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (provider, external_id)
);

CREATE INDEX idx_payments_user_created ON payments(user_id, created_at DESC, id);

CREATE TABLE payment_provider_events (
    provider VARCHAR(40) NOT NULL,
    event_id VARCHAR(255) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (provider, event_id)
);

ALTER TABLE subscriptions
    ADD COLUMN provider_event_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE subscription_expiry_notifications (
    subscription_id UUID PRIMARY KEY REFERENCES subscriptions(id) ON DELETE CASCADE,
    notified_at TIMESTAMP WITH TIME ZONE NOT NULL
);

