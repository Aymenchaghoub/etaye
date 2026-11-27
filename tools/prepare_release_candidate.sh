#!/usr/bin/env bash
set -euo pipefail
umask 077

reports=${1:?Pass a new absolute evidence directory outside the checkout}
git_sha=${2:?Pass the expected full commit SHA}
repo=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo"
if [[ "$git_sha" != "$(git rev-parse HEAD)" || -n "$(git status --porcelain --untracked-files=normal)" ]]; then
  echo "The release rehearsal requires an exact, clean source revision." >&2
  exit 1
fi
reports=$(realpath -m -- "$reports")
case "$reports/" in
  "$repo/"*) echo "Write evidence outside the build context." >&2; exit 1 ;;
esac
mkdir -- "$reports"

# Never attach to or clean an existing Compose project.
project_suffix=$(openssl rand -hex 8)
export COMPOSE_PROJECT_NAME="etaye-release-$project_suffix"
export ETAYE_DEMO_WEB_PORT=${ETAYE_DEMO_WEB_PORT:-4189}
POSTGRES_ADMIN_PASSWORD=$(openssl rand -hex 24)
ETAYE_OWNER_PASSWORD=$(openssl rand -hex 24)
ETAYE_RUNTIME_PASSWORD=$(openssl rand -hex 24)
ETAYE_IDENTITY_PASSWORD=$(openssl rand -hex 24)
export POSTGRES_ADMIN_PASSWORD ETAYE_OWNER_PASSWORD ETAYE_RUNTIME_PASSWORD ETAYE_IDENTITY_PASSWORD
compose=(docker compose -f infra/demo/compose.yml)
scanner_temp=$(mktemp -d)
cleanup() {
  local status=$?
  if ! "${compose[@]}" down --volumes --remove-orphans --rmi local; then
    echo "Could not remove the disposable package $COMPOSE_PROJECT_NAME." >&2
    status=1
  fi
  rm -rf -- "$scanner_temp"
  exit "$status"
}
trap cleanup EXIT

capture_runtime() {
  local service id
  for service in api web migrate postgres; do
    id=$("${compose[@]}" ps -a -q "$service")
    docker container inspect "$id" | python3 -c '
import json, sys
container, = json.load(sys.stdin)
print(json.dumps(container["Id"]), json.dumps(container["Image"]), json.dumps(container.get("ImageManifestDescriptor")))
'
  done
}

# Reuse the exact package build and journeys used by Package verify.
bash tools/build_demo_package.sh
capture_runtime > "$reports/runtime-before.txt"
for service in api web migrate postgres; do
  # Save the image by the ID recorded on the actual Compose container. The archive preserves the platform
  # manifest bytes. A docker-daemon to OCI conversion can reserialize the config and change the digest.
  container=$("${compose[@]}" ps -a -q "$service")
  runtime_id=$(docker container inspect --format '{{.Image}}' "$container")
  docker image save --platform linux/amd64 --output "$reports/$service.tar" "$runtime_id"
done
bash tools/run_package_smoke.sh
capture_runtime > "$reports/runtime-after.txt"
cmp "$reports/runtime-before.txt" "$reports/runtime-after.txt"
bash tools/run_cloud_proxy_checks.sh "$reports"
cat "$reports/cloud-runtime-before.txt" >> "$reports/runtime-before.txt"
cat "$reports/cloud-runtime-after.txt" >> "$reports/runtime-after.txt"
cmp "$reports/runtime-before.txt" "$reports/runtime-after.txt"
for spec in record measurements patients; do
  cp -- "apps/web/playwright-report/real-$spec.json" "$reports/"
done

# Account for the upstream PostgreSQL base separately from the tested, derived package image.
read -r instruction upstream trailing < infra/demo/postgres.Dockerfile
if [[ "$instruction" != FROM || -n "$trailing" || ! "$upstream" =~ ^postgres:[0-9.]+@sha256:[a-f0-9]{64}$ ]]; then
  echo "The PostgreSQL base image must be pinned and traceable." >&2
  exit 1
fi
docker buildx imagetools inspect --raw "$upstream" > "$reports/upstream-postgres-index.json"
docker pull --platform linux/amd64 "$upstream"
docker image save --platform linux/amd64 --output "$reports/upstream-postgres.tar" "$upstream"

# Use one database snapshot for every image. Only the released scanner binary is executed.
bash tools/install_trivy.sh "$scanner_temp"
scanner="$scanner_temp/trivy"
cache=$(realpath -m -- "${RELEASE_TRIVY_CACHE:-$scanner_temp/cache}")
"$scanner" image --cache-dir "$cache" --download-db-only
"$scanner" image --cache-dir "$cache" --download-java-db-only
"$scanner" --version --format json --cache-dir "$cache" > "$reports/scanner.json"
(
  cd "$cache"
  sha256sum db/trivy.db java-db/trivy-java.db
) > "$reports/scanner-database-sha256.txt"
scan=("$scanner" image --cache-dir "$cache" --skip-db-update --skip-java-db-update --offline-scan --quiet)
for service in api web migrate postgres cloud-web upstream-postgres; do
  (
    cd "$reports"
    "${scan[@]}" --input "$service.tar" --format cyclonedx --output "sbom-$service.cdx.json"
    "${scan[@]}" --input "$service.tar" --scanners vuln --severity HIGH,CRITICAL --format json \
      --exit-code 0 --output "trivy-$service.json"
  )
done
(cd "$cache" && sha256sum --check "$reports/scanner-database-sha256.txt")
python3 tools/release_artifacts.py generate "$reports" --git-sha "$git_sha"
python3 tools/release_artifacts.py verify "$reports" --git-sha "$git_sha"
