#!/usr/bin/env bash
set -euo pipefail

admin_user="${POSTGRES_ADMIN_USER:-${POSTGRES_USER:-postgres}}"
target_db="${POSTGRES_DB:-${PGDATABASE:-}}"

case "$admin_user" in
  etaye_owner|etaye_runtime|etaye_identity)
    echo "Refusing a reserved Étaye application role as administrator." >&2
    exit 1
    ;;
esac

if [[ "$target_db" == etaye_demo && "$admin_user" == postgres ]]; then
  :
elif [[ "$target_db" =~ ^etaye_demo_[a-z0-9_]{1,52}$ && "${ETAYE_MANAGED_DEMO_DB:-}" == "$target_db" ]]; then
  :
else
  echo "Refusing to mark a database outside the dedicated synthetic demo naming convention." >&2
  exit 1
fi

if [[ ( -n "${PGHOST:-}" || -n "${PGHOSTADDR:-}" || -n "${PGSERVICE:-}" ) && -z "${PGPASSWORD:-}" && -n "${POSTGRES_ADMIN_PASSWORD:-}" ]]; then
  export PGPASSWORD="$POSTGRES_ADMIN_PASSWORD"
fi

psql -X -w -v ON_ERROR_STOP=1 --username "$admin_user" --dbname "$target_db" \
  --single-transaction --file=- <<'SQL'
SET LOCAL search_path = pg_catalog;

DO $demo_mark$
DECLARE
    administrator name := current_user;
    needs_grant boolean;
    bootstrap_owner oid;
    demo_owner oid;
BEGIN
    IF NOT EXISTS (SELECT FROM pg_database WHERE datname = current_database() AND datdba = administrator::regrole) THEN
        RAISE EXCEPTION 'The synthetic database must be marked by its database owner';
    END IF;

    SELECT nspowner INTO bootstrap_owner FROM pg_namespace WHERE nspname = 'etaye_demo_bootstrap';
    SELECT nspowner INTO demo_owner FROM pg_namespace WHERE nspname = 'etaye_demo';

    IF bootstrap_owner = administrator::regrole AND demo_owner = 'etaye_owner'::regrole THEN
        RETURN;
    END IF;

    IF bootstrap_owner IS NOT NULL OR demo_owner IS NOT NULL OR EXISTS (
        SELECT FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = 'public'
    ) THEN
        RAISE EXCEPTION 'The target must be marked before installing its schema';
    END IF;

    SELECT NOT rolsuper INTO STRICT needs_grant FROM pg_roles WHERE rolname = administrator;
    IF needs_grant THEN
        IF pg_has_role(administrator, 'etaye_owner', 'SET')
           OR pg_has_role(administrator, 'etaye_owner', 'USAGE') THEN
            RAISE EXCEPTION 'Bootstrap administrator already has owner SET or INHERIT access';
        END IF;
        EXECUTE format('GRANT etaye_owner TO %I WITH SET TRUE, INHERIT FALSE', administrator);
    END IF;

    CREATE SCHEMA etaye_demo_bootstrap;
    CREATE SCHEMA etaye_demo AUTHORIZATION etaye_owner;

    IF needs_grant THEN
        EXECUTE format('REVOKE SET OPTION FOR etaye_owner FROM %I', administrator);
    END IF;
END
$demo_mark$;
SQL
