-- Create all mediq databases
-- mediq_users is created by POSTGRES_DB env var
-- Additional databases created here
SELECT 'CREATE DATABASE mediq_doctors'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'mediq_doctors')\gexec

SELECT 'CREATE DATABASE mediq_appointments'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'mediq_appointments')\gexec

SELECT 'CREATE DATABASE mediq_notifications'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'mediq_notifications')\gexec

SELECT 'CREATE DATABASE mediq_emr'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'mediq_emr')\gexec

SELECT 'CREATE DATABASE mediq_analytics'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'mediq_analytics')\gexec

GRANT ALL PRIVILEGES ON DATABASE mediq_doctors TO mediq;
GRANT ALL PRIVILEGES ON DATABASE mediq_appointments TO mediq;
GRANT ALL PRIVILEGES ON DATABASE mediq_notifications TO mediq;
GRANT ALL PRIVILEGES ON DATABASE mediq_emr TO mediq;
GRANT ALL PRIVILEGES ON DATABASE mediq_analytics TO mediq;
