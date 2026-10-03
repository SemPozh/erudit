CREATE TABLE subscription_plans (
    code VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    price DECIMAL(12, 2) NOT NULL CHECK (price >= 0),
    currency CHAR(3) NOT NULL,
    period VARCHAR(20) NOT NULL CHECK (period IN ('MONTH', 'YEAR'))
);

CREATE TABLE subscriptions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    plan_code VARCHAR(50) NOT NULL REFERENCES subscription_plans(code),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'EXPIRED')),
    start_date TIMESTAMP WITH TIME ZONE NOT NULL,
    end_date TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_subscription_period CHECK (end_date > start_date)
);

CREATE INDEX idx_subscriptions_user_status_dates
    ON subscriptions(user_id, status, start_date, end_date);

INSERT INTO subscription_plans (code, name, price, currency, period) VALUES
    ('PREMIUM_MONTHLY', 'Premium monthly', 299.00, 'RUB', 'MONTH'),
    ('PREMIUM_YEARLY', 'Premium yearly', 2990.00, 'RUB', 'YEAR');
