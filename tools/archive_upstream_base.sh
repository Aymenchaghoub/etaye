#!/usr/bin/env bash
# Copy the pinned Linux amd64 platform of an upstream base straight from its registry.
# Docker Engine 28 saves images of its classic store as normalized OCI archives with a reserialized
# manifest and uncompressed layers, so a pull-then-save archive never matches the pinned platform digest.
set -euo pipefail
umask 077

index=${1:?Pass the raw upstream index}
repository=${2:?Pass the upstream repository, for example docker.io/library/postgres}
archive=${3:?Pass the output OCI archive}
git_sha=${4:?Pass the expected full commit SHA}
repo=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)

# The pin comes from the Git object, so an edited index in the evidence directory is rejected first.
platform=$(python3 "$repo/tools/release_artifacts.py" upstream-platform "$(dirname -- "$index")" --git-sha "$git_sha")
skopeo copy --preserve-digests "docker://$repository@$platform" "oci-archive:$archive:upstream-postgres"
