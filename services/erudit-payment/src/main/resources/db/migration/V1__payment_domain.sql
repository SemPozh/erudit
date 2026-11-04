CREATE TABLE subscription_plans (
 code VARCHAR(50) PRIMARY KEY, name VARCHAR(100) NOT NULL,
 price NUMERIC(12,2) NOT NULL CHECK(price >= 0), currency VARCHAR(3) NOT NULL,
 period VARCHAR(20) NOT NULL CHECK(period IN ('MONTH','YEAR')), available BOOLEAN NOT NULL DEFAULT TRUE
);
-- Remote user IDs deliberately have no foreign key into another service's database.
CREATE TABLE subscription_owners (user_id UUID PRIMARY KEY);
CREATE TABLE subscriptions (
 id UUID PRIMARY KEY, revision BIGINT GENERATED ALWAYS AS IDENTITY, user_id UUID NOT NULL REFERENCES subscription_owners(user_id),
 plan_code VARCHAR(50) NOT NULL REFERENCES subscription_plans(code),
 status VARCHAR(20) NOT NULL CHECK(status IN ('ACTIVE','EXPIRED')),
 start_date TIMESTAMPTZ NOT NULL, end_date TIMESTAMPTZ NOT NULL CHECK(end_date > start_date)
);
CREATE UNIQUE INDEX one_active_subscription ON subscriptions(user_id) WHERE status='ACTIVE';
CREATE INDEX subscriptions_history ON subscriptions(user_id,start_date DESC,id);
CREATE TABLE payments (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES subscription_owners(user_id),
 subscription_id UUID REFERENCES subscriptions(id), amount NUMERIC(12,2) NOT NULL CHECK(amount >= 0),
 currency VARCHAR(3) NOT NULL, status VARCHAR(20) NOT NULL CHECK(status IN ('PENDING','PAID','FAILED','REFUNDED')),
 provider VARCHAR(30) NOT NULL, external_id VARCHAR(100) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL, paid_at TIMESTAMPTZ,
 UNIQUE(provider,external_id)
);
CREATE INDEX payments_history ON payments(user_id,created_at DESC,id);
INSERT INTO subscription_plans(code,name,price,currency,period) VALUES
 ('PREMIUM_MONTHLY','Premium monthly',299.00,'RUB','MONTH'),
 ('PREMIUM_YEARLY','Premium yearly',2990.00,'RUB','YEAR');
