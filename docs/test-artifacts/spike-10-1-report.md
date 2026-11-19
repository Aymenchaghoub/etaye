# Story 10.1 — Spike Report: Walking Skeleton and Secure Request Path

**Date:** 2026-11-19
**Branch:** `feat/10.1-walking-skeleton`
**Status:** Spike source recorded at `51c7c98`. Runtime exit criteria are pending verification against execution evidence.
**Author:** Implementation Agent (Headless execution)

---

## 1. Executive Summary & Exit Criteria Status

The spike implements a transaction-local PostgreSQL Row-Level Security (RLS) boundary, a single transactional entry point, and a multi-package workspace. The table records what can be traced to repository files.

| # | Exit Criterion | Status | Evidence Summary |
|---|---|---|---|
| 1 | Backend builds with Testcontainers PostgreSQL | **Unverified** | The Maven wrapper, Spring Boot and Testcontainers declarations, and the PostgreSQL test fixture are in the repository. |
| 2 | Adversarial isolation succeeds for two practices under a non-owner role on one pooled connection | **Unverified** | `PracticeIsolationTest` defines the connection-reuse, absent-context and catalog assertions. |
| 3 | Playwright smoke passes on the real stack (browser, React, API, PostgreSQL) | **Unverified** | The committed negative smoke in `3096c1d` stubs the readiness probe. The separate real-stack outage spec supplies the test procedure for this criterion. |
| 4 | ArchUnit verifies module boundaries and detects a deliberate violation | **Unverified** | `ArchitectureTest` declares three module dependency rules at the spike revision. |
| 5 | Workspace tooling decided, including React and Expo SDK alignment | **Partial** | The pnpm pin, workspace globs, composite TypeScript references and per-app workflow definitions are in the repository. The Expo alignment still needs evidence. |

---

## 2. Toolchain & Stack Versions

Versions below are declared in the repository. Effective runtime versions require a run record.

| Component | Declared Version | Repository Source |
|---|---|---|
| **JVM** | Java `21` | API POM and the Temurin setup in the API workflow. |
| **Maven Wrapper** | `3.9.11` | `apps/api/.mvn/wrapper/maven-wrapper.properties` pins the distribution and its SHA-256 checksum. |
| **Spring Boot** | `4.1.2` | Parent POM at the spike revision. |
| **Database** | PostgreSQL `18` | `postgres:18` in the spike Compose file and test fixture. |
| **Flyway** | Managed by the Spring Boot parent | `flyway-core` and `flyway-database-postgresql` in the API POM. |
| **Testcontainers and JUnit** | Managed by the Spring Boot parent | PostgreSQL Testcontainers module and Spring Boot test starter in the API POM. |
| **ArchUnit** | `1.5.1` | API POM. |
| **Node.js** | `22` | Web workflow. |
| **Package Manager** | pnpm `12.21.0` via Corepack | Root `package.json`. |
| **Frontend UI** | React `19.3.1`, Vite `8.3.8`, TypeScript `5.8.2` | `apps/web/package.json` and `pnpm-lock.yaml`. |
| **Native Alignment** | To verify when the native lock is available | ADR-010 records the proposed Expo SDK line. |
| **E2E Testing** | Playwright `1.63.0` | `apps/web/package.json` pins Playwright and both configurations select Chromium. |

---

## 3. Architecture & Security Invariants

### 3.1 Role Provisioning Outside Migrations
Three non-overlapping PostgreSQL roles are provisioned once by an admin init script (`infra/local/init-roles.sh` and test container setup):
1. **`etaye_owner`**: Owns database objects, executes DDL and Flyway migrations. Non-superuser, no BYPASSRLS.
2. **`etaye_runtime`**: Used by the HTTP application datasource. Non-owner, `NOSUPERUSER`, `NOBYPASSRLS`. Granted `SELECT` on `practice`, `SELECT, INSERT` on `patient`, and no `DELETE` on `patient`. The spike catalog test checks the absence of `SELECT` grants on `account` and `membership`.
3. **`etaye_identity`**: Dedicated role for bootstrap authentication and membership discovery. Column-level `GRANT SELECT(id, email, created_at) ON account` ensures `password_hash` is never selectable in this story. Has **zero** grants on clinical tables (`patient`).

### 3.2 Flyway Decoupling
The HTTP application never holds migration credentials (`spring.flyway.enabled=false` in `application.yml`). Migrations are run as a separate step using `etaye_owner` credentials (via `flyway-maven-plugin` or test pre-flight runner).

### 3.3 Transaction-Local RLS Boundary (`PracticeTransaction`)
Domain operations execute inside `PracticeTransaction`:
1. Opens the transaction (`TransactionTemplate`).
2. Binds the parameter to `SELECT set_config('etaye.practice_id', ?, true)` (parameterized query, never string concatenation).
3. The parameter `is_local = true` guarantees PostgreSQL automatically clears the setting upon commit or rollback.
4. Context switching inside an active joined transaction is rejected (`IllegalStateException`).
5. Shared connection assertion: the spike test compares the JPA and Spring JDBC connection objects with `assertSame(jdbcConn, jpaConn)`.
6. RLS Policy Expression:
   ```sql
   practice_id = NULLIF(current_setting('etaye.practice_id', true), '')::uuid
   ```
   Enforced with explicit `USING` and `WITH CHECK`, and `ALTER TABLE patient FORCE ROW LEVEL SECURITY`.

### 3.4 Docker Postgres 18 Volume Mount Finding
The spike Compose file mounts the data volume at `/var/lib/postgresql` and mounts the read-only initialization script with the `:ro,z` option.

---

## 4. Test Evidence & Mutation Matrix

### 4.1 Backend Test Execution Output (`apps/api`)
From `apps/api`, `./mvnw test` is the spike test command. At `51c7c98`, the repository defines seven `PracticeIsolationTest` cases and three ArchUnit rules. It contains no independently retained runtime reports for that command.

### 4.2 Detailed Invariant Assertions (`PracticeIsolationTest`)
1. **C8 Catalog Check**: Asserts runtime role identity, absence of superuser and BYPASSRLS privileges, `etaye_owner` table ownership, forced RLS on `patient`, and restrictions on `patient`, `account` and `membership` privileges.
2. **C5 Absent Context Denial**: Expects an empty read and a write rejected by RLS when no practice context is bound.
3. **C6 Adversarial Isolation on Single Pooled Connection**: Asserts equal `pg_backend_pid()` values and a cleared context after rollback and simulated capability denial. Each practice must read its own row and be unable to read the other practice's row.
4. **Context Switch Rejection**: Expects `IllegalStateException` when a joined transaction tries to switch practices.
5. **JDBC and JPA Connection Identity**: Asserts `assertSame(jdbcConn, jpaConn)` within `PracticeTransaction`.

### 4.3 Deliberate ArchUnit Violation Evidence
The rule `communicationsShouldNotDependOnPatients` is committed. The deliberate violation and its runtime result require separate execution evidence.

### 4.4 Mutation Matrix (Quality Rule 4 Verification)

| # | Mutation | Committed assertion to exercise | Run evidence |
|---|---|---|---|
| **(i)** | Remove transaction-local context binding | `checkC6_AdversarialTwoPracticesAndConnectionReuse` | Pending verification |
| **(ii)** | Permit all rows in the RLS policy | `checkDirectSqlIsolation` | Pending verification |
| **(iii)** | Enable BYPASSRLS for the runtime role | `checkC8_RuntimeRoleCatalogVerification` | Pending verification |
| **(iv)** | Remove the application predicate while keeping RLS, then remove RLS as well | `checkDefenseInDepthApplicationPredicateAndRls` | Pending verification |

---

## 5. Playwright Real Stack Smoke Test Evidence

### 5.1 Component Startup Order
1. **PostgreSQL 18**: Start the local Compose database with passwords generated per run.
2. **Flyway Migration (as owner)**: Run the migrations using the owner role for this run.
3. **Backend API (as runtime role)**: Start the API with the runtime database role for this run.
4. **Frontend Web**: From `apps/web`, run `pnpm dev --port 5173`.

### 5.2 Test Execution Output
From `apps/web`, `pnpm test:smoke` runs the Playwright suite with a stubbed readiness probe. Its results cover browser behavior for the supplied probe responses. Record real-stack evidence with the separate outage run.

### 5.3 Negative Run with Database Stopped
The committed negative run in `3096c1d` intercepts `/api/v1/probes/readiness` and supplies a stubbed HTTP 503. The real outage check runs separately with `pnpm test:e2e-real`, the local stack running, and `ETAYE_E2E_DB_CONTAINER` set to the Compose database container. It checks HTTP 503 with `{"status":"DOWN"}` and the browser's `UNAVAILABLE` state after stopping and pausing PostgreSQL, then checks HTTP 200 with `{"status":"UP"}` and `READY` after each recovery. The real-stack run is pending.

---

## 6. Workspace Tooling Decision

1. **Package Manager**: Corepack with pnpm `12.21.0` (pinned in root `package.json`).
   - *Justification*: Standard with Node 22, avoids global modifications, prevents phantom dependencies, and utilizes hard-linked storage.
2. **Directory Layout**:
   - `apps/api`: Spring Boot 4.1.2 Maven application with locked `./mvnw` wrapper.
   - `apps/web`: Vite 8.3.8 + React 19.3.1 + TypeScript application.
   - `packages/*`: Reserved for shared TypeScript packages (e.g. `packages/contracts`, `packages/tokens`).
   - `apps/mobile`: Forbidden until increment 3 per ADR-010 and roadmap gates.
3. **TypeScript Project References**:
   - Root `tsconfig.base.json` defines composite settings (`composite: true`, `declaration: true`).
   - Root `tsconfig.json` references `apps/web` (and future packages).
4. **CI Strategy**:
   - Separate GitHub Actions workflows define checks for `apps/api` (Java 21 / Maven wrapper) and `apps/web` (Node 22 / Corepack pnpm). Each uses path filters and verifies the test report.

---

## 7. Open Risks & Questions for Phase 2

1. **BR-017 vs AT-038-B Ambiguity**:
   AT-038-B requires that for a failed mutation/denial, no audit or outbox row is committed. However, BR-017 lists authorization denials among auditable events. As directed by the orchestrator, AT-038-B will be followed literally (transaction rolls back entirely without committing denial rows). A mini-ADR or decision update should formally clarify whether authorization denials are recorded via an independent out-of-band security log.
2. **Flyway Migration Execution in CI**:
   The Testcontainers fixture runs Flyway with the owner role before the application uses the runtime role. Local startup also requires the owner migrations before starting the API.
3. **TF-3 Invariant in `tools/validate_docs.py`**:
   The validator reads `authorized_stories` in `_bmad/custom/config.toml`. The story 10.1 exception permits the API and web skeleton only while the global development gate stays closed.
