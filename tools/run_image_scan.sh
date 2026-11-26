#!/usr/bin/env bash
set -euo pipefail

# Report all HIGH and CRITICAL findings in the four images built by Compose.
# Scan failures fail the step; findings remain visible as evidence during the observation period.
# Verify the upstream release before executing the scanner; no third-party action.
scanner_version=0.76.2
scanner_sha256=bc0fc726e017b8927a62b2d3531d7a84ee01769daf5c99ed866dced54089fdbf
project=${COMPOSE_PROJECT_NAME:-etaye-demo}
repo=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
scanner_temp=$(mktemp -d)
trap 'rm -rf "$scanner_temp"' EXIT
scanner_archive="$scanner_temp/trivy.tar.gz"
curl --fail --silent --show-error --location \
  "https://github.com/aquasecurity/trivy/releases/download/v${scanner_version}/trivy_${scanner_version}_Linux-64bit.tar.gz" \
  --output "$scanner_archive"
printf '%s  %s\n' "$scanner_sha256" "$scanner_archive" | sha256sum --check --status
tar -xzf "$scanner_archive" --directory "$scanner_temp" trivy

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
