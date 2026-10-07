CREATE TABLE notification_templates (
    id UUID PRIMARY KEY,
    notification_type VARCHAR(40) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    title VARCHAR(250),
    body VARCHAR(10000) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_notification_template_type_channel UNIQUE (notification_type, channel)
);

CREATE TABLE notification_template_parameters (
    template_id UUID NOT NULL REFERENCES notification_templates(id) ON DELETE CASCADE,
    parameter_name VARCHAR(50) NOT NULL,
    PRIMARY KEY (template_id, parameter_name)
);
