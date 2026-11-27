#!/usr/bin/env bash
set -euo pipefail

# Report all HIGH and CRITICAL findings in the four images built by Compose.
# Scan failures fail the step; findings remain visible as evidence during the observation period.
# Verify the upstream release before executing the scanner; no third-party action.
project=${COMPOSE_PROJECT_NAME:-etaye-demo}
repo=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
scanner_temp=$(mktemp -d)
trap 'rm -rf "$scanner_temp"' EXIT
bash "$repo/tools/install_trivy.sh" "$scanner_temp"

reports="$repo/reports/image-scan"
mkdir -p "$reports"
for service in api web postgres migrate; do
  image="${project}-${service}:latest"
  docker image inspect "$image" > /dev/null
  rm -f -- "$reports/$service.json"
  "$scanner_temp/trivy" image --quiet --image-src docker --offline-scan --scanners vuln \
    --severity HIGH,CRITICAL --format json --exit-code 0 --output "$reports/$service.json" "$image"
done

python3 - "$reports" <<'PYTHON'
import json
from pathlib import Path
import sys

reports = Path(sys.argv[1])
with (reports / "summary.md").open("w", encoding="utf-8") as summary:
    print("### Package image scan (report only)", file=summary)
    print("| Service | HIGH | CRITICAL | Distinct advisory IDs | Fixable package findings |", file=summary)
    print("| --- | ---: | ---: | ---: | ---: |", file=summary)
    for service in ("api", "web", "postgres", "migrate"):
        report = json.loads((reports / f"{service}.json").read_text(encoding="utf-8"))
        if not report["Results"]:
            raise SystemExit(f"No result targets in the scan of {service}")
        findings = [item for result in report["Results"] for item in result.get("Vulnerabilities") or []]
        high = sum(item["Severity"] == "HIGH" for item in findings)
        critical = sum(item["Severity"] == "CRITICAL" for item in findings)
        distinct = len({item["VulnerabilityID"] for item in findings})
        fixable = sum(bool(item.get("FixedVersion")) for item in findings)
        print(f"| {service} | {high} | {critical} | {distinct} | {fixable} |", file=summary)
PYTHON
cat "$reports/summary.md"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  cat "$reports/summary.md" >> "$GITHUB_STEP_SUMMARY"
fi
