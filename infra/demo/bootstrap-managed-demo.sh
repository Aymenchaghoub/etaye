#!/usr/bin/env bash
set -euo pipefail

target_db="${POSTGRES_DB:-${PGDATABASE:-}}"
if [[ ! "$target_db" =~ ^etaye_demo_[a-z0-9_]{1,52}$ || "${ETAYE_MANAGED_DEMO_DB:-}" != "$target_db" ]]; then
  echo "Refusing seed without explicit, matching ETAYE_MANAGED_DEMO_DB for a dedicated etaye_demo_* database." >&2
  exit 1
fi

: "${ETAYE_OWNER_PASSWORD:?ETAYE_OWNER_PASSWORD is required}"

repo=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
demo_dir="$repo/apps/api/src/main/resources/db/demo"

# Check the complete historical guard before substituting it in memory. The managed
# guard below runs in the same transaction. Flyway continues to use the original files.
guard=$(cat <<'SQL'
-- Applied only by the local demo package, after its dedicated database has been provisioned.
DO $$
BEGIN
    IF current_database() <> 'etaye_demo' OR NOT EXISTS (
        SELECT FROM pg_namespace WHERE nspname = 'etaye_demo_bootstrap'
            AND nspowner = 'postgres'::regrole
    ) THEN
        RAISE EXCEPTION 'The synthetic fixture requires its dedicated PostgreSQL database';
    END IF;
END
$$;
SQL
)
v5=$(cat "$demo_dir/V5__local_synthetic_demo.sql")
if [[ "$v5" != "$guard"$'\n'* ]]; then
  echo "V5 bootstrap guard differs from the reviewed migration." >&2
  exit 1
fi
# The managed schema is already at V11. Install the historical definitions in
# order, then invoke V12's restore once. Earlier top-level calls precede their
# foreign-key-safe replacement and cannot run against the complete schema.
without_initial_call() {
  awk -v call="$1" '$0 == call { count++; next } { print } END { if (count != 1) exit 1 }'
}
v5_body=$(without_initial_call 'SELECT etaye_demo.restore_fixture();' <<< "${v5#"$guard"}")
v6=$(without_initial_call 'SELECT etaye_demo.restore_fixture(false);' < "$demo_dir/V6__correct_invitation_fixture_dates.sql")
v10=$(without_initial_call 'SELECT etaye_demo.seed_evidence();' < "$demo_dir/V10__seed_record_evidence.sql")
v12=$(without_initial_call 'SELECT etaye_demo.seed_measurements();' < "$demo_dir/V12__seed_measurements.sql")

export PGPASSWORD="$ETAYE_OWNER_PASSWORD"

# Lock concurrent invocations, validate the exact compatible schema and refuse
# existing application data before any historical fixture statement can truncate it.
{
  cat <<'SQL'
SET LOCAL search_path = pg_catalog, public;
SELECT pg_advisory_xact_lock(98201, 91);

DO $managed_guard$
BEGIN
    IF current_user <> 'etaye_owner' OR current_database() !~ '^etaye_demo_[a-z0-9_]{1,52}$'
       OR NOT EXISTS (
           SELECT FROM pg_catalog.pg_namespace n
           JOIN pg_catalog.pg_database d ON d.datname = current_database() AND d.datdba = n.nspowner
           JOIN pg_catalog.pg_roles r ON r.oid = n.nspowner
           WHERE n.nspname = 'etaye_demo_bootstrap'
             AND r.rolname NOT IN ('etaye_owner', 'etaye_runtime', 'etaye_identity')
       )
       OR NOT EXISTS (
           SELECT FROM pg_catalog.pg_namespace
           WHERE nspname = 'etaye_demo' AND nspowner = 'etaye_owner'::regrole
       )
       OR to_regclass('public.flyway_schema_history') IS NULL THEN
        RAISE EXCEPTION 'A marked dedicated database with schema-only Flyway migrations is required';
    END IF;

    IF EXISTS (SELECT FROM public.flyway_schema_history WHERE NOT success)
       OR (SELECT array_agg(version::integer ORDER BY version::integer)
           FROM public.flyway_schema_history WHERE version IS NOT NULL)
           IS DISTINCT FROM ARRAY[1, 2, 3, 4, 7, 8, 9, 11] THEN
        RAISE EXCEPTION 'The managed demo requires schema versions 1, 2, 3, 4, 7, 8, 9 and 11';
    END IF;
END
$managed_guard$;

SELECT to_regclass('etaye_demo.fixture') IS NOT NULL AS seeded \gset
\if :seeded
DO $verify_seeded$
BEGIN
    IF NOT EXISTS (SELECT FROM etaye_demo.fixture WHERE version = 'etaye-design-v1' AND generation > 0)
       OR to_regprocedure('etaye_demo.reset_fixture(uuid,text,text,text,text)') IS NULL
       OR to_regprocedure('etaye_demo.seed_measurements()') IS NULL THEN
        RAISE EXCEPTION 'The existing synthetic fixture is incomplete';
    END IF;
END
$verify_seeded$;
\else
DO $empty$
DECLARE
    table_info record;
    has_data boolean;
BEGIN
    FOR table_info IN
        SELECT c.oid, c.relname, c.relrowsecurity
        FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind = 'r' AND c.relname <> 'flyway_schema_history'
        ORDER BY c.relname
    LOOP
        EXECUTE format('LOCK TABLE public.%I IN ACCESS EXCLUSIVE MODE', table_info.relname);
        IF table_info.relrowsecurity THEN
            -- FORCE RLS can hide rows from the migration owner. Require a fresh,
            -- zero-page heap without disabling RLS or granting a bypass role.
            has_data := pg_relation_size(table_info.oid) <> 0;
        ELSE
            EXECUTE format('SELECT EXISTS (SELECT FROM public.%I)', table_info.relname) INTO has_data;
        END IF;
        IF has_data THEN
            RAISE EXCEPTION 'Refusing to seed a database containing application data or an initialized RLS heap: %',
                table_info.relname;
        END IF;
    END LOOP;
END
$empty$;
SQL
  printf '%s\n' "$v5_body" "$v6" "$v10" "$v12"
  printf 'SELECT etaye_demo.restore_fixture(true);\n\\endif\n'
} | psql -X -w -v ON_ERROR_STOP=1 --username etaye_owner --dbname "$target_db" --single-transaction --file=-

echo "Managed synthetic demo fixture verified in '$target_db'."
