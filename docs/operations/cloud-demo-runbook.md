# Cloud demonstration deployment

This environment serves a synthetic portfolio demonstration. All identities and records are fictional. The application has no clinical validation or certification. Display the French demonstration notice before sign-in and keep real personal and medical information out of the environment.

## Deployment record

The first deployment uses protected main `8ea8b9826685aef45ea32bd8c5a59ef1bb3a1d11`. PR #102 passed the nine strict required GitHub checks and the applicable SonarQube gate. Tooling main closed both S8705 findings as fixed. The API and Web gates are green, and the nine historical API security findings remain open for individual review. [PR #102](https://github.com/Aymenchaghoub/etaye/pull/102) · [Main Tooling run](https://github.com/Aymenchaghoub/etaye/actions/runs/46028069589)

| Component | Status | Verified evidence or next gate |
| --- | --- | --- |
| Neon PostgreSQL 18.7 | Active for schema and synthetic data | Project `etaye`, ID `delicate-mud-08456349`, branch `demo`, ID `br-flat-dawn-bhljxkzz`, database `etaye_demo_cloud`. One direct read/write compute at 0.25 CU. |
| Neon role remediation and database creation | Approved and executed | The owner approved moving the legacy `etaye` database to `etaye_admin`, removing the three privileged roles, recreating them with SQL, and provisioning and marking the dedicated database. Both legacy databases were retained. SQL and management metadata agreed on the ownership transfer and managed-role deletion. |
| Migration and fixture | Approved and executed | The owner separately approved migration and seed in the dedicated database. Flyway validated versions 1, 2, 3, 4, 7, 8, 9, 11. The original managed bootstrap installed `etaye-design-v1`, generation 1. |
| Backup | Export verified | Admin logical dump contained schema history and the checked synthetic rows. An isolated Neon restore succeeded and its scratch target was removed. |
| Azure | Provider registered | The owner approved registration of `Microsoft.App` alone. After registration, the usage endpoint reports one environment available in each of the five allowed regions. Belgium Central contains the Consumption environment in the approved resource group. The deployment managed identity is provisioned. The application awaits the signed release. |
| Azure Bicep foundation | Implemented | [Subscription template](../../infra/azure/foundation.bicep) and [environment module](../../infra/azure/environment.bicep) describe the foundation. Provider-level what-if passed in Belgium Central. The approved foundation is active from the merged Bicep. |
| GitHub Container Registry | Published and verified | Protected [release run 46028272952](https://github.com/Aymenchaghoub/etaye/actions/runs/46028272952) retained the signed release manifest and Trivy reports. The three images are pullable anonymously by verified digest and have SLSA and CycloneDX attestations. |
| Runtime and continuous delivery | First release accepted | External HTTPS and real Chromium checks passed against Neon. The protected delivery workflow verifies the signed release and uses Azure OIDC. The initial runtime uses Single revision mode. |

The owner authorized the remaining deployment operations within the existing scope and cost limits under UD-77. Record the deployed revision, exact source SHA, API and Web registry digests, migrator reference, app URL, schema version, release manifest and previous compatible revision after deployment. Store sensitive evidence outside Git. The operator's private checkpoint, migration catalog results and audit logs remain in the deployment evidence directory.

| Image | Exact first-release reference |
| --- | --- |
| API | `ghcr.io/aymenchaghoub/etaye-api@sha256:eb369ce4f5b13cbb7c003a3561a77d87dcf3cd1f6afc294b19810764652cb99e` |
| Cloud Web | `ghcr.io/aymenchaghoub/etaye-web@sha256:0647dded658e2c996f9193388b2b000466eaa59b43580f27c30a139cf47d3043` |
| Migrator | `ghcr.io/aymenchaghoub/etaye-migrate@sha256:855912c4d1e6fac69427afd711127293c24466191f17e8cecc959dc8f91798e9` |

## Neon provisioning and credentials

Use Neon CLI with the existing authenticated profile and PostgreSQL 18 client tools. Resolve the target by explicit project and branch IDs on every management command. Check the scope and the endpoint returned for that branch before PostgreSQL operations. The protected `production` branch and other Neon projects are outside the deployment scope.

Use the provider management API to transfer ownership of the retained legacy database. Check `pg_shdepend` across databases and `pg_auth_members` before deleting exact provider-managed roles. Wait for catalog operations to finish and verify the resulting state in the API and SQL. Neon can include a project-level endpoint synchronization operation alongside a demo catalog operation. The demonstrated branch scope belongs to the latter and to the actual resource request. Create the dedicated database with the administrator as owner and revoke `PUBLIC` database privileges on that database.

Run [role initialization](../../infra/local/init-roles.sh) and [managed demo marking](../../infra/demo/mark-demo-database.sh) using the existing administrator credential and new independent application passwords. The marker requires exact and matching database configuration and an empty target. Keep management credentials and temporary files in a private directory with mode `0700` and files with mode `0600`. Use a trusted local secret file or a private credential store, avoid shell tracing and use secret references for the hosted API. Access the administrator and migration-owner credentials only during controlled operations.

| Role | Verified allocation |
| --- | --- |
| `etaye_admin` | Owns the databases `etaye` and `etaye_demo_cloud` on branch `demo` and the latter's `etaye_demo_bootstrap` schema. Its provider privileges and BYPASSRLS remain available for provisioning and logical backup. |
| `etaye_owner` | Owns `public`, `etaye_demo` and all application tables. The administrator has role administration but gained no persistent SET or INHERIT access to this owner. |
| `etaye_runtime` | Dedicated login for application SQL, with only an explicit database CONNECT grant and schema usage. |
| `etaye_identity` | Dedicated login with column and function grants for identity resolution. Direct clinical SQL is denied. |

All three application roles are login roles with no SUPERUSER, CREATEDB, CREATEROLE, REPLICATION or BYPASSRLS and no inbound role memberships. None can create database schemas or temporary tables. The two operational roles have no CREATE on the application schema. Recheck PostgreSQL catalogs after role changes, since provider-managed role listings and SQL role state have different visibility.

Use libpq with `PGSSLMODE=verify-full`, `PGSSLROOTCERT=system` and `PGCHANNELBINDING=require` on supported clients. Use the direct hostname verified for this branch. Java uses `sslmode=verify-full`, `sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory` and `channelBinding=require`. Check the actual trust store in each packaged JVM. Direct libpq verified TLS 1.3 at the Neon proxy. The server-side TLS view reflects the connection behind Neon's proxy. Both Java 25 with pgJDBC 42.7.14 and the one-shot Flyway image authenticated with hostname verification. [Neon role documentation](https://neon.com/docs/manage/roles) · [Neon database ownership API](https://neon.com/docs/manage/databases) · [Neon TLS](https://neon.com/docs/connect/connect-securely)

## One-shot migration and synthetic fixture

The application keeps Flyway disabled. Execute migrations once from a controlled release using the schema owner. Pin the migrator image and upstream Linux amd64 manifest. The initial one-shot run used Flyway OSS 13.13.0. Build input came from the tracked baseline files. The upstream index digest was `sha256:53b3979ee3928ffab3019aadac89704dc939d54489e02dc3a600d86a4afb110c`, the Linux amd64 base manifest was `sha256:880e6227ef2c99ef435dc4907f593c4342ecac83f28b88e424efc4f5695fe35f`, and the local derived platform manifest was `sha256:bdb6a4b72be22df4c407d1456b1977b4fa942884adcd19d3fda46ecbc8c54f99`. These references identify the local one-shot build and its upstream base. Published release attestations remain pending. The previous integrated scan reported 16 HIGH and zero CRITICAL Flyway occurrences. Reassess findings on each candidate using the existing [dispositions](deployment-readiness.md#7-sboms-trivy-0762-image-scans-and-ghcr-release-design).

1. Verify the target branch, dedicated database, migration role, previous schema version and backup decision. Prevent overlapping migrations and require the named release approval.
2. First deployment only: run `flyway info` against the validated empty, marked database. Mount the private config read-only into the one-shot container as a user who can read it. Set the location to `filesystem:/flyway/schema`, default schema to `public`, schema creation off, clean disabled, migration naming validation on and baseline-on-migrate off. Use the verified TLS settings without credentials in the URL.
3. Run `flyway migrate`, then `flyway validate`. Compare successful versions, checksums and `installed_by` in `public.flyway_schema_history`. Stop and diagnose any mismatch. Existing migrations V1–V12 are immutable.
4. First deployment only: run [managed synthetic bootstrap](../../infra/demo/bootstrap-managed-demo.sh) with the exact matching database variables. It checks the marker, takes a transaction advisory lock, verifies schema compatibility and refuses pre-existing application rows or allocated RLS heaps. Subsequent migrations must preserve data. Add new compatible fixture instructions when schema revisions require them.
5. Verify table owners, tables, foreign keys, indexes, FORCE RLS and effective role grants. Use the existing synthetic practices to check unscoped and cross-practice denials, transaction reuse and login resolution. Leave read-only verification transactions closed. Reserve the database-dependent readiness URL for bounded acceptance checks.

Live Neon checks on the baseline verified 19 public application tables, all 12 expected FORCE RLS tables, ten named indexes and nine named foreign keys. Scoped patient results were 3 and 1 in the two synthetic practices, with zero foreign practice rows on each query and zero unscoped patient, record and measurement rows. Commit and rollback cleared the transaction-local setting on the same connection. The identity role resolved a known synthetic sign-in and was denied clinical reads. The runtime role was denied SET ROLE to the schema owner. The fresh fixture contained two practices, four patients and four records, six measurements, only fictional email domains, and zero active sessions.

## Backup and recovery

Use the direct TLS endpoint and PostgreSQL tools of the server's major version. Take the dump with the existing administrator on this dedicated synthetic database. The actual owner-only data export was refused under FORCE RLS. Export `public` and `etaye_demo` in custom format with `pg_dump`. Keep the administrator-owned marker out of the archive. Use a restricted output and store its SHA-256 separately. Check the table of contents with `pg_restore --list` and check representative row counts from an in-memory `pg_restore --data-only` extraction. The verified export contained 8 Flyway versions, 4 patients, 4 records, 6 measurements and 1 fixture row. Protect the dump even when it contains only synthetic data.

Create and mark a fresh dedicated synthetic restore database. Prepare the same role names, and exclude the precreated `public` and `etaye_demo` schema creation entries. Neon preinstalls default table and sequence grants for `cloud_admin`. Compare those exact default ACLs in the source and target before excluding their two provider-owned entries from the restore list. Retain application ACLs. Run `pg_restore --no-owner --exit-on-error --single-transaction --use-list` as the schema owner. This sequence was verified on Neon. The baseline provider ACLs matched, the first failed attempt rolled back fully, and the retry preserved history, ownership, row counts and all expected isolation rules. The temporary database was removed after verification. Check required extensions, ownership, ACL, Flyway history, row counts, tenant isolation and app journeys before switching any database reference. Retain the active database while verifying the isolated recovery target. Rehearse before a consequential schema promotion. Prefer on-demand backups before migration over frequent fixed schedules.

## Azure foundation and cost

Germany West Central returned `MaxNumberOfEnvironmentsInSubExceeded` at provider validation despite the usage counter advertising one available environment. The same template passed provider-level what-if in Belgium Central, Norway East, Poland Central and Italy North. Belgium Central is the active region. The first warm cloud acceptance measured a median of about 52 ms against the same-origin readiness endpoint from the operator network.

The subscription and region must match the approved deployment scope. Bicep describes `rg-etaye-demo` and a `cae-etaye-demo` environment with one Consumption profile. A separate application deployment will use `ca-etaye-demo`. The foundation omits paid plans, persistent log sinks, private endpoints and reserved networking. ARM represents disabled persistence with a null log destination. Direct log streams remain the troubleshooting interface. [Container Apps logging](https://learn.microsoft.com/en-us/azure/container-apps/log-options)

Build and lint Bicep locally. Before creation, confirm the subscription offer and spending limit, approved region, applicable inventory and current quota, and perform a provider-level what-if for this exact commit:

```bash
az bicep build --file infra/azure/foundation.bicep --stdout > /dev/null
az bicep lint --file infra/azure/foundation.bicep
az deployment sub what-if --subscription "$ETAYE_AZURE_SUBSCRIPTION" \
  --location belgiumcentral --template-file infra/azure/foundation.bicep \
  --parameters location=belgiumcentral --validation-level Provider
```

For a new authorized foundation, use subscription-scope deployment with the same parameters and incremental mode. Confirm the two expected resources, the Consumption profile, and absence of any workspace or unrelated resource. Record the complete deployed inventory from Azure. Use the application and identity configuration approved under UD-77.

Current observed budget: EUR 1 monthly, EUR 0 reported, with alerts at 50% and 100% actual and 100% forecast. The student spending limit is On. The owner confirmed USD 100 available through November 2027 in the student sponsorship portal. The monthly plan includes 180,000 vCPU-seconds, 360,000 GiB-seconds and 2 million incoming HTTP requests across the Azure subscription. A 0.75 vCPU / 1.5 GiB replica can use approximately 66.7 active hours under the two resource grants. The initial target is at most 60 hours of combined active replicas, with headroom for unusual activity. Allow for cold startup, scale-down delay, requests, transfer and any overlapping revision. Scope any change to the approved financial envelope. [Azure billing](https://learn.microsoft.com/en-us/azure/container-apps/billing) · [Student offer](https://azure.microsoft.com/en-us/pricing/offers/ms-azr-0170p/) · [Sponsorship balance](https://www.microsoftazuresponsorships.com/balance)

| Scenario | Active replica hours | vCPU-seconds | GiB-seconds | Neon compute at 0.25 CU over the same active hours |
| --- | ---: | ---: | ---: | ---: |
| Limited demonstrations | 8 | 21,600 | 43,200 | 2 CU-hours |
| Regular demonstrations | 30 | 81,000 | 162,000 | 7.5 CU-hours |
| Upper trial envelope | 60 | 162,000 | 324,000 | 15 CU-hours |
| Sustained traffic | 100 | 270,000 | 540,000 | 25 CU-hours |

Neon's compute invoice includes wakeups, its independent suspension delay and activity on other branches of the same project. Current provider Free limits include 100 CU-hours per project per month and 1 GB project storage. Verify actual current transfer and usage allocations in the project console. Poll cloud metrics only during bounded tests and incidents to preserve suspension. [Neon Free limits](https://neon.com/blog/neon-free-plan-1-gb-per-project)

## Runtime, release and continuous delivery

The runtime uses one same-origin Container App. Nginx is the only ingress on port 8081 and proxies to Spring Boot on loopback port 8080. Port 8082 serves the platform's database-free readiness probe. The direct Neon JDBC URL requires hostname verification and channel binding. The API uses separate runtime and identity secrets, small connection pools and zero minimum idle. Flyway stays disabled on the HTTP replica.

The first access model requires the operator's approved IPv4 address and independent Basic authentication before application sign-in. Keep visitor credentials in the operator's private store. Open the French synthetic-data notice before entering the app and use only fixture identities. The shared demonstration allows clinical reads. Patient writes, fixture reset, invitation acceptance and account recovery are blocked at ingress. The runtime uses no external AI or email services.

The [cloud access preparation](cloud-demo-access.md) documents the explicit IP-restricted profile, the default Basic rollback, optional same-origin HTTPS hostname binding and the separate rollout gates. Azure retains the operator-only policy in both private profiles.

The [release workflow](../../.github/workflows/release.yml) dispatches on protected main. It verifies the exact candidate archives and reports before the protected publishing job copies the verified OCI manifests to GHCR. That job compares registry bytes, verifies SLSA and CycloneDX metadata and signs the final release manifest. The initial API and Web scans had zero high or critical findings. The migrator had 16 reviewed high findings and zero critical findings. Secret scans of the exact three archives and image configurations found zero findings. Store the signed release record and its evidence independently of the workflow artifact retention.

For first deployment, verify the signed manifest and its source SHA, verify all three image attestations and compare the anonymous registry manifests by digest. Supply the selected API and Web digests, direct Neon hostname, authorized operator IPv4, visitor password hash and separate runtime and identity passwords to the Bicep parameters. Use a mode-0600 secure parameter file outside Git and a separate placeholder file for provider validation and what-if. Apply only the expected Container App change. Remove transient parameter files after use. Verify the ingress hostname, resource allocation, revision state and exact image digests from Azure.

The [protected delivery workflow](../../.github/workflows/deploy-demo.yml) takes the publication run ID and full release SHA. Start with `stage=false` to verify release integrity and federated access without writing the app. The GitHub `demo` environment requires review and restricts deployment to main. The Azure managed identity trusts the one immutable GitHub repository subject for `Aymenchaghoub/etaye` and `demo`. The subject is the repository's `sub_claim_prefix` from `GET /repos/Aymenchaghoub/etaye/actions/oidc/customization/sub`, followed by `:environment:demo`. Keep the exact issuer `https://token.actions.githubusercontent.com` and audience `api://AzureADTokenExchange`. The initial legacy name-only binding returned `AADSTS700213`. [GitHub immutable subjects](https://docs.github.com/en/actions/reference/security/oidc#immutable-subject-claims) Its custom revision publisher role is assigned only to `ca-etaye-demo`. Runtime database credentials stay in Container Apps secrets. Protected run 46031644684 verified this federation in read-only mode against the actual healthy revision. [OIDC verification](https://github.com/Aymenchaghoub/etaye/actions/runs/46031644684)

The staging step requires Multiple revision mode and verifies that existing traffic is already pinned to one named revision before applying the new image digests. Keep Single mode for the first release and its replica envelope. Before enabling staging on a future release, assess overlapping replicas, verify the migration and compatibility prerequisites, pin known-good traffic and document how to test the candidate through restricted access. Switch traffic as a separate controlled operation after readiness and authenticated acceptance. [Entra federation](https://learn.microsoft.com/en-us/entra/workload-id/workload-identity-federation-create-trust-user-assigned-managed-identity) · [GitHub environments](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments)

Both settings are application-scope changes: they create no revision and leave the serving replicas and the ingress rule as they are. Read back `Multiple` and one named entry at `100` with no `latestRevision` entry before any staging run.

```bash
az containerapp revision set-mode --subscription "$ETAYE_AZURE_SUBSCRIPTION" \
  --resource-group rg-etaye-demo --name ca-etaye-demo --mode multiple

az containerapp ingress traffic set --subscription "$ETAYE_AZURE_SUBSCRIPTION" \
  --resource-group rg-etaye-demo --name ca-etaye-demo \
  --revision-weight "$ETAYE_KNOWN_GOOD_REVISION=100"
```

### Stage a release with database changes

The protected `demo` environment accepts an operator-signed migration receipt for releases with changes under the database resource directory. Stage the exact source SHA and publication run. Keep the operator's signing key and the Neon credential outside GitHub. After the key enrollment is approved, set `DEMO_MIGRATION_PUBLIC_KEY` in the protected environment to the first two fields of its Ed25519 OpenSSH public key. Staging verifies the signature with that key and preserves the receipt's SHA-256 digest in the revision evidence.

1. Download and verify the published release. Confirm the serving revision, its source SHA and its traffic pin in Azure. Confirm the exact Neon demo database, Flyway history and intended migrations. Rehearse a fresh backup restore and upgrade on an isolated copy, preserving fixture generation and testing the old runtime against the upgraded schema. The protected Neon branch stays outside this procedure.
2. After approval for the live step, take or confirm a fresh recoverable backup and apply the schema migrations with the exact signed migrator as the schema owner. Inspect the pending list first, then run migrate and validate with `filesystem:/flyway/schema`. Use one migration process and preserve the old workload. The switch from V11 to the published `016d99a` schema applies V13 and V15. V14 is the local fixture loader. The live fixture retains its generation and existing rows.
3. Record the results after checking the serving version on the upgraded database. Sign a private `receipt.json` with these fields:

   | Field | Required value |
   | --- | --- |
   | `version`, `database` | `1`, `etaye_demo_cloud` |
   | `activeSha`, `activeRevision` | Exact source SHA and Azure revision still serving |
   | `candidateSha`, `migrator` | Exact candidate SHA and `.images.migrate.reference` from the signed manifest |
   | `schemaBefore`, `schemaAfter` | Arrays of version strings in numeric order, matching the successful managed Flyway histories and the respective release trees |
   | `backupRestored`, `flywayValidated`, `activeCompatible` | True after the documented checks pass |
   | `verifiedAt` | UTC UNIX timestamp in seconds. A receipt is valid for 24 hours. |

   ```sh
   ssh-keygen -Y sign -f "$ETAYE_MIGRATION_SIGNING_KEY" -n etaye-demo-migration receipt.json
   printf '%s.%s' "$(base64 -w0 receipt.json)" "$(base64 -w0 receipt.json.sig)" > receipt-input.txt
   gh workflow run deploy-demo.yml --ref main -f release_run="$ETAYE_RELEASE_RUN_ID" \
     -f source_sha="$ETAYE_RELEASE_SHA" -f stage=true -F migration_receipt=@receipt-input.txt
   ```

4. Review the protected `demo` job only after the receipt and capacity assessment match the approved operation. The workflow checks that the same traffic pin holds while waiting for the candidate. Follow the [exact candidate hostname procedure](cloud-demo-access.md#verify-an-unpromoted-revision) for the restricted browser campaign and record the deployment evidence. Traffic promotion remains a separate operator action. Releases without database changes take no receipt.

### Private cutover and rollback

On 2026-11-28 D6 moved the demo to `704d04f` and D7 promoted the signed HSTS release `5a71c40ec434cf36b0ab08cabac7471b1bb9ed4f`. The app tag `SourceSha` records that source. `ca-etaye-demo--review-ip-restricted-180152` receives 100% in Single mode and is the only active revision. Azure retains the one operator IP allow rule and redirects HTTP to HTTPS. Étaye application sign-in protects the clinical and portal journeys. The Neon demo uses V15 with synthetic fixture generation 1. D7 required no schema change. [Signed D7 images](https://github.com/Aymenchaghoub/etaye/actions/runs/46166845682) · [D7 staging](https://github.com/Aymenchaghoub/etaye/actions/runs/46173341663) · [Final independent probe](https://github.com/Aymenchaghoub/etaye/actions/runs/46173868184)

The immediate rollback is `ca-etaye-demo--review-ip-restricted-162140` from D6, source `704d04f9cab861493725517a35f7f8ae78a6b78c`. It was tested on V15. Read the revision list and ingress first. Switch to Multiple mode with the serving traffic pinned, reactivate the retained revision and wait for Azure to report it healthy. Assign 100% to it with `az containerapp ingress traffic set`. Verify the operator rule, direct sign-in, readiness, allowed reads and independent denial. Then update `SourceSha` to the verified rollback source and keep Multiple mode while the newer revision exists. A browser can retain the D7 HSTS policy for up to 300 seconds from its last HSTS response. Both releases serve HTTPS. [Signed D6 images](https://github.com/Aymenchaghoub/etaye/actions/runs/46151266640)

The Basic fallback is `ca-etaye-demo--git-8ea8b9826685` with the first-release digests recorded above. Its Basic challenge and sign-in passed on V15 before D6. If Azure prunes a retained revision, restage its immutable API and Cloud Web digests with the corresponding access profile. Check the current IP rule and rerun acceptance before changing traffic. Application rollback uses V15. Database recovery follows the separate backup and restore procedure.

## First acceptance, rollback and operation

Before first rollout, verify the approved audience, resources and costs, and record the exact source SHA and immutable image digests. Verify role restrictions and validated schema before adding traffic. Gate subsequent promotion on known-good compatibility, staging and approved traffic switching. Keep authenticated Playwright traffic within the approved ingress access rules.

- Capture DNS, certificate, HTTP redirection, frontend load, API routing, readiness, response headers and cookie attributes. Exercise sign-in, logout and CSRF, practice switching, patient search, record and measurement read, portal permissions, cross-practice denial, and reset denial with synthetic identities.
- Check restricted and error states, browser console, accessibility smoke, runtime and identity privilege boundaries and sanitized logs. Measure cold and warm response times, Neon wake time, replica count, CPU, memory and errors. Label confidence limits. The first campaign verified the restricted TLS route, secure cookies, CSRF, cross-practice denial, operator route denial and the clinical and portal journeys. A first timeline assertion timed out at five seconds during startup. Both the warm rerun and the rerun after Azure reported zero replicas passed. The first HTTPS challenge after scale-to-zero took 42.4 s. The next authenticated database-ready call took 115 ms, followed by eight readiness calls in 44–58 ms. The window reported a peak working set of about 317 MiB and peak Azure CPU metric of 21%. The bounded sequence did not separate Neon wake latency from the startup of the app and its network.
- Capture app and revision status plus short live system and container streams for startup incidents. Inspect subscription cost and Neon project usage when testing. Keep an approved operator path to stop the app if usage becomes abnormal and to restart the same verified revision. Avoid monitoring pings that keep the service awake.
- Roll application back to the previous known-good compatible revision by immutable digest or controlled revision activation. Verify readiness, security headers, synthetic access and tenant denials. Database migrations remain forward-only. When data recovery is independently approved, restore to an isolated target and verify before switching.
- Rotate the runtime and identity passwords independently and update their corresponding app secret references. Verify new connections and quarantine old local copies. Keep the schema owner and provisioning credentials outside the running API. Revoke continuous delivery by removing its federated credential and demo-scoped Azure assignments after approval.
- If Neon sleeps, check the direct endpoint, allow bounded retry after wake and review application and database startup logs. An unhealthy revision, an unavailable image digest, failed attestation verification or a quota error blocks promotion.

The global development authorization remains false.


### Inspect, pause and resume the demo

Use the Azure control plane for routine status so the checks do not send traffic to the app. Review a small, sanitized log sample only when diagnosing an incident.

```bash
az containerapp revision list --subscription "$ETAYE_AZURE_SUBSCRIPTION" \
  --resource-group rg-etaye-demo --name ca-etaye-demo \
  --query '[].{revision:name,health:properties.healthState,state:properties.runningState,replicas:properties.replicas}' -o table

az containerapp logs show --subscription "$ETAYE_AZURE_SUBSCRIPTION" \
  --resource-group rg-etaye-demo --name ca-etaye-demo --type system --tail 30
```

If activity exceeds the approved envelope, first confirm that `demo-operator` is the sole ingress allow rule. Replace its address with the reserved documentation address below to pause external traffic while preserving the HTTPS and visitor authentication configuration. Azure then has room to scale the app to zero. Set `ETAYE_APPROVED_IPV4` from the operator's current authorized address and apply the second command to resume. Keep the ingress rule list and audit evidence private.

```bash
az containerapp ingress access-restriction set --subscription "$ETAYE_AZURE_SUBSCRIPTION" \
  --resource-group rg-etaye-demo --name ca-etaye-demo \
  --rule-name demo-operator --action Allow --ip-address 192.0.2.1/32

az containerapp ingress access-restriction set --subscription "$ETAYE_AZURE_SUBSCRIPTION" \
  --resource-group rg-etaye-demo --name ca-etaye-demo \
  --rule-name demo-operator --action Allow --ip-address "$ETAYE_APPROVED_IPV4/32"
```

After resumption, verify the expected serving revision, the approved ingress rule and signed-in synthetic readiness over HTTPS. In `basic` mode, expect a visitor challenge before Étaye sign-in. In `ip-restricted` mode, expect direct access to Étaye sign-in from the operator network and denial from an independent network. Use the signed initial release as the first known-good digest pair. For a future Multiple revision deployment, restore the reviewed known-good traffic pin when a candidate fails. Treat database recovery separately and validate a restore in an isolated synthetic target before considering a switch.

To revoke deployment access, remove the one `Etaye Demo Revision Publisher` role assignment at the `ca-etaye-demo` resource and, if needed, remove the exact `github-demo` federated credential after recording the rollback decision. This identity serves workflow access to Azure only. Runtime database secrets and the public registry images have independent access paths.
