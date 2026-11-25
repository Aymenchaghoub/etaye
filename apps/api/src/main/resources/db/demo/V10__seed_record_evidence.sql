-- Story 3.2: the synthetic evidence of the demonstration records (source revisions and per-statement assertions)
-- and its restoration by the reset. Applied only by the local demo package, after V9 created the tables.
--
-- The reset function of V5 truncates an explicit table list and the new tables hold foreign keys to
-- public.patient_record, so PostgreSQL would refuse the truncation until they are listed. The V5 body is replaced
-- here with those two tables added and nothing else changed; the applied V5 file stays untouched.

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
        public.source_revision, public.patient_record,
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

ALTER FUNCTION etaye_demo.restore_fixture(boolean) RENAME TO restore_fixture_v6;

-- The dataset keeps the design identifiers (etaye-design-v1): each seeded row is derived from the alias of the
-- design fixture, so a reset restores exactly the same identifiers. The fixture rows HIST-A-001 to HIST-A-003 are
-- aggregates, while the contract and the data dictionary model one assertion per statement: HIST-A-001 becomes its
-- two cited statements, and the two UNKNOWN rows become dated UNKNOWN statements without text or source. Their
-- recording instant is not in the design fixture; they are recorded at the dataset baseline (story 3.2 finding F-5).
-- The stored text of the weight revisions is the text the practitioner could read at the time, so that an as-of view
-- between the two revisions never reveals the later correction (story 3.2 finding F-4).
CREATE FUNCTION etaye_demo.seed_evidence() RETURNS void
LANGUAGE plpgsql SET search_path = pg_catalog
AS $$
DECLARE
    practice_a uuid := etaye_demo.fixture_id('PRA-A');
    practice_b uuid := etaye_demo.fixture_id('PRA-B');
    previous_scope text := current_setting('etaye.practice_id', true);
    baseline timestamptz := '2026-11-02 16:00:00+01';
BEGIN
    PERFORM set_config('etaye.practice_id', practice_a::text, true);
    INSERT INTO public.source_revision (id, practice_id, record_id, lineage_id, revision, reference, kind, label, excerpt,
        source_type, event_precision, event_at, event_timezone, recorded_at, author_label, locator, supersedes_id,
        correction_reason)
    SELECT etaye_demo.fixture_id(alias), practice_a, etaye_demo.fixture_id('R-A-001'),
        etaye_demo.fixture_id('lineage/' || lineage), revision, alias, kind, label, excerpt, source_type,
        'INSTANT', occurred, 'Europe/Paris', recorded, author, locator,
        CASE WHEN supersedes IS NOT NULL THEN etaye_demo.fixture_id(supersedes) END, reason
    FROM (VALUES
        ('SRC-A-001', 'SRC-A-001', 1, 'CONSULTATION_NOTE', 'Allergies déclarées lors de la consultation',
            'Aucune allergie connue, selon la déclaration recueillie ce jour.', 'PATIENT_REPORTED',
            '2026-09-14 09:30:00+02'::timestamptz, '2026-09-14 09:45:00+02'::timestamptz, 'Dr Nora Lefebvre',
            'section:allergies', NULL, NULL),
        ('SRC-A-002', 'SRC-A-002', 1, 'MANUAL_LAB_PANEL', 'Créatinine 96 µmol/L',
            'Créatinine 96 µmol/L, intervalle fourni 60–110 µmol/L.', 'DOCUMENT_REPORTED',
            '2026-09-14 08:00:00+02'::timestamptz, '2026-09-15 10:00:00+02'::timestamptz, 'Dr Nora Lefebvre',
            'LAB-A-001', NULL, NULL),
        ('SRC-A-003', 'SRC-A-003', 1, 'UPLOADED_DOCUMENT', 'Compte rendu de laboratoire fictif',
            'Exemple de compte rendu de laboratoire fictif : créatinine 104 µmol/L ; glucose 5,2 mmol/L.',
            'DOCUMENT_REPORTED', '2026-10-12 08:00:00+02'::timestamptz, '2026-10-13 10:00:00+02'::timestamptz,
            'Dr Sami Rahmani', 'DOC-A-001:page-1', NULL, NULL),
        ('SRC-A-004', 'SRC-A-004', 1, 'PRACTITIONER_MEASUREMENT', 'Pression artérielle 134/82 mmHg',
            'Pression artérielle mesurée au cabinet : 134/82 mmHg.', 'PRACTITIONER_OBSERVED',
            '2026-10-20 10:10:00+02'::timestamptz, '2026-10-20 10:12:00+02'::timestamptz, 'Dr Nora Lefebvre',
            'MEAS-A-002', NULL, NULL),
        ('SRC-A-005', 'SRC-A-005', 1, 'PATIENT_PREPARATION', 'Reçue de Claire Martin',
            'Je me souviens d’une réaction à la pénicilline dans ma jeunesse. Je ne sais plus laquelle.',
            'PATIENT_REPORTED', '2026-11-02 09:10:00+01'::timestamptz, '2026-11-02 09:10:00+01'::timestamptz,
            'Claire Martin (patiente)', 'PREP-A-001:allergies', NULL, NULL),
        ('SRC-A-006', 'SRC-A-006', 1, 'CONSULTATION_NOTE', 'Consultation de suivi · Dr Nora Lefebvre',
            'Consultation de suivi : résultats disponibles relus avec la patiente. Traitements actuels non renseignés dans ce jeu de données.',
            'PRACTITIONER_OBSERVED', '2026-10-20 10:30:00+02'::timestamptz, '2026-10-20 10:35:00+02'::timestamptz,
            'Dr Nora Lefebvre', 'CONS-A-001:report', NULL, NULL),
        ('SRC-A-007-v1', 'SRC-A-007', 1, 'PRACTITIONER_MEASUREMENT', 'Poids 86,4 kg', 'Poids saisi : 86,4 kg.',
            'PRACTITIONER_OBSERVED', '2026-10-20 10:12:00+02'::timestamptz, '2026-10-20 10:13:00+02'::timestamptz,
            'Dr Nora Lefebvre', 'MEAS-A-003:version-1', NULL, NULL),
        ('SRC-A-008', 'SRC-A-008', 1, 'PRACTITIONER_MEASUREMENT', 'Pression artérielle 128/80 mmHg',
            'Pression artérielle : 128/80 mmHg.', 'PRACTITIONER_OBSERVED',
            '2026-09-14 09:35:00+02'::timestamptz, '2026-09-14 09:36:00+02'::timestamptz, 'Dr Nora Lefebvre',
            'MEAS-A-001', NULL, NULL)
    ) sources(alias, lineage, revision, kind, label, excerpt, source_type, occurred, recorded, author, locator,
        supersedes, reason);

    -- The correction is inserted after the revision it supersedes: the order guard of V9 reads its predecessor.
    INSERT INTO public.source_revision (id, practice_id, record_id, lineage_id, revision, reference, kind, label, excerpt,
        source_type, event_precision, event_at, event_timezone, recorded_at, author_label, locator, supersedes_id,
        correction_reason)
    SELECT etaye_demo.fixture_id(alias), practice_a, etaye_demo.fixture_id('R-A-001'),
        etaye_demo.fixture_id('lineage/' || lineage), revision, alias, kind, label, excerpt, source_type,
        'INSTANT', occurred, 'Europe/Paris', recorded, author, locator,
        CASE WHEN supersedes IS NOT NULL THEN etaye_demo.fixture_id(supersedes) END, reason
    FROM (VALUES
        ('SRC-A-007-v2', 'SRC-A-007', 2, 'PRACTITIONER_MEASUREMENT', 'Poids 68,4 kg', 'Poids corrigé : 68,4 kg.',
            'PRACTITIONER_OBSERVED', '2026-10-20 10:12:00+02'::timestamptz, '2026-10-20 10:15:00+02'::timestamptz,
            'Dr Nora Lefebvre', 'MEAS-A-003:version-2', 'SRC-A-007-v1',
            'Correction d’une erreur de saisie, 86,4 devient 68,4 kg.')
    ) sources(alias, lineage, revision, kind, label, excerpt, source_type, occurred, recorded, author, locator,
        supersedes, reason);

    INSERT INTO public.clinical_assertion (id, practice_id, record_id, lineage_id, revision, category, assertion_kind,
        text, event_precision, event_at, event_timezone, source_revision_id, recorded_at)
    SELECT etaye_demo.fixture_id(alias), practice_a, etaye_demo.fixture_id(record_alias),
        etaye_demo.fixture_id('lineage/' || alias), 1, category, assertion_kind, statement,
        CASE WHEN occurred IS NULL THEN 'UNKNOWN' ELSE 'INSTANT' END, occurred,
        CASE WHEN occurred IS NULL THEN NULL ELSE 'Europe/Paris' END,
        CASE WHEN source IS NOT NULL THEN etaye_demo.fixture_id(source) END, recorded
    FROM (VALUES
        ('HIST-A-001/SRC-A-001', 'R-A-001', 'ALLERGY', 'EXPLICITLY_ABSENT',
            'Aucune allergie connue, selon la déclaration recueillie ce jour.',
            '2026-09-14 09:30:00+02'::timestamptz, 'SRC-A-001', '2026-09-14 09:45:00+02'::timestamptz),
        ('HIST-A-001/SRC-A-005', 'R-A-001', 'ALLERGY', 'PRESENT',
            'Je me souviens d’une réaction à la pénicilline dans ma jeunesse. Je ne sais plus laquelle.',
            '2026-11-02 09:10:00+01'::timestamptz, 'SRC-A-005', '2026-11-02 09:10:00+01'::timestamptz),
        ('HIST-A-002', 'R-A-001', 'REPORTED_TREATMENT', 'UNKNOWN', NULL, NULL, NULL, baseline),
        ('HIST-A-003', 'R-A-002', 'HISTORY', 'UNKNOWN', NULL, NULL, NULL, baseline)
    ) assertions(alias, record_alias, category, assertion_kind, statement, occurred, source, recorded);

    PERFORM set_config('etaye.practice_id', practice_b::text, true);
    INSERT INTO public.source_revision (id, practice_id, record_id, lineage_id, revision, reference, kind, label, excerpt,
        source_type, event_precision, event_at, event_timezone, recorded_at, author_label, locator)
    VALUES (etaye_demo.fixture_id('SRC-B-001'), practice_b, etaye_demo.fixture_id('R-B-001'),
        etaye_demo.fixture_id('lineage/SRC-B-001'), 1, 'SRC-B-001', 'CONSULTATION_NOTE',
        'Consultation · Dr Élodie Morel', 'Note fictive du Cabinet du Canal.', 'PRACTITIONER_OBSERVED', 'INSTANT',
        '2026-10-28 11:00:00+01'::timestamptz, 'Europe/Paris', '2026-10-28 11:20:00+01'::timestamptz,
        'Dr Élodie Morel', 'CONS-B-001:report');

    PERFORM set_config('etaye.practice_id', coalesce(previous_scope, ''), true);
END
$$;

-- Reloading the fixture restores the evidence with it; the repair mode of V6 (no reload) leaves it as it is.
CREATE FUNCTION etaye_demo.restore_fixture(reload boolean DEFAULT true) RETURNS void
LANGUAGE plpgsql SET search_path = pg_catalog
AS $$
BEGIN
    PERFORM etaye_demo.restore_fixture_v6(reload);
    IF reload THEN
        PERFORM etaye_demo.seed_evidence();
    END IF;
END
$$;

REVOKE ALL ON FUNCTION etaye_demo.restore_fixture(boolean) FROM PUBLIC;
REVOKE ALL ON FUNCTION etaye_demo.restore_fixture_v6(boolean) FROM PUBLIC;
REVOKE ALL ON FUNCTION etaye_demo.seed_evidence() FROM PUBLIC;

-- Seed the existing demonstration without reloading its fixture or closing its sessions.
SELECT etaye_demo.seed_evidence();
