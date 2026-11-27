#!/usr/bin/env bash
set -euo pipefail
umask 077

manifest=${1:?Pass the previously verified release manifest}
source=${2:?Pass the verified source SHA}
stage=${3:?Pass true to stage or false for read-only OIDC verification}
for identifier in "${AZURE_CLIENT_ID:-}" "${AZURE_TENANT_ID:-}" "${AZURE_SUBSCRIPTION_ID:-}"; do
    [[ "$identifier" =~ ^[a-fA-F0-9-]{36}$ ]]
done
[[ "$source" =~ ^[a-f0-9]{40}$ && "$stage" =~ ^(true|false)$ ]]
[[ $(jq -er .gitSha "$manifest") == "$source" ]]

# The certificate is ephemeral and exchanged in an isolated runner Azure directory.
oidc=$(curl --fail --silent --show-error --max-time 15 \
    --header "Authorization: bearer $ACTIONS_ID_TOKEN_REQUEST_TOKEN" \
    "${ACTIONS_ID_TOKEN_REQUEST_URL}&audience=api%3A%2F%2FAzureADTokenExchange" | jq -er .value)
printf '::add-mask::%s\n' "$oidc"
az login --service-principal --username "$AZURE_CLIENT_ID" --tenant "$AZURE_TENANT_ID" \
    --federated-token "$oidc" --allow-no-subscriptions --only-show-errors --output none
unset oidc
path="/subscriptions/$AZURE_SUBSCRIPTION_ID/resourceGroups/rg-etaye-demo/providers/Microsoft.App/containerApps/ca-etaye-demo"
version='api-version=2025-07-01'
url="https://management.azure.com$path"
read_app() {
    az rest --method get --url "$url?$version" --only-show-errors --output json
}
before=$(read_app)
jq -e '
    .name == "ca-etaye-demo" and
    (.location | gsub(" "; "") | ascii_downcase) == "belgiumcentral" and
    .properties.workloadProfileName == "Consumption" and
    .properties.template.scale.minReplicas == 0 and
    .properties.template.scale.maxReplicas == 1 and
    .properties.configuration.ingress.allowInsecure == false and
    (.properties.configuration.ingress.ipSecurityRestrictions | length) > 0
' <<<"$before" >/dev/null
evidence="$RUNNER_TEMP/demo-revision-evidence.json"
if [[ "$stage" == false ]]; then
    jq '{app:.name,mode:"oidc-read-only",location, latest:.properties.latestRevisionName,
         ready:.properties.latestReadyRevisionName}' <<<"$before" > "$evidence"
    exit 0
fi

# Pin traffic to the previously tested revision before staging. The operator uses the
# restricted revision URL and the browser suite before any separate traffic promotion.
jq -e '
    .properties.configuration.activeRevisionsMode == "Multiple" and
    all(.properties.configuration.ingress.traffic[]; .latestRevision != true) and
    any(.properties.configuration.ingress.traffic[]; .weight == 100 and (.revisionName | type == "string"))
' <<<"$before" >/dev/null
active_source=$(jq -er '.tags.SourceSha | select(test("^[a-f0-9]{40}$"))' <<<"$before")
git merge-base --is-ancestor "$active_source" "$source"
git diff --quiet "$active_source" "$source" -- apps/api/src/main/resources/db ||
    { echo 'This release requires controlled one-shot schema migration and compatibility verification.' >&2; exit 1; }
payload=$(mktemp "$RUNNER_TEMP/demo-template.XXXXXX")
trap 'unlink "$payload"' EXIT
api=$(jq -er '.images.api.reference' "$manifest")
web=$(jq -er '.images.web.reference' "$manifest")
[[ "$api" =~ ^ghcr\.io/aymenchaghoub/etaye-api@sha256:[a-f0-9]{64}$ ]]
[[ "$web" =~ ^ghcr\.io/aymenchaghoub/etaye-web@sha256:[a-f0-9]{64}$ ]]
revision="git-${source:0:12}-${GITHUB_RUN_ID}"
jq --arg api "$api" --arg web "$web" --arg revision "$revision" '
    .properties.template |
    .revisionSuffix = $revision |
    (.containers[] | select(.name == "api") | .image) = $api |
    (.containers[] | select(.name == "web") | .image) = $web |
    {properties:{template:.}}
' <<<"$before" > "$payload"
az rest --method patch --url "$url?$version" --body "@$payload" --only-show-errors --output none
for attempt in {1..40}; do
    result=$(read_app)
    if jq -e --arg revision "ca-etaye-demo--$revision" '.properties.latestReadyRevisionName == $revision' <<<"$result" >/dev/null; then
        jq --arg sha "$source" '
            {app:.name,mode:"staged-no-traffic",sourceSha:$sha,revision:.properties.latestReadyRevisionName,
             traffic:.properties.configuration.ingress.traffic,images:[.properties.template.containers[].image]}
        ' <<<"$result" > "$evidence"
        exit 0
    fi
    sleep 5
done
echo 'The candidate did not become ready. The previous traffic pin still applies.' >&2
exit 1
