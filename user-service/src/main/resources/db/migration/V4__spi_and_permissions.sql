-- ── V4: SPI support + persistent role-permission table ──────────────────────

-- 1. password_hash on users (SPI reads this for BCrypt verification)
ALTER TABLE mediq_users.users
    ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);

-- 2. role_permissions — single source of truth for fine-grained permissions
CREATE TABLE IF NOT EXISTS mediq_users.role_permissions (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    role_name   VARCHAR(20) NOT NULL,
    permission  VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (role_name, permission)
);

-- 3. Seed default role-permission mappings (idempotent via ON CONFLICT DO NOTHING)
INSERT INTO mediq_users.role_permissions (role_name, permission) VALUES
  ('PATIENT', 'READ_OWN_PROFILE'),
  ('PATIENT', 'WRITE_OWN_PROFILE'),
  ('PATIENT', 'READ_DOCTORS'),
  ('PATIENT', 'READ_DOCTOR_AVAILABILITY'),
  ('PATIENT', 'WRITE_OWN_APPOINTMENT'),
  ('PATIENT', 'READ_OWN_APPOINTMENT'),
  ('PATIENT', 'CANCEL_OWN_APPOINTMENT'),
  ('PATIENT', 'READ_OWN_NOTIFICATIONS'),
  ('PATIENT', 'SEND_OTP'),
  ('PATIENT', 'VERIFY_OTP'),

  ('DOCTOR', 'READ_OWN_PROFILE'),
  ('DOCTOR', 'WRITE_OWN_PROFILE'),
  ('DOCTOR', 'READ_PATIENT_PROFILE'),
  ('DOCTOR', 'READ_OWN_APPOINTMENT'),
  ('DOCTOR', 'WRITE_APPOINTMENT_SLOT'),
  ('DOCTOR', 'CONFIRM_APPOINTMENT'),
  ('DOCTOR', 'CANCEL_APPOINTMENT'),
  ('DOCTOR', 'READ_EMR'),
  ('DOCTOR', 'WRITE_EMR'),
  ('DOCTOR', 'READ_OWN_ANALYTICS'),
  ('DOCTOR', 'READ_OWN_NOTIFICATIONS'),

  ('NURSE', 'READ_OWN_PROFILE'),
  ('NURSE', 'READ_PATIENT_PROFILE'),
  ('NURSE', 'READ_OWN_APPOINTMENT'),
  ('NURSE', 'WRITE_OWN_APPOINTMENT'),
  ('NURSE', 'CANCEL_APPOINTMENT'),
  ('NURSE', 'READ_EMR'),
  ('NURSE', 'READ_OWN_NOTIFICATIONS'),

  ('ADMIN', 'READ_OWN_PROFILE'),
  ('ADMIN', 'READ_ANY_PROFILE'),
  ('ADMIN', 'WRITE_ANY_PROFILE'),
  ('ADMIN', 'VERIFY_DOCTOR'),
  ('ADMIN', 'DEACTIVATE_USER'),
  ('ADMIN', 'READ_DOCTORS'),
  ('ADMIN', 'READ_PATIENT_PROFILE'),
  ('ADMIN', 'READ_OWN_APPOINTMENT'),
  ('ADMIN', 'READ_ANY_APPOINTMENT'),
  ('ADMIN', 'CANCEL_ANY_APPOINTMENT'),
  ('ADMIN', 'WRITE_APPOINTMENT_SLOT'),
  ('ADMIN', 'READ_EMR'),
  ('ADMIN', 'WRITE_EMR'),
  ('ADMIN', 'READ_ANALYTICS'),
  ('ADMIN', 'READ_ANY_NOTIFICATIONS'),
  ('ADMIN', 'MANAGE_ROLES'),
  ('ADMIN', 'SEND_OTP'),
  ('ADMIN', 'VERIFY_OTP')
ON CONFLICT (role_name, permission) DO NOTHING;
