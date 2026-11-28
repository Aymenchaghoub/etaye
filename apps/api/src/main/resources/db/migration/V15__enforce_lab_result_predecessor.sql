-- Every correction follows an already visible predecessor in the same lineage.
ALTER TABLE public.lab_result ADD CONSTRAINT ck_lab_result_distinct_predecessor CHECK (supersedes_id <> id);

CREATE OR REPLACE FUNCTION public.lab_result_order_guard() RETURNS trigger
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
        IF NOT FOUND OR (
            prior.revision <> NEW.revision - 1 OR prior.recorded_at > NEW.recorded_at OR prior.analyte <> NEW.analyte
        ) THEN
            RAISE EXCEPTION 'A correction must follow the prior revision of the same analyte' USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    RETURN NEW;
END
$$;
