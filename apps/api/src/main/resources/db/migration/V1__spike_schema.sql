-- V1__spike_schema.sql
-- Étaye Walking Skeleton minimal spike schema

-- Table: practice
CREATE TABLE practice (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Table: patient
CREATE TABLE patient (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    practice_id UUID NOT NULL REFERENCES practice(id) ON DELETE RESTRICT,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_patient_practice_id UNIQUE (practice_id, id),
    PRIMARY KEY (id)
);

-- RLS on practice-owned table: patient
ALTER TABLE patient ENABLE ROW LEVEL SECURITY;
ALTER TABLE patient FORCE ROW LEVEL SECURITY;

CREATE POLICY patient_practice_isolation_policy ON patient
    FOR ALL
    USING (practice_id = NULLIF(current_setting('etaye.practice_id', true), '')::uuid)
    WITH CHECK (practice_id = NULLIF(current_setting('etaye.practice_id', true), '')::uuid);

-- Identity schema (account & membership) for bootstrap authentication / lookup
CREATE TABLE account (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE membership (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    practice_id UUID NOT NULL REFERENCES practice(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL,
    capabilities VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_membership_account_practice UNIQUE (account_id, practice_id)
);

-- Spring Session JDBC minimal schema for PostgreSQL (versioned in Flyway, auto-init disabled)
CREATE TABLE spring_session (
    primary_id CHAR(36) NOT NULL PRIMARY KEY,
    session_id CHAR(36) NOT NULL UNIQUE,
    creation_time BIGINT NOT NULL,
    last_access_time BIGINT NOT NULL,
    max_inactive_interval INT NOT NULL,
    expiry_time BIGINT NOT NULL,
    principal_name VARCHAR(100)
);

CREATE INDEX spring_session_ix1 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix2 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
    session_primary_id CHAR(36) NOT NULL REFERENCES spring_session(primary_id) ON DELETE CASCADE,
    attribute_name VARCHAR(200) NOT NULL,
    attribute_bytes BYTEA NOT NULL,
    PRIMARY KEY (session_primary_id, attribute_name)
);

-- Minimal Grants for runtime role (etaye_runtime)
GRANT SELECT ON practice TO etaye_runtime;
GRANT SELECT, INSERT ON patient TO etaye_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON spring_session TO etaye_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON spring_session_attributes TO etaye_runtime;

-- Identity role grants: restricted column-level grant on account (password_hash is NEVER selectable)
GRANT SELECT(id, email, created_at) ON account TO etaye_identity;
GRANT SELECT ON membership TO etaye_identity;

-- Strict least-privilege enforcement
REVOKE ALL ON account FROM etaye_runtime;
REVOKE ALL ON membership FROM etaye_runtime;
REVOKE ALL ON patient FROM etaye_identity;
