-- V1__initial_schema.sql
-- Étaye Walking Skeleton and Secure Request Path: initial production schema

-- Table: practice (Tenant boundary)
CREATE TABLE practice (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name VARCHAR(255) NOT NULL,
    timezone VARCHAR(100) NOT NULL DEFAULT 'Europe/Paris',
    booking_policy JSONB NOT NULL DEFAULT '{}'::jsonb,
    state VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_practice_state CHECK (state IN ('ACTIVE', 'SUSPENDED'))
);

-- Table: account (Global authentication identity)
CREATE TABLE account (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    normalized_email VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    state VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    locale VARCHAR(10) NOT NULL DEFAULT 'fr',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_account_state CHECK (state IN ('ACTIVE', 'SUSPENDED'))
);

-- Table: membership (Account access to one practice with contract capabilities)
CREATE TABLE membership (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    practice_id UUID NOT NULL REFERENCES practice(id) ON DELETE CASCADE,
    state VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    capabilities TEXT[] NOT NULL DEFAULT '{}',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_membership_account_practice UNIQUE (account_id, practice_id),
    CONSTRAINT ck_membership_state CHECK (state IN ('ACTIVE', 'SUSPENDED')),
    CONSTRAINT ck_membership_capabilities CHECK (
        capabilities <@ ARRAY[
            'TEAM_MANAGE', 'PRACTICE_SETTINGS', 'ADMIN_AUDIT',
            'CLINICAL_READ', 'CLINICAL_WRITE', 'SCHEDULE_MANAGE',
            'CLINICAL_VALIDATE', 'PATIENT_PUBLISH', 'CLINICAL_AUDIT',
            'PATIENT_REGISTER'
        ]::text[]
    )
);

-- Table: patient (Practice-local patient identity)
CREATE TABLE patient (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    practice_id UUID NOT NULL REFERENCES practice(id) ON DELETE RESTRICT,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    date_of_birth DATE NOT NULL,
    contact_email VARCHAR(255),
    phone VARCHAR(40),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_patient_practice_id UNIQUE (practice_id, id),
    PRIMARY KEY (id)
);

-- Table: patient_record (Longitudinal record boundary)
CREATE TABLE patient_record (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    practice_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    record_revision INTEGER NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_patient_record PRIMARY KEY (id),
    CONSTRAINT uq_patient_record_practice_id UNIQUE (practice_id, id),
    CONSTRAINT uq_patient_record_practice_patient UNIQUE (practice_id, patient_id),
    CONSTRAINT fk_patient_record_patient FOREIGN KEY (practice_id, patient_id)
        REFERENCES patient(practice_id, id) ON DELETE RESTRICT
);

-- Table: audit_event (Scoped append-only accountability)
CREATE TABLE audit_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    practice_id UUID NOT NULL,
    actor_account_id UUID,
    service_identity VARCHAR(100),
    event_type VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id UUID,
    resource_version BIGINT,
    outcome VARCHAR(50) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_id VARCHAR(200) NOT NULL,
    safe_metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

-- Table: outbox_event (Transactional outbox for after-commit distribution)
CREATE TABLE outbox_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    practice_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    resource_id UUID NOT NULL,
    resource_version BIGINT NOT NULL,
    dedupe_key VARCHAR(255) NOT NULL UNIQUE,
    safe_payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivery_state VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    attempt INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Table: idempotency_receipt (Safe retry without duplicate business effects)
CREATE TABLE idempotency_receipt (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    practice_id UUID NOT NULL,
    actor_id UUID NOT NULL,
    operation VARCHAR(100) NOT NULL,
    key_digest VARCHAR(255) NOT NULL,
    body_hash VARCHAR(255) NOT NULL,
    response_resource UUID,
    outcome VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_idempotency_receipt UNIQUE (actor_id, practice_id, operation, key_digest),
    CONSTRAINT fk_idempotency_receipt_patient FOREIGN KEY (practice_id, response_resource)
        REFERENCES patient(practice_id, id) ON DELETE RESTRICT
);

-- Spring Session JDBC official schema for PostgreSQL
CREATE TABLE spring_session (
    primary_id CHAR(36) NOT NULL,
    session_id CHAR(36) NOT NULL,
    creation_time BIGINT NOT NULL,
    last_access_time BIGINT NOT NULL,
    max_inactive_interval INT NOT NULL,
    expiry_time BIGINT NOT NULL,
    principal_name VARCHAR(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
    session_primary_id CHAR(36) NOT NULL,
    attribute_name VARCHAR(200) NOT NULL,
    attribute_bytes BYTEA NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id) REFERENCES spring_session(primary_id) ON DELETE CASCADE
);

-- Row-Level Security on authentication tables (bounded identity lookup)
ALTER TABLE account ENABLE ROW LEVEL SECURITY;
ALTER TABLE account FORCE ROW LEVEL SECURITY;

CREATE POLICY account_identity_isolation_policy ON account
    FOR ALL
    TO etaye_identity
    USING (id = NULLIF(current_setting('etaye.account_id', true), '')::uuid)
    WITH CHECK (id = NULLIF(current_setting('etaye.account_id', true), '')::uuid);

CREATE POLICY account_owner_policy ON account
    FOR ALL
    TO etaye_owner
    USING (true)
    WITH CHECK (true);

ALTER TABLE membership ENABLE ROW LEVEL SECURITY;
ALTER TABLE membership FORCE ROW LEVEL SECURITY;

CREATE POLICY membership_identity_isolation_policy ON membership
    FOR ALL
    TO etaye_identity
    USING (account_id = NULLIF(current_setting('etaye.account_id', true), '')::uuid)
    WITH CHECK (account_id = NULLIF(current_setting('etaye.account_id', true), '')::uuid);

CREATE POLICY membership_owner_policy ON membership
    FOR ALL
    TO etaye_owner
    USING (true)
    WITH CHECK (true);

-- Row-Level Security on practice-owned tables
ALTER TABLE patient ENABLE ROW LEVEL SECURITY;
ALTER TABLE patient FORCE ROW LEVEL SECURITY;
CREATE POLICY patient_practice_isolation_policy ON patient
    FOR ALL
    USING (practice_id = NULLIF(current_setting('etaye.practice_id', true), '')::uuid)
    WITH CHECK (practice_id = NULLIF(current_setting('etaye.practice_id', true), '')::uuid);

ALTER TABLE patient_record ENABLE ROW LEVEL SECURITY;
ALTER TABLE patient_record FORCE ROW LEVEL SECURITY;
CREATE POLICY patient_record_practice_isolation_policy ON patient_record
    FOR ALL
    USING (practice_id = NULLIF(current_setting('etaye.practice_id', true), '')::uuid)
    WITH CHECK (practice_id = NULLIF(current_setting('etaye.practice_id', true), '')::uuid);

ALTER TABLE audit_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_event FORCE ROW LEVEL SECURITY;
CREATE POLICY audit_event_practice_isolation_policy ON audit_event
    FOR ALL
    USING (practice_id = NULLIF(current_setting('etaye.practice_id', true), '')::uuid)
    WITH CHECK (practice_id = NULLIF(current_setting('etaye.practice_id', true), '')::uuid);

ALTER TABLE outbox_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox_event FORCE ROW LEVEL SECURITY;
CREATE POLICY outbox_event_practice_isolation_policy ON outbox_event
    FOR ALL
    USING (practice_id = NULLIF(current_setting('etaye.practice_id', true), '')::uuid)
    WITH CHECK (practice_id = NULLIF(current_setting('etaye.practice_id', true), '')::uuid);

ALTER TABLE idempotency_receipt ENABLE ROW LEVEL SECURITY;
ALTER TABLE idempotency_receipt FORCE ROW LEVEL SECURITY;
CREATE POLICY idempotency_receipt_practice_isolation_policy ON idempotency_receipt
    FOR ALL
    USING (practice_id = NULLIF(current_setting('etaye.practice_id', true), '')::uuid)
    WITH CHECK (practice_id = NULLIF(current_setting('etaye.practice_id', true), '')::uuid);

-- Minimal Grants for runtime role (etaye_runtime):
-- Least privilege: no SELECT on practice, audit_event, or outbox_event (INSERT only on last two)
GRANT SELECT, INSERT ON patient TO etaye_runtime;
GRANT SELECT, INSERT ON patient_record TO etaye_runtime;
GRANT INSERT ON audit_event TO etaye_runtime;
GRANT INSERT ON outbox_event TO etaye_runtime;
GRANT SELECT, INSERT ON idempotency_receipt TO etaye_runtime;

-- Grants for Spring Session JDBC on etaye_runtime
GRANT SELECT, INSERT, UPDATE, DELETE ON spring_session TO etaye_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON spring_session_attributes TO etaye_runtime;

-- Identity role grants: restricted column-level grant on account (password_hash is NEVER selectable)
GRANT SELECT(id, normalized_email, display_name, state, locale, created_at, updated_at) ON account TO etaye_identity;
GRANT SELECT(id, account_id, practice_id, state, capabilities, created_at, updated_at) ON membership TO etaye_identity;

-- Strict least-privilege enforcement
REVOKE ALL ON practice FROM etaye_runtime;
REVOKE ALL ON account FROM etaye_runtime;
REVOKE ALL ON membership FROM etaye_runtime;
REVOKE ALL ON patient FROM etaye_identity;
REVOKE ALL ON patient_record FROM etaye_identity;
REVOKE ALL ON audit_event FROM etaye_identity;
REVOKE ALL ON outbox_event FROM etaye_identity;
REVOKE ALL ON idempotency_receipt FROM etaye_identity;
