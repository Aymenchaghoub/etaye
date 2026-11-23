-- Story 3.1: practice-local patient search and identity correction.
-- One normalization lives in the database: it feeds the stored keys and every query token, so
-- they cannot drift. The function is declared IMMUTABLE for generated columns; it depends on the
-- database ctype only for the alphanumeric test, which is stable within one cluster.
CREATE FUNCTION public.patient_search_key(value text) RETURNS text
LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
AS $$
    SELECT btrim(regexp_replace(
        translate(
            replace(replace(replace(lower(value), 'œ', 'oe'), 'æ', 'ae'), 'ß', 'ss'),
            'àáâãäåçèéêëìíîïñòóôõöùúûüýÿāăąćĉċčďēĕėęěĝğġģĥĩīĭįĵķĺļľńņňōŏőŕŗřśŝşšţťũūŭůűųŵŷźżžơưǎǐǒǔǖǘǚǜǟǡǧǩǫǭǰǵǹǻȁȃȅȇȉȋȍȏȑȓȕȗșțȟȧȩȫȭȯȱȳøđħłŧıð',
            'aaaaaaceeeeiiiinooooouuuuyyaaaccccdeeeeegggghiiiijklllnnnooorrrssssttuuuuuuwyzzzouaiouuuuuaagkoojgnaaaeeiioorruusthaeooooyodhltid'),
        '[^[:alnum:]]+', ' ', 'g'))
$$;

ALTER TABLE public.patient
    ADD COLUMN first_name_key text GENERATED ALWAYS AS (public.patient_search_key(first_name)) STORED,
    ADD COLUMN last_name_key text GENERATED ALWAYS AS (public.patient_search_key(last_name)) STORED;

-- Search order and keyset pagination: practice, last name, first name, id.
CREATE INDEX idx_patient_practice_order ON public.patient (practice_id, last_name_key, first_name_key, id);
-- Duplicate rule and exact birth-date tokens.
CREATE INDEX idx_patient_practice_birth ON public.patient (practice_id, date_of_birth);

-- Identity correction updates only these columns; the key columns are generated.
GRANT UPDATE (first_name, last_name, date_of_birth, contact_email, phone, version, updated_at)
    ON public.patient TO etaye_runtime;
