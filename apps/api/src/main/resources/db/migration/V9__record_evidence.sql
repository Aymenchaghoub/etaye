-- Story 3.2: immutable evidence of a patient record (source revisions) and the per-statement assertions that
-- cite them. The runtime role only reads these tables: entry and correction arrive with increment 2 (D-60).
-- Every row is practice and record owned, the keys are composite so a row can never point at another practice or
-- record, and forced row-level security applies on top (BR-001, data model).

-- A stored zone is always one PostgreSQL knows: a misspelled zone would otherwise make every read of the record fail.
CREATE FUNCTION public.valid_time_zone(zone text) RETURNS boolean
LANGUAGE plpgsql STABLE
AS $$
BEGIN
    PERFORM now() AT TIME ZONE zone;
    RETURN true;
EXCEPTION WHEN invalid_parameter_value THEN
    RETURN false;
END
$$;

CREATE TABLE source_revision (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    practice_id UUID NOT NULL,
    record_id UUID NOT NULL,
    -- The revisions of one source share a lineage; a correction appends the next revision (BR-008).
    lineage_id UUID NOT NULL,
    revision INTEGER NOT NULL,
    -- Human identifier shown with the revision, for example SRC-A-005.
    reference VARCHAR(100) NOT NULL,
    kind VARCHAR(40) NOT NULL,
    label VARCHAR(500) NOT NULL,
    -- The dated plain text stored with this revision. Never interpreted as markup.
    excerpt VARCHAR(5000),
    source_type VARCHAR(30) NOT NULL,
    event_precision VARCHAR(10) NOT NULL,
    event_at TIMESTAMPTZ,
    event_date DATE,
    event_timezone VARCHAR(80),
    -- Server-provided recording instant: it alone decides what an asOf view contains (UD-32).
    recorded_at TIMESTAMPTZ NOT NULL,
    author_label VARCHAR(200) NOT NULL,
    locator VARCHAR(500),
    supersedes_id UUID,
    correction_reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_source_revision PRIMARY KEY (id),
    CONSTRAINT uq_source_revision_practice_id UNIQUE (practice_id, id),
    CONSTRAINT uq_source_revision_record_id UNIQUE (practice_id, record_id, id),
    CONSTRAINT uq_source_revision_lineage_id UNIQUE (practice_id, record_id, lineage_id, id),
    CONSTRAINT uq_source_revision_lineage_revision UNIQUE (practice_id, record_id, lineage_id, revision),
    CONSTRAINT fk_source_revision_record FOREIGN KEY (practice_id, record_id)
        REFERENCES patient_record (practice_id, id) ON DELETE RESTRICT,
    -- A superseding revision stays in its own record and lineage.
    CONSTRAINT fk_source_revision_supersedes FOREIGN KEY (practice_id, record_id, lineage_id, supersedes_id)
        REFERENCES source_revision (practice_id, record_id, lineage_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_source_revision_type CHECK (source_type IN ('PATIENT_REPORTED', 'PRACTITIONER_OBSERVED', 'DOCUMENT_REPORTED')),
    CONSTRAINT ck_source_revision_kind CHECK (kind IN ('CONSULTATION_NOTE', 'PRACTITIONER_MEASUREMENT', 'MANUAL_LAB_PANEL',
        'UPLOADED_DOCUMENT', 'PATIENT_PREPARATION')),
    CONSTRAINT ck_source_revision_text CHECK (btrim(reference) <> '' AND btrim(label) <> '' AND btrim(author_label) <> ''
        AND (excerpt IS NULL OR btrim(excerpt) <> '')),
    -- An unknown event date carries no instant and no date, never a fabricated one; a date-only event names its zone.
    CONSTRAINT ck_source_revision_event CHECK (
        (event_precision = 'INSTANT' AND event_at IS NOT NULL AND event_date IS NULL)
        OR (event_precision = 'DATE' AND event_date IS NOT NULL AND event_at IS NULL AND event_timezone IS NOT NULL)
        OR (event_precision = 'UNKNOWN' AND event_at IS NULL AND event_date IS NULL AND event_timezone IS NULL)),
    CONSTRAINT ck_source_revision_zone CHECK (event_timezone IS NULL OR public.valid_time_zone(event_timezone)),
    CONSTRAINT ck_source_revision_correction CHECK (
        (revision = 1 AND supersedes_id IS NULL AND correction_reason IS NULL)
        OR (revision > 1 AND supersedes_id IS NOT NULL AND correction_reason IS NOT NULL AND btrim(correction_reason) <> ''))
);

CREATE INDEX idx_source_revision_record_recorded ON source_revision (practice_id, record_id, recorded_at);

CREATE TABLE clinical_assertion (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    practice_id UUID NOT NULL,
    record_id UUID NOT NULL,
    lineage_id UUID NOT NULL,
    revision INTEGER NOT NULL,
    category VARCHAR(30) NOT NULL,
    assertion_kind VARCHAR(30) NOT NULL,
    text VARCHAR(5000),
    event_precision VARCHAR(10) NOT NULL,
    event_at TIMESTAMPTZ,
    event_date DATE,
    event_timezone VARCHAR(80),
    -- Only an UNKNOWN or NOT_ASKED statement may stand without a cited source revision.
    source_revision_id UUID,
    recorded_at TIMESTAMPTZ NOT NULL,
    supersedes_id UUID,
    correction_reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_clinical_assertion PRIMARY KEY (id),
    CONSTRAINT uq_clinical_assertion_lineage_id UNIQUE (practice_id, record_id, lineage_id, id),
    CONSTRAINT uq_clinical_assertion_lineage_revision UNIQUE (practice_id, record_id, lineage_id, revision),
    CONSTRAINT fk_clinical_assertion_record FOREIGN KEY (practice_id, record_id)
        REFERENCES patient_record (practice_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_clinical_assertion_source FOREIGN KEY (practice_id, record_id, source_revision_id)
        REFERENCES source_revision (practice_id, record_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_clinical_assertion_supersedes FOREIGN KEY (practice_id, record_id, lineage_id, supersedes_id)
        REFERENCES clinical_assertion (practice_id, record_id, lineage_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_clinical_assertion_category CHECK (category IN ('HISTORY', 'ALLERGY', 'REPORTED_TREATMENT')),
    CONSTRAINT ck_clinical_assertion_kind CHECK (assertion_kind IN ('PRESENT', 'EXPLICITLY_ABSENT', 'UNKNOWN', 'NOT_ASKED')),
    CONSTRAINT ck_clinical_assertion_text CHECK (text IS NULL OR btrim(text) <> ''),
    CONSTRAINT ck_clinical_assertion_source CHECK (source_revision_id IS NOT NULL OR assertion_kind IN ('UNKNOWN', 'NOT_ASKED')),
    CONSTRAINT ck_clinical_assertion_event CHECK (
        (event_precision = 'INSTANT' AND event_at IS NOT NULL AND event_date IS NULL)
        OR (event_precision = 'DATE' AND event_date IS NOT NULL AND event_at IS NULL AND event_timezone IS NOT NULL)
        OR (event_precision = 'UNKNOWN' AND event_at IS NULL AND event_date IS NULL AND event_timezone IS NULL)),
    CONSTRAINT ck_clinical_assertion_zone CHECK (event_timezone IS NULL OR public.valid_time_zone(event_timezone)),
    CONSTRAINT ck_clinical_assertion_correction CHECK (
        (revision = 1 AND supersedes_id IS NULL AND correction_reason IS NULL)
        OR (revision > 1 AND supersedes_id IS NOT NULL AND correction_reason IS NOT NULL AND btrim(correction_reason) <> ''))
);

CREATE INDEX idx_clinical_assertion_record_recorded ON clinical_assertion (practice_id, record_id, category, recorded_at);

-- Evidence is immutable: a revision is superseded, never edited or removed. The runtime role holds no write grant
-- below; this guard also stops a privileged session from changing a row by mistake. A demo reset truncates the tables.
CREATE FUNCTION public.reject_evidence_change() RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Evidence rows are immutable: append a superseding revision instead'
        USING ERRCODE = 'restrict_violation';
END
$$;

-- A correction follows the revision it supersedes, in revision order and in recording time, and an assertion is never
-- recorded before the revision it cites: an asOf view can then never show a statement whose evidence does not exist yet.
CREATE FUNCTION public.source_revision_order_guard() RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    prior public.source_revision%ROWTYPE;
BEGIN
    IF NEW.supersedes_id IS NOT NULL THEN
        SELECT * INTO prior FROM public.source_revision WHERE id = NEW.supersedes_id;
        IF NOT FOUND OR prior.revision <> NEW.revision - 1 OR prior.recorded_at > NEW.recorded_at THEN
            RAISE EXCEPTION 'A correction must follow the revision it supersedes (ck_source_revision_order)'
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    RETURN NEW;
END
$$;

CREATE FUNCTION public.clinical_assertion_order_guard() RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    prior public.clinical_assertion%ROWTYPE;
    cited_at timestamptz;
BEGIN
    IF NEW.source_revision_id IS NOT NULL THEN
        SELECT recorded_at INTO cited_at FROM public.source_revision WHERE id = NEW.source_revision_id;
        IF cited_at IS NULL OR cited_at > NEW.recorded_at THEN
            RAISE EXCEPTION 'An assertion cannot be recorded before the source revision it cites (ck_clinical_assertion_order)'
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    IF NEW.supersedes_id IS NOT NULL THEN
        SELECT * INTO prior FROM public.clinical_assertion WHERE id = NEW.supersedes_id;
        IF NOT FOUND OR prior.revision <> NEW.revision - 1 OR prior.recorded_at > NEW.recorded_at THEN
            RAISE EXCEPTION 'A correction must follow the revision it supersedes (ck_clinical_assertion_order)'
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER source_revision_order BEFORE INSERT ON source_revision
    FOR EACH ROW EXECUTE FUNCTION public.source_revision_order_guard();
CREATE TRIGGER clinical_assertion_order BEFORE INSERT ON clinical_assertion
    FOR EACH ROW EXECUTE FUNCTION public.clinical_assertion_order_guard();

CREATE TRIGGER source_revision_immutable BEFORE UPDATE OR DELETE ON source_revision
    FOR EACH ROW EXECUTE FUNCTION public.reject_evidence_change();
CREATE TRIGGER clinical_assertion_immutable BEFORE UPDATE OR DELETE ON clinical_assertion
    FOR EACH ROW EXECUTE FUNCTION public.reject_evidence_change();

ALTER TABLE source_revision ENABLE ROW LEVEL SECURITY;
ALTER TABLE source_revision FORCE ROW LEVEL SECURITY;
CREATE POLICY source_revision_practice_isolation_policy ON source_revision
    FOR ALL
    USING (practice_id = NULLIF(current_setting('etaye.practice_id', true), '')::uuid)
    WITH CHECK (practice_id = NULLIF(current_setting('etaye.practice_id', true), '')::uuid);

ALTER TABLE clinical_assertion ENABLE ROW LEVEL SECURITY;
ALTER TABLE clinical_assertion FORCE ROW LEVEL SECURITY;
CREATE POLICY clinical_assertion_practice_isolation_policy ON clinical_assertion
    FOR ALL
    USING (practice_id = NULLIF(current_setting('etaye.practice_id', true), '')::uuid)
    WITH CHECK (practice_id = NULLIF(current_setting('etaye.practice_id', true), '')::uuid);

-- Least privilege: the application reads evidence and never writes it in this increment.
REVOKE ALL ON source_revision FROM PUBLIC, etaye_runtime, etaye_identity;
REVOKE ALL ON clinical_assertion FROM PUBLIC, etaye_runtime, etaye_identity;
GRANT SELECT ON source_revision TO etaye_runtime;
GRANT SELECT ON clinical_assertion TO etaye_runtime;
