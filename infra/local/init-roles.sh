#!/usr/bin/env bash
set -euo pipefail

# Provision three non-overlapping roles outside migrations:
# 1. etaye_owner: owns tables, executes DDL and Flyway migrations
# 2. etaye_runtime: used by the HTTP application; non-owner, NOBYPASSRLS
# 3. etaye_identity: bootstrap authentication and membership discovery; non-owner, NOBYPASSRLS
#
# Passwords come from the environment and are read by psql itself (\getenv), so they never appear
# on a command line. psql does not interpolate variables inside dollar-quoted bodies, therefore the
# statements are generated with format(%I, %L) and executed with \gexec.

: "${ETAYE_OWNER_PASSWORD:?ETAYE_OWNER_PASSWORD is required}"
: "${ETAYE_RUNTIME_PASSWORD:?ETAYE_RUNTIME_PASSWORD is required}"
: "${ETAYE_IDENTITY_PASSWORD:?ETAYE_IDENTITY_PASSWORD is required}"

echo "Initializing Étaye PostgreSQL roles..."

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'EOSQL'
\getenv owner_pass ETAYE_OWNER_PASSWORD
\getenv runtime_pass ETAYE_RUNTIME_PASSWORD
\getenv identity_pass ETAYE_IDENTITY_PASSWORD

SELECT format('CREATE ROLE %I WITH LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS',
              'etaye_owner', :'owner_pass')
WHERE NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'etaye_owner') \gexec

SELECT format('CREATE ROLE %I WITH LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS',
              'etaye_runtime', :'runtime_pass')
WHERE NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'etaye_runtime') \gexec

SELECT format('CREATE ROLE %I WITH LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS',
              'etaye_identity', :'identity_pass')
WHERE NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'etaye_identity') \gexec

SELECT format('GRANT CONNECT ON DATABASE %I TO etaye_owner, etaye_runtime, etaye_identity', current_database()) \gexec

-- The owner role owns the schema so Flyway can run as the owner; runtime and identity only get usage.
ALTER SCHEMA public OWNER TO etaye_owner;
GRANT USAGE ON SCHEMA public TO etaye_runtime, etaye_identity;
EOSQL

echo "Étaye PostgreSQL roles initialized successfully."
