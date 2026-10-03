ALTER TABLE subscription_plans
    ADD COLUMN available BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_subscription_plans_available_price
    ON subscription_plans(available, price, code);
