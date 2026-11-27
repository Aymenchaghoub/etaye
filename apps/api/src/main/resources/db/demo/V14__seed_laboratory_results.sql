-- Reset includes the lab table because it owns foreign keys to the record and its sources.
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
        public.source_revision, public.measurement, public.lab_result, public.patient_record,
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

CREATE FUNCTION etaye_demo.seed_laboratory_results() RETURNS void
LANGUAGE plpgsql SET search_path = pg_catalog
AS $$
DECLARE
    practice_a uuid := etaye_demo.fixture_id('PRA-A');
    claire uuid := etaye_demo.fixture_id('R-A-001');
    marie uuid := etaye_demo.fixture_id('R-A-003');
    previous_scope text := current_setting('etaye.practice_id', true);
    note text := 'Correction de transcription du résultat : chiffres inversés.';
    comparator_note text := 'Analyse B : < 0,060 unité B. Intervalle non fourni.';
BEGIN
    PERFORM set_config('etaye.practice_id', practice_a::text, true);

    -- The newer immutable excerpt supplies the ranges that the first synthetic excerpt left unspecified.
    INSERT INTO public.source_revision (id, practice_id, record_id, lineage_id, revision, reference, kind, label, excerpt,
        source_type, event_precision, event_at, event_date, event_timezone, recorded_at, author_label, locator,
        supersedes_id, correction_reason)
    SELECT etaye_demo.fixture_id('SRC-A-003-v2'), practice_id, record_id, lineage_id, 2, reference, kind, label,
        'Créatinine 104 µmol/L, intervalle fourni 60–110 µmol/L ; glucose 5,2 mmol/L, intervalle fourni 4,1–6,1 mmol/L.',
        source_type, event_precision, event_at, event_date, event_timezone,
        '2026-10-13 10:01:00+02', author_label, locator, id, 'Extrait complété avec les deux intervalles fournis.'
    FROM public.source_revision WHERE id = etaye_demo.fixture_id('SRC-A-003');

    INSERT INTO public.lab_result (id, practice_id, record_id, lineage_id, revision, analyte, numeric_value, unit,
        lower_bound, upper_bound, interval_unit, interval_source, specimen_at, recorded_at, author_label, source_revision_id)
    SELECT etaye_demo.fixture_id(alias), practice_a, claire, etaye_demo.fixture_id('lineage/' || alias),
        1, analyte, reading, unit, lower_bound, upper_bound, unit, 'Laboratoire fictif',
        specimen, recorded, author_label, etaye_demo.fixture_id(source)
    FROM (VALUES
        ('LAB-A-001', 'Créatinine', 96::numeric, 'µmol/L', 60::numeric, 110::numeric,
            '2026-09-14 08:00:00+02'::timestamptz, '2026-09-15 10:00:00+02'::timestamptz, 'Dr Nora Lefebvre', 'SRC-A-002'),
        ('LAB-A-002', 'Créatinine', 104, 'µmol/L', 60, 110,
            '2026-10-12 08:00:00+02'::timestamptz, '2026-10-13 10:01:00+02'::timestamptz, 'Dr Sami Rahmani', 'SRC-A-003-v2'),
        ('LAB-A-003', 'Glucose', 5.2, 'mmol/L', 4.1, 6.1,
            '2026-10-12 08:00:00+02'::timestamptz, '2026-10-13 10:01:00+02'::timestamptz, 'Dr Sami Rahmani', 'SRC-A-003-v2')
    ) entries(alias, analyte, reading, unit, lower_bound, upper_bound, specimen, recorded, author_label, source);

    -- Marie carries the probe cases, leaving Claire's three canonical results and Louis's empty record intact.
    INSERT INTO public.source_revision (id, practice_id, record_id, lineage_id, revision, reference, kind, label, excerpt,
        source_type, event_precision, event_date, event_timezone, recorded_at, author_label, locator)
    SELECT etaye_demo.fixture_id(alias), practice_a, marie, etaye_demo.fixture_id('lineage/' || alias), 1, alias,
        'MANUAL_LAB_PANEL', 'Résultats fictifs transcrits', excerpt, 'DOCUMENT_REPORTED', precision, date_value,
        CASE WHEN date_value IS NOT NULL THEN 'Europe/Paris' END, recorded, 'Dr Nora Lefebvre', 'Extrait synthétique'
    FROM (VALUES
        ('SRC-A-LAB-CORR-1', 'Analyse A : 7,40 unité A, intervalle non fourni.', 'DATE',
            DATE '2026-10-20', '2026-10-21 10:00:00+02'::timestamptz),
        ('SRC-A-LAB-COMPARATOR', comparator_note, 'DATE', DATE '2026-10-22', '2026-10-23 10:00:00+02'::timestamptz),
        ('SRC-A-LAB-TEXT', 'Analyse C : Non détecté dans le prélèvement. Unité et intervalle non fournis.', 'UNKNOWN',
            NULL, '2026-10-23 10:00:00+02'::timestamptz),
        ('SRC-A-LAB-TREND', 'Analyse D, méthode A, à jeun le matin : 6,80 unité A le 14/10 et 7,04 le 22/10. Intervalle fourni 2–8 unité A.',
            'DATE', DATE '2026-10-22', '2026-10-23 10:00:00+02'::timestamptz)
    ) sources(alias, excerpt, precision, date_value, recorded);

    INSERT INTO public.source_revision (id, practice_id, record_id, lineage_id, revision, reference, kind, label, excerpt,
        source_type, event_precision, event_date, event_timezone, recorded_at, author_label, supersedes_id, correction_reason)
    VALUES (etaye_demo.fixture_id('SRC-A-LAB-CORR-2'), practice_a, marie, etaye_demo.fixture_id('lineage/SRC-A-LAB-CORR-1'),
        2, 'SRC-A-LAB-CORR-2', 'MANUAL_LAB_PANEL', 'Résultat fictif corrigé', 'Analyse A : 7,04 unité A, intervalle non fourni.',
        'DOCUMENT_REPORTED', 'DATE', DATE '2026-10-20', 'Europe/Paris', '2026-10-21 10:15:00+02', 'Dr Nora Lefebvre',
        etaye_demo.fixture_id('SRC-A-LAB-CORR-1'), note);

    INSERT INTO public.lab_result (id, practice_id, record_id, lineage_id, revision, analyte, numeric_value,
        comparator, unit, specimen_on, recorded_at, author_label, source_revision_id)
    VALUES
        (etaye_demo.fixture_id('LAB-A-PROOF-1'), practice_a, marie, etaye_demo.fixture_id('lineage/LAB-A-PROOF'), 1,
            'Analyse A', 7.40, NULL, 'unité A', DATE '2026-10-20', '2026-10-21 10:00:00+02',
            'Dr Nora Lefebvre', etaye_demo.fixture_id('SRC-A-LAB-CORR-1')),
        (etaye_demo.fixture_id('LAB-A-COMPARATOR'), practice_a, marie, etaye_demo.fixture_id('lineage/LAB-A-COMPARATOR'), 1,
            'Analyse B', 0.060, 'LT', 'unité B', DATE '2026-10-22', '2026-10-23 10:00:00+02',
            'Dr Nora Lefebvre', etaye_demo.fixture_id('SRC-A-LAB-COMPARATOR'));

    INSERT INTO public.lab_result (id, practice_id, record_id, lineage_id, revision, analyte, numeric_value,
        unit, specimen_on, recorded_at, author_label, source_revision_id, supersedes_id, correction_reason)
    VALUES (etaye_demo.fixture_id('LAB-A-PROOF-2'), practice_a, marie, etaye_demo.fixture_id('lineage/LAB-A-PROOF'), 2,
        'Analyse A', 7.04, 'unité A', DATE '2026-10-20', '2026-10-21 10:15:00+02',
        'Dr Nora Lefebvre', etaye_demo.fixture_id('SRC-A-LAB-CORR-2'), etaye_demo.fixture_id('LAB-A-PROOF-1'), note);

    INSERT INTO public.lab_result (id, practice_id, record_id, lineage_id, revision, analyte, qualitative_value,
        recorded_at, author_label, source_revision_id)
    VALUES (etaye_demo.fixture_id('LAB-A-TEXT'), practice_a, marie, etaye_demo.fixture_id('lineage/LAB-A-TEXT'), 1,
        'Analyse C', 'Non détecté dans le prélèvement', '2026-10-23 10:00:00+02',
        'Dr Nora Lefebvre', etaye_demo.fixture_id('SRC-A-LAB-TEXT'));

    INSERT INTO public.lab_result (id, practice_id, record_id, lineage_id, revision, analyte, numeric_value, unit,
        lower_bound, upper_bound, interval_unit, interval_source, interval_context, comparison_method, comparison_fasting, comparison_window,
        specimen_at, recorded_at, author_label, source_revision_id)
    SELECT etaye_demo.fixture_id(alias), practice_a, marie, etaye_demo.fixture_id('lineage/' || alias), 1,
        'Analyse D', reading, 'unité A', 2, 8, 'unité A', 'Laboratoire fictif', 'Méthode A, à jeun, matin', 'Méthode A', 'À jeun', 'Matin',
        specimen, '2026-10-23 10:00:00+02', 'Dr Nora Lefebvre', etaye_demo.fixture_id('SRC-A-LAB-TREND')
    FROM (VALUES ('LAB-A-TREND-1', 6.80::numeric, '2026-10-14 08:20:00+02'::timestamptz),
                 ('LAB-A-TREND-2', 7.04, '2026-10-22 08:15:00+02'::timestamptz)) entries(alias, reading, specimen);

    PERFORM set_config('etaye.practice_id', coalesce(previous_scope, ''), true);
END
$$;

CREATE OR REPLACE FUNCTION etaye_demo.restore_fixture(reload boolean DEFAULT true) RETURNS void
LANGUAGE plpgsql SET search_path = pg_catalog
AS $$
BEGIN
    PERFORM etaye_demo.restore_fixture_v6(reload);
    IF reload THEN
        PERFORM etaye_demo.seed_evidence();
        PERFORM etaye_demo.seed_measurements();
        PERFORM etaye_demo.seed_laboratory_results();
    END IF;
END
$$;

REVOKE ALL ON FUNCTION etaye_demo.restore_fixture(boolean) FROM PUBLIC;
REVOKE ALL ON FUNCTION etaye_demo.seed_laboratory_results() FROM PUBLIC;

SELECT etaye_demo.seed_laboratory_results();
