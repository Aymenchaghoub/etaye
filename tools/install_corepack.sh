#!/usr/bin/env bash
set -euo pipefail

# Node 26 no longer bundles Corepack. Install the exact release whose npm tarball digest was verified, so a
# registry change cannot replace the package manager bootstrapper unnoticed. pnpm itself is pinned with an
# integrity hash in the packageManager field of package.json and verified by Corepack.
corepack_version=0.36.0
corepack_sha512=4a2889b01859a9d0623c74c497a393dec012ad1aca21c61340cb15197c7a104fe033c5853306237885fc4edf11894e08bf627a2db4fc2a919f08eb01a51581ff

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
(cd "$work" && npm pack "corepack@${corepack_version}" --silent > /dev/null)
printf '%s  %s\n' "$corepack_sha512" "$work/corepack-${corepack_version}.tgz" | sha512sum --check --status
npm install --global "$work/corepack-${corepack_version}.tgz"
