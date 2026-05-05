CREATE SCHEMA IF NOT EXISTS mediq_doctors;

CREATE TABLE doctor_profile (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL UNIQUE,
    keycloak_id         VARCHAR(255),
    license_number      VARCHAR(100) NOT NULL UNIQUE,
    first_name          VARCHAR(100) NOT NULL,
    last_name           VARCHAR(100) NOT NULL,
    years_of_experience INT NOT NULL DEFAULT 0,
    consultation_fee    DECIMAL(10,2),
    is_active           BOOLEAN NOT NULL DEFAULT true,
    is_verified         BOOLEAN NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE doctor_specialization (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id       UUID NOT NULL REFERENCES doctor_profile(id),
    specialization  VARCHAR(100) NOT NULL,
    is_primary      BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE doctor_availability (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id       UUID NOT NULL REFERENCES doctor_profile(id),
    day_of_week     VARCHAR(10) NOT NULL CHECK (day_of_week IN ('MON','TUE','WED','THU','FRI','SAT','SUN')),
    start_time      TIME NOT NULL,
    end_time        TIME NOT NULL,
    slot_duration   INT NOT NULL DEFAULT 30,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE doctor_search_view (
    id                      UUID PRIMARY KEY,
    user_id                 UUID NOT NULL,
    full_name               VARCHAR(200) NOT NULL,
    primary_specialization  VARCHAR(100),
    all_specializations     TEXT[],
    consultation_fee        DECIMAL(10,2),
    years_of_experience     INT,
    is_verified             BOOLEAN NOT NULL DEFAULT false,
    is_active               BOOLEAN NOT NULL DEFAULT true,
    available_days          TEXT[],
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_doctor_profile_user_id ON doctor_profile(user_id);
CREATE INDEX idx_doctor_profile_verified ON doctor_profile(is_verified, is_active);
CREATE INDEX idx_doctor_specialization_doctor ON doctor_specialization(doctor_id);
CREATE INDEX idx_doctor_availability_doctor ON doctor_availability(doctor_id);
CREATE INDEX idx_doctor_search_specialization ON doctor_search_view(primary_specialization);
CREATE INDEX idx_doctor_search_verified ON doctor_search_view(is_verified, is_active);
CREATE INDEX idx_doctor_search_fee ON doctor_search_view(consultation_fee);
