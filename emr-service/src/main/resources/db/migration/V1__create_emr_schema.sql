CREATE SCHEMA IF NOT EXISTS mediq_emr;

CREATE TABLE mediq_emr.patient_event_store (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id      VARCHAR(255)  NOT NULL,
    event_type      VARCHAR(100)  NOT NULL,
    sequence_number BIGINT        NOT NULL,
    payload         JSONB         NOT NULL,
    recorded_by     VARCHAR(255),
    occurred_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (patient_id, sequence_number)
);

CREATE INDEX idx_emr_event_patient_seq ON mediq_emr.patient_event_store (patient_id, sequence_number);
CREATE INDEX idx_emr_event_occurred_at ON mediq_emr.patient_event_store (patient_id, occurred_at);

CREATE TABLE mediq_emr.patient_summary (
    patient_id          VARCHAR(255)  PRIMARY KEY,
    full_name           VARCHAR(255),
    date_of_birth       DATE,
    blood_type          VARCHAR(10),
    allergies           TEXT[]        NOT NULL DEFAULT '{}',
    current_medications TEXT[]        NOT NULL DEFAULT '{}',
    active_conditions   TEXT[]        NOT NULL DEFAULT '{}',
    last_visit_date     DATE,
    primary_doctor_id   VARCHAR(255),
    last_updated        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version             BIGINT        NOT NULL DEFAULT 0
);
