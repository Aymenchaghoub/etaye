# Deployment readiness assessment

Updated 2026-11-26. Owner: Aymen Chaghoub.

Covers: local capacity measurements, security headers, backup and restore rehearsal, Render Free feasibility, Neon compatibility, secrets inventory and outstanding deployment-readiness work. No remote deployment or paid service was activated.

## 1. Local capacity measurements

All measurements were taken against the isolated `etaye-quality-baseline` compose stack on `389c48da64a2aed78c57418b122b0e5585989a50`. No CPU constraints were applied for these baseline numbers.

### Unconstrained baseline (1 profile)

| Metric | Value | Notes |
|---|---|---|
| Startup time | 13.46 s | From container start to first successful health probe |
| Idle RSS (VmRSS) | ~596 MB | Before any HTTP load |
| Idle cgroup current | 650 MB / 652 MB peak | cgroup memory.current / peak |
| Peak RSS after load | ~633 MB | After single-client + 4-client HTTP loads |
| Peak cgroup after load | 681 MB / 684 MB peak | cgroup memory.current / peak |
| CPU time (idle→load) | 44.4 → 47.9 s usage_usec | Total CPU seconds consumed |
| Threads at idle | 54 | JVM thread count |
| Threads after load | 57 | JVM thread count after two load phases |

### HTTP throughput (single Vite-build, synthetic health/session endpoints)

| Scenario | Clients | Samples | Wall (s) | RPS | p50 (ms) | p95 (ms) | p99 (ms) | Errors |
|---|---|---|---|---|---|---|---|---|
| Serial (single client) | 1 | 40 | 0.55 | 72.66 | 14.0 | 23.4 | 48.6 | 0 |
| Bounded (4 clients) | 4 | 160 | 0.57 | 281.4 | 15.1 | 24.7 | 35.7 | 0 |
| Recovery (post-load) | 1 | 12 | 0.10 | 117.4 | 10.0 | 13.2 | 13.2 | 0 |

All responses 200 OK. Database connections held by both `etaye_identity` and `etaye_runtime` roles after load, confirming connection pool activity.

### Render Free feasibility — preliminary assessment

> [!WARNING]
> **Render Free is currently not feasible** for this application without JVM tuning. The unconstrained RSS is ~633 MB, which exceeds Render Free's 512 MB memory limit by approximately 23%. Under the CPU constraints of the free tier (shared CPU, throttled during low-usage periods), startup may exceed the 4-minute HTTP probe timeout observed in the previous session's constrained experiment (HTTP 504). These are **preliminary local observations**; actual Render behavior was not tested.

**Blockers identified:**

1. **Memory**: Unconstrained RSS ~633 MB > Render Free 512 MB limit. Even at idle (~596 MB), the gap is narrow. Under the HotSpot JIT, the RSS grows further after first requests.
2. **Startup**: 13.46 s startup on an unconstrained local host. Under Render's shared CPU with throttling, this scales unpredictably. The previous session saw >4 min startup under 20% CPU with competing test processes.
3. **Sleeping**: Render Free suspends instances after 15 minutes of inactivity. The JVM startup time makes cold-start latency unacceptable for a clinical demo without an explicit warming strategy.

**Potential mitigations (not yet implemented):**

- `-XX:MaxRAM=400m -XX:MaxMetaspaceSize=96m -Xss256k` JVM flags to constrain heap and metaspace. Requires verification that the application functions correctly under those limits.
- Spring Boot layered JARs / CDS class data sharing to reduce startup time.
- A lightweight health-check warm-up endpoint.
- Render paid tier ($7/month, 512 MB) would not resolve the memory issue. The 2 GB tier ($25/month) would.

**Recommended path:**

1. Test JVM memory tuning locally before any remote deployment.
2. If the application fits within 400 MB RSS with tuning, re-assess Render Free feasibility.
3. If not, propose Option D (VPS: €3.49–€6.49/month, OVHcloud or Hetzner) as the primary free-budget alternative. This avoids sleeping, provides a stable HTTPS origin and supports all technical requirements.

> [!IMPORTANT]
> No deployment decision, no paid service activation and no remote database modification is authorized without Aymen's explicit written approval.

## 2. Security headers (OWASP ZAP baseline)

**Baseline scan** (`389c48da64a2aed78c57418b122b0e5585989a50`, 12 URLs, Automation Framework):

| Rule | Before | After |
|---|---|---|
| 10020 Anti-clickjacking Header | WARN-NEW (4 URLs) | PASS |
| 10021 X-Content-Type-Options | WARN-NEW (5 URLs) | PASS |
| 10036 Server Version Disclosure | WARN-NEW (5 URLs) | PASS |
| 10038 Content-Security-Policy | WARN-NEW (4 URLs) | PASS |
| 10109 Modern Web Application | WARN-NEW (informational) | PASS (unchanged) |

**After fix** (`cfe71f9`, same target, nginx config reloaded): `FAIL-NEW: 0 WARN-NEW: 0 PASS: 62`. All 62 rules pass. The automation plan warning about HTTP 421 on `/` is expected: ZAP's spider sends the container IP as the Host header, which the existing loopback guard rejects by design.

Fix location: [`infra/demo/nginx.conf`](../../infra/demo/nginx.conf) on branch `fix/demo-response-headers` → [PR #86](https://github.com/Aymenchaghoub/etaye/pull/86).

## 3. Backup and restore rehearsal

A backup-and-restore cycle was performed against the isolated `etaye-quality-restore` stack using synthetic data only.

| Step | Result |
|---|---|
| PostgreSQL dump (pg_dump) | Completed. Dump stored at `/tmp/etaye-baseline.Oj8sC7FL/restore/etaye-demo.dump` |
| Fresh container provisioning | Compose stack `etaye-quality-restore` started with fresh volume |
| Schema migration (Flyway) | 6/6 migrations applied on fresh DB |
| Data restoration (pg_restore) | Synthetic rows loaded: `server=180007`, `records=21`, `measurements=6` |
| RLS verification | `etaye_runtime` role: 2 connections granted, 0 without context (fail-closed confirmed) |
| E2E smoke (restored stack) | `e2e/measurements.spec.ts`, `e2e/record.spec.ts`, `e2e/patients.spec.ts` passed |

Containers for the restore rehearsal are still running (`etaye-quality-restore-*`).

> [!NOTE]
> The restore rehearsal used synthetic data and an isolated local stack. A production backup strategy requires: versioned encrypted dump storage, restore-test automation in CI, and a defined RTO/RPO. The 6-hour Neon free-tier restore window is not a backup strategy.

## 4. Neon compatibility

Not tested in this session. The Neon Free tier offers 0.5 vCPU, 2 GB RAM and a 10 GB storage limit per branch. Key compatibility considerations:

- The connection pool must use TLS and must set the RLS session context (`SET LOCAL etaye.practice_id`) in the same transaction as queries.
- Neon uses Neon Serverless Driver (HTTP/WebSocket) for serverless functions; for Spring Boot JDBC, the standard PostgreSQL JDBC driver applies.
- The existing `etaye_runtime` and `etaye_identity` roles and RLS policies are schema-level and migrate correctly to Neon.
- The 6-hour restore window and branching model differ from local PostgreSQL. Branching provides a copy-on-write clone for staging.

> [!CAUTION]
> No remote Neon modifications or migrations are authorized. Neon compatibility must be tested against a local replica or a dedicated test branch, not the production Neon project.

## 5. Secrets inventory

| Secret | Location | Current status |
|---|---|---|
| `POSTGRES_ADMIN_PASSWORD` | `infra/local/.env` (gitignored) | Generated locally by `start.sh` |
| `ETAYE_OWNER_PASSWORD` | `infra/local/.env` (gitignored) | Generated locally by `start.sh` |
| `ETAYE_RUNTIME_PASSWORD` | `infra/local/.env` (gitignored) | Generated locally by `start.sh` |
| `ETAYE_IDENTITY_PASSWORD` | `infra/local/.env` (gitignored) | Generated locally by `start.sh` |
| GitHub Actions secrets | GitHub repository secrets | Not inspected in this session |
| SonarQube token | GitHub Actions secret `SONAR_TOKEN` | Required for SonarQube Cloud analysis |

No secrets are committed to the repository. The `.env` files are gitignored. No paid service credentials were created or modified.

## 6. SBOM and artifact provenance

Not completed in this session. The package workflow already runs Trivy for vulnerability scanning against the Docker images. A formal SBOM (CycloneDX or SPDX) would require:

- `syft` or `trivy sbom` integration in the package workflow.
- Attestation via `cosign attest` or GitHub attestations for GHCR-pushed images.

This is deferred. See [deferred-work.md](../planning-artifacts/deferred-work.md).

## 7. GHCR release readiness

Not completed. The `package.yml` workflow builds and scans Docker images but does not push to GHCR. Release readiness requires:

- Defining a release tagging strategy (semantic versioning).
- GHCR push with pinned digest (not `:latest`).
- Attestation and SBOM attachment.

No release or container publication is authorized without Aymen's explicit approval.

## 8. Rollback runbook

A rollback event requires reverting to the previous Docker image digest. The current approach:

1. Identify the previous release digest in GHCR (not yet published).
2. Update the `compose.yml` or deployment configuration to reference the previous digest.
3. Run `docker compose pull && docker compose up -d` to pull and restart.
4. Verify health checks pass and RLS functions correctly.
5. If the migration was destructive, restore from the last known-good dump (see section 3).

> [!IMPORTANT]
> Applied migrations are immutable once implementation begins. No destructive migration is permitted without a confirmed rollback path.

## 9. Operational observability

Not completed. The application emits Spring Boot Actuator health and metrics endpoints. For Render Free:

- The `/actuator/health` endpoint is used as the health check.
- Metrics are not externally exposed (no Prometheus, no Grafana).
- Log access on Render requires the Render dashboard or `render logs` CLI.

A minimal observability baseline would include:
- Structured JSON logging (already in place via Logback).
- `/actuator/health` and `/actuator/info` exposed behind authentication.
- A log-aggregation sink (e.g., Render log drains, free tier of Betterstack Logs).

## 10. Next steps

| Item | Status | Blocker |
|---|---|---|
| JVM memory tuning under constraints | Not started | Requires isolated benchmark without competing processes |
| Constrained startup benchmark | Preliminary (504 seen) | Needs clean isolated measurement |
| SBOM generation | Not started | No authorization blocker; deferred |
| GHCR release readiness | Not started | No deployment without Aymen's approval |
| Neon local compatibility test | Not started | Requires dedicated Neon test branch |
| Rollback runbook drill | Not started | Requires GHCR image first |
| Observability sink setup | Not started | No deployment decision yet |
| Accessibility audit | Not started | Requires running application + screen reader |
| Mutation testing (API) | Pilot complete (15/15) | Full API scope not yet covered |
