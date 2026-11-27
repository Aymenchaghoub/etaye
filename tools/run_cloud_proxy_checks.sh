#!/usr/bin/env bash
set -euo pipefail
umask 077

project=${COMPOSE_PROJECT_NAME:?Use the disposable package project}
repo=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo"
container="$project-cloud-web-1"
image="$project-cloud-web"
api="$project-api-1"
reports=${1:-}
test "$(docker container inspect --format '{{index .Config.Labels "com.docker.compose.project"}}' "$api")" = "$project"
if docker container inspect "$container" >/dev/null 2>&1 || docker image inspect "$image" >/dev/null 2>&1; then
    echo "Cloud proxy check already has resources for this project." >&2
    exit 1
fi
auth=$(mktemp -d)
cleanup() {
    if [[ $? != 0 ]]; then
        docker logs --tail 15 "$container" >&2 || true
    fi
    docker container rm --force "$container" >/dev/null 2>&1 || true
    docker image rm "$image" >/dev/null 2>&1 || true
    if [[ -f "$auth/visitors" ]]; then
        unlink "$auth/visitors"
    fi
    rmdir "$auth"
}
trap cleanup EXIT
password=$(openssl rand -hex 24)
hash=$(printf '%s\n' "$password" | openssl passwd -6 -stdin)
printf 'demo:%s\n' "$hash" > "$auth/visitors"
# The nginx worker reads the mounted hash. The directory stays owner-only.
chmod 644 "$auth/visitors"
docker build --platform linux/amd64 --target cloud --file infra/demo/web.Dockerfile --tag "$image" .
docker run --detach --name "$container" --network "container:$api" --cpus 0.25 --memory 512m \
    --env ETAYE_INGRESS_HOST=127.0.0.1 \
    --volume "$auth/visitors:/run/etaye-secrets/visitors:ro,Z" "$image" >/dev/null
capture() {
    docker container inspect "$container" | python3 -c '
import json, sys
record, = json.load(sys.stdin)
print(json.dumps(record["Id"]), json.dumps(record["Image"]), json.dumps(record.get("ImageManifestDescriptor")))
'
}
if [[ -n "$reports" ]]; then
    capture > "$reports/cloud-runtime-before.txt"
fi
host=$(docker container inspect --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$api")
ETAYE_PROXY_TEST_PASSWORD="$password" python3 - "$host" <<'PY'
import base64
from contextlib import closing
import http.client
import os
import sys
import time

host, = sys.argv[1:]
token = base64.b64encode(("demo:" + os.environ["ETAYE_PROXY_TEST_PASSWORD"]).encode()).decode()

def exchange(path, status, *, method="GET", authorized=True, request_host="127.0.0.1", port=8081):
    headers = {"Host": request_host}
    if authorized:
        headers["Authorization"] = "Basic " + token
    conn = http.client.HTTPConnection(host, port, timeout=5)
    with closing(conn):
        conn.request(method, path, headers=headers)
        response = conn.getresponse()
        body = response.read()
    assert response.status == status, f"{method} {path}: {response.status} != {status}"
    return body, response.headers

deadline = time.monotonic() + 30
while time.monotonic() < deadline:
    try:
        exchange("/ready", 200, authorized=False, port=8082)
        break
    except (OSError, AssertionError):
        time.sleep(1)
else:
    raise SystemExit("Cloud proxy did not reach its loopback API.")

exchange("/", 421, request_host="other.example.test")
exchange("/", 401, authorized=False)
notice, headers = exchange("/", 200)
assert "aucune information personnelle ou médicale réelle".encode() in notice
assert headers.get_all("X-Frame-Options") == ["DENY"]
assert headers.get_all("X-Content-Type-Options") == ["nosniff"]
assert "frame-ancestors 'none'" in headers["Content-Security-Policy"]
exchange("/connexion", 200)
exchange("/tokens.css", 200)
exchange("/etaye-demo-notice.css", 200)
exchange("/api/v1/probes/readiness", 200)
exchange("/api/v1/session", 401)
exchange("/api/v1/demo/reset", 404, method="POST")
exchange("/api/v1/invitations/accept", 404, method="POST")
exchange("/api/v1/access-recovery", 404, method="POST")
exchange("/api/v1/practices/example/patients", 403, method="POST")
print("PASS cloud proxy: readiness, host check, edge authentication, static notice, headers, session routing and write isolation")
PY
if [[ -n "$reports" ]]; then
    capture > "$reports/cloud-runtime-after.txt"
    cmp "$reports/cloud-runtime-before.txt" "$reports/cloud-runtime-after.txt"
    runtime_id=$(docker container inspect --format '{{.Image}}' "$container")
    docker image save --platform linux/amd64 --output "$reports/cloud-web.tar" "$runtime_id"
fi
