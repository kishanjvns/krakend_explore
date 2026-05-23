-- ── V6: Canonical seed users (all authenticate via SPI, no native Keycloak users) ──
-- Password for all seed users: Test@1234
-- BCrypt hash ($2a$10$, rounds=10): $2a$10$ZO3lJBeZXMqxjSgCCV8UCeVMHh8MxV2k5NSAJ4PZHBLinIpoqGcly

-- 1. admin@mediq.com — fix name + add password hash
UPDATE mediq_users.users
SET first_name     = 'Platform',
    last_name      = 'Admin',
    is_verified    = true,
    password_hash  = '$2a$10$ZO3lJBeZXMqxjSgCCV8UCeVMHh8MxV2k5NSAJ4PZHBLinIpoqGcly'
WHERE id = 'ffaab809-6d3f-4c3e-b0d1-2c5362893814';

-- 2. dr.mehta@mediq.com — add password hash, mark pre-verified (seed test data)
UPDATE mediq_users.users
SET is_verified   = true,
    password_hash = '$2a$10$ZO3lJBeZXMqxjSgCCV8UCeVMHh8MxV2k5NSAJ4PZHBLinIpoqGcly'
WHERE id = '1c767d7d-d0fa-485e-8bba-af93b21b1cbc';

-- 3. nurse.priya@mediq.com — fix email, add password hash, mark pre-verified
UPDATE mediq_users.users
SET is_verified   = true,
    password_hash = '$2a$10$ZO3lJBeZXMqxjSgCCV8UCeVMHh8MxV2k5NSAJ4PZHBLinIpoqGcly'
WHERE id = '6eed7456-968e-49cb-9982-d11e7556e95f';

UPDATE mediq_users.user_contact
SET contact_value = 'nurse.priya@mediq.com'
WHERE user_id     = '6eed7456-968e-49cb-9982-d11e7556e95f'
  AND contact_type = 'EMAIL';

-- 4. testpatient@mediq.com — new user (not in any prior seed)
INSERT INTO mediq_users.users
    (id, user_type, first_name, last_name, is_active, is_verified, password_hash)
VALUES
    ('c3d4e5f6-a7b8-4012-8def-345678901234', 'PATIENT', 'Test', 'Patient', true, true,
     '$2a$10$ZO3lJBeZXMqxjSgCCV8UCeVMHh8MxV2k5NSAJ4PZHBLinIpoqGcly')
ON CONFLICT (id) DO NOTHING;

INSERT INTO mediq_users.user_contact (user_id, contact_type, contact_value, is_primary)
VALUES ('c3d4e5f6-a7b8-4012-8def-345678901234', 'EMAIL', 'testpatient@mediq.com', true)
ON CONFLICT DO NOTHING;
