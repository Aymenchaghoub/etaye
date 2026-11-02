#!/usr/bin/env bash
set -euo pipefail

# Verify the upstream release before executing the scanner; no licensed action or API key.
scanner_version=8.30.1
scanner_sha256=551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb
scanner_temp=$(mktemp -d)
trap 'rm -rf "$scanner_temp"' EXIT
scanner_archive="$scanner_temp/gitleaks.tar.gz"
curl --fail --silent --show-error --location \
  "https://github.com/gitleaks/gitleaks/releases/download/v${scanner_version}/gitleaks_${scanner_version}_linux_x64.tar.gz" \
  --output "$scanner_archive"
printf '%s  %s\n' "$scanner_sha256" "$scanner_archive" | sha256sum --check --status
tar -xzf "$scanner_archive" --directory "$scanner_temp" gitleaks
mkdir -p reports
"$scanner_temp/gitleaks" git --log-opts="--all" --redact=100 \
  --report-format json --report-path reports/gitleaks.json --no-banner .
