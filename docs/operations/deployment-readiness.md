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

Pending pull requests prepared for Aymen's independent review (all checks green on GitHub, left unmerged):

| Stream | Branch / PR | Scope and verified highlights |
|---|---|---|
| CI evidence integrity | `fix/coverage-report-integrity` · [PR #84](https://github.com/Aymenchaghoub/etaye/pull/84) (`5f8f9c7`) | Enforces measured LCOV/JaCoCo/Cobertura line validation via `tools/assert_coverage_reports.py`; rejects summary-only/contradictory LCOV records (`16/16` checks passing); adds tooling baseline record `docs/operations/quality-baseline.md` |
| API hardening & tests | `fix/quality-api` · [PR #85](https://github.com/Aymenchaghoub/etaye/pull/85) (`ef34b4a`) | Remediated Sonar `java:S4502` (`/api/v1/csrf` removed from CSRF ignore list in [`SecurityConfig.java`](../../apps/api/src/main/java/dev/aymen/etaye/infrastructure/security/SecurityConfig.java)), verified all `java:S2077` queries keep bound parameters, trimmed `compiler-debt.json` (`46` → `42`), **296** local Maven tests passing (`0` failures/skips), JaCoCo **93.24% lines** (`2151/2307`), **86.33% branches** (`777/900`), SonarQube API `OK` at **91.3%** overall coverage (`0` bugs, A/A/A), `12/12` executed checks passing |
| Demo proxy security headers | `fix/demo-response-headers` · [PR #86](https://github.com/Aymenchaghoub/etaye/pull/86) (`15d58df`) | Added `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Permissions-Policy`, strict `Content-Security-Policy`, removed upstream header duplication on `/api/` in [`nginx.conf`](../../infra/demo/nginx.conf), and verified headers in [`run_package_smoke.sh`](../../tools/run_package_smoke.sh) (`10/10` executed checks passing) |
| Python tooling quality | `fix/quality-tooling` · [PR #87](https://github.com/Aymenchaghoub/etaye/pull/87) (`06c12a7`) | Fixed `pythonsecurity:S8705` ref validation and path-traversal checks, bumped `soupsieve==4.1.3` (`GHSA-mmhr-3c5m-f64f`), expanded Python test suite (`292` unit tests, `93.54%` line / `88.91%` branch local, SonarQube Tooling `OK` at `91.5%` overall / `93.3%` new, `16/16` checks passing) |
| Web tests & browser coverage | `test/quality-web` · [PR #88](https://github.com/Aymenchaghoub/etaye/pull/88) (`9c7e000`) | Added component tests (`154/154` Vitest passing, ESLint `0` warnings) and Chromium V8-to-Istanbul merged LCOV pipeline (**97.47% lines** `2352/2413`, **84.09% branches** local; SonarQube Web `OK` at `90.3%` overall / `96.3%` line / `83.9%` branch / `92.3%` new, `16/16` checks passing) |

### OWASP ZAP passive baseline, authenticated OpenAPI scan, and browser CSP walkthrough (`PR #86` target)

- **Unauthenticated ZAP passive scan (`--network container:<web>`, `http://127.0.0.1/`)**: `11` spidered URLs, **0 HTTP 421 rejections** (`Host` loopback guard honored), **65 PASS, 0 FAIL, 3 Low WARN** (`COEP`/`COOP`/`CORP` rule `90004`). Earlier HTTP `421` noise only occurred when scanning container bridge IPs instead of loopback.
- **Authenticated ZAP OpenAPI scan**: `124` scanned API URLs across an authenticated session, **119 PASS, 0 FAIL, 1 Low WARN** (`CORP` `90004`).
- **Real Chromium CSP walkthrough**: `9` demo/application routes walked in headless Chromium (`light` and `dark`), **0 console errors, 0 CSP violations, 0 unexpected HTTP >= 400 responses**, and `playwright.demo.config.ts` (`4/4` real-stack specs) passed.

### Multi-language mutation pilots (local verification benchmarks, no blocking CI gates added)

| Stack & tool | Scope | Mutants generated | Killed / timed out | Mutation score | Test strength | Key remaining gaps |
|---|---|---:|---:|---:|---:|---|
| Java (`PIT`) | 7 security & domain classes | 170 | 161 killed (0 timeout) | **95%** (253/266 covered lines) | **96%** | `IdentityResolutionFilter` `35/41` (`85.4%`), `DatabaseUnavailable` `31/33` (`93.9%`), `BrowserCorsConfig` `21/22` (`95.5%`); `AccessDenialAudit`, `BrowserSessionLifetime`, `PracticeSecurityService`, `SectionStateRule` all `100%` |
| TypeScript (`StrykerJS`) | 10 pure `.ts` domain/format modules (112 Vitest specs) | 1,587 | 1,148 killed + 4 timed out | **72.59%** | **77.52%** | `session-work`, `contact-email`, `routes` `100%`; `evidence.ts` `45.99%` (`67` uncovered mutants) and `asof.ts` `66.08%` are highest-value future unit test targets |
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
| **Azure Container Apps Consumption + Neon Free** *(Best EUR 0 sustainable option)* | Regions restricted by `sys.regionrestriction` to `norwayeast`, `belgiumcentral`, `polandcentral`, `italynorth`, `germanywestcentral`. Consumption monthly free grant: **`180,000 vCPU-s`**, **`360,000 GiB-s`**, **`2M requests`** with scale-to-zero (`= 100 active hours/month` at `0.5 vCPU / 1 GiB` or `200 hours/month` at `0.25 vCPU / 0.5 GiB` inside the free grant). Paired with Neon Free (`$0/month`, `100 CU-h`, `1 GB`). | **EUR 0 / month** within free grants (even after Nov 2027 credit expiry), provided scale-to-zero & alerts are enforced | Meets EUR 0 budget, keeps same-origin routing (`proxy` + `api` or combined container) with TLS termination, avoids VM burn and 30-day DB expiry; requires managed non-superuser PG fix (`init-roles.sh` + demo seed) and future OIDC deployment setup |
| **Azure PostgreSQL Flexible Server (`B1ms`)** | `germanywestcentral` `B1ms`: `0.0179 EUR/h` (~`13.07 EUR/month`) + `0.123 EUR/GB/month` after 12-month student free grant | **~13+ EUR/month** after first 12 months | Viable within credit while student grant holds, exceeds EUR 0 target afterward unless replaced by Neon Free |
| **Azure VMs (`Azure for Students`)** | All `Standard_B*` burstable VM SKUs return `NotAvailableForSubscription`. Cheapest available `belgiumcentral` <=4 vCPU SKUs are `D2s v4` / `D2as v4` (`0.1032 EUR/h` ≈ `75.34 EUR/month` always-on) | **~75.34 EUR/month** (drains USD 100 credit in ~5 weeks if always-on) | **Rejected** for always-on hosting under student/EUR 0 budget |
| **Render Free Web + Neon Free** | Render Free Web: `512 MB` RAM, ~`0.1` CPU, `15 min` sleep, `750 h/month` workspace pool. Render Free PG expires after `30 days` (unusable beyond short tests) | **EUR 0 / month** (with Render cold-start latency); Starter Web (`$7/month`) stays at `512 MB` RAM | Fits `512 MB` memory only with JVM SerialGC/Tier-1 tuning (`218.6 MB` cgroup peak at `0.25c`), but ~`0.1` CPU + Render Free Postgres expiry make Container Apps Consumption + Neon Free stronger if OIDC/packaging are approved |
| **Option D (OVH VPS-1 / Hetzner CX23)** | Non-free always-on benchmark (~€3.81 ex-VAT OVH; €3.49–€6.49 ex-VAT Hetzner) | **€3.49–€6.49+ / month ex-VAT** | Fallback if Aymen explicitly authorizes paid always-on hosting later (not needed yet) |

## 6. Secrets inventory

| Secret | Location | Current status |
|---|---|---|
| `POSTGRES_ADMIN_PASSWORD`, `ETAYE_OWNER_PASSWORD`, `ETAYE_RUNTIME_PASSWORD`, `ETAYE_IDENTITY_PASSWORD` | Local gitignored `.env` (`infra/local/`) | Generated ephemerally per local stack; never committed |
| `SONAR_TOKEN` | GitHub Actions repository secret | Active for existing CI SonarQube Cloud scans; untouched |
| Cloud / registry deploy secrets (Azure OIDC federated credential, Neon runtime DSN, Render deploy hook) | None configured | Not requested or created; deferred until Aymen chooses a deployment target |

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
- **GHCR release and provenance design (gated, not pushed or published)**:
  - [`package.yml`](../../.github/workflows/package.yml) already builds multi-container images and runs Trivy + real-stack verification without pushing to GHCR.
  - A future release workflow can push immutable digests (`ghcr.io/aymenchaghoub/etaye-*@sha256:...`) using repository-scoped `GITHUB_TOKEN` (`packages: write`, `id-token: write`, `attestations: write`) plus `actions/attest-build-provenance` and `actions/attest-sbom`, which both belong to the already-allowlisted `actions/*` publisher and preserve CI ruleset/allowlist policy without extra PATs.

## 8. Rollback runbook

Every deployment promotes an immutable container image digest verified by CI (`api`, `web`, `migrate`) — never a source rebuild during promotion or mutable `:latest` tag.

1. Record the previously verified image digests and confirm pre-change logical dump (`pg_dump -Fc`) and schema migration version (`flyway_schema_history`).
2. Update deployment manifest / Compose digest pins to the last known-good digests and redeploy containers (`docker compose pull && docker compose up -d` or container revision traffic shift back to the prior digest revision).
3. Verify `GET /api/v1/probes/readiness` returns HTTP `200`, Nginx security headers hold (`X-Frame-Options: DENY`, CSP, no TLS/session regressions), and `etaye_runtime` returns `0` unscoped RLS rows.
4. Schema migrations remain **forward-only and immutable** once applied on any shared environment. Only if an unrecoverable pre-cutover demo state occurred would operators drop/provision a clean database, restore the pre-release `pg_dump` dump (section 3), and bring up the prior image digests.

## 9. Operational observability

Confirmed directly in source (`SecurityConfig.java`, `ReadinessProbeController.java`, `application.yml`):

- **Health/readiness endpoint**: Exposed at **`GET /api/v1/probes/readiness`** (JSON payload verified in smoke and capacity benchmarks). Spring Boot Actuator HTTP exposure is explicitly disabled (`management.endpoints.web.exposure.include` is none; startup logs confirm `Exposing 0 endpoints beneath base path '/actuator'`). Any platform health check configured against `/actuator/health` would fail with `404`.
- **Logging and metrics**: Structured Logback JSON output with HTTP correlation IDs is active in `api`, while Prometheus/Grafana metrics endpoints remain unexposed by design in Sprint 3 (`No external telemetry or third-party APM`). Platform log drain (Azure Log Analytics free allowance or Render CLI/dashboard) suffices for initial synthetic demo debugging without clinical data risk.

## 10. Outstanding work and owner decisions

| Item | Current status | Next decision / action |
|---|---|---|
| Merge pending consolidation PRs (#84–#88 + operations doc PR) | All existing PRs #84–#88 verified green on GitHub; left unmerged for Aymen | Owner reviews PRs #84–#88 and this operations PR, then authorizes sequential merge and ruleset/observation review if desired |
| Managed PG 18 (`init-roles.sh` + demo seed `V5`) compatibility | Local non-superuser gap reproduced and solution verified (`GRANT ... WITH SET TRUE` + schema-only separation) | Small infra PR when a managed PG environment is authorized; never modify remote Neon `production` before approval |
| GHCR release & provenance workflow | Architecture designed (`GITHUB_TOKEN` + `actions/attest-*`), no images published | Explicit owner approval before adding a push/publish workflow |
| Target free/demo runtime selection | Empirical capacity (`218.6 MB` tuned peak in `512 MiB` / `262.9 MB` in `1 GiB`) & Azure/Neon/Render TCO verified | Owner decides between Azure Container Apps Consumption + Neon Free (EUR 0/month sustainable with scale-to-zero) vs Render Free vs waiting until Sprint 4+/Story 10.3 |
| Functional Story 3.4 | Untouched (`development_authorized = false`) | Separate design/story authorization step after consolidation PRs close |
