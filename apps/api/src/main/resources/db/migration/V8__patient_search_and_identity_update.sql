-- Story 3.1: practice-local patient search and identity correction.
-- One normalization lives in the database: it feeds the stored keys and every query word, so they
-- cannot drift. The functions are declared IMMUTABLE for generated columns; they depend on the
-- database ctype only for the alphanumeric test, which is stable within one cluster.

-- Query words: lowercase, combining marks removed (so a decomposed accent equals a precomposed one),
-- Latin diacritics folded, ligatures expanded, every other non-alphanumeric character a separator.
CREATE FUNCTION public.patient_search_key(value text) RETURNS text
LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
AS $$
    SELECT btrim(regexp_replace(
        translate(
            replace(replace(replace(regexp_replace(lower(value), '[\u0300-\u036f]', '', 'g'), 'œ', 'oe'), 'æ', 'ae'), 'ß', 'ss'),
            'àáâãäåçèéêëìíîïñòóôõöùúûüýÿāăąćĉċčďēĕėęěĝğġģĥĩīĭįĵķĺļľńņňōŏőŕŗřśŝşšţťũūŭůűųŵŷźżžơưǎǐǒǔǖǘǚǜǟǡǧǩǫǭǰǵǹǻȁȃȅȇȉȋȍȏȑȓȕȗșțȟȧȩȫȭȯȱȳøđħłŧıð',
            'aaaaaaceeeeiiiinooooouuuuyyaaaccccdeeeeegggghiiiijklllnnnooorrrssssttuuuuuuwyzzzouaiouuuuuaagkoojgnaaaeeiioorruusthaeooooyodhltid'),
        '[^[:alnum:]]+', ' ', 'g'))
$$;

-- Stored name keys: the same words, falling back to the lowercase text when the database character
-- classification leaves nothing, so that an accepted name never has an empty key.
CREATE FUNCTION public.patient_name_key(value text) RETURNS text
LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
AS $$
    SELECT coalesce(nullif(public.patient_search_key(value), ''), btrim(lower(value)))
$$;

ALTER TABLE public.patient
    ADD COLUMN first_name_key text GENERATED ALWAYS AS (public.patient_name_key(first_name)) STORED,
    ADD COLUMN last_name_key text GENERATED ALWAYS AS (public.patient_name_key(last_name)) STORED;

-- Search order and keyset pagination: practice, last name, first name, id.
CREATE INDEX idx_patient_practice_order ON public.patient (practice_id, last_name_key, first_name_key, id);
-- Duplicate rule and exact birth-date tokens.
CREATE INDEX idx_patient_practice_birth ON public.patient (practice_id, date_of_birth);

-- Identity correction updates only these columns; the key columns are generated. The record version
-- moves with the identity it embeds, so the conditional record read never serves a stale patient.
GRANT UPDATE (first_name, last_name, date_of_birth, contact_email, phone, version, updated_at)
    ON public.patient TO etaye_runtime;
GRANT UPDATE (version, updated_at) ON public.patient_record TO etaye_runtime;
