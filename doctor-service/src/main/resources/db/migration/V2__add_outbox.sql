CREATE TABLE mediq_doctors.service_outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    VARCHAR(255) NOT NULL,
    aggregate_type  VARCHAR(100) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    destination     VARCHAR(255) NOT NULL,
    payload         JSONB NOT NULL,
    timestamp       BIGINT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_doctor_outbox_created_at ON mediq_doctors.service_outbox(created_at);
GRANT SELECT ON mediq_doctors.service_outbox TO debezium;
GRANT USAGE ON SCHEMA mediq_doctors TO debezium;
