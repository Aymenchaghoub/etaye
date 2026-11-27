# Deployment readiness and quality consolidation assessment

Updated 2026-11-26. Owner: Aymen Chaghoub.

Covers: local constrained and unconstrained capacity measurements, multi-language quality and security hardening across PRs #84 to #88, OWASP ZAP and real-browser CSP verification, accessibility and mutation pilots, backup and restore rehearsal, non-superuser PostgreSQL 18 / Neon compatibility, Azure for Students + Render + Neon hosting assessment, SBOM/Trivy/GHCR provenance readiness, rollback procedure, and operational observability. No remote deployment, container publication, Azure provider registration, or paid service was activated.

## 1. Local capacity measurements

Measurements were collected against an isolated container stack built from baseline commit `389c48da64a2aed78c57418b122b0e5585989a50` (`postgres:18` + `api` image) with `--memory-swap` pinned equal to `--memory` (swap disabled) and no competing workload. Earlier session notes reported `~596 MB` idle / `~633 MB` loaded VmRSS (`650 MB` -> `684 MB` cgroup peak) on an unconstrained 30 GiB host where default JVM ergonomics expanded the heap aggressively; explicit cgroup bounds show true container footprints.

### Constrained container profiles (`--memory-swap == --memory`, authenticated measurements + readiness load)

| Profile | Container limits & JVM / Hikari tuning | Startup to HTTP 200 | Idle VmRSS / cgroup | Post-load VmRSS / peak cgroup | Auth serial p50 / p95 (30 req) | Auth 4-client concurrent (120 req) | OOMKilled |
|---|---|---|---|---|---|---|---|
| `0.25c_512m_default` | `0.25 vCPU`, `512 MiB`, default JVM | `61.03 s` | `272,908 kB` / `257.8 MB` | `287,260 kB` / `276.4 MB` | `93.63 / 198.84 ms` | `21.5 RPS`, p50 `198.17 ms`, p95 `299.86 ms`, `0` err | `false` |
| `0.25c_512m_tuned` | `0.25 vCPU`, `512 MiB`, `-Xmx192m -XX:MaxMetaspaceSize=96m -Xss256k -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Djava.security.egd=file:/dev/urandom`, Hikari max `5` | `36.47 s` | `215,224 kB` / `201.4 MB` | `228,816 kB` / `218.6 MB` | `50.59 / 92.41 ms` | `32.5 RPS`, p50 `101.17 ms`, p95 `198.58 ms`, `0` err | `false` |
| `0.5c_1024m_tuned` | `0.5 vCPU`, `1024 MiB`, `-XX:MaxRAMPercentage=60.0 -XX:MaxMetaspaceSize=112m -Xss256k -Djava.security.egd=file:/dev/urandom`, Hikari max `8` | `27.95 s` | `271,028 kB` / `260.0 MB` | `274,364 kB` (`277,436 kB` HWM) / `262.9 MB` | `15.83 / 93.50 ms` | `43.1 RPS`, p50 `98.84 ms`, p95 `105.20 ms`, `0` err | `false` |
| `1.0c_2048m_default` | `1.0 vCPU`, `2048 MiB`, default JVM | `13.68 s` | `274,764 kB` / `260.2 MB` | `289,560 kB` / `279.4 MB` | `11.92 / 51.87 ms` | `87.6 RPS`, p50 `11.33 ms`, p95 `91.53 ms`, `0` err | `false` |
| `2.0c_4096m_default` | `2.0 vCPU`, `4096 MiB`, default JVM | `6.96 s` | `328,548 kB` / `319.4 MB` | `345,112 kB` / `335.5 MB` | `12.50 / 16.92 ms` | `189.2 RPS`, p50 `10.29 ms`, p95 `62.53 ms`, `0` err | `false` |

Unconstrained `GET /api/v1/probes/readiness` (`40` serial requests) completed with `0` errors across all five profiles (`p50`: `2.72 ms`–`3.37 ms`; `p95`: `4.04 ms` on `2.0c_4096m_default`, `4.85 ms` on `1.0c_2048m_default`, `77.18 ms`–`89.03 ms` on `0.25c`–`0.5c`).

### Capacity interpretation

- **512 MiB feasibility**: Inside an actual 512 MiB container cgroup, peak RAM was `276.4 MB` untuned and `218.6 MB` tuned (~`293 MB` headroom, `0` OOM kills). Tuning with `SerialGC` and `TieredStopAtLevel=1` cut `0.25 vCPU` cold startup by `40%` (`61.03 s` → `36.47 s`) and increased 4-client throughput by `51%` (`21.5` → `32.5 RPS`) by stopping C2 JIT and G1 background threads from competing for scarce CPU quota.
- **CPU remains the primary cold-start constraint**: On Render Free (~`0.1 vCPU`, 15 min idle sleep), cold-start latency scales beyond `0.25 vCPU` measurements (~`70–90 s` tuned estimate, over `150 s` untuned estimate) and remains tight against future PDF rendering (PDFBox headroom not yet exercised in Sprint 3). A `0.5 vCPU / 1 GiB` or `1.0 vCPU / 2 GiB` container profile (`13.68–27.95 s` startup) provides clean latency and headroom.

## 2. Quality baseline, security remediation, mutation pilots, and accessibility audit

All six consolidation pull requests were squash-merged to `main` in order #87 → #86 → #84 → #88 → #85 → #89. The [quality baseline](quality-baseline.md#verified-integrated-main-at-b4e8ff9) records post-merge metrics and executed verification on `b4e8ff9`. The rows below include PR-level and local measurements from the consolidation.

| Stream | Branch / PR | Scope and verified highlights |
|---|---|---|
| CI evidence integrity | `fix/coverage-report-integrity` · [PR #84](https://github.com/Aymenchaghoub/etaye/pull/84) (`193cf90`, merged) | Enforces measured LCOV/JaCoCo/Cobertura line validation via `tools/assert_coverage_reports.py`; rejects summary-only/contradictory LCOV records (`16/16` checks passing); adds [quality-baseline.md](quality-baseline.md) |
| API hardening & tests | `fix/quality-api` · [PR #85](https://github.com/Aymenchaghoub/etaye/pull/85) (`9cf30a0`, merged) | Remediated Sonar `java:S4502` (`/api/v1/csrf` removed from CSRF ignore list in [`SecurityConfig.java`](../../apps/api/src/main/java/dev/aymen/etaye/infrastructure/security/SecurityConfig.java)) and `java:S6829` (`@Autowired` `LocalRecoveryInbox`), verified all `java:S2077` queries keep bound parameters, trimmed `compiler-debt.json` (`46` → `42`), **297** local Maven tests passing (`0` failures/skips), JaCoCo **93.41% lines** (`2155/2307`), **86.44% branches** (`778/900`), SonarQube API `OK` at **100.0% new coverage / 91.5% overall / 86.4% branches** (`0` open PR issues, `12/12` executed checks passing) |
| Demo proxy security headers | `fix/demo-response-headers` · [PR #86](https://github.com/Aymenchaghoub/etaye/pull/86) (`0c11cdf`, merged) | Added `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Permissions-Policy`, strict `Content-Security-Policy`, removed upstream header duplication on `/api/` in [`nginx.conf`](../../infra/demo/nginx.conf), and verified headers in [`run_package_smoke.sh`](../../tools/run_package_smoke.sh) (`10/10` executed checks passing) |
| Python tooling quality | `fix/quality-tooling` · [PR #87](https://github.com/Aymenchaghoub/etaye/pull/87) (`9d6c38b`, merged) | Fixed `pythonsecurity:S8705` ref validation, traversal checks and 3 CodeQL test annotations, bumped `soupsieve==4.1.3` (`GHSA-mmhr-3c5m-f64f`), expanded Python suite (`292` unit tests, `93.54%` line / `88.91%` branch local, SonarQube Tooling `OK` at `91.5%` overall / `93.3%` new, `16/16` checks passing) |
| Web tests & browser coverage | `test/quality-web` · [PR #88](https://github.com/Aymenchaghoub/etaye/pull/88) (`8d93ae5`, merged) | Added unit & component tests (**163/163** Vitest across `23` files, ESLint `0` warnings, `eslint-debt` `21 → 17`) and Chromium V8-to-Istanbul merged LCOV pipeline (**97.80% lines** `2360/2413`, **88.09% branches** `2382/2704` local; SonarQube Web `OK` at **92.9% overall / 97.5% line / 88.1% branch / 92.3% new**, `16/16` checks passing) |

### OWASP ZAP passive baseline, authenticated OpenAPI scan, and browser CSP walkthrough (`PR #86` target)

- **Unauthenticated ZAP passive scan (`--network container:<web>`, `http://127.0.0.1/`)**: `11` spidered URLs, **0 HTTP 421 rejections** (`Host` loopback guard honored), **65 PASS, 0 FAIL, 3 Low WARN** (`COEP`/`COOP`/`CORP` rule `90004`). Earlier HTTP `421` noise only occurred when scanning container bridge IPs instead of loopback.
- **Authenticated ZAP OpenAPI scan**: `124` scanned API URLs across an authenticated session, **119 PASS, 0 FAIL, 1 Low WARN** (`CORP` `90004`).
- **Real Chromium CSP walkthrough**: `9` demo/application routes walked in headless Chromium (`light` and `dark`), **0 console errors, 0 CSP violations, 0 unexpected HTTP >= 400 responses**, and `playwright.demo.config.ts` (`4/4` real-stack specs) passed.

### Multi-language mutation pilots (local verification benchmarks, no blocking CI gates added)

| Stack & tool | Scope | Mutants generated | Killed / timed out | Mutation score | Test strength | Key remaining gaps |
|---|---|---:|---:|---:|---:|---|
| Java (`PIT`) | 7 security & domain classes | 170 | 161 killed (0 timeout) | **95%** (253/266 covered lines) | **96%** | `IdentityResolutionFilter` `35/41` (`85.4%`), `DatabaseUnavailable` `31/33` (`93.9%`), `BrowserCorsConfig` `21/22` (`95.5%`); `AccessDenialAudit`, `BrowserSessionLifetime`, `PracticeSecurityService`, `SectionStateRule` all `100%` |
| TypeScript (`StrykerJS`) | 10 pure `.ts` domain/format modules (`119` Vitest unit specs in pilot) | 1,587 | 1,430 killed + 5 timed out | **90.42%** | **90.54%** | `session-work`, `contact-email`, `routes` `100%`; `evidence.ts` `97.53%`, `memory.ts` `92.31%`, `attempt-key.ts` `88.64%`, `measurements.ts` `87.90%`, `asof.ts` `87.28%`, `zone.ts` `86.89%`, `date.ts` `84.95%` |
| Python (bounded AST sample) | `25` sampled mutants each in `ci_scope.py`, `check_workflow_policy.py`, `assert_coverage_reports.py` | 73 runnable (`23+25+25`) | 64 killed | **87.7%** | — | `ci_scope.py` `23/23` (`100%`), `check_workflow_policy.py` `25/25` (`100%`), `assert_coverage_reports.py` `16/25` (`64.0%`, Cobertura boundary `< 1`/`< 0` and DTD regex `or` survivors) |

### Live Chromium accessibility audit (`@axe-core/playwright`)

- Checked **22 scenes** (`11` routes/states × light & dark themes, desktop + `390x844` mobile drawer): `demo-showcase`, `sign-in`, `sign-in-patient`, `recovery`, `account-entry`, `patients-list`, `patient-form-new`, `record-chronology`, `record-history`, `record-measurements`, and `mobile-drawer-open` (verifying `inert` background and Escape dismissal).
- Observed **0 WCAG 2.0 / 2.1 / 2.2 A/AA violation groups**, single `<h1>` per scene, and `0` missing image `alt` attributes across all 22 scenes. Automated checks cannot replace screen-reader navigation or `200%` zoom testing, which remain deferred.

## 3. Backup and restore rehearsal

An isolated backup-and-restore cycle ran locally using synthetic data on a throwaway Compose stack built from baseline `389c48da64a2aed78c57418b122b0e5585989a50`:

| Step | Observed result |
|---|---|
| Logical dump (`pg_dump -Fc`) | Exported complete synthetic dataset cleanly |
| Fresh role provisioning (`init-roles.sh`) | Provisioned `etaye_owner`, `etaye_runtime`, `etaye_identity` with `rolsuper=f, rolbypassrls=f` |
| Flyway migrations (`migrate`) | **12/12 migrations applied** (`8` schema + `4` demo) on the clean target database |
| Data restoration (`pg_restore`) | Loaded expected synthetic rows (`records=21`, `measurements=6`, fixture version verified) |
| Fail-closed RLS verification | `etaye_runtime` queried `0` rows without transaction scope and resolved tenant rows only with explicit `SET LOCAL` practice context |
| Real-stack E2E smoke | `e2e/measurements.spec.ts`, `e2e/record.spec.ts`, `e2e/patients.spec.ts` passed against the restored stack |

All temporary rehearsal stacks (`etaye-quality-baseline`, `etaye-quality-restore`, `etaye-cap-*`) were stopped and removed (`docker compose down --volumes --remove-orphans`) after measurement.

- **Proposed demo RPO / RTO (not yet automated in production CI)**: Daily scheduled encrypted logical dump (`RPO <= 24 h`, supplemented by host PITR window on managed PG where available) and scripted clean-schema + `pg_restore` target recovery (`RTO <= 30 min`). Neon Free's 6-hour / 1 GB history window is **not** a durable backup strategy.

## 4. Non-superuser PostgreSQL 18 and Neon compatibility

Neon Free (`neon.com/pricing`, reconfirmed 2026-11-26) provides **`100` CU-hours/month**, **`1 GB` storage per project** (not `10 GB`), up to **`2` CU (~`8 GB` RAM) autoscaling**, **`10` branches**, **`5 GB` egress**, and **`5` minutes inactivity scale-to-zero**.

An isolated local PostgreSQL 18 non-superuser rehearsal (`NOSUPERUSER CREATEDB CREATEROLE BYPASSRLS`, simulating `neon_superuser` / `azure_pg_admin`) proved three concrete gaps against the current bootstrap/demo scripts:

1. **Unmodified `infra/local/init-roles.sh` fails under `CREATEROLE`**: Line 67 (`ALTER SCHEMA public OWNER TO etaye_owner`) fails with `ERROR: must be able to SET ROLE "etaye_owner"` (exit code `1`) when run as a non-superuser role lacking membership in `etaye_owner`. Granting temporary membership (`GRANT etaye_owner TO <admin> WITH SET TRUE;` → `ALTER SCHEMA` → `REVOKE`) allows all three roles (`etaye_owner`, `etaye_runtime`, `etaye_identity`) to provision cleanly with `rolsuper=f, rolbypassrls=f`.
2. **Schema-only Flyway migrations pass `8/8`**: Running `FLYWAY_LOCATIONS=filesystem:/flyway/schema` (`V1..V4, V7..V9, V11`) as non-superuser `etaye_owner` applied all 8 schema migrations cleanly, created all `12` tables with `relrowsecurity=t, relforcerowsecurity=t`, and preserved `etaye_runtime` fail-closed RLS (`0` rows without scope).
3. **Demo migrations (`apps/api/src/main/resources/db/demo/V5__local_synthetic_demo.sql`) do not target managed non-superuser PostgreSQL**: Fails both on `SQL State 42704: role "postgres" does not exist` (`nspowner = 'postgres'::regrole`) and on its safety guard requiring `current_database() = 'etaye_demo'` (Neon's configured database is named `etaye`). Managed demo provisioning therefore needs a dedicated non-superuser demo seed/reset adaptation or pre-sanitized fixture load.

> [!CAUTION]
> No remote Neon modification, branch creation, role change, or migration is authorized. Keep the existing Neon `production` branch untouched.

## 5. Multi-cloud free/low-cost feasibility (Render, Neon, Azure for Students)

Read-only local inspection via `az` CLI on Aymen's **Azure for Students** subscription (`USD 100` credit expiring `2027-11-26`, `USD 0` consumed; zero providers registered, zero resources created) plus verified vendor pricing surfaced key structural constraints:

| Platform / option | Verified quota, region & SKU facts | Monthly steady-state cost (after student credit) | Key architectural verdict |
|---|---|---|---|
| **Azure Container Apps Consumption + Neon Free + GHCR** | Five allowed regions in the policy snapshot. Monthly grant includes 180,000 vCPU-s, 360,000 GiB-s and 2 million requests. Proposed same-origin API plus Nginx totals 0.75 vCPU / 1.5 GiB, yielding about 66.7 active replica-hours before migration or other usage. | EUR 0 target under the full measured envelope and an eligible active subscription; student credit expiry creates a separate access dependency. | Owner's provisional preference. Verify complete same-origin package, provider role assumptions, OIDC and ancillary charges before cutover. |
| **Azure PostgreSQL Flexible Server (`B1ms`)** | Historical `germanywestcentral` quote: `0.0179 EUR/h` (~`13.07 EUR/month`) plus `0.123 EUR/GB/month` storage after the introductory grant. | **~13+ EUR/month** after introductory allowance. | Neon Free remains preferred for the synthetic database. |
| **Azure VMs (`Azure for Students`)** | Observed `Standard_B*` restriction, `D2s v4` / `D2as v4` estimate of `0.1032 EUR/h` (~`75.34 EUR/month`). | **~75.34 EUR/month** for always-on compute. | Owner excluded VMs at this stage. |
| **Render Free Web + Neon Free** | Render Free Web: 512 MB RAM, ~0.1 CPU, 15 min idle sleep and 750 h/month workspace pool. | EUR 0 target within account quotas. | Fallback pending complete same-origin package and cold-start validation at Render CPU limits. |
| **Option D (OVH VPS-1 / Hetzner CX23)** | Historical always-on benchmark (~€3.81 ex-VAT OVH; €3.49–€6.49 ex-VAT Hetzner). | **€3.49–€6.49+ / month ex-VAT**. | Paid option outside current authorization. |

For detailed usage assumptions, see the [hosting and cost assessment](hosting-cost-assessment.md#complete-consumption-workload).


## 6. Secrets inventory

| Secret | Location | Current status |
|---|---|---|
| `POSTGRES_ADMIN_PASSWORD`, `ETAYE_OWNER_PASSWORD`, `ETAYE_RUNTIME_PASSWORD`, `ETAYE_IDENTITY_PASSWORD` | Local gitignored `.env` (`infra/local/`) | Generated ephemerally per local stack; never committed |
| `SONAR_TOKEN` | GitHub Actions repository secret | Active for existing CI SonarQube Cloud scans; untouched |
| Cloud / registry deploy secrets (Azure OIDC federated credential, Neon runtime DSN, Render deploy hook) | None configured | Not requested or created; deferred until explicit configuration and deployment authorization |

## 7. SBOMs, Trivy 0.76.2 image scans, and GHCR release design

Verified locally on baseline images built from `389c48d`:

- **Offline Trivy `0.76.2` (`--skip-db-update --skip-java-db-update`)**:
  - `api`: **0 HIGH / 0 CRITICAL**
  - `web`: **0 HIGH / 0 CRITICAL**
  - `migrate` (`flyway/flyway:13.13.0`): **16 HIGH / 0 CRITICAL** (`10` distinct CVEs, all upstream Java packages such as `lz4-java`, `jackson-core`)
  - `postgres` (`postgres:18` `18.7`): **97 HIGH / 2 CRITICAL** (`48` distinct CVEs, `26` upstream Debian/upstream fixable)
- **Generated CycloneDX 1.7 SBOMs (validated schemas, component/PURL completeness)**:
  - `api`: `208` components (`206` PURLs)
  - `web`: `22` components (`21` PURLs)
  - `migrate`: `489` components (`488` PURLs)
  - `postgres`: `154` components (`152` PURLs)
  - `workspace` runtime: `150` components (`146` PURLs); `workspace-all`: `554` components (`550` PURLs)
- **GHCR release and provenance design on integrated main**:
  - [`package.yml`](../../.github/workflows/package.yml) already builds multi-container images and runs Trivy + real-stack verification without pushing to GHCR.
  - A future release workflow can push immutable digests (`ghcr.io/aymenchaghoub/etaye-*@sha256:...`) using repository-scoped `GITHUB_TOKEN` (`packages: write`, `id-token: write`, `attestations: write`) plus `actions/attest-build-provenance` and `actions/attest-sbom`, which both belong to the already-allowlisted `actions/*` publisher and preserve CI ruleset/allowlist policy without extra PATs.

### Exact integrated main image evidence

Exact-main [Package run 45895449895](https://github.com/Aymenchaghoub/etaye/actions/runs/45895449895) ran role provisioning, image build/start, real-stack journeys, Trivy and artifact upload. Artifact `package-evidence` was retrieved. Real Playwright reports record 3 record + 3 measurement + 5 patient tests with no unexpected, flaky or skipped results. Exact-main [CodeQL run 45895387720](https://github.com/Aymenchaghoub/etaye/actions/runs/45895387720) executed all three language analyzers successfully. Read-only GitHub endpoints returned zero open CodeQL and Dependabot alerts. Dependency review is PR-scoped, with the final [PR #89 dependency review](https://github.com/Aymenchaghoub/etaye/actions/runs/45895113393/job/140166977152) successful.

| Trivy 0.76.2 at exact main | High / critical occurrences | Unique IDs | Rows with scanner-supplied fix version |
| --- | ---: | ---: | ---: |
| API | 0 / 0 | 0 | 0 |
| Web | 0 / 0 | 0 | 0 |
| PostgreSQL 18.7 derived image (Debian 13.8) | 97 / 2 | 48 | 26 |
| Flyway 13.13.0 derived image (Ubuntu 24.04) | 16 / 0 | 10 | 16 |

- PostgreSQL CVE-2026-6653 affects installed Debian libxml2 `2.12.7+dfsg+really2.9.14-2.1+deb13u3`. Trivy labels it CRITICAL. Debian rates the impact minor, retains vulnerable/no-DSA status on trixie and lists a fix on unstable/forky, with the upstream parser fix in libxml2 2.11.0. ELF inspection of the pinned parent PostgreSQL server shows linkage to libxml2. The application and migrations reference no SQL/XML parser path and HTTP serves JSON. A database principal that can directly invoke XML functions remains a meaningful exposure boundary. Keep the Compose service unexposed, preserve least privilege and reassess if SQL/XML is introduced, a supported trixie/official image update carries a fix, or the database exposure changes. A managed Neon deployment would use the provider's server image, with separate maintenance evidence. [Debian status](https://security-tracker.debian.org/tracker/CVE-2026-6653) · [PostgreSQL XML capability](https://www.postgresql.org/docs/18/datatype-xml.html)
- PostgreSQL CVE-2025-68121 applies to Go TLS session resumption before Go 1.24.13 / 1.25.7 and is reported against the `gosu` startup binary containing Go 1.24.6, not against PostgreSQL's OpenSSL implementation. The pinned parent binary contained the reported version with no `crypto/tls` symbol indicators in static strings. Upstream 1.19 release and issue evidence identify gosu as a local privilege-drop helper and report no affected TLS code in the release binary. The locally pinned binary checksum did not match the public amd64 release download, so the upstream binary result is corroborating context. Keep the occurrence in the inventory and reassess when gosu changes or the Docker library ships a verified patched build. [Go advisory](https://pkg.go.dev/vuln/GO-2026-4337) · [gosu release](https://github.com/tianon/gosu/releases/tag/1.19) · [upstream reachability discussion](https://github.com/tianon/gosu/issues/176)
- Other PostgreSQL occurrences come from Debian userland (73 total including the libxml2 critical) and Go stdlib inventory for gosu (26 including the TLS critical). The 26 rows with a scanner fix are Go entries. This is no evidence that a compatible refreshed Docker base containing those fixes exists. Preserve digest pinning and compare candidate images with the same scanner database before changing the base.
- Flyway's ten unique IDs are concentrated in unused third-party bundled drivers: Databricks JDBC 3.4.2 (lz4-java 1.10.1 CVE-2026-106451 fixed at 1.11.4; Jackson; Guava; Apache HTTP Core/H2 5.3.6 CVE-2026-54399/54428 with 5.4.3 listed), Couchbase core-io 3.12.3 (Jackson and Guava), GCP Guava 33.5.0-jre and SQL Server driver. The one-shot job has no listening port, uses a PostgreSQL-only JDBC URL and uses committed SQL with dedicated migration credentials. No current path was established from untrusted application requests to the affected drivers or shared Flyway temporary storage. Scan evidence and conditions remain relevant if the job accepts external input, a different protocol, insecure shared volumes, or migrator service exposure. [Redgate release notes](https://documentation.red-gate.com/flyway/release-notes-and-older-versions/release-notes-for-flyway-engine) · [lz4 developer advisory](https://github.com/yawkat/lz4-java/security/advisories/GHSA-mcr4-qmvw-px4g)
- Flyway's SQL Server jar name is `mssql-jdbc-12.10.2.jre11.jar`. Trivy labels its extracted version `12.10.2` as affected by CVE-2025-59250 and lists `12.10.2.jre11` itself among the fixes. Microsoft explicitly lists the hostname validation security fix in v12.10.2. The Flyway base jar matches Maven Central’s patched `12.10.2.jre11` bytes (SHA-256 `dbf27d0d7ff85f133bbc80b0282ec2cd4d1fa1f0954033dd6bbd68612a1c0a6e`). Its internal Maven/OSGi metadata is the unsuffixed `12.10.2`, corroborating a scanner version-mapping false positive. Preserve the raw finding and reassess if the Flyway package or Trivy matching changes. [Microsoft changelog](https://github.com/microsoft/mssql-jdbc/releases/tag/v12.10.2)

Re-evaluate all material rows on each upstream digest, a credible update to reachability, or before authorizing any release that ships the image. Retain the JSON reports and compare equivalent images using one scanner DB snapshot. Scanner counts record occurrences and do not establish exploitability.

## 8. Rollback runbook

Every deployment promotes an immutable container image digest verified by CI (`api`, `web`, `migrate`) — never a source rebuild during promotion or mutable `:latest` tag.

1. Record the previously verified image digests and confirm pre-change logical dump (`pg_dump -Fc`) and schema migration version (`flyway_schema_history`).
2. Update deployment manifest / Compose digest pins to the last known-good digests and redeploy containers (`docker compose pull && docker compose up -d` or container revision traffic shift back to the prior digest revision).
3. Verify `GET /api/v1/probes/readiness` returns HTTP `200`, Nginx security headers hold (`X-Frame-Options: DENY`, CSP, no TLS/session regressions), and `etaye_runtime` returns `0` unscoped RLS rows.
4. Schema migrations remain **forward-only and immutable** once applied on any shared environment. Only if an unrecoverable pre-cutover demo state occurred would operators drop/provision a clean database, restore the pre-release `pg_dump` dump (section 3), and bring up the prior image digests.

## 9. Operational observability

Confirmed directly in source (`SecurityConfig.java`, `ReadinessProbeController.java`, `application.yml`):

- **Health/readiness endpoint**: Exposed at **`GET /api/v1/probes/readiness`** (JSON payload verified in smoke and capacity benchmarks). Spring Boot Actuator HTTP exposure is explicitly disabled (`management.endpoints.web.exposure.include` is none; startup logs confirm `Exposing 0 endpoints beneath base path '/actuator'`). Any platform health check configured against `/actuator/health` would fail with `404`.
- **Logging and metrics**: Structured Logback JSON output with HTTP correlation IDs is active in `api`, while Prometheus/Grafana metrics endpoints remain unexposed by design in Sprint 3 (`No external telemetry or third-party APM`). For a first synthetic trial, evaluate live logs and cap costs before enabling durable ingestion or an external telemetry destination. [Logging cost assumptions](hosting-cost-assessment.md#complete-consumption-workload)

## 10. Outstanding work and owner decisions

| Item | Current status | Next decision / action |
|---|---|---|
| Integrated consolidation | Six PRs merged and exact main evidence recorded | Use the [verified main baseline](quality-baseline.md#verified-integrated-main-at-b4e8ff9) for future comparisons. |
| Managed PostgreSQL 18 | Draft [PR #90](https://github.com/Aymenchaghoub/etaye/pull/90) (`812d97c`) verified locally (`298` Maven tests on clean verify, `6` non-superuser role provisioning cases, zero Flyway checksum changes) with `14/14` executed CI checks passing. | Review PR #90 and remaining provider/TLS/password assumptions before deciding whether to merge. No remote Neon work is authorized. |
| GHCR release and provenance | Draft [PR #92](https://github.com/Aymenchaghoub/etaye/pull/92) (`21581f1`) verified with `305` tooling tests, live `prepare_release_candidate.sh` / `release_artifacts.py verify` dry run (Playwright + CycloneDX SBOMs + Trivy 0.76.2), and `16/16` green CI checks. | Review PR #92. Separate authorization is required for merge, GHCR activation, registry writes, and published provenance attestations. |
| Demo host | Provisional Azure Container Apps Consumption + Neon Free + GHCR, with Render Free + Neon fallback. | Confirm exact artifacts, access, cost and rollback gates before any request to deploy. |
| Functional Story 3.4 | Next candidate, pending design review and story authorization. | Prepare phase 0 only when requested. See the [delivery order](../planning-artifacts/story-delivery-order.md). |
