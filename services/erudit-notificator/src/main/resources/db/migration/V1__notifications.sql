CREATE TABLE notification_preferences (
    user_id UUID PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    channels VARCHAR(200) NOT NULL DEFAULT 'IN_APP'
);
CREATE TABLE notification_templates (
    id UUID PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    title_template VARCHAR(300) NOT NULL,
    body_template VARCHAR(2000) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    template_code VARCHAR(100) NOT NULL,
    title VARCHAR(300) NOT NULL,
    body VARCHAR(2000) NOT NULL,
    read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_notifications_user_created ON notifications(user_id, created_at DESC, id DESC);
INSERT INTO notification_templates(id,code,title_template,body_template) VALUES
('40000000-0000-0000-0000-000000000001','DEMO','Erudit update','A new learning item is available');
