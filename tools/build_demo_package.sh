#!/usr/bin/env bash
set -euo pipefail

repo=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
start="$repo/infra/demo/start.sh"
log=$(mktemp)
trap 'rm -f -- "$log"' EXIT

central='https://repo\.maven\.apache\.org/maven2'
transient='(429|502|503|504)'
retryable_maven_failure() {
  # Restrict the fallback to explicit HTTP failures during the Maven distribution or dependency transfer.
  # Compilation, checksum, Docker resolution and configuration errors keep their original failure.
  if grep -qiE 'checksum (failed|validation failed|verification failed|mismatch)|failed to validate|COMPILATION ERROR' "$log"; then
    return 1
  fi
  grep -qE "Could not transfer artifact .* from/to central \($central\): .*status code: $transient([^0-9]|$)" "$log" ||
    grep -qE "java\.io\.IOException: Server returned HTTP response code: $transient for URL: $central/org/apache/maven/apache-maven/" "$log" ||
    { grep -qE "wget: Failed to fetch $central/org/apache/maven/apache-maven/[^[:space:]]+" "$log" &&
      grep -qE "ERROR $transient:" "$log"; } ||
    { grep -qE "curl: Failed to fetch $central/org/apache/maven/apache-maven/[^[:space:]]+" "$log" &&
      grep -qE "curl: \(22\).*error: $transient([^0-9]|$)" "$log"; }
}

"$start" --build-only web postgres migrate
if MAVEN_CENTRAL_SOURCE=central "$start" --build-only api 2>&1 | tee "$log"; then
  :
elif retryable_maven_failure; then
  echo "Maven Central returned a transient HTTP error; retrying the API build once via its mirror." >&2
  MAVEN_CENTRAL_SOURCE=mirror "$start" --build-only api
else
  exit 1
fi
"$start" --no-build
