#!/usr/bin/env bash
set -euo pipefail

manifest=${1:?Pass the published manifest path}
sha=${2:?Pass the exact release SHA}
repository=Aymenchaghoub/etaye
[[ "$sha" =~ ^[a-f0-9]{40}$ ]]

proof=(--repo "$repository" --signer-workflow "$repository/.github/workflows/release.yml"
       --source-ref refs/heads/main --source-digest "$sha" --deny-self-hosted-runners)
gh attestation verify "$manifest" "${proof[@]}"
jq -e --arg sha "$sha" '
    .mode == "published" and .gitSha == $sha and .provenance.status == "verified" and
    (.images | keys) == ["api", "migrate", "web"]
' "$manifest" >/dev/null
for name in api web migrate; do
    image=$(jq -er --arg name "$name" '.images[$name].reference' "$manifest")
    [[ "$image" =~ ^ghcr\.io/aymenchaghoub/etaye-$name@sha256:[a-f0-9]{64}$ ]]
    digest=${image##*@}
    received=$(docker buildx imagetools inspect --raw "$image" | sha256sum)
    [[ ${received%% *} == "${digest#sha256:}" ]]
    gh attestation verify "oci://$image" "${proof[@]}"
    gh attestation verify "oci://$image" "${proof[@]}" --predicate-type https://cyclonedx.org/bom
done
