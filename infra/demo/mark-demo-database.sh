#!/usr/bin/env bash
set -euo pipefail

[[ "$POSTGRES_DB" == etaye_demo ]]
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
CREATE SCHEMA etaye_demo_bootstrap AUTHORIZATION postgres;
CREATE SCHEMA etaye_demo AUTHORIZATION etaye_owner;
GRANT USAGE ON SCHEMA etaye_demo_bootstrap TO etaye_owner, etaye_runtime;
SQL
