-- Story 3.3: the structured measurements of a patient record, one immutable revision per row. They cite the source
-- revision that supports them (story 3.2), so value, unit and provenance travel together and a correction appends the
-- next revision of the same lineage. The runtime role only reads this table: entry and correction arrive with
-- increment 2 (D-24). Every row is practice and record owned, the keys are composite so a row can never point at another
-- practice or record, and forced row-level security applies on top (BR-001, data model).
CREATE TABLE measurement (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    practice_id UUID NOT NULL,
    record_id UUID NOT NULL,
    -- The revisions of one measurement share a lineage; a correction appends the next revision (BR-008, BR-009).
    lineage_id UUID NOT NULL,
    revision INTEGER NOT NULL,
    -- A code such as WEIGHT or SYSTOLIC_BLOOD_PRESSURE, compared exactly: the catalogue belongs to entry (D-66).
    concept VARCHAR(100) NOT NULL,
    -- Stored with the scale it was entered with, never rounded: NUMERIC without a declared scale keeps 68.40 as 68.40.
    value_decimal NUMERIC NOT NULL,
    -- The unit as entered. It is never converted (BR-009): two spellings are two series.
    unit VARCHAR(40) NOT NULL,
    -- An observation is always an instant (BR-009, D-65): an unknown or date-only time is not representable.
    observed_at TIMESTAMPTZ NOT NULL,
    -- Server-provided recording instant: it alone decides what an asOf view contains (UD-32).
    recorded_at TIMESTAMPTZ NOT NULL,
    author_label VARCHAR(200) NOT NULL,
    source_revision_id UUID NOT NULL,
    -- The components of one blood-pressure reading share a group; any other measurement has none.
    group_id UUID,
    supersedes_id UUID,
    correction_reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_measurement PRIMARY KEY (id),
    CONSTRAINT uq_measurement_practice_id UNIQUE (practice_id, id),
    CONSTRAINT uq_measurement_record_id UNIQUE (practice_id, record_id, id),
    CONSTRAINT uq_measurement_lineage_id UNIQUE (practice_id, record_id, lineage_id, id),
    CONSTRAINT uq_measurement_lineage_revision UNIQUE (practice_id, record_id, lineage_id, revision),
    CONSTRAINT fk_measurement_record FOREIGN KEY (practice_id, record_id)
        REFERENCES patient_record (practice_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_measurement_source FOREIGN KEY (practice_id, record_id, source_revision_id)
        REFERENCES source_revision (practice_id, record_id, id) ON DELETE RESTRICT,
    -- A superseding revision stays in its own record and lineage.
    CONSTRAINT fk_measurement_supersedes FOREIGN KEY (practice_id, record_id, lineage_id, supersedes_id)
        REFERENCES measurement (practice_id, record_id, lineage_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_measurement_text CHECK (btrim(concept) <> '' AND btrim(unit) <> '' AND btrim(author_label) <> ''),
    -- The contract accepts up to 12 integer and 6 fractional digits.
    CONSTRAINT ck_measurement_value CHECK (scale(value_decimal) <= 6 AND abs(value_decimal) < 1000000000000),
    CONSTRAINT ck_measurement_correction CHECK (
        (revision = 1 AND supersedes_id IS NULL AND correction_reason IS NULL)
        OR (revision > 1 AND supersedes_id IS NOT NULL AND correction_reason IS NOT NULL AND btrim(correction_reason) <> ''))
);

CREATE INDEX idx_measurement_record_observed ON measurement (practice_id, record_id, observed_at);
CREATE INDEX idx_measurement_record_concept ON measurement (practice_id, record_id, concept, observed_at);

-- A correction follows the revision it supersedes, in revision order and in recording time, and keeps the concept and
-- the group of its lineage; a measurement is never recorded before the source revision it cites, so an asOf view can
-- never show a measurement whose evidence does not exist yet.
CREATE FUNCTION public.measurement_order_guard() RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    prior public.measurement%ROWTYPE;
    cited_at timestamptz;
BEGIN
    SELECT recorded_at INTO cited_at FROM public.source_revision WHERE id = NEW.source_revision_id;
    IF cited_at IS NULL OR cited_at > NEW.recorded_at THEN
        RAISE EXCEPTION 'A measurement cannot be recorded before the source revision it cites (ck_measurement_order)'
            USING ERRCODE = 'check_violation';
    END IF;
    IF NEW.supersedes_id IS NOT NULL THEN
        SELECT * INTO prior FROM public.measurement WHERE id = NEW.supersedes_id;
        IF NOT FOUND OR prior.revision <> NEW.revision - 1 OR prior.recorded_at > NEW.recorded_at
                OR prior.concept <> NEW.concept OR prior.group_id IS DISTINCT FROM NEW.group_id THEN
            RAISE EXCEPTION 'A correction must follow the revision it supersedes (ck_measurement_order)'
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER measurement_order BEFORE INSERT ON measurement
    FOR EACH ROW EXECUTE FUNCTION public.measurement_order_guard();
-- Evidence is immutable: a revision is superseded, never edited or removed (the guard of V9). A demo reset truncates.
CREATE TRIGGER measurement_immutable BEFORE UPDATE OR DELETE ON measurement
    FOR EACH ROW EXECUTE FUNCTION public.reject_evidence_change();

ALTER TABLE measurement ENABLE ROW LEVEL SECURITY;
ALTER TABLE measurement FORCE ROW LEVEL SECURITY;
CREATE POLICY measurement_practice_isolation_policy ON measurement
    FOR ALL
    USING (practice_id = NULLIF(current_setting('etaye.practice_id', true), '')::uuid)
    WITH CHECK (practice_id = NULLIF(current_setting('etaye.practice_id', true), '')::uuid);

-- Least privilege: the application reads measurements and never writes them in this increment.
REVOKE ALL ON measurement FROM PUBLIC, etaye_runtime, etaye_identity;
GRANT SELECT ON measurement TO etaye_runtime;

-- D-64: the membership context also carries the IANA time zone of its practice, which the record screens use to show
-- and read every instant. A portal context has none. The zone is now checked where it is stored, so a practice can never
-- carry a name the screens would have to guess around. The return type changes, so the V4 function is replaced.
ALTER TABLE practice ADD CONSTRAINT ck_practice_zone CHECK (public.valid_time_zone(timezone));
DROP FUNCTION public.current_access_contexts(uuid);
CREATE FUNCTION public.current_access_contexts(actor uuid)
RETURNS TABLE (grant_id uuid, grant_type text, practice_id uuid, practice_name text, display_role text, practice_timezone text)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog
AS $$
    WITH active_grants AS (
        SELECT grants.grant_id, grants.grant_type
        FROM public.current_session_grants(actor) grants
        JOIN public.account a ON a.id = actor AND a.state = 'ACTIVE'
    )
    SELECT g.grant_id, g.grant_type, p.id, p.display_name::text,
        CASE
            WHEN m.capabilities && ARRAY['CLINICAL_READ', 'CLINICAL_WRITE', 'CLINICAL_VALIDATE',
                    'PATIENT_PUBLISH', 'CLINICAL_AUDIT'] THEN 'CLINICAL'
            WHEN m.capabilities && ARRAY['TEAM_MANAGE', 'PRACTICE_SETTINGS', 'ADMIN_AUDIT'] THEN 'ADMINISTRATION'
            WHEN 'SCHEDULE_MANAGE' = ANY(m.capabilities) THEN 'SCHEDULING'
            ELSE 'TEAM'
        END,
        p.timezone::text
    FROM active_grants g
    JOIN public.membership m ON g.grant_type = 'MEMBERSHIP' AND m.id = g.grant_id
    JOIN public.practice p ON p.id = m.practice_id
    UNION ALL
    SELECT g.grant_id, g.grant_type, p.id, p.display_name::text, 'PATIENT', NULL::text
    FROM active_grants g
    JOIN public.portal_link link ON g.grant_type = 'PORTAL' AND link.id = g.grant_id
    JOIN public.practice p ON p.id = link.practice_id
$$;

REVOKE ALL ON FUNCTION public.current_access_contexts(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.current_access_contexts(uuid) TO etaye_identity;
