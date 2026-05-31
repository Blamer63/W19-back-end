-- Browser Web Push subscriptions for installed PWA notifications.
-- Apply this before enabling WEB_PUSH_ENABLED=true in production when
-- spring.jpa.hibernate.ddl-auto=validate is active.

CREATE TABLE IF NOT EXISTS push_subscriptions (
    id UUID PRIMARY KEY,
    profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    endpoint VARCHAR(2048) NOT NULL UNIQUE,
    p256dh VARCHAR(512) NOT NULL,
    auth_secret VARCHAR(256) NOT NULL,
    expiration_time BIGINT,
    last_success_at TIMESTAMP,
    last_failure_at TIMESTAMP,
    failure_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_push_subscription_profile
    ON push_subscriptions(profile_id);
