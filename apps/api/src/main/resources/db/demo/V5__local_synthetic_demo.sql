-- Applied only by the local demo package, after its dedicated database has been provisioned.
DO $$
BEGIN
    IF current_database() <> 'etaye_demo' OR NOT EXISTS (
        SELECT FROM pg_namespace WHERE nspname = 'etaye_demo_bootstrap'
            AND nspowner = 'postgres'::regrole
    ) THEN
        RAISE EXCEPTION 'The synthetic fixture requires its dedicated PostgreSQL database';
    END IF;
END
$$;

GRANT USAGE ON SCHEMA etaye_demo TO etaye_runtime;

CREATE TABLE etaye_demo.fixture (
    singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
    version text NOT NULL,
    generation bigint NOT NULL CHECK (generation > 0),
    last_reset_at timestamptz
);
INSERT INTO etaye_demo.fixture (version, generation) VALUES ('etaye-design-v1', 1);
GRANT SELECT (version, generation) ON etaye_demo.fixture TO etaye_runtime;

CREATE TABLE etaye_demo.reset_receipt (
    key_digest text PRIMARY KEY,
    reason_digest text NOT NULL,
    correlation_id text NOT NULL,
    finished_at timestamptz NOT NULL
);

CREATE FUNCTION etaye_demo.fixture_id(alias text) RETURNS uuid
LANGUAGE sql IMMUTABLE STRICT SET search_path = pg_catalog
RETURN md5('etaye-design-v1/' || alias)::uuid;

CREATE FUNCTION etaye_demo.restore_fixture() RETURNS void
LANGUAGE plpgsql SET search_path = pg_catalog
AS $$
DECLARE
    stamp timestamptz := '2026-11-02 16:00:00+01';
    pending_expiry timestamptz := clock_timestamp() + interval '72 hours';
    practice_a uuid := etaye_demo.fixture_id('PRA-A');
    practice_b uuid := etaye_demo.fixture_id('PRA-B');
    nora uuid := etaye_demo.fixture_id('ACC-NORA');
    claire uuid := etaye_demo.fixture_id('ACC-CLAIRE');
    activated timestamptz := '2026-10-18 12:00:00+02';
    fixture_row record;
BEGIN
    -- Keep the table list explicit. New foreign keys must be reviewed before they can be reset.
    TRUNCATE public.spring_session_attributes, public.spring_session, public.invitation, public.portal_link,
        public.account_recovery_token, public.account_recovery_receipt, public.account_access_audit,
        public.idempotency_receipt, public.outbox_event, public.audit_event, public.patient_record,
        public.patient, public.membership, public.account, public.practice;

    INSERT INTO public.practice (id, display_name, created_at, updated_at) VALUES
        (practice_a, 'Cabinet des Tilleuls', stamp, stamp),
        (practice_b, 'Cabinet du Canal', stamp, stamp);

    -- The published fixture password is synthetic. Each account has its own PBKDF2 salt.
    INSERT INTO public.account (id, display_name, normalized_email, password_hash, created_at, updated_at)
    SELECT etaye_demo.fixture_id(alias), display_name, email, '{pbkdf2-sha256-600000}' || encoded, stamp, stamp
    FROM (VALUES
        ('ACC-NORA', 'Nora Lefebvre', 'nora.lefebvre@example.test', '2c2626211c1ddaaebbec53d658d9b6bc5bf5dead4d93cb8b03e405e8fde02c72835f91d48158ba4de4a76d93d9c03f54'),
        ('ACC-SAMI', 'Sami Rahmani', 'sami.rahmani@example.test', 'd6c1bf7e1813349bc4776842d8de5b8ca3d1af0e7c5e7bc7be77c8d42e259a7437b1331270af71108f4dad04f8f2eda3'),
        ('ACC-ELODIE', 'Élodie Morel', 'elodie.morel@example.test', '1993b2bb3a680ad21180d055c52ab36949232821694f8d92ad2ee0780b857e2cb9b8e09077706069450631ad9d9d0c51'),
        ('ACC-MILA', 'Mila Dubois', 'mila.dubois@example.test', 'db5973e1ad35fab9c706967575de617db18d4cc704a912a325dd0ea99d172fa010b3e711961f4d97c85c5f6ddcd05901'),
        ('ACC-CLAIRE', 'Claire Martin', 'claire.martin@example.test', '522e7ad46d64c68c970bc6ea66b3819cfbdb5c520b3b42718b176407e6b547bd04e4bc6549f689715ab0729efc2e244b'),
        ('ACC-OPERATOR', 'Opérateur de démonstration', 'operateur@example.test', 'b18ef59d0913e80bf2ffaab05fef773dfb99f314605f2891967c8e8a987b59f3dbdbd0ac631b8a60a9b523f7ee414137')
    ) accounts(alias, display_name, email, encoded);

    INSERT INTO public.membership (id, account_id, practice_id, capabilities, created_at, updated_at)
    SELECT etaye_demo.fixture_id(alias), etaye_demo.fixture_id(actor), practice_id, capabilities, stamp, stamp
    FROM (VALUES
        ('MEM-NORA-A', 'ACC-NORA', practice_a, ARRAY['CLINICAL_READ','CLINICAL_WRITE','CLINICAL_VALIDATE','PATIENT_PUBLISH','SCHEDULE_MANAGE','CLINICAL_AUDIT']),
        ('MEM-SAMI-A', 'ACC-SAMI', practice_a, ARRAY['CLINICAL_READ','CLINICAL_WRITE','CLINICAL_VALIDATE','PATIENT_PUBLISH','SCHEDULE_MANAGE','CLINICAL_AUDIT']),
        ('MEM-SAMI-B', 'ACC-SAMI', practice_b, ARRAY['CLINICAL_READ','CLINICAL_WRITE','CLINICAL_VALIDATE','SCHEDULE_MANAGE','CLINICAL_AUDIT']),
        ('MEM-ELODIE-B', 'ACC-ELODIE', practice_b, ARRAY['CLINICAL_READ','CLINICAL_WRITE','CLINICAL_VALIDATE','PATIENT_PUBLISH','SCHEDULE_MANAGE','CLINICAL_AUDIT']),
        ('MEM-MILA-A', 'ACC-MILA', practice_a, ARRAY['PRACTICE_SETTINGS','TEAM_MANAGE','ADMIN_AUDIT','SCHEDULE_MANAGE','PATIENT_REGISTER'])
    ) grants(alias, actor, practice_id, capabilities);

    FOR fixture_row IN SELECT * FROM (VALUES
        ('R-A-001', practice_a, 'Claire', 'Martin', DATE '1978-04-18', 8),
        ('R-A-002', practice_a, 'Louis', 'Bernard', DATE '1991-07-09', 1),
        ('R-A-003', practice_a, 'Marie', 'Collet', DATE '1990-03-12', 1),
        ('R-B-001', practice_b, 'Hugo', 'Petit', DATE '1986-02-25', 2)
    ) patients(alias, practice_id, given_name, family_name, birth_date, revision)
    LOOP
        PERFORM set_config('etaye.practice_id', fixture_row.practice_id::text, true);
        INSERT INTO public.patient (id, practice_id, first_name, last_name, date_of_birth, created_at, updated_at)
        VALUES (etaye_demo.fixture_id('patient/' || fixture_row.alias), fixture_row.practice_id,
            fixture_row.given_name, fixture_row.family_name, fixture_row.birth_date, stamp, stamp);
        INSERT INTO public.patient_record (id, practice_id, patient_id, record_revision, created_at, updated_at)
        VALUES (etaye_demo.fixture_id(fixture_row.alias), fixture_row.practice_id,
            etaye_demo.fixture_id('patient/' || fixture_row.alias), fixture_row.revision, stamp, stamp);
    END LOOP;

    PERFORM set_config('etaye.practice_id', practice_a::text, true);
    INSERT INTO public.portal_link (id, account_id, practice_id, record_id, verified_by, verified_at, state)
    VALUES (etaye_demo.fixture_id('PORTAL-CLAIRE'), claire, practice_a,
        etaye_demo.fixture_id('R-A-001'), nora, activated, 'ACTIVE');

    INSERT INTO public.invitation (id, practice_id, purpose, intended_recipient, token_digest,
        record_id, capabilities, issuer_id, state, expires_at, accepted_at, accepted_by,
        accepted_portal_link_id, created_at, updated_at)
    SELECT etaye_demo.fixture_id(alias), practice_a, purpose, email,
        encode(sha256(convert_to('etaye-fictional-invitation-' || alias, 'UTF8')), 'hex'),
        etaye_demo.fixture_id(record_alias), capabilities, nora, state, expires_at,
        CASE WHEN state = 'ACCEPTED' THEN activated END,
        CASE WHEN state = 'ACCEPTED' THEN claire END,
        CASE WHEN state = 'ACCEPTED' THEN etaye_demo.fixture_id('PORTAL-CLAIRE') END,
        stamp, stamp
    FROM (VALUES
        ('INV-TEAM-01', 'TEAM_MEMBERSHIP', 'alex.riviere@example.test', NULL, ARRAY['SCHEDULE_MANAGE'], 'PENDING', pending_expiry),
        ('INV-TEAM-02', 'TEAM_MEMBERSHIP', 'lea.marchand@example.test', NULL, ARRAY['SCHEDULE_MANAGE'], 'REVOKED', '2026-11-05 12:00:00+01'),
        ('INV-PORTAL-01', 'PATIENT_PORTAL', 'claire.martin@example.test', 'R-A-001', '{}', 'ACCEPTED', '2026-10-20 12:00:00+02'),
        ('INV-PORTAL-02', 'PATIENT_PORTAL', 'louis.bernard@example.test', 'R-A-002', '{}', 'EXPIRED', '2026-10-30 12:00:00+01'),
        ('INV-PORTAL-03', 'PATIENT_PORTAL', 'marie.collet@example.test', 'R-A-003', '{}', 'PENDING', pending_expiry),
        ('INV-PORTAL-04', 'PATIENT_PORTAL', 'chloe.dumas@example.test', 'R-A-002', '{}', 'PENDING', pending_expiry)
    ) invitations(alias, purpose, email, record_alias, capabilities, state, expires_at);
    PERFORM set_config('etaye.practice_id', '', true);
END
$$;

REVOKE ALL ON FUNCTION etaye_demo.restore_fixture() FROM PUBLIC;
SELECT etaye_demo.restore_fixture();

CREATE FUNCTION etaye_demo.reset_fixture(actor uuid, browser_session text, idem text, reason text, correlation text)
RETURNS TABLE (outcome text, correlation_id text, retry_after integer)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog SET lock_timeout = '1500ms'
AS $$
DECLARE
    receipt etaye_demo.reset_receipt%ROWTYPE;
    now_at timestamptz := clock_timestamp();
    next_at timestamptz;
BEGIN
    IF actor IS DISTINCT FROM etaye_demo.fixture_id('ACC-OPERATOR') THEN
        RETURN QUERY SELECT 'FORBIDDEN', correlation, 0;
        RETURN;
    END IF;
    IF NOT pg_try_advisory_xact_lock(98201, 91) THEN
        RETURN QUERY SELECT 'BUSY', correlation, 0;
        RETURN;
    END IF;
    IF NOT EXISTS (
        SELECT FROM public.spring_session s
        JOIN public.account a ON a.id::text = s.principal_name
        WHERE s.session_id = browser_session AND a.id = actor AND a.state = 'ACTIVE'
            AND s.expiry_time > extract(epoch FROM now_at) * 1000
    ) THEN
        RETURN QUERY SELECT 'UNAUTHENTICATED', correlation, 0;
        RETURN;
    END IF;

    SELECT r.* INTO receipt FROM etaye_demo.reset_receipt r
    WHERE r.key_digest = idem AND r.finished_at > now_at - interval '24 hours';
    IF FOUND THEN
        IF receipt.reason_digest = reason THEN
            RETURN QUERY SELECT 'REPLAYED', receipt.correlation_id, 0;
        ELSE
            RETURN QUERY SELECT 'CONFLICT', correlation, 0;
        END IF;
        RETURN;
    END IF;

    SELECT last_reset_at + interval '30 seconds' INTO next_at FROM etaye_demo.fixture;
    IF next_at > now_at THEN
        RETURN QUERY SELECT 'RATE_LIMITED', correlation, ceil(extract(epoch FROM next_at - now_at))::integer;
        RETURN;
    END IF;

    PERFORM etaye_demo.restore_fixture();
    now_at := clock_timestamp();
    UPDATE etaye_demo.fixture SET generation = generation + 1, last_reset_at = now_at;
    DELETE FROM etaye_demo.reset_receipt r WHERE r.finished_at <= now_at - interval '24 hours';
    INSERT INTO etaye_demo.reset_receipt VALUES (idem, reason, correlation, now_at);
    RETURN QUERY SELECT 'RESET', correlation, 0;
EXCEPTION WHEN lock_not_available THEN
    RETURN QUERY SELECT 'BUSY', correlation, 0;
END
$$;

REVOKE ALL ON FUNCTION etaye_demo.reset_fixture(uuid, text, text, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION etaye_demo.reset_fixture(uuid, text, text, text, text) TO etaye_runtime;
