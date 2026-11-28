#!/usr/bin/env bash
set -euo pipefail
umask 077

manifest=${1:?Pass the verified published manifest}
source=${2:?Pass the candidate release SHA}
active_source=${3:?Pass the currently serving release SHA}
active_revision=${4:?Pass the currently serving Azure revision}
receipt=${DEMO_MIGRATION_RECEIPT:-}
public_key=${DEMO_MIGRATION_PUBLIC_KEY:-}

[[ "$source" =~ ^[a-f0-9]{40}$ && "$active_source" =~ ^[a-f0-9]{40}$ ]]
[[ "$active_revision" =~ ^ca-etaye-demo--[a-z0-9-]+$ ]]
[[ "${#receipt}" -le 4096 && "$receipt" =~ ^([A-Za-z0-9+/]+=*)\.([A-Za-z0-9+/]+=*)$ ]] ||
    { echo 'A signed migration receipt is required for this release.' >&2; exit 1; }
document=${BASH_REMATCH[1]}
signature=${BASH_REMATCH[2]}
key_pattern='^ssh-ed25519 [A-Za-z0-9+/]+=*$'
[[ "${#public_key}" -le 128 && "$public_key" =~ $key_pattern ]] ||
    { echo 'The demo migration signing public key is unavailable.' >&2; exit 1; }

proof=$(mktemp -d)
trap 'rm -r -- "$proof"' EXIT
printf '%s' "$document" | base64 --decode > "$proof/receipt.json"
printf '%s' "$signature" | base64 --decode > "$proof/receipt.sig"
printf 'etaye-demo-migration %s\n' "$public_key" > "$proof/allowed-signers"
ssh-keygen -Y verify -f "$proof/allowed-signers" -I etaye-demo-migration \
    -n etaye-demo-migration -s "$proof/receipt.sig" < "$proof/receipt.json" >/dev/null

migrator=$(jq -er '.images.migrate.reference' "$manifest")
[[ "$migrator" =~ ^ghcr\.io/aymenchaghoub/etaye-migrate@sha256:[a-f0-9]{64}$ ]]
schema_versions() {
    git ls-tree --name-only "$1:apps/api/src/main/resources/db/migration" |
        jq -Rsec '
            split("\n") | map(select(length > 0))
            | if all(.[]; test("^V[0-9]+__.+\\.sql$")) then
                  map(capture("^V(?<version>[0-9]+)__").version) | sort_by(tonumber)
              else error("Unexpected managed migration filename") end'
}
before=$(schema_versions "$active_source")
after=$(schema_versions "$source")
now=$(date +%s)
jq -es --arg candidate "$source" --arg active "$active_source" --arg revision "$active_revision" \
    --arg migrator "$migrator" --argjson before "$before" --argjson after "$after" --argjson now "$now" '
    length == 1 and (.[0] |
    .version == 1 and .database == "etaye_demo_cloud" and
    .candidateSha == $candidate and .activeSha == $active and .activeRevision == $revision and
    .migrator == $migrator and
    ($before | length > 0) and ($after | length > 0) and
    .schemaBefore == $before and .schemaAfter == $after and
    .backupRestored == true and .flywayValidated == true and .activeCompatible == true and
    (.verifiedAt | type == "number") and .verifiedAt <= $now and .verifiedAt >= ($now - 86400))
' "$proof/receipt.json" >/dev/null
sha256sum < "$proof/receipt.json" | cut -d' ' -f1
