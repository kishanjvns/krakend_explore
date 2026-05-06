CREATE SCHEMA IF NOT EXISTS mediq_payments;

CREATE TABLE payment (
    id                           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id               UUID NOT NULL UNIQUE,   -- Fix 5: UNIQUE prevents duplicate charges
    patient_id                   UUID NOT NULL,
    stripe_payment_intent_id     VARCHAR(255) UNIQUE,
    stripe_client_secret         VARCHAR(500),
    amount                       DECIMAL(10,2) NOT NULL,
    currency                     VARCHAR(3) NOT NULL DEFAULT 'inr',
    status                       VARCHAR(30) NOT NULL DEFAULT 'PENDING'
                                 CHECK (status IN (
                                     'PENDING',
                                     'PROCESSING',
                                     'SUCCEEDED',
                                     'FAILED',
                                     'CANCELLED',
                                     'REFUNDED'
                                 )),
    failure_reason               TEXT,
    temporal_workflow_id         VARCHAR(255),
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Explicit unique index on appointment_id (created by UNIQUE constraint above,
-- named here for clarity in monitoring and EXPLAIN plans)
CREATE UNIQUE INDEX idx_payment_appointment_unique ON payment(appointment_id);

CREATE INDEX idx_payment_stripe_intent  ON payment(stripe_payment_intent_id);
CREATE INDEX idx_payment_status         ON payment(status);
