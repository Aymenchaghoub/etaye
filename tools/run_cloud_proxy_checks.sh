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
# Exercise the maximum length accepted by the startup script and the Nginx host map.
printf -v label 'a%.0s' {1..63}
custom_host="$label.$label.$label.${label:0:61}"
test "${#custom_host}" = 253
start_proxy() {
    docker run --detach --name "$container" --network "container:$api" --cpus 0.25 --memory 512m \
        --env ETAYE_INGRESS_HOST=127.0.0.1 --env ETAYE_CUSTOM_INGRESS_HOST="$custom_host" \
        "$@" "$image" >/dev/null
}

capture() {
    docker container inspect "$container" | python3 -c '
import json, sys
record, = json.load(sys.stdin)
print(json.dumps(record["Id"]), json.dumps(record["Image"]), json.dumps(record.get("ImageManifestDescriptor")))
'
}
host=$(docker container inspect --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$api")
check_proxy() {
    ETAYE_PROXY_TEST_PASSWORD="$password" python3 - "$host" "$1" "$custom_host" <<'PY'
import base64
from contextlib import closing
import http.client
from http.cookies import SimpleCookie
import json
import os
import sys
import time

host, mode, custom_host = sys.argv[1:]
token = base64.b64encode(("demo:" + os.environ["ETAYE_PROXY_TEST_PASSWORD"]).encode()).decode()

def exchange(path, status, *, method="GET", request_host="127.0.0.1", port=8081, body=None, extra=None):
    headers = {"Host": request_host, **(extra or {})}
    conn = http.client.HTTPConnection(host, port, timeout=5)
    with closing(conn):
        conn.request(method, path, headers=headers, body=body)
        response = conn.getresponse()
        payload = response.read()
    assert response.status == status, f"{method} {path}: {response.status} != {status}"
    expected_hsts = ["max-age=300"] if port == 8081 else None
    assert response.headers.get_all("Strict-Transport-Security") == expected_hsts, f"{method} {path}: HSTS on port {port}"
    return payload, response.headers


def request(path, status, *, extra=None, **kwargs):
    headers = {"Authorization": "Basic " + token} if mode == "basic" else {}
    headers.update(extra or {})
    return exchange(path, status, extra=headers, **kwargs)


deadline = time.monotonic() + 30
while time.monotonic() < deadline:
    try:
        exchange("/ready", 200, port=8082)
        break
    except (OSError, AssertionError):
        time.sleep(1)
else:
    raise SystemExit("Cloud proxy did not reach its loopback API.")

exchange("/", 421, request_host="other.example.test")
exchange("/ready", 200, port=8082)
for hostname in ("127.0.0.1", custom_host):
    if mode == "basic":
        _, challenge = exchange("/connexion", 401, request_host=hostname)
        assert challenge["WWW-Authenticate"] == 'Basic realm="Etaye Demo"'
    notice, headers = request("/", 200, request_host=hostname)
    assert "aucune information personnelle ou médicale réelle".encode() in notice
    assert headers.get_all("X-Frame-Options") == ["DENY"]
    assert headers.get_all("X-Content-Type-Options") == ["nosniff"]
    assert "frame-ancestors 'none'" in headers["Content-Security-Policy"]
    assert headers["Cache-Control"] == "no-store"
    assert headers["X-Robots-Tag"] == "noindex, nofollow"
    _, login_headers = request("/connexion", 200, request_host=hostname)
    assert "WWW-Authenticate" not in login_headers
    _, session_headers = request("/api/v1/session", 401, request_host=hostname)
    assert "WWW-Authenticate" not in session_headers
    request("/api/v1/practices", 401, request_host=hostname)
    request("/api/v1/demo/reset", 404, method="POST", request_host=hostname)
    request("/api/v1/invitations/accept", 404, method="POST", request_host=hostname)
    request("/api/v1/access-recovery", 404, method="POST", request_host=hostname)
    request("/api/v1/practices/example/patients", 403, method="POST", request_host=hostname)
request("/tokens.css", 200)
request("/etaye-demo-notice.css", 200)
request("/api/v1/probes/readiness", 200)

if mode == "ip-restricted":
    login = json.dumps({"email": "missing@example.test", "password": "synthetic-invalid-password"})
    payload, headers = request("/api/v1/csrf", 200, request_host=custom_host)
    csrf = json.loads(payload)
    cookie = SimpleCookie()
    cookie.load(headers["Set-Cookie"])
    session = cookie["__Host-etaye-session"]
    assert session["secure"] and session["httponly"] and session["samesite"].lower() == "lax"
    assert session["path"] == "/" and not session["domain"]
    login_headers = {"Content-Type": "application/json", "Cookie": f"{session.key}={session.value}"}
    request("/api/v1/session", 403, method="POST", request_host=custom_host, body=login, extra=login_headers)
    login_headers[csrf["headerName"]] = csrf["token"]
    _, rejected = request("/api/v1/session", 401, method="POST", request_host=custom_host, body=login, extra=login_headers)
    assert "WWW-Authenticate" not in rejected
    request("/api/v1/session", 401, request_host=custom_host, extra={"Cookie": login_headers["Cookie"]})

print(f"PASS cloud proxy ({mode}): both hosts, readiness, visitor gate, headers, session routing and write isolation")
PY
}

start_proxy --volume "$auth/visitors:/run/etaye-secrets/visitors:ro,Z"
if [[ -n "$reports" ]]; then
    capture > "$reports/cloud-runtime-before.txt"
fi
check_proxy basic
if [[ -n "$reports" ]]; then
    capture > "$reports/cloud-runtime-after.txt"
    cmp "$reports/cloud-runtime-before.txt" "$reports/cloud-runtime-after.txt"
fi
runtime_id=$(docker container inspect --format '{{.Image}}' "$container")
docker container rm --force "$container" >/dev/null
start_proxy --env ETAYE_EDGE_AUTH_MODE=ip-restricted
test "$(docker container inspect --format '{{.Image}}' "$container")" = "$runtime_id"
check_proxy ip-restricted

reject_configuration() {
    local message=$1 output
    shift
    if output=$(timeout 15s docker run --rm --env ETAYE_INGRESS_HOST=demo.example.test "$@" "$image" 2>&1); then
        echo "Cloud proxy unexpectedly started with an invalid configuration." >&2
        return 1
    fi
    grep -Fxq "$message" <<< "$output"
}
reject_configuration "Invalid edge authentication mode." --env ETAYE_EDGE_AUTH_MODE=disabled
reject_configuration "Invalid custom ingress hostname." --env ETAYE_CUSTOM_INGRESS_HOST=$'demo.example.test\nother.example.test'
reject_configuration "Missing visitor password file in Basic mode."
echo "PASS invalid cloud edge settings fail closed"

if [[ -n "$reports" ]]; then
    docker image save --platform linux/amd64 --output "$reports/cloud-web.tar" "$runtime_id"
fi
