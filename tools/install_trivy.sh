#!/usr/bin/env bash
set -euo pipefail

# Download a pinned release from the upstream publisher and verify before executing it.
directory=${1:?Pass an existing destination directory}
version=0.76.2
digest=bc0fc726e017b8927a62b2d3531d7a84ee01769daf5c99ed866dced54089fdbf
archive="$directory/trivy.tar.gz"
curl --fail --silent --show-error --location \
  "https://github.com/aquasecurity/trivy/releases/download/v${version}/trivy_${version}_Linux-64bit.tar.gz" \
  --output "$archive"
printf '%s  %s\n' "$digest" "$archive" | sha256sum --check --status
tar -xzf "$archive" --directory "$directory" trivy
