-- Fixed, expired example for Swagger webhooks; never grants premium on startup.
INSERT INTO subscription_owners(user_id) VALUES('00000000-0000-0000-0000-000000000001') ON CONFLICT DO NOTHING;
INSERT INTO subscriptions(id,user_id,plan_code,status,start_date,end_date) VALUES
 ('00000000-0000-0000-0000-000000008700','00000000-0000-0000-0000-000000000001',
  'PREMIUM_MONTHLY','EXPIRED','2026-01-01T00:00:00Z','2026-02-01T00:00:00Z') ON CONFLICT DO NOTHING;
