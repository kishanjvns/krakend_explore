CREATE SCHEMA IF NOT EXISTS mediq_users;

-- Core user identity (Patient, Doctor, Admin)
CREATE TABLE users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_id         VARCHAR(255) UNIQUE,          -- set after Keycloak sync
    user_type           VARCHAR(20) NOT NULL           -- PATIENT, DOCTOR, ADMIN
                        CHECK (user_type IN ('PATIENT','DOCTOR','ADMIN')),
    first_name          VARCHAR(100) NOT NULL,
    last_name           VARCHAR(100) NOT NULL,
    date_of_birth       DATE,
    is_active           BOOLEAN NOT NULL DEFAULT true,
    is_verified         BOOLEAN NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID
);

-- Addresses (HOME, WORK, BILLING)
CREATE TABLE user_address (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    address_type    VARCHAR(20) NOT NULL
                    CHECK (address_type IN ('HOME','WORK','BILLING')),
    address_line1   VARCHAR(255) NOT NULL,
    address_line2   VARCHAR(255),
    city            VARCHAR(100) NOT NULL,
    state           VARCHAR(100) NOT NULL,
    country         VARCHAR(100) NOT NULL DEFAULT 'India',
    zip             VARCHAR(20) NOT NULL,
    is_primary      BOOLEAN NOT NULL DEFAULT false,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Contact info (EMAIL, PHONE)
CREATE TABLE user_contact (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    contact_type    VARCHAR(10) NOT NULL
                    CHECK (contact_type IN ('EMAIL','PHONE')),
    contact_value   VARCHAR(255) NOT NULL,
    is_primary      BOOLEAN NOT NULL DEFAULT false,
    is_verified     BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Doctor-specific identity data
CREATE TABLE doctor_profile (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID NOT NULL UNIQUE REFERENCES users(id),
    license_number       VARCHAR(100) NOT NULL UNIQUE,
    license_expiry       DATE NOT NULL,
    years_of_experience  INT NOT NULL DEFAULT 0,
    verification_status  VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                         CHECK (verification_status IN ('PENDING','VERIFIED','REJECTED')),
    verified_by          UUID,                         -- Admin user_id
    verified_at          TIMESTAMPTZ,
    rejection_reason     VARCHAR(500),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indexes
CREATE INDEX idx_users_user_type ON users(user_type);
CREATE INDEX idx_users_keycloak_id ON users(keycloak_id);
CREATE INDEX idx_user_address_user_id ON user_address(user_id);
CREATE INDEX idx_user_contact_user_id ON user_contact(user_id);
CREATE INDEX idx_doctor_profile_user_id ON doctor_profile(user_id);
CREATE INDEX idx_doctor_profile_verification ON doctor_profile(verification_status);
