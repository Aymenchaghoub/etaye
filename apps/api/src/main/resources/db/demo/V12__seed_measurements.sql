-- Story 3.3: the synthetic measurements of the demonstration records and their restoration by the reset. Applied only by
-- the local demo package, after V11 created the table and V10 seeded the source revisions they cite.
--
-- The reset function of V5 truncates an explicit table list and `measurement` holds foreign keys to
-- public.patient_record and public.source_revision, so PostgreSQL would refuse the truncation until it is listed. The
-- V10 body is replaced here with that table added and nothing else changed; the applied V5 and V10 files stay untouched.

CREATE OR REPLACE FUNCTION etaye_demo.restore_fixture_v5() RETURNS void
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
        public.idempotency_receipt, public.outbox_event, public.audit_event, public.clinical_assertion,
        public.source_revision, public.measurement, public.patient_record,
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

-- The design fixture rows MEAS-A-001 to MEAS-A-003 become one measurement per component, as the contract and the data
-- dictionary model them: a blood-pressure reading is two components that share a group, and the weight is one lineage of
-- two revisions (86.4 kg, then 68.4 kg). Each row cites the source revision of story 3.2 that supports it, and the
-- correction reason is the one stored on the source revision, so there is a single wording.
CREATE FUNCTION etaye_demo.seed_measurements() RETURNS void
LANGUAGE plpgsql SET search_path = pg_catalog
AS $$
DECLARE
    practice_a uuid := etaye_demo.fixture_id('PRA-A');
    previous_scope text := current_setting('etaye.practice_id', true);
BEGIN
    PERFORM set_config('etaye.practice_id', practice_a::text, true);
    INSERT INTO public.measurement (id, practice_id, record_id, lineage_id, revision, concept, value_decimal, unit,
        observed_at, recorded_at, author_label, source_revision_id, group_id)
    SELECT etaye_demo.fixture_id(alias), practice_a, etaye_demo.fixture_id('R-A-001'),
        etaye_demo.fixture_id('lineage/' || lineage), 1, concept, reading, unit, observed, recorded,
        'Dr Nora Lefebvre', etaye_demo.fixture_id(source),
        CASE WHEN grouped IS NOT NULL THEN etaye_demo.fixture_id('group/' || grouped) END
    FROM (VALUES
        ('MEAS-A-001/SYS', 'MEAS-A-001/SYS', 'SYSTOLIC_BLOOD_PRESSURE', 128::numeric, 'mmHg',
            '2026-09-14 09:35:00+02'::timestamptz, '2026-09-14 09:36:00+02'::timestamptz, 'SRC-A-008', 'MEAS-A-001'),
        ('MEAS-A-001/DIA', 'MEAS-A-001/DIA', 'DIASTOLIC_BLOOD_PRESSURE', 80::numeric, 'mmHg',
            '2026-09-14 09:35:00+02'::timestamptz, '2026-09-14 09:36:00+02'::timestamptz, 'SRC-A-008', 'MEAS-A-001'),
        ('MEAS-A-002/SYS', 'MEAS-A-002/SYS', 'SYSTOLIC_BLOOD_PRESSURE', 134::numeric, 'mmHg',
            '2026-10-20 10:10:00+02'::timestamptz, '2026-10-20 10:12:00+02'::timestamptz, 'SRC-A-004', 'MEAS-A-002'),
        ('MEAS-A-002/DIA', 'MEAS-A-002/DIA', 'DIASTOLIC_BLOOD_PRESSURE', 82::numeric, 'mmHg',
            '2026-10-20 10:10:00+02'::timestamptz, '2026-10-20 10:12:00+02'::timestamptz, 'SRC-A-004', 'MEAS-A-002'),
        ('MEAS-A-003-v1', 'MEAS-A-003', 'WEIGHT', 86.4::numeric, 'kg',
            '2026-10-20 10:12:00+02'::timestamptz, '2026-10-20 10:13:00+02'::timestamptz, 'SRC-A-007-v1', NULL)
    ) readings(alias, lineage, concept, reading, unit, observed, recorded, source, grouped);

    -- The correction is inserted after the revision it supersedes: the order guard of V11 reads its predecessor.
    INSERT INTO public.measurement (id, practice_id, record_id, lineage_id, revision, concept, value_decimal, unit,
        observed_at, recorded_at, author_label, source_revision_id, supersedes_id, correction_reason)
    VALUES (etaye_demo.fixture_id('MEAS-A-003-v2'), practice_a, etaye_demo.fixture_id('R-A-001'),
        etaye_demo.fixture_id('lineage/MEAS-A-003'), 2, 'WEIGHT', 68.4, 'kg',
        '2026-10-20 10:12:00+02', '2026-10-20 10:15:00+02', 'Dr Nora Lefebvre', etaye_demo.fixture_id('SRC-A-007-v2'),
        etaye_demo.fixture_id('MEAS-A-003-v1'), 'Correction d’une erreur de saisie, 86,4 devient 68,4 kg.');

    PERFORM set_config('etaye.practice_id', coalesce(previous_scope, ''), true);
END
$$;

-- Reloading the fixture restores the evidence and the measurements with it; the repair mode of V6 leaves them as they are.
CREATE OR REPLACE FUNCTION etaye_demo.restore_fixture(reload boolean DEFAULT true) RETURNS void
LANGUAGE plpgsql SET search_path = pg_catalog
AS $$
BEGIN
    PERFORM etaye_demo.restore_fixture_v6(reload);
    IF reload THEN
        PERFORM etaye_demo.seed_evidence();
        PERFORM etaye_demo.seed_measurements();
    END IF;
END
$$;

REVOKE ALL ON FUNCTION etaye_demo.restore_fixture(boolean) FROM PUBLIC;
REVOKE ALL ON FUNCTION etaye_demo.seed_measurements() FROM PUBLIC;

-- Seed the existing demonstration without reloading its fixture or closing its sessions.
SELECT etaye_demo.seed_measurements();
