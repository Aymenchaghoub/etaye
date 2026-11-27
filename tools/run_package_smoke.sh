#!/usr/bin/env bash
set -euo pipefail

# Real-stack browser journeys against the running demonstration package (Spring, PostgreSQL, React, real sessions
# and CSRF). Start the package first, with the same two
# variables exported for both commands (this script restarts the API container of that Compose project):
#   export COMPOSE_PROJECT_NAME=etaye-demo-ci ETAYE_DEMO_WEB_PORT=4179
#   ./infra/demo/start.sh && bash tools/run_package_smoke.sh
# Each spec result is validated for zero-test, skipped and failed runs; the package stays disposable.
project=${COMPOSE_PROJECT_NAME:-etaye-demo}
port=${ETAYE_DEMO_WEB_PORT:-4179}
base="http://127.0.0.1:${port}"
repo=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
reports="$repo/apps/web/playwright-report"
mkdir -p "$reports"

wait_ready() {
  local deadline=$(( SECONDS + 150 ))
  until curl --fail --silent --max-time 3 "$base/api/v1/probes/readiness" | grep -q '"UP"'; do
    if (( SECONDS >= deadline )); then
      echo "The package did not report readiness at $base within 150 seconds." >&2
      return 1
    fi
    sleep 2
  done
}

verify_security_headers() {
  python3 - "$base" <<'PY'
import http.client
import sys
from urllib.parse import urlparse

base = urlparse(sys.argv[1])
routes = [
    ("/", "200"),
    ("/connexion", "200"),
    ("/tokens.css", "200"),
    ("/api/v1/meta", "200"),
    ("/api/v1/session", "401"),
]
for path, expected_status in routes:
    conn = http.client.HTTPConnection(base.hostname, base.port, timeout=5)
    conn.request("GET", path, headers={"Host": "127.0.0.1"})
    resp = conn.getresponse()
    resp.read()
    headers = {}
    for k, v in resp.getheaders():
        headers.setdefault(k.lower(), []).append(v)
    conn.close()
    assert str(resp.status) == expected_status, f"{path}: expected {expected_status}, got {resp.status}"
    assert headers.get("x-frame-options") == ["DENY"], f"{path}: X-Frame-Options={headers.get('x-frame-options')}"
    assert headers.get("x-content-type-options") == ["nosniff"], f"{path}: X-Content-Type-Options={headers.get('x-content-type-options')}"
    assert len(headers.get("content-security-policy", [])) == 1, f"{path}: CSP missing or duplicated"
    assert "frame-ancestors 'none'" in headers["content-security-policy"][0], f"{path}: CSP frame-ancestors"
    assert len(headers.get("permissions-policy", [])) == 1, f"{path}: Permissions-Policy missing"
    assert "nginx/" not in " ".join(headers.get("server", [])), f"{path}: Server leaks version: {headers.get('server')}"
# Verify Host header guard returns 421 and still includes security headers
conn = http.client.HTTPConnection(base.hostname, base.port, timeout=5)
conn.request("GET", "/", headers={"Host": "evil.example.test"})
resp = conn.getresponse()
resp.read()
h421 = {k.lower(): v for k, v in resp.getheaders()}
conn.close()
assert resp.status == 421, f"foreign Host expected 421, got {resp.status}"
assert h421.get("x-frame-options") == "DENY", f"421 missing X-Frame-Options: {h421}"
assert "content-security-policy" in h421, f"421 missing CSP: {h421}"
print(f"PASS security headers verified on {len(routes)} routes + 421")
PY
}

# spec name and the number of tests it must run: a spec that runs fewer is a failure, not a pass.
specs=("record:3" "measurements:3" "laboratory:3" "patients:5")
wait_ready
verify_security_headers
for entry in "${specs[@]}"; do
  spec=${entry%%:*}
  minimum=${entry##*:}
  # The package limits sign-ins per address each minute; restarting the API container clears the limiter.
  docker restart "${project}-api-1" > /dev/null
  wait_ready
  echo "== e2e/${spec}.spec.ts"
  (
    cd "$repo/apps/web"
    ETAYE_E2E_BASE_URL="$base" ETAYE_E2E_DB_CONTAINER="${project}-postgres-1" ETAYE_E2E_DB_NAME=etaye_demo \
      PLAYWRIGHT_JSON_OUTPUT_FILE="$reports/real-${spec}.json" \
      pnpm exec playwright test --config=playwright.real.config.ts "e2e/${spec}.spec.ts" \
      --reporter=json --output "test-results/real-${spec}" > /dev/null
  )
  python3 "$repo/tools/assert_test_reports.py" playwright "$reports/real-${spec}.json" \
    --min-tests "$minimum" --require-file "e2e/${spec}.spec.ts"
done
