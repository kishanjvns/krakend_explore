CREATE SCHEMA IF NOT EXISTS mediq_analytics;

CREATE TABLE mediq_analytics.daily_appointment_summary (
    id               UUID  PRIMARY KEY DEFAULT gen_random_uuid(),
    summary_date     DATE  NOT NULL UNIQUE,
    total_booked     INT   NOT NULL DEFAULT 0,
    total_confirmed  INT   NOT NULL DEFAULT 0,
    total_cancelled  INT   NOT NULL DEFAULT 0,
    total_completed  INT   NOT NULL DEFAULT 0
);

CREATE TABLE mediq_analytics.doctor_performance_summary (
    doctor_id               VARCHAR(255) NOT NULL,
    summary_date            DATE         NOT NULL,
    total_appointments      INT          NOT NULL DEFAULT 0,
    completed_appointments  INT          NOT NULL DEFAULT 0,
    cancelled_appointments  INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (doctor_id, summary_date)
);

CREATE TABLE mediq_analytics.platform_metric (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    metric_name   VARCHAR(100) NOT NULL UNIQUE,
    metric_value  BIGINT       NOT NULL DEFAULT 0,
    last_updated  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO mediq_analytics.platform_metric (metric_name, metric_value) VALUES
    ('total_registered_users',        0),
    ('total_registered_doctors',      0),
    ('total_appointments_booked',     0),
    ('total_appointments_completed',  0);
