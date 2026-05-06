ALTER TABLE mediq_appointments.appointment_outbox DROP COLUMN IF EXISTS status;
ALTER TABLE mediq_appointments.appointment_outbox DROP COLUMN IF EXISTS published_at;
DROP INDEX IF EXISTS mediq_appointments.idx_outbox_pending;

ALTER TABLE mediq_appointments.appointment_outbox
    ADD COLUMN IF NOT EXISTS destination VARCHAR(255)
    NOT NULL DEFAULT 'mediq.appointment.events';

ALTER TABLE mediq_appointments.appointment_outbox
    ADD COLUMN IF NOT EXISTS timestamp BIGINT
    NOT NULL DEFAULT EXTRACT(EPOCH FROM NOW())::BIGINT * 1000;

CREATE INDEX IF NOT EXISTS idx_appointment_outbox_created_at
    ON mediq_appointments.appointment_outbox(created_at);

GRANT SELECT ON mediq_appointments.appointment_outbox TO debezium;
GRANT USAGE ON SCHEMA mediq_appointments TO debezium;
