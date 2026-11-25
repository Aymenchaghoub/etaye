#!/usr/bin/env bash
set -euo pipefail

# Report fixable high and critical vulnerabilities of the images the demonstration package built. Report only:
# the findings sit mostly in third-party base images that a rebuild or a Dependabot digest update addresses, so a
# blocking gate would be red without any change the repository can make. See docs/operations/ci-cd-quality-audit.md.
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

mkdir -p "$repo/reports"
report="$repo/reports/image-scan.txt"
: > "$report"
mapfile -t images < <(docker image ls --filter "reference=${project}-*" --format '{{.Repository}}:{{.Tag}}' | sort)
if (( ${#images[@]} == 0 )); then
  echo "No image of the ${project} package was found to scan." >&2
  exit 1
fi
for image in "${images[@]}"; do
  echo "## ${image}" | tee -a "$report"
  # A scanner failure (network, database download) is reported, not hidden: only findings are non-blocking.
  "$scanner_temp/trivy" image --quiet --scanners vuln --severity HIGH,CRITICAL --ignore-unfixed \
    --format table --exit-code 0 "$image" | tee -a "$report"
done
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  { echo '### Image scan (report only: fixable HIGH and CRITICAL)'; echo '```'; cat "$report"; echo '```'; } >> "$GITHUB_STEP_SUMMARY"
fi
