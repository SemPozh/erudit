-- Disposable demo profile only; never included in normal migrations.
INSERT INTO notification_preferences(user_id,enabled,channels) VALUES('00000000-0000-0000-0000-000000000001',TRUE,'IN_APP') ON CONFLICT DO NOTHING;
INSERT INTO notifications(id,user_id,template_code,title,body,created_at) VALUES('41000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001','DEMO','Demo inbox','Local mock notification','2026-01-01T00:00:00Z') ON CONFLICT DO NOTHING;
INSERT INTO delivery_attempts(id,notification_id,attempt,status,payload_preview,created_at) VALUES('42000000-0000-0000-0000-000000000001','41000000-0000-0000-0000-000000000001',1,'MOCK_DELIVERED','Demo mock receipt','2026-01-01T00:00:00Z') ON CONFLICT DO NOTHING;
