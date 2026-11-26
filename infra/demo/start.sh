#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"

if [[ ! -f .env ]]; then
    umask 077
    temp=$(mktemp .env.XXXXXX)
    trap 'rm -f -- "$temp"' EXIT
    for field in POSTGRES_ADMIN_PASSWORD ETAYE_OWNER_PASSWORD ETAYE_RUNTIME_PASSWORD ETAYE_IDENTITY_PASSWORD; do
        password=$(openssl rand -hex 24)
        printf '%s=%s\n' "$field" "$password"
    done > "$temp"
    # A concurrent first start retains the first completed environment file.
    ln -- "$temp" .env 2>/dev/null || [[ -f .env ]]
fi

case "${1:-}" in
    --build-only) shift; docker compose --progress plain build "$@" ;;
    --no-build) shift; docker compose up --no-build -d "$@" ;;
    *) docker compose up --build -d "$@" ;;
esac
