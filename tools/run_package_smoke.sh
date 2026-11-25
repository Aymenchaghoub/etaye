#!/usr/bin/env bash
set -euo pipefail

# Real-stack browser journeys against the running demonstration package (Spring, PostgreSQL, React, real sessions
# and CSRF): the same three specs that every story runs by hand. Start the package first:
#   COMPOSE_PROJECT_NAME=etaye-demo-ci ETAYE_DEMO_WEB_PORT=4179 ./infra/demo/start.sh
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

# spec name and the number of tests it must run: a spec that runs fewer is a failure, not a pass.
specs=("record:3" "measurements:3" "patients:5")
wait_ready
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
