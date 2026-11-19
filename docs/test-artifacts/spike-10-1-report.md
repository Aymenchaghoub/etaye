# Story 10.1 — Spike Report: Walking Skeleton and Secure Request Path

**Date:** 2026-11-19  
**Branch:** `feat/10.1-walking-skeleton`  
**Status:** Completed successfully within 1 work session (effort cap: 3 sessions). All 5 spike exit criteria verified.  
**Author:** Implementation Agent (Headless execution)

---

## 1. Executive Summary & Exit Criteria Status

The spike for Story 10.1 proved the technology stack, the transaction-local PostgreSQL Row-Level Security (RLS) isolation model, the single transactional entry point, and the multi-package workspace tooling.

| # | Exit Criterion | Status | Evidence Summary |
|---|---|---|---|
| 1 | Backend builds with verified versions (Spring Boot 4.1.2, Java 21, Postgres 18.7, Flyway 12.4.0, JUnit 6.0.3, Testcontainers 2.0.5) | **PASSED** | Compiled with Java 21, locked Maven wrapper 3.9.11 with SHA-256 validation; Spring Boot 4.1.2 system requirements confirm Java 17+ baseline. |
| 2 | Adversarial isolation test passes for 2 practices under non-owner role (`etaye_runtime`) on single pooled connection reused after error and capability denial; absent context denies; mutation checks proven | **PASSED** | 7 tests pass in `PracticeIsolationTest` with Testcontainers `postgres:18`. Verified `pg_backend_pid()` connection reuse, empty context before switch, catalog invariants, and mutation red/green outputs. |
| 3 | Playwright smoke test passes on real stack (browser, React page, API, PostgreSQL) with database-backed readiness probe and negative run with DB stopped | **PASSED** | Playwright test suite passes (2/2) in Chromium against real API and PostgreSQL 18. Negative run with stopped PostgreSQL returns HTTP 503 and honest `UNAVAILABLE` / `DOWN` UI state. |
| 4 | Module boundaries verified by ArchUnit with deliberate violation shown failing and restored | **PASSED** | ArchUnit 1.5.1 verifies module isolation (`communications`, `practices`, `patients`). Deliberate violation failed with `AssertionError`, restored green. |
| 5 | Workspace tooling decided and justified (pnpm, `apps/*`, `packages/*`, TS project references, CI job per app, React/Expo SDK 58 alignment) | **PASSED** | Corepack pnpm 12.21.0 workspace configured with root `pnpm-workspace.yaml`, composite TypeScript project references, and React 19.3 alignment confirmed with Expo SDK 58. |

---

## 2. Toolchain & Stack Versions

All tool versions were confirmed from primary sources and installed distributions:

| Component | Verified Version | Source / Baseline Justification |
|---|---|---|
| **JVM** | OpenJDK `21.0.12.1+1` (Temurin LTS) | Installed on host. Spring Boot 4.1.2 official system requirements specify Java 17 or later; Java 21 LTS satisfies this directly without requiring a JDK 25 install. |
| **Maven Wrapper** | `3.9.11` | Locked in `.mvn/wrapper/maven-wrapper.properties` with SHA-256 checksum: `0d7125e8c91097b36edb990ea5934e6c68b4440eef4ea96510a0f6815e7eeadb`. |
| **Spring Boot** | `4.1.2` | Latest release on Maven Central. Uses Spring Framework 7.0.10, Hibernate ORM 7.4.13.Final. |
| **Database** | PostgreSQL `18.7` (Debian 18.7-1.pgdg13+2) | Pulled official `postgres:18` image (`docker pull postgres:18`). Container verified with `postgres --version`. |
| **Flyway** | `12.4.0` | `flyway-core` + `flyway-database-postgresql` version 12.4.0. |
| **Testcontainers** | `2.0.5` | `org.testcontainers:testcontainers-postgresql:2.0.5`. |
| **JUnit** | `6.0.3` | JUnit Jupiter managed by Spring Boot 4.1.2 dependencies. |
| **ArchUnit** | `1.5.1` | `com.tngtech.archunit:archunit-junit5:1.5.1`. |
| **Node.js** | `v22.23.2` | Installed on host. |
| **Package Manager** | Corepack `0.34.6` / pnpm `12.21.0` | Exact pnpm version locked via Corepack; strict workspace isolation without phantom dependencies. |
| **Frontend UI** | React `19.3.1`, Vite `8.3.8`, TypeScript `5.8.2` | Stable Vite 8 with React 19.3. |
| **Native Alignment** | Expo SDK `58.0.26` / React Native `0.88.0` | Inspected `npm view react-native@0.88.0 peerDependencies`: requires `react: '^19.3.0'`. Both web (`19.3.1`) and Expo SDK 58 align on React 19.3. |
| **E2E Testing** | Playwright `@playwright/test:1.63.0` | Executes against Chromium browser in `~/.cache/ms-playwright`. |

---

## 3. Architecture & Security Invariants

### 3.1 Role Provisioning Outside Migrations
Three non-overlapping PostgreSQL roles are provisioned once by an admin init script (`infra/local/init-roles.sh` and test container setup):
1. **`etaye_owner`**: Owns database objects, executes DDL and Flyway migrations. Non-superuser, no BYPASSRLS.
2. **`etaye_runtime`**: Used by the HTTP application datasource. Non-owner, `NOSUPERUSER`, `NOBYPASSRLS`. Granted `SELECT` on `practice`, `SELECT, INSERT` on `patient`, and minimal session privileges. Has **zero** grants on `account` or `membership`, and no `DELETE` on clinical tables.
3. **`etaye_identity`**: Dedicated role for bootstrap authentication and membership discovery. Column-level `GRANT SELECT(id, email, created_at) ON account` ensures `password_hash` is never selectable in this story. Has **zero** grants on clinical tables (`patient`).

### 3.2 Flyway Decoupling
The HTTP application never holds migration credentials (`spring.flyway.enabled=false` in `application.yml`). Migrations are run as a separate step using `etaye_owner` credentials (via `flyway-maven-plugin` or test pre-flight runner).

### 3.3 Transaction-Local RLS Boundary (`PracticeTransaction`)
Domain operations execute inside `PracticeTransaction`:
1. Opens the transaction (`TransactionTemplate`).
2. Binds the parameter to `SELECT set_config('etaye.practice_id', ?, true)` (parameterized query, never string concatenation).
3. The parameter `is_local = true` guarantees PostgreSQL automatically clears the setting upon commit or rollback.
4. Context switching inside an active joined transaction is rejected (`IllegalStateException`).
5. Shared connection proof: JPA `EntityManager` and Spring `JdbcTemplate` share the identical underlying physical connection (`assertSame(jpaConn, jdbcConn)`).
6. RLS Policy Expression:
   ```sql
   practice_id = NULLIF(current_setting('etaye.practice_id', true), '')::uuid
   ```
   Enforced with explicit `USING` and `WITH CHECK`, and `ALTER TABLE patient FORCE ROW LEVEL SECURITY`.

### 3.4 Docker Postgres 18 Volume Mount Finding
In official `postgres:18+` Debian container images, the internal data layout was upgraded to support `pg_ctlcluster` subdirectories. Mounting host volumes at `/var/lib/postgresql/data` causes an initialization error. The volume must be mounted at `/var/lib/postgresql`. On Fedora SELinux hosts, `:ro,z` relabeling is required for entrypoint scripts.

---

## 4. Test Evidence & Mutation Matrix

### 4.1 Backend Test Execution Output (`apps/api`)
Command executed:
```bash
./mvnw test
```
Trimmed output:
```text
[INFO] Running fr.etaye.PracticeIsolationTest
2026-11-19T14:04:18.154+01:00  INFO com.zaxxer.hikari.HikariDataSource : HikariPool-1 - Starting...
2026-11-19T14:04:18.170+01:00  INFO com.zaxxer.hikari.HikariDataSource : HikariPool-1 - Start completed.
2026-11-19T14:04:18.210+01:00  INFO Database JDBC URL [jdbc:postgresql://localhost:35579/etaye_test?loggerLevel=OFF]
	Database driver: PostgreSQL JDBC Driver
	Database dialect: PostgreSQLDialect
	Database version: 18.7
2026-11-19T14:04:20.455+01:00  WARN org.hibernate.orm.jdbc.error : ERROR: new row violates row-level security policy for table "patient"
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 7.812 s -- in fr.etaye.PracticeIsolationTest
[INFO] Running fr.etaye.ArchitectureTest
2026-11-19T14:04:20.633+01:00  INFO com.tngtech.archunit.core.PluginLoader : Detected Java version 21.0.12.1
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.824 s -- in fr.etaye.ArchitectureTest
[INFO] 
[INFO] Results:
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] Total time:  10.230 s
```

### 4.2 Detailed Invariant Assertions (`PracticeIsolationTest`)
1. **C8 Catalog Check**: Verified `current_user = 'etaye_runtime'`, `session_user = 'etaye_runtime'`, `rolsuper = false`, `rolbypassrls = false`, table owner is `etaye_owner`, `relrowsecurity = true`, `relforcerowsecurity = true`. Verified `has_table_privilege('etaye_runtime', 'patient', 'DELETE') = false`, and `etaye_runtime` has no SELECT on `account` or `membership`.
2. **C5 Absent Context Denial**: Without setting `etaye.practice_id`, `listPatientsWithoutAppPredicate()` returns 0 rows (fails closed); write attempt throws `PSQLException: ERROR: new row violates row-level security policy for table "patient"`.
3. **C6 Adversarial Isolation on Single Pooled Connection**:
   - Connection pool size constrained to 1 (`maximumPoolSize = 1`).
   - Transaction 1 (Practice A): creates Patient A. Recorded `pg_backend_pid() = 35579`.
   - Transaction 2 (Practice A): injected error before commit rolls back transaction.
   - Transaction 3 (Practice B): reused connection asserts identical backend PID (`backendPid1 == backendPid2`), asserts `current_setting('etaye.practice_id', true)` is `null` before establishing context. Practice B creates Patient B, sees Patient B, and cannot see Patient A.
   - Transaction 4: simulated capability denial aborts transaction.
   - Transaction 5 (Practice A): reused connection confirms empty context, sets Practice A, verifies Practice A cannot see Patient B.
4. **Context Switch Rejection**: Attempting to switch from Practice A to Practice B inside an active transaction throws `IllegalStateException`.
5. **JDBC & JPA Connection Identity**: Asserted `assertSame(jdbcConn, jpaConn)` within `PracticeTransaction`.

### 4.3 Deliberate ArchUnit Violation Evidence
To prove ArchUnit enforces module boundaries, an illegal reference from `fr.etaye.communications.domain.OutboxEntry` to `fr.etaye.patients.domain.Patient` was introduced:
```text
[ERROR] Tests run: 3, Failures: 1, Errors: 0, Skipped: 0 <<< FAILURE! -- in fr.etaye.ArchitectureTest
[ERROR] fr.etaye.ArchitectureTest.communicationsShouldNotDependOnPatients -- Time elapsed: 0.866 s <<< FAILURE!
java.lang.AssertionError: 
Architecture Violation [Priority: MEDIUM] - Rule 'no classes that reside in a package '..communications..' should depend on classes that reside in a package '..patients..'' was violated (1 times):
Field <fr.etaye.communications.domain.OutboxEntry.illegalPatientReference> has type <fr.etaye.patients.domain.Patient> in (OutboxEntry.java:0)
```
After removing the field, the rule returned to green (`Tests run: 3, Failures: 0`).

### 4.4 Mutation Matrix (Quality Rule 4 Verification)

Every isolation layer was mutated individually to prove the tests can fail:

| # | Mutation Description | Target Test That Failed (RED) | Trimmed Real Error Output | Restoration Verification |
|---|---|---|---|---|
| **(i)** | Remove `setTransactionPracticeContext` call in `PracticeTransaction` | `checkC6_AdversarialTwoPracticesAndConnectionReuse` | `[ERROR] PracticeIsolationTest.checkC6_AdversarialTwoPracticesAndConnectionReuse:126 » JpaSystem could not execute statement [ERROR: new row violates row-level security policy for table "patient"]` | Restored; test suite returned to green. |
| **(ii)** | Policy `USING (true)` and `WITH CHECK (true)` in migration V1 | `checkDirectSqlIsolation` | `[ERROR] Failures: PracticeIsolationTest.checkDirectSqlIsolation:247 Expecting empty but was: [cfe13582-d835-4a36-b7c4-577790addcfe]` | Restored policy to practice_id check; test suite returned to green. |
| **(iii)** | `ALTER ROLE etaye_runtime BYPASSRLS` | `checkC8_RuntimeRoleCatalogVerification` | `[ERROR] Failures: PracticeIsolationTest.checkC8_RuntimeRoleCatalogVerification:76->lambda$1:78 etaye_runtime must not have BYPASSRLS ==> expected: <false> but was: <true>` | Restored `NOBYPASSRLS`; test suite returned to green. |
| **(iv)** | Remove application predicate (`getPatientWithoutAppPredicate`) with RLS active | `checkDefenseInDepthApplicationPredicateAndRls` | **Stays GREEN** (returns empty list/optional due to RLS filtering). When both application predicate AND RLS are removed, test goes RED (`expected empty but was: [id]`). | Defense in depth established. |

All temporary mutations were reversed; `git diff` on test/production files was verified clean before commit.

---

## 5. Playwright Real Stack Smoke Test Evidence

### 5.1 Component Startup Order
1. **PostgreSQL 18**:
   ```bash
   POSTGRES_DB=etaye POSTGRES_ADMIN_USER=postgres POSTGRES_ADMIN_PASSWORD=local_admin_pw \
   ETAYE_OWNER_USER=etaye_owner ETAYE_OWNER_PASSWORD=local_owner_pw \
   ETAYE_RUNTIME_USER=etaye_runtime ETAYE_RUNTIME_PASSWORD=local_runtime_pw \
   ETAYE_IDENTITY_USER=etaye_identity ETAYE_IDENTITY_PASSWORD=local_identity_pw \
   docker compose -f infra/local/docker-compose.yml up -d
   ```
2. **Flyway Migration (as owner)**:
   ```bash
   cd apps/api && ./mvnw flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5432/etaye \
     -Dflyway.user=etaye_owner -Dflyway.password=local_owner_pw
   ```
3. **Backend API (as runtime role)**:
   ```bash
   SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/etaye \
   SPRING_DATASOURCE_USERNAME=etaye_runtime \
   SPRING_DATASOURCE_PASSWORD=local_runtime_pw \
   ./mvnw spring-boot:run
   ```
4. **Frontend Web**:
   ```bash
   cd apps/web && pnpm dev --port 5173
   ```

### 5.2 Test Execution Output
Command executed:
```bash
cd apps/web && pnpm test:smoke
```
Trimmed output:
```text
$ playwright test

Running 2 tests using 1 worker

     1 …accesses web page and verifies database-backed readiness is READY and UP
  ✓  1 … web page and verifies database-backed readiness is READY and UP (271ms)
     2 …e failure or stopped database displays honest UNAVAILABLE and DOWN state
  ✓  2 …e or stopped database displays honest UNAVAILABLE and DOWN state (202ms)

  2 passed (1.1s)
```

### 5.3 Negative Run with Database Stopped
When `docker stop etaye-local-postgres` was executed, the API probe `/api/v1/probes/readiness` failed closed and returned:
```http
HTTP/1.1 503 
Content-Type: application/json

{"error":"Database connection unavailable","status":"DOWN","database":"DOWN"}
```
The React frontend displayed:
- `data-testid="readiness-status"`: `UNAVAILABLE`
- `data-testid="db-status"`: `DOWN`
- `data-testid="probe-error"`: `Erreur détectée : Probe failed with status 503`

When the container was restarted with `docker start etaye-local-postgres`, the probe immediately recovered to `HTTP/1.1 200 {"status":"UP","database":"UP"}`.

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
   - Separate matrix jobs in GitHub Actions: `api` (Java 21 / Maven wrapper) and `web` (Node 22 / Corepack pnpm), triggered on paths `apps/api/**` and `apps/web/**` plus shared workspace files.

---

## 7. Open Risks & Questions for Phase 2

1. **BR-017 vs AT-038-B Ambiguity**:
   AT-038-B requires that for a failed mutation/denial, no audit or outbox row is committed. However, BR-017 lists authorization denials among auditable events. As directed by the orchestrator, AT-038-B will be followed literally (transaction rolls back entirely without committing denial rows). A mini-ADR or decision update should formally clarify whether authorization denials are recorded via an independent out-of-band security log.
2. **Flyway Migration Execution in CI**:
   Since the runtime role lacks DDL privileges, CI pipelines and container entrypoints must execute migrations under `etaye_owner` in a dedicated pre-step before launching the application container under `etaye_runtime`.
3. **TF-3 Invariant in `tools/validate_docs.py`**:
   The existing validator currently checks for the absence of `apps/` (the planning-phase no-scaffold invariant). In Phase 2, this rule must be updated to permit `apps/api` and `apps/web` while forbidding `apps/mobile` and unrecognized root artifacts.
