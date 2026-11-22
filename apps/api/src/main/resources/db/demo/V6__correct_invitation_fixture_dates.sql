ALTER FUNCTION etaye_demo.restore_fixture() RENAME TO restore_fixture_v5;

CREATE FUNCTION etaye_demo.restore_fixture(reload boolean DEFAULT true) RETURNS void
LANGUAGE plpgsql SET search_path = pg_catalog
AS $$
DECLARE
    practice_a uuid := etaye_demo.fixture_id('PRA-A');
    previous_scope text := current_setting('etaye.practice_id', true);
BEGIN
    IF reload THEN
        PERFORM etaye_demo.restore_fixture_v5();
    END IF;

    PERFORM set_config('etaye.practice_id', practice_a::text, true);
    UPDATE public.invitation i SET created_at = dates.created_at, updated_at = dates.updated_at
    FROM (VALUES
        ('INV-PORTAL-01', 'ACCEPTED', '2026-10-16 12:00:00+02'::timestamptz, '2026-10-18 12:00:00+02'::timestamptz),
        ('INV-PORTAL-02', 'EXPIRED', '2026-10-28 12:00:00+01'::timestamptz, '2026-10-30 12:00:00+01'::timestamptz)
    ) dates(alias, state, created_at, updated_at)
    WHERE i.practice_id = practice_a AND i.id = etaye_demo.fixture_id(dates.alias)
        AND i.state = dates.state AND i.created_at = '2026-11-02 16:00:00+01'::timestamptz;
    PERFORM set_config('etaye.practice_id', coalesce(previous_scope, ''), true);
END
$$;

REVOKE ALL ON FUNCTION etaye_demo.restore_fixture(boolean) FROM PUBLIC;

-- Repair the existing demo without reloading its fixture or closing its sessions.
SELECT etaye_demo.restore_fixture(false);
