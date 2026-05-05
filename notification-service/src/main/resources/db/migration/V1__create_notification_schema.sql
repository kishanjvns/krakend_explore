CREATE SCHEMA IF NOT EXISTS mediq_notifications;

CREATE TABLE notification (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_user_id   UUID NOT NULL,
    recipient_email     VARCHAR(255),
    recipient_phone     VARCHAR(20),
    channel             VARCHAR(10) NOT NULL CHECK (channel IN ('EMAIL','SMS','PUSH')),
    notification_type   VARCHAR(100) NOT NULL,
    subject             VARCHAR(500),
    body                TEXT NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SENT','FAILED')),
    idempotency_key     VARCHAR(255) NOT NULL UNIQUE,
    retry_count         INT NOT NULL DEFAULT 0,
    last_error          TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at             TIMESTAMPTZ
);

CREATE TABLE notification_dlq (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    original_id         UUID NOT NULL REFERENCES notification(id),
    event_type          VARCHAR(100),
    event_payload       JSONB,
    failure_reason      TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notification_user ON notification(recipient_user_id);
CREATE INDEX idx_notification_status ON notification(status);
CREATE INDEX idx_notification_idempotency ON notification(idempotency_key);
