CREATE TABLE notification_preferences (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    quiet_hours_start TIME,
    quiet_hours_end TIME,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE notification_preference_channels (
    user_id UUID NOT NULL REFERENCES notification_preferences(user_id) ON DELETE CASCADE,
    channel VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, channel)
);

CREATE TABLE notification_preference_types (
    user_id UUID NOT NULL REFERENCES notification_preferences(user_id) ON DELETE CASCADE,
    notification_type VARCHAR(40) NOT NULL,
    PRIMARY KEY (user_id, notification_type)
);
