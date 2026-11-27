-- A laboratory result is an immutable revision of one practice-local record fact (BR-008/009).
CREATE TABLE lab_result (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    practice_id uuid NOT NULL,
    record_id uuid NOT NULL,
    lineage_id uuid NOT NULL,
    revision integer NOT NULL,
    analyte varchar(100) NOT NULL,
    -- Exact labels get an opaque, record-scoped selector so clinical text stays out of request URLs.
    analyte_key text NOT NULL,
    numeric_value numeric,
    qualitative_value varchar(500),
    comparator varchar(2),
    unit varchar(40),
    lower_bound numeric,
    upper_bound numeric,
    reference_interval_text varchar(500),
    interval_source varchar(500),
    interval_unit varchar(40),
    interval_context varchar(500),
    comparison_method varchar(100),
    comparison_fasting varchar(100),
    comparison_window varchar(100),
    outside_interval varchar(5),
    unconfirmed_transcription boolean NOT NULL DEFAULT false,
    specimen_at timestamptz,
    specimen_on date,
    result_at timestamptz,
    result_on date,
    recorded_at timestamptz NOT NULL,
    author_label varchar(200) NOT NULL,
    source_revision_id uuid NOT NULL,
    supersedes_id uuid,
    correction_reason varchar(1000),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_lab_result_lineage_id UNIQUE (practice_id, record_id, lineage_id, id),
    CONSTRAINT uq_lab_result_lineage_revision UNIQUE (practice_id, record_id, lineage_id, revision),
    CONSTRAINT fk_lab_result_record FOREIGN KEY (practice_id, record_id) REFERENCES patient_record (practice_id, id),
    CONSTRAINT fk_lab_result_source FOREIGN KEY (practice_id, record_id, source_revision_id)
        REFERENCES source_revision (practice_id, record_id, id),
    CONSTRAINT fk_lab_result_predecessor FOREIGN KEY (practice_id, record_id, lineage_id, supersedes_id)
        REFERENCES lab_result (practice_id, record_id, lineage_id, id),
    CONSTRAINT ck_lab_result_kind CHECK (
        (numeric_value IS NOT NULL AND qualitative_value IS NULL AND
            (comparator IS NULL OR comparator IN ('EQ', 'LT', 'LE', 'GT', 'GE')))
        OR (numeric_value IS NULL AND qualitative_value IS NOT NULL AND btrim(qualitative_value) <> '' AND comparator IS NULL)),
    CONSTRAINT ck_lab_result_decimals CHECK (
        (numeric_value IS NULL OR (scale(numeric_value) <= 6 AND abs(numeric_value) < 1000000000000))
        AND (lower_bound IS NULL OR (scale(lower_bound) <= 6 AND abs(lower_bound) < 1000000000000))
        AND (upper_bound IS NULL OR (scale(upper_bound) <= 6 AND abs(upper_bound) < 1000000000000))
        AND (lower_bound IS NULL OR upper_bound IS NULL OR lower_bound <= upper_bound)),
    CONSTRAINT ck_lab_result_text CHECK (btrim(analyte) <> '' AND btrim(author_label) <> ''
        AND (unit IS NULL OR btrim(unit) <> '') AND (interval_unit IS NULL OR btrim(interval_unit) <> '')
        AND (interval_source IS NULL OR btrim(interval_source) <> '')
        AND (interval_context IS NULL OR btrim(interval_context) <> '')
        AND (reference_interval_text IS NULL OR btrim(reference_interval_text) <> '')),
    CONSTRAINT ck_lab_result_flag CHECK (outside_interval IS NULL OR
        (outside_interval IN ('BELOW', 'ABOVE') AND
         (lower_bound IS NOT NULL OR upper_bound IS NOT NULL OR reference_interval_text IS NOT NULL))),
    CONSTRAINT ck_lab_result_comparison CHECK (
        num_nonnulls(comparison_method, comparison_fasting, comparison_window) IN (0, 3)
        AND (comparison_method IS NULL OR
            (btrim(comparison_method) <> '' AND btrim(comparison_fasting) <> '' AND btrim(comparison_window) <> ''))),
    CONSTRAINT ck_lab_result_dates CHECK (
        (specimen_at IS NULL OR specimen_on IS NULL) AND (result_at IS NULL OR result_on IS NULL)),
    CONSTRAINT ck_lab_result_correction CHECK (
        (revision = 1 AND supersedes_id IS NULL AND correction_reason IS NULL)
        OR (revision > 1 AND supersedes_id IS NOT NULL AND correction_reason IS NOT NULL
            AND btrim(correction_reason) <> ''))
);

CREATE INDEX idx_lab_result_record_view ON lab_result (practice_id, record_id, recorded_at);
CREATE INDEX idx_lab_result_record_analyte ON lab_result (practice_id, record_id, analyte_key);

CREATE FUNCTION public.lab_result_order_guard() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog
AS $$
DECLARE
    source_recorded timestamptz;
    prior public.lab_result%ROWTYPE;
BEGIN
    NEW.analyte_key := encode(sha256(convert_to(NEW.practice_id::text || ':' || NEW.record_id::text || ':' || NEW.analyte,
        'UTF8')), 'hex');
    SELECT recorded_at INTO source_recorded FROM public.source_revision
    WHERE (practice_id, record_id, id) = (NEW.practice_id, NEW.record_id, NEW.source_revision_id);
    IF source_recorded > NEW.recorded_at THEN
        RAISE EXCEPTION 'A result cannot precede its source revision' USING ERRCODE = 'check_violation';
    END IF;

    IF NEW.supersedes_id IS NOT NULL THEN
        SELECT * INTO prior FROM public.lab_result
        WHERE (practice_id, record_id, lineage_id, id) = (NEW.practice_id, NEW.record_id, NEW.lineage_id, NEW.supersedes_id);
        IF FOUND AND (
            prior.revision <> NEW.revision - 1 OR prior.recorded_at > NEW.recorded_at OR prior.analyte <> NEW.analyte
        ) THEN
            RAISE EXCEPTION 'A correction must follow the prior revision of the same analyte' USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER lab_result_order BEFORE INSERT ON lab_result
    FOR EACH ROW EXECUTE FUNCTION public.lab_result_order_guard();
CREATE TRIGGER lab_result_immutable BEFORE UPDATE OR DELETE ON lab_result
    FOR EACH ROW EXECUTE FUNCTION public.reject_evidence_change();

ALTER TABLE lab_result ENABLE ROW LEVEL SECURITY;
ALTER TABLE lab_result FORCE ROW LEVEL SECURITY;
CREATE POLICY lab_result_practice_isolation_policy ON lab_result
    USING (practice_id = NULLIF(current_setting('etaye.practice_id', true), '')::uuid)
    WITH CHECK (practice_id = NULLIF(current_setting('etaye.practice_id', true), '')::uuid);

-- I-07 and the mutations are allocated to increment 2.
REVOKE ALL ON lab_result FROM PUBLIC, etaye_runtime, etaye_identity;
GRANT SELECT ON lab_result TO etaye_runtime;
