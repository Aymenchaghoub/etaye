#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
prefix="etaye-init-$(openssl rand -hex 12)"
containers=()
failed=0

cleanup() {
  if (( ${#containers[@]} )); then
    docker rm --force --volumes "${containers[@]}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

start_postgres() {
  local name=$1 admin_user=$2 owner_password=$3
  local admin_password runtime_password identity_password
  admin_password=$(openssl rand -hex 24) || return 1
  runtime_password=$(openssl rand -hex 24) || return 1
  identity_password=$(openssl rand -hex 24) || return 1
  containers+=("$name")
  POSTGRES_PASSWORD="$admin_password" ETAYE_OWNER_PASSWORD="$owner_password" \
    ETAYE_RUNTIME_PASSWORD="$runtime_password" ETAYE_IDENTITY_PASSWORD="$identity_password" \
    docker run --detach --name "$name" \
      --env "POSTGRES_USER=$admin_user" --env POSTGRES_DB=etaye --env POSTGRES_PASSWORD \
      --env POSTGRES_HOST_AUTH_METHOD=scram-sha-256 \
      --env ETAYE_OWNER_PASSWORD --env ETAYE_RUNTIME_PASSWORD --env ETAYE_IDENTITY_PASSWORD \
      --volume "$script_dir/init-roles.sh:/docker-entrypoint-initdb.d/01-init-roles.sh:ro,z" \
      postgres:18 >/dev/null
}

before_deadline() {
  local remaining=$(( $1 - SECONDS ))
  shift
  (( remaining > 0 )) && timeout "${remaining}s" "$@"
}

wait_for_init() {
  local name=$1 deadline=$((SECONDS + 60)) running logs
  while (( SECONDS < deadline )); do
    running=$(before_deadline "$deadline" docker inspect --format '{{.State.Running}}' "$name") || return 1
    [[ "$running" == true ]] || return 1
    logs=$(before_deadline "$deadline" docker logs "$name" 2>&1) || return 1
    # The init server has no TCP listener. Check the final server after the log marker.
    if [[ "$logs" == *"PostgreSQL init process complete"* ]] &&
       before_deadline "$deadline" docker exec "$name" pg_isready --host=127.0.0.1 --username=postgres --dbname=etaye >/dev/null; then
      return 0
    fi
    (( SECONDS < deadline )) || return 1
    sleep 1
  done
  return 1
}

verify_roles_and_schema() {
  local name="$prefix-roles" password result
  password=$(openssl rand -hex 24) || return 1
  start_postgres "$name" postgres "$password" && wait_for_init "$name" || return 1
  result=$(docker exec -i "$name" psql -X -w -At -v ON_ERROR_STOP=1 --username=postgres --dbname=etaye <<'SQL'
SELECT count(*) = 3
   AND bool_and(rolcanlogin AND NOT (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls))
   AND NOT EXISTS (
       SELECT FROM pg_catalog.pg_auth_members AS membership
       JOIN pg_catalog.pg_roles AS member_role ON member_role.oid = membership.member
       WHERE member_role.rolname IN ('etaye_owner', 'etaye_runtime', 'etaye_identity')
   )
   AND EXISTS (
       SELECT FROM pg_catalog.pg_namespace AS schema
       JOIN pg_catalog.pg_roles AS owner ON owner.oid = schema.nspowner
       WHERE schema.nspname = 'public' AND owner.rolname = 'etaye_owner'
   )
FROM pg_catalog.pg_roles
WHERE rolname IN ('etaye_owner', 'etaye_runtime', 'etaye_identity');
SQL
  ) || return 1
  [[ "$result" == t ]]
}

verify_admin_collision() {
  local name="$prefix-collision" password exit_code logs
  password=$(openssl rand -hex 24) || return 1
  start_postgres "$name" etaye_runtime "$password" || return 1
  exit_code=$(timeout 60s docker wait "$name") || return 1
  logs=$(docker logs "$name" 2>&1) || return 1
  [[ "$exit_code" =~ ^[1-9][0-9]*$ &&
     "$logs" == *"Refusing POSTGRES_USER=etaye_runtime: reserved Étaye application role."* ]]
}

verify_literal_password() {
  local name="$prefix-password" password result
  password=$(openssl rand -hex 24) || return 1
  password+="';\"\$(x)"
  start_postgres "$name" postgres "$password" && wait_for_init "$name" || return 1
  result=$(PGPASSWORD="$password" docker exec --env PGPASSWORD "$name" \
    psql -X -w -At -v ON_ERROR_STOP=1 --host=127.0.0.1 --username=etaye_owner --dbname=etaye \
    --command='SELECT current_user') || return 1
  [[ "$result" == etaye_owner ]]
}

run_case() {
  local label=$1
  shift
  if "$@"; then
    printf 'PASS %s\n' "$label"
  else
    printf 'FAIL %s\n' "$label"
    failed=1
  fi
}

run_case "Application role attributes and public schema owner" verify_roles_and_schema
run_case "Admin collision with etaye_runtime is refused" verify_admin_collision
run_case "Owner can authenticate with the literal password" verify_literal_password
exit "$failed"
