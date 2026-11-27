# Cloud demonstration deployment

This environment serves a synthetic portfolio demonstration. All identities and records are fictional. The application has no clinical validation or certification. Display the French demonstration notice before sign-in and keep real personal and medical information out of the environment.

## Deployment record

Baseline: [protected main at `3484df8`](https://github.com/Aymenchaghoub/etaye/commit/3484df863d9ef9e98c920d8b383369433fc2b59e), inspected 2026-11-27. The nine strict required GitHub checks and SonarQube policy were verified. The three live SonarQube quality gates were OK. The API security rating remained D and its historical findings retain their existing dispositions.

| Component | Status | Verified evidence or next gate |
| --- | --- | --- |
| Neon PostgreSQL 18.7 | Active for schema and synthetic data | Project `etaye`, ID `delicate-mud-08456349`, branch `demo`, ID `br-flat-dawn-bhljxkzz`, database `etaye_demo_cloud`. One direct read/write compute at 0.25 CU. |
| Neon role remediation and database creation | Approved and executed | The owner approved moving the legacy `etaye` database to `etaye_admin`, removing the three privileged roles, recreating them with SQL, and provisioning and marking the dedicated database. Both legacy databases were retained. SQL and management metadata agreed on the ownership transfer and managed-role deletion. |
| Migration and fixture | Approved and executed | The owner separately approved migration and seed in the dedicated database. Flyway validated versions 1, 2, 3, 4, 7, 8, 9, 11. The original managed bootstrap installed `etaye-design-v1`, generation 1. |
| Backup | Export verified | Admin logical dump contained schema history and the checked synthetic rows. An isolated Neon restore awaits approval. |
| Azure | Provider registered | The owner approved registration of `Microsoft.App` alone. After registration, the usage endpoint reports one environment available in each of the five allowed regions. No group, environment, application or managed identity exists yet. |
| Azure Bicep foundation | Implemented | [Subscription template](../../infra/azure/foundation.bicep) and [environment module](../../infra/azure/environment.bicep) describe the foundation. Provider-level what-if passed in Belgium Central, the proposed first region. Resource creation requires a further approval. |
| GitHub Container Registry | Dry-run tooling available | Publishing, package visibility, registry digests, signed SBOMs and provenance await their approval and verification. |
| Runtime and continuous delivery | Proposed | The application URL, validated cloud revision, OIDC identity and cloud acceptance evidence are pending. |

The first staging approval covers only the inventory named in the corresponding owner decision. Record the deployed revision, exact source SHA, API and Web registry digests, migrator reference, app URL, schema version, release manifest and previous compatible revision after deployment. Store sensitive evidence outside Git. The operator's private checkpoint, migration catalog results and audit logs remain in the deployment evidence directory.

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

After isolated-restore approval, create and mark a fresh dedicated synthetic database. Prepare the same role names, exclude the precreated `public` and `etaye_demo` schema creation entries, and use `pg_restore --no-owner --exit-on-error --single-transaction --use-list` as the schema owner. Treat this as a proposed sequence until a real Neon restore proves the archive sections and grants behave as expected. Check required extensions, ownership, ACL, Flyway history, row counts, tenant isolation and app journeys before switching any database reference. Retain the active database while verifying the isolated recovery target. Rehearse before a consequential schema promotion. Prefer on-demand backups before migration over frequent fixed schedules.

## Azure foundation and cost

Germany West Central returned `MaxNumberOfEnvironmentsInSubExceeded` at provider validation despite the usage counter advertising one available environment. The same template passed provider-level what-if in Belgium Central, Norway East, Poland Central and Italy North. Belgium Central is the proposed first region based on the allowed alternatives and proximity to Frankfurt. Latency has not been measured.

The subscription and region must match the approved deployment scope. Bicep describes `rg-etaye-demo` and a `cae-etaye-demo` environment with one Consumption profile. A separate application deployment will use `ca-etaye-demo`. The foundation omits paid plans, persistent log sinks, private endpoints and reserved networking. ARM represents disabled persistence with a null log destination. Direct log streams remain the troubleshooting interface. [Container Apps logging](https://learn.microsoft.com/en-us/azure/container-apps/log-options)

Build and lint Bicep locally. Before creation, confirm the subscription offer and spending limit, approved region, applicable inventory and current quota, and perform a provider-level what-if for this exact commit:

```bash
az bicep build --file infra/azure/foundation.bicep --stdout > /dev/null
az bicep lint --file infra/azure/foundation.bicep
az deployment sub what-if --subscription "$ETAYE_AZURE_SUBSCRIPTION" \
  --location belgiumcentral --template-file infra/azure/foundation.bicep \
  --parameters location=belgiumcentral --validation-level Provider
```

After the owner separately approves creation and the exact template has been reviewed and merged, use subscription-scope deployment with the same parameters and incremental mode. Confirm the two expected resources, the Consumption profile, and absence of any workspace or unrelated resource. Record the complete deployed inventory from Azure. Leave the application ingress and deployment identity for their separate approvals.

Current observed budget: EUR 1 monthly, EUR 0 reported, with alerts at 50% and 100% actual and 100% forecast. The student spending limit is On. The credit balance is checked in the sponsorship portal. The monthly plan includes 180,000 vCPU-seconds, 360,000 GiB-seconds and 2 million incoming HTTP requests across the Azure subscription. A 0.75 vCPU / 1.5 GiB replica can use approximately 66.7 active hours under the two resource grants. The initial target is at most 60 hours of combined active replicas, with headroom for unusual activity. Allow for cold startup, scale-down delay, requests, transfer and any overlapping revision. Scope any change to the approved financial envelope. [Azure billing](https://learn.microsoft.com/en-us/azure/container-apps/billing) · [Student offer](https://azure.microsoft.com/en-us/pricing/offers/ms-azr-0170p/) · [Sponsorship balance](https://www.microsoftazuresponsorships.com/balance)

| Scenario | Active replica hours | vCPU-seconds | GiB-seconds | Neon compute at 0.25 CU over the same active hours |
| --- | ---: | ---: | ---: | ---: |
| Limited demonstrations | 8 | 21,600 | 43,200 | 2 CU-hours |
| Regular demonstrations | 30 | 81,000 | 162,000 | 7.5 CU-hours |
| Upper trial envelope | 60 | 162,000 | 324,000 | 15 CU-hours |
| Sustained traffic | 100 | 270,000 | 540,000 | 25 CU-hours |

Neon's compute invoice includes wakeups, its independent suspension delay and activity on other branches of the same project. Current provider Free limits include 100 CU-hours per project per month and 1 GB project storage. Verify actual current transfer and usage allocations in the project console. Poll cloud metrics only during bounded tests and incidents to preserve suspension. [Neon Free limits](https://neon.com/blog/neon-free-plan-1-gb-per-project)

## Runtime, release and continuous delivery design

The proposed first runtime has one same-origin Container App. Nginx uses the only ingress port. It proxies API requests to a loopback Spring Boot listener. The local package's Docker resolver and `api` service hostname are Compose-specific. Use an exact Azure host allowlist, HTTPS, existing CSP and security headers, redacted request-path logs and a bounded request body. Preserve the secure host-only session cookie and CSRF. Run with zero to one replica, a small JDBC pool with zero minimum idle and no keepalive, and probes that check local startup and liveness without regular database queries. Use `GET /api/v1/probes/readiness` when explicitly checking database connectivity.

The first access model remains subject to explicit approval. The proposal is a restricted HTTPS origin with a narrowly scoped IPv4 allow rule, owner-controlled ingress access, synthetic app sign-in and a visible demonstration notice using the existing authentication shell. The operator reset is local-only under [UD-56](../planning-artifacts/decisions-assumptions.md). Public registration, recovery email provider, dataset reset and arbitrary patient writes need their own deliberate access decisions. Avoid deploying the local demo trust model on an open ingress. The application has no need for storage or external AI and email providers in this baseline.

The [release-candidate workflow](../../.github/workflows/release.yml) remains read-only and dispatches on protected main. Activation will retain the exact tested archives, reports and manifest. A separate protected publishing environment will bind the candidate run ID and exact source SHA, ensure original artifact checksums, enforce the reviewed vulnerability dispositions and copy the approved images without rebuilding. After copying, read the raw manifests back by digest and verify their bytes. The publish job uses the repository token exclusively for approved images. Verify GitHub-generated provenance and CycloneDX attestations for each exact digest and retain a signed final manifest and previous known-good release reference. Decide public visibility explicitly and validate anonymous pulls before use. Suppressions and scanner downgrades are outside this plan. [Existing release design](cicd-deployment-strategy.md#future-activation)

The proposed GitHub deployment environment is `demo`, main-only and protected by required review. Azure federation uses one user-assigned managed identity, audience `api://AzureADTokenExchange` and subject `repo:Aymenchaghoub/etaye:environment:demo`. Grant only the named resource permissions needed for application updates and revision operations. Do not put Neon administrator or migration credentials in GitHub or use an Azure client secret. Confirm approver independence and tenant capability first. Use a separate explicit operation for migrations and keep workflow concurrency serialized. [Entra workload federation](https://learn.microsoft.com/en-us/entra/workload-id/workload-identity-federation-create-trust-user-assigned-managed-identity) · [GitHub environments](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments)

## First acceptance, rollback and operation

Before first rollout, require explicit owner approval for the exact approved audience, resources, source SHA, immutable image digests and costs. Verify role restrictions and validated schema before adding traffic. Gate subsequent promotion on known-good compatibility, staging and approved traffic switching. Keep authenticated Playwright traffic within the approved ingress access rules.

- Capture DNS, certificate, HTTP redirection, frontend load, API routing, readiness, response headers and cookie attributes. Exercise sign-in, logout and CSRF, practice switching, patient search, record and measurement read, portal permissions, cross-practice denial, and reset denial with synthetic identities.
- Check restricted and error states, browser console, accessibility smoke, runtime and identity privilege boundaries and sanitized logs. Measure cold and warm response times, Neon wake time, replica count, CPU, memory and errors. Label confidence limits. No such cloud measurements have been executed yet.
- Capture app and revision status plus short live system and container streams for startup incidents. Inspect subscription cost and Neon project usage when testing. Keep an approved operator path to stop the app if usage becomes abnormal and to restart the same verified revision. Avoid monitoring pings that keep the service awake.
- Roll application back to the previous known-good compatible revision by immutable digest or controlled revision activation. Verify readiness, security headers, synthetic access and tenant denials. Database migrations remain forward-only. When data recovery is independently approved, restore to an isolated target and verify before switching.
- Rotate the runtime and identity passwords independently and update their corresponding app secret references. Verify new connections and quarantine old local copies. Keep the schema owner and provisioning credentials outside the running API. Revoke continuous delivery by removing its federated credential and demo-scoped Azure assignments after approval.
- If Neon sleeps, check the direct endpoint, allow bounded retry after wake and review application and database startup logs. An unhealthy revision, an unavailable image digest, failed attestation verification or a quota error blocks promotion.

The next functional candidate is Story 3.4 phase 0. Its design and written authorization are independent of this deployment. The global development authorization remains false.
