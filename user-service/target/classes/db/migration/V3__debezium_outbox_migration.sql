ALTER TABLE mediq_users.user_outbox DROP COLUMN IF EXISTS status;
ALTER TABLE mediq_users.user_outbox DROP COLUMN IF EXISTS published_at;
DROP INDEX IF EXISTS mediq_users.idx_user_outbox_pending;

-- destination: Debezium EventRouter routes INSERT events to this Kafka topic
ALTER TABLE mediq_users.user_outbox
    ADD COLUMN IF NOT EXISTS destination VARCHAR(255)
    NOT NULL DEFAULT 'mediq.user.events';

-- timestamp: epoch millis, required by Debezium EventRouter
ALTER TABLE mediq_users.user_outbox
    ADD COLUMN IF NOT EXISTS timestamp BIGINT
    NOT NULL DEFAULT EXTRACT(EPOCH FROM NOW())::BIGINT * 1000;

CREATE INDEX IF NOT EXISTS idx_user_outbox_created_at
    ON mediq_users.user_outbox(created_at);

GRANT SELECT ON mediq_users.user_outbox TO debezium;
GRANT USAGE ON SCHEMA mediq_users TO debezium;
