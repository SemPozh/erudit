-- Fixed, expired example for Swagger webhooks; never grants premium on startup.
INSERT INTO subscription_owners(user_id) VALUES('00000000-0000-0000-0000-000000000001') ON CONFLICT DO NOTHING;
INSERT INTO subscriptions(id,user_id,plan_code,status,start_date,end_date) VALUES
 ('00000000-0000-0000-0000-000000008700','00000000-0000-0000-0000-000000000001',
  'PREMIUM_MONTHLY','EXPIRED','2026-01-01T00:00:00Z','2026-02-01T00:00:00Z') ON CONFLICT DO NOTHING;
INSERT INTO payments(id,user_id,subscription_id,amount,currency,status,provider,external_id,created_at,paid_at) VALUES
('00000000-0000-0000-0000-000000009400','00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000008700',299,'RUB','PAID','MOCK','demo-payment-94','2026-01-01T00:00:00Z','2026-01-01T00:00:00Z') ON CONFLICT DO NOTHING;
