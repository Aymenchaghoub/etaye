#!/usr/bin/env bash
set -euo pipefail

# Provision three non-overlapping roles outside migrations:
# 1. Owner: owns tables, executes DDL and Flyway migrations
# 2. Runtime: used by HTTP application; non-owner, NOBYPASSRLS
# 3. Identity: used for bootstrap authentication and membership discovery; non-owner, NOBYPASSRLS

echo "Initializing Étaye PostgreSQL roles..."

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    DO \$\$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '${ETAYE_OWNER_USER:-etaye_owner}') THEN
            CREATE ROLE ${ETAYE_OWNER_USER:-etaye_owner} WITH LOGIN PASSWORD '${ETAYE_OWNER_PASSWORD}' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
        END IF;
        IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '${ETAYE_RUNTIME_USER:-etaye_runtime}') THEN
            CREATE ROLE ${ETAYE_RUNTIME_USER:-etaye_runtime} WITH LOGIN PASSWORD '${ETAYE_RUNTIME_PASSWORD}' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
        END IF;
        IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '${ETAYE_IDENTITY_USER:-etaye_identity}') THEN
            CREATE ROLE ${ETAYE_IDENTITY_USER:-etaye_identity} WITH LOGIN PASSWORD '${ETAYE_IDENTITY_PASSWORD}' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
        END IF;
    END
    \$\$;

    GRANT CONNECT ON DATABASE "${POSTGRES_DB}" TO "${ETAYE_OWNER_USER:-etaye_owner}", "${ETAYE_RUNTIME_USER:-etaye_runtime}", "${ETAYE_IDENTITY_USER:-etaye_identity}";

    -- Transfer public schema ownership to owner role
    ALTER SCHEMA public OWNER TO "${ETAYE_OWNER_USER:-etaye_owner}";

    -- Grant schema usage to runtime and identity roles
    GRANT USAGE ON SCHEMA public TO "${ETAYE_RUNTIME_USER:-etaye_runtime}", "${ETAYE_IDENTITY_USER:-etaye_identity}";
EOSQL

echo "Étaye PostgreSQL roles initialized successfully."
