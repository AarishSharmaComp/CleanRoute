CREATE TABLE user_notification (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    kind VARCHAR(32) NOT NULL CHECK (kind IN ('HIGH_POLLUTION_FORECAST', 'CLEANER_ALTERNATIVE')),
    dedup_key VARCHAR(160) NOT NULL,
    title VARCHAR(140) NOT NULL,
    message VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_user_notification_dedup UNIQUE (user_id, dedup_key)
);
CREATE INDEX ix_user_notification_created ON user_notification(user_id, created_at DESC);
CREATE INDEX ix_user_notification_unread ON user_notification(user_id, read_at, created_at DESC);
