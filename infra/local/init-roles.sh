#!/usr/bin/env bash
set -euo pipefail

# Provision three non-overlapping roles outside migrations:
# 1. etaye_owner: owns tables, executes DDL and Flyway migrations
# 2. etaye_runtime: used by the HTTP application; non-owner, NOBYPASSRLS
# 3. etaye_identity: bootstrap authentication and membership discovery; non-owner, NOBYPASSRLS
#
# Use POSTGRES_ADMIN_USER and libpq connection settings for a database-owning
# non-superuser administrator with CREATEROLE and ADMIN OPTION on pre-existing roles.
#
# Passwords come from the environment and are read by psql itself (\getenv), so they never appear
# on a command line. psql does not interpolate variables inside dollar-quoted bodies, therefore the
# role-creation statements are generated with format(%I, %L) and executed with \gexec.

admin_user="${POSTGRES_ADMIN_USER:-${POSTGRES_USER:-postgres}}"
target_db="${POSTGRES_DB:-${PGDATABASE:-etaye}}"

case "$admin_user" in
  etaye_owner|etaye_runtime|etaye_identity)
    echo "Refusing POSTGRES_USER=$admin_user: reserved Étaye application role." >&2
    exit 1
    ;;
esac

: "${ETAYE_OWNER_PASSWORD:?ETAYE_OWNER_PASSWORD is required}"
: "${ETAYE_RUNTIME_PASSWORD:?ETAYE_RUNTIME_PASSWORD is required}"
: "${ETAYE_IDENTITY_PASSWORD:?ETAYE_IDENTITY_PASSWORD is required}"

# When connecting to a managed or remote host over TCP, pass admin password via PGPASSWORD without CLI args.
if [[ -n "${PGHOST:-}" && -z "${PGPASSWORD:-}" && -n "${POSTGRES_ADMIN_PASSWORD:-}" ]]; then
  export PGPASSWORD="$POSTGRES_ADMIN_PASSWORD"
fi

echo "Initializing Étaye PostgreSQL roles..."

psql -X -w -v ON_ERROR_STOP=1 --username "$admin_user" --dbname "$target_db" \
  --single-transaction --file=- <<'EOSQL' || exit 1
SET LOCAL search_path = pg_catalog;
\getenv owner_pass ETAYE_OWNER_PASSWORD
\getenv runtime_pass ETAYE_RUNTIME_PASSWORD
\getenv identity_pass ETAYE_IDENTITY_PASSWORD

SELECT format('CREATE ROLE %I WITH LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS',
              'etaye_owner', :'owner_pass')
WHERE NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'etaye_owner') \gexec

SELECT format('CREATE ROLE %I WITH LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS',
              'etaye_runtime', :'runtime_pass')
WHERE NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'etaye_runtime') \gexec

SELECT format('CREATE ROLE %I WITH LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS',
              'etaye_identity', :'identity_pass')
WHERE NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'etaye_identity') \gexec

DO $verify$
DECLARE
    invalid_role name;
BEGIN
    SELECT required.role_name INTO invalid_role
    FROM (VALUES ('etaye_owner'), ('etaye_runtime'), ('etaye_identity')) AS required(role_name)
    LEFT JOIN pg_catalog.pg_roles AS actual ON actual.rolname = required.role_name::name
    WHERE actual.oid IS NULL
       OR actual.rolsuper OR actual.rolcreatedb OR actual.rolcreaterole
       OR actual.rolreplication OR actual.rolbypassrls OR NOT actual.rolcanlogin
       OR EXISTS (SELECT FROM pg_catalog.pg_auth_members AS membership WHERE membership.member = actual.oid)
    ORDER BY required.role_name
    LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION 'Invalid Étaye PostgreSQL role: %', invalid_role;
    END IF;
END;
$verify$;

SELECT format('GRANT CONNECT ON DATABASE %I TO etaye_owner, etaye_runtime, etaye_identity', current_database()) \gexec

-- A role created by a non-superuser comes with an ADMIN-only grant to the
-- creator. Preserve that grant and add SET only for this transaction.
DO $transfer$
DECLARE
    administrator name := current_user;
    needs_grant boolean;
BEGIN
    SELECT NOT rolsuper INTO STRICT needs_grant
    FROM pg_roles WHERE rolname = administrator;

    IF needs_grant THEN
        IF pg_has_role(administrator, 'etaye_owner', 'SET')
           OR pg_has_role(administrator, 'etaye_owner', 'USAGE') THEN
            RAISE EXCEPTION 'Bootstrap administrator already has owner SET or INHERIT access';
        END IF;
        EXECUTE format('GRANT etaye_owner TO %I WITH SET TRUE, INHERIT FALSE', administrator);
    END IF;

    IF (SELECT nspowner FROM pg_namespace WHERE nspname = 'public') <> 'etaye_owner'::regrole THEN
        ALTER SCHEMA public OWNER TO etaye_owner;
    END IF;

    SET LOCAL ROLE etaye_owner;
    REVOKE CREATE ON SCHEMA public FROM PUBLIC, etaye_runtime, etaye_identity;
    GRANT USAGE ON SCHEMA public TO etaye_runtime, etaye_identity;
    RESET ROLE;

    IF has_database_privilege('etaye_runtime', current_database(), 'CREATE')
       OR has_database_privilege('etaye_identity', current_database(), 'CREATE')
       OR has_schema_privilege('etaye_runtime', 'public', 'CREATE')
       OR has_schema_privilege('etaye_identity', 'public', 'CREATE') THEN
        RAISE EXCEPTION 'Étaye runtime and identity roles must not have schema creation privileges';
    END IF;

    IF needs_grant THEN
        EXECUTE format('REVOKE SET OPTION FOR etaye_owner FROM %I', administrator);
    END IF;
END
$transfer$;

EOSQL

echo "Étaye PostgreSQL roles initialized successfully."
