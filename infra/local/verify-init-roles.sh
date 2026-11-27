#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
prefix="etaye-init-$(openssl rand -hex 12)"
containers=()
failed=0

# The image under test is the one the Compose file ships (pinned by digest there), not a second copy of its name.
postgres_image=$(
  POSTGRES_ADMIN_PASSWORD=x ETAYE_OWNER_PASSWORD=x ETAYE_RUNTIME_PASSWORD=x ETAYE_IDENTITY_PASSWORD=x \
    docker compose -f "$script_dir/docker-compose.yml" config --format json |
    python3 -c 'import json, sys; print(json.load(sys.stdin)["services"]["postgres"]["image"])'
)
[[ -n "$postgres_image" ]]

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
      "$postgres_image" >/dev/null
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

healthcheck_command() {
  POSTGRES_ADMIN_PASSWORD=x ETAYE_OWNER_PASSWORD=x ETAYE_RUNTIME_PASSWORD=x ETAYE_IDENTITY_PASSWORD=x \
    docker compose -f "$script_dir/docker-compose.yml" config --format json |
    python3 -c 'import json, sys; print(json.load(sys.stdin)["services"]["postgres"]["healthcheck"]["test"][1])'
}

verify_health_follows_role_attributes() {
  # One change at a time on a fully provisioned container: healthy, then unhealthy because of a single
  # privileged attribute or membership, then healthy again. The init hook runs only once, so only the
  # health check can notice such a drift on an initialized volume.
  local command name="$prefix-health" password
  command=$(healthcheck_command) || return 1
  password=$(openssl rand -hex 24) || return 1
  start_postgres "$name" postgres "$password" && wait_for_init "$name" || return 1
  local psql_admin=(docker exec "$name" psql -X -w -At -v ON_ERROR_STOP=1 --username=postgres --dbname=etaye --command)
  healthy() { docker exec "$name" sh -c "$command" >/dev/null 2>&1; }
  healthy || return 1
  "${psql_admin[@]}" "ALTER ROLE etaye_runtime SUPERUSER" >/dev/null || return 1
  ! healthy || return 1
  "${psql_admin[@]}" "ALTER ROLE etaye_runtime NOSUPERUSER" >/dev/null || return 1
  healthy || return 1
  "${psql_admin[@]}" "GRANT etaye_owner TO etaye_runtime" >/dev/null || return 1
  ! healthy || return 1
  "${psql_admin[@]}" "REVOKE etaye_owner FROM etaye_runtime" >/dev/null || return 1
  healthy
}

verify_compose_settings() {
  # The administrator name must stay fixed even when an override is supplied, and the port must stay on the loopback.
  local json
  json=$(POSTGRES_ADMIN_PASSWORD=x ETAYE_OWNER_PASSWORD=x ETAYE_RUNTIME_PASSWORD=x ETAYE_IDENTITY_PASSWORD=x \
    POSTGRES_ADMIN_USER=etaye_runtime \
    docker compose -f "$script_dir/docker-compose.yml" config --format json) || return 1
  python3 -c '
import json, sys
service = json.loads(sys.argv[1])["services"]["postgres"]
assert service["environment"]["POSTGRES_USER"] == "postgres", service["environment"]["POSTGRES_USER"]
ports = service["ports"]
assert ports and all(port.get("host_ip") == "127.0.0.1" for port in ports), ports
' "$json"
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

verify_nonsuperuser_createrole_provisioning() {
  local name="$prefix-managed" root_password admin_password owner_password runtime_password identity_password result
  root_password=$(openssl rand -hex 24) || return 1
  admin_password=$(openssl rand -hex 24) || return 1
  owner_password=$(openssl rand -hex 24) || return 1
  runtime_password=$(openssl rand -hex 24) || return 1
  identity_password=$(openssl rand -hex 24) || return 1
  containers+=("$name")
  docker run --detach --name "$name" \
    --env POSTGRES_USER=postgres --env "POSTGRES_PASSWORD=$root_password" \
    --env POSTGRES_DB=postgres --env POSTGRES_HOST_AUTH_METHOD=scram-sha-256 \
    "$postgres_image" >/dev/null || return 1

  local deadline=$((SECONDS + 60)) running
  while (( SECONDS < deadline )); do
    running=$(before_deadline "$deadline" docker inspect --format '{{.State.Running}}' "$name") || return 1
    [[ "$running" == true ]] || return 1
    if before_deadline "$deadline" docker exec "$name" pg_isready --host=127.0.0.1 --username=postgres --dbname=postgres >/dev/null; then
      break
    fi
    sleep 1
  done

  PGPASSWORD="$root_password" docker exec -i --env PGPASSWORD "$name" \
    psql -X -w -v ON_ERROR_STOP=1 --host=127.0.0.1 --username=postgres --dbname=postgres <<SQL >/dev/null || return 1
CREATE ROLE etaye_test_admin WITH LOGIN PASSWORD '$admin_password' NOSUPERUSER NOCREATEDB CREATEROLE NOBYPASSRLS;
CREATE DATABASE etaye_demo_managed OWNER etaye_test_admin;
SQL

  run_managed_init() {
    docker exec -i \
      --env PGHOST=127.0.0.1 \
      --env POSTGRES_ADMIN_USER=etaye_test_admin \
      --env "POSTGRES_ADMIN_PASSWORD=$admin_password" \
      --env POSTGRES_DB=etaye_demo_managed \
      --env "ETAYE_OWNER_PASSWORD=$owner_password" \
      --env "ETAYE_RUNTIME_PASSWORD=$runtime_password" \
      --env "ETAYE_IDENTITY_PASSWORD=$identity_password" \
      "$name" bash -s < "$script_dir/init-roles.sh" >/dev/null
  }

  run_managed_init || return 1
  # Resuming provisioning also removes a stale explicit grant from this schema.
  PGPASSWORD="$owner_password" docker exec --env PGPASSWORD "$name" \
    psql -X -w -v ON_ERROR_STOP=1 --host=127.0.0.1 --username=etaye_owner --dbname=etaye_demo_managed \
    --command='GRANT CREATE ON SCHEMA public TO etaye_runtime' >/dev/null || return 1
  run_managed_init || return 1

  result=$(PGPASSWORD="$admin_password" docker exec -i --env PGPASSWORD "$name" \
    psql -X -w -At -v ON_ERROR_STOP=1 --host=127.0.0.1 --username=etaye_test_admin --dbname=etaye_demo_managed <<'SQL'
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
   AND has_schema_privilege('etaye_runtime', 'public', 'USAGE')
   AND has_schema_privilege('etaye_identity', 'public', 'USAGE')
   AND NOT has_schema_privilege('etaye_runtime', 'public', 'CREATE')
   AND NOT has_schema_privilege('etaye_identity', 'public', 'CREATE')
   AND NOT pg_has_role('etaye_test_admin', 'etaye_owner', 'SET')
   AND NOT pg_has_role('etaye_test_admin', 'etaye_owner', 'USAGE')
FROM pg_catalog.pg_roles
WHERE rolname IN ('etaye_owner', 'etaye_runtime', 'etaye_identity');
SQL
  ) || return 1
  [[ "$result" == t ]] || return 1

  # Also verify mark-demo-database.sh refuses unmarked custom DB names and succeeds once explicitly confirmed.
  if docker exec -i \
      --env PGHOST=127.0.0.1 \
      --env POSTGRES_ADMIN_USER=etaye_test_admin \
      --env "POSTGRES_ADMIN_PASSWORD=$admin_password" \
      --env POSTGRES_DB=etaye_demo_managed \
      "$name" bash -s < "$script_dir/../demo/mark-demo-database.sh" >/dev/null 2>&1; then
    return 1
  fi

  docker exec -i \
    --env PGHOST=127.0.0.1 \
    --env POSTGRES_ADMIN_USER=etaye_test_admin \
    --env "POSTGRES_ADMIN_PASSWORD=$admin_password" \
    --env POSTGRES_DB=etaye_demo_managed \
    --env ETAYE_MANAGED_DEMO_DB=etaye_demo_managed \
    "$name" bash -s < "$script_dir/../demo/mark-demo-database.sh" >/dev/null || return 1

  local marked
  marked=$(PGPASSWORD="$admin_password" docker exec -i --env PGPASSWORD "$name" \
    psql -X -w -At -v ON_ERROR_STOP=1 --host=127.0.0.1 --username=etaye_test_admin --dbname=etaye_demo_managed <<'SQL'
SELECT EXISTS (
    SELECT FROM pg_catalog.pg_namespace n
    JOIN pg_catalog.pg_roles r ON r.oid = n.nspowner
    WHERE n.nspname = 'etaye_demo_bootstrap' AND r.rolname = 'etaye_test_admin'
) AND EXISTS (
    SELECT FROM pg_catalog.pg_namespace n
    JOIN pg_catalog.pg_roles r ON r.oid = n.nspowner
    WHERE n.nspname = 'etaye_demo' AND r.rolname = 'etaye_owner'
) AND NOT pg_has_role('etaye_test_admin', 'etaye_owner', 'SET');
SQL
  ) || return 1
  [[ "$marked" == t ]]
}

run_case "Application role attributes and public schema owner" verify_roles_and_schema
run_case "Admin collision with etaye_runtime is refused" verify_admin_collision
run_case "Owner can authenticate with the literal password" verify_literal_password
run_case "Compose fixes the administrator name and binds the port to the loopback" verify_compose_settings
run_case "Health check follows the role attributes on every start" verify_health_follows_role_attributes
run_case "Non-superuser CREATEROLE admin provisions roles, public owner, and demo schemas idempotently" verify_nonsuperuser_createrole_provisioning
exit "$failed"
