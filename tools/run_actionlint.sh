#!/usr/bin/env bash
set -euo pipefail

# Verify the upstream release before executing the linter; no third-party action. actionlint checks workflow
# syntax and expressions, and runs ShellCheck on every `run:` script when it is on the PATH (it is on the
# GitHub-hosted Ubuntu image).
linter_version=1.7.12
linter_sha256=8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8
linter_temp=$(mktemp -d)
trap 'rm -rf "$linter_temp"' EXIT
linter_archive="$linter_temp/actionlint.tar.gz"
curl --fail --silent --show-error --location \
  "https://github.com/rhysd/actionlint/releases/download/v${linter_version}/actionlint_${linter_version}_linux_amd64.tar.gz" \
  --output "$linter_archive"
printf '%s  %s\n' "$linter_sha256" "$linter_archive" | sha256sum --check --status
tar -xzf "$linter_archive" --directory "$linter_temp" actionlint
# Explicit paths: project detection needs a `.git` directory, which a Git worktree does not have.
"$linter_temp/actionlint" -color .github/workflows/*.y*ml
