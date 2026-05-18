-- Manual production schema patch for the persisted notification center.
--
-- Apply this before deploying a build that contains Notification.java while
-- production is configured with spring.jpa.hibernate.ddl-auto=validate.

CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY,
    recipient_id UUID NOT NULL REFERENCES profiles(id),
    actor_id UUID REFERENCES profiles(id),
    type VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT,
    target_url VARCHAR(255),
    read_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient_created
    ON notifications (recipient_id, created_at);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient_read
    ON notifications (recipient_id, read_at);

UPDATE notifications SET type = 'POST_REACTION' WHERE type = 'POST_LIKE';
