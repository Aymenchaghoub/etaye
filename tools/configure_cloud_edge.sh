#!/bin/sh
set -eu

fail() {
    printf '%s\n' "$1" >&2
    exit 1
}

valid_host() {
    # Check the alphabet first so an embedded newline cannot pass grep's line-based match.
    case "$1" in
        ''|*[!a-z0-9.-]*) return 1 ;;
    esac
    [ "${#1}" -le 253 ] &&
        printf '%s\n' "$1" | grep -Eq '^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$'
}

host=${ETAYE_INGRESS_HOST:?Set the Container Apps ingress hostname}
alias=${ETAYE_CUSTOM_INGRESS_HOST:-}
valid_host "$host" || fail "Invalid Container Apps ingress hostname."
if [ -n "$alias" ]; then
    valid_host "$alias" || fail "Invalid custom ingress hostname."
    [ "$alias" != "$host" ] || fail "The custom hostname must differ from the Container Apps hostname."
fi

config=/etc/nginx/cloud-conf
case "${ETAYE_EDGE_AUTH_MODE-basic}" in
    basic)
        [ -r /run/etaye-secrets/visitors ] || fail "Missing visitor password file in Basic mode."
        printf '%s\n' 'auth_basic "Etaye Demo";' 'auth_basic_user_file /run/etaye-secrets/visitors;' > "$config/visitor-auth.inc"
        ;;
    ip-restricted)
        printf '%s\n' 'auth_basic off;' > "$config/visitor-auth.inc"
        ;;
    *) fail "Invalid edge authentication mode." ;;
esac

{
    printf '"%s" 1;\n' "$host"
    if [ -n "$alias" ]; then
        printf '"%s" 1;\n' "$alias"
    fi
} > "$config/allowed-hosts.inc"
