# Story 10.1 — implementation record

Status: implemented on branch `feat/10.1-walking-skeleton` (PR #33), verified locally and green on GitHub Actions; not merged. Date: 2026-11-19. Last code commit verified: `c6003e8` for the API suite and the real stack, plus `a783a89` for the report guard (base `0fc825f`). Authorization: UD-41, story 10.1 only, a named exception to the CORE design gate because the story has no screen. This record claims no clinical validation, certification or regulatory compliance; every person and value in the code, tests and screenshots is synthetic.

## 1. Who did what

| Role | Work |
|---|---|
| Claude Code (orchestrator) | Story documents, context packet, prompts and containment rules, independent verification of every phase (full test runs, mutation runs, real-stack runs), integration, this record. |
| `agy` agent, Gemini 3.8 Flash (High); Claude Opus 4.6 for the first fix of the final review only (its quota ran out mid-run) | `apps/api`: schema, roles, security, request path, tests, `infra/local`. |
| Codex CLI | `apps/web`, the two workflows, `tools/assert_test_reports.py`, the TF-3 change of `tools/validate_docs.py`, database provisioning hardening; and the independent reviews (plan, backend slice, final, targeted re-review). |

The code agents reported claims that were sometimes wrong or optimistic (a test class count, a nonexistent test class, a mutation left in the migration after a network failure, a provisioning script that never worked on a real container). Only the evidence in section 5, produced by the orchestrator, is retained.

## 2. Sources consulted

The context packet ([story-10-1-context-packet.md](story-10-1-context-packet.md), section 10) lists the last commit of each planning source read before coding: epic 10, ADR-002, 003, 004, 007, 009, 011, the architecture spine, data model and dictionary, business rules, access matrix, contract README. Library behavior was checked against the official documentation of the exact versions (Spring Boot 4.1 Java baseline, Jackson 3 coercion, Spring Session JDBC schema, PostgreSQL RLS). Planning documents were not changed by the implementation except the deferred-work register, the story documents and the verified commands in AGENTS.md. No acceptance criterion was changed; the effects on AT-038-A and AT-038-B are in section 6.

## 3. What was built

- `apps/api`: Spring Boot 4.1.2 on Java 21, Maven wrapper 3.9.11, packages `dev.aymen.etaye.{audit,communications,identity,practices,records,infrastructure}`. Operations: `getCsrfToken` (GET `/api/v1/csrf`), `createPatient` (POST `/api/v1/practices/{practiceId}/patients`), `getPatientRecord` (GET `/api/v1/practices/{practiceId}/records/{recordId}`), plus the public readiness probe `GET /api/v1/probes/readiness` that answers only `{"status":"UP"}` or `{"status":"DOWN"}` (503).
- One Flyway migration `V1__initial_schema.sql`: practice, account, membership, patient, patient_record, audit_event, outbox_event, idempotency_receipt and the Spring Session JDBC tables. Composite `(practice_id, id)` references, row-level security enabled and forced on every practice-owned table and, for the identity role, on account and membership.
- Three database roles created outside migrations by `infra/local/init-roles.sh`: `etaye_owner` (migrations), `etaye_runtime` (HTTP, no ownership, no BYPASSRLS, insert-only on audit and outbox, no access to practice, account or membership), `etaye_identity` (column grants, never `password_hash`, rows limited by `etaye.account_id`). Flyway never runs inside the HTTP application.
- `PracticeTransaction`: the single transactional entry point. It sets the context with a parameterized transaction-local `set_config`, refuses to join a transaction it did not open (decided by a private synchronization marker, so a foreign transaction or a `REQUIRES_NEW` inner one cannot forge it), rejects a second practice, and refuses a leaked context.
- Request path: session cookie `__Host-etaye-session` (HttpOnly, Secure, SameSite=Lax, host-only), the account and memberships re-resolved on every request including replays, CSRF token bound to the session and read only from `X-CSRF-TOKEN`, idempotency with an advisory lock on (account, practice, operation, key digest) and a canonical body hash, audit and outbox written in the same transaction, RFC 9457 problems from one shared writer, database outages answered 503.
- `apps/web`: a one-page React 19.3.1 + Vite 8 page that shows LOADING, READY or UNAVAILABLE from the readiness probe (6-second abort, response body always consumed), with a stubbed smoke suite and a separate real-stack outage spec.
- `infra/local`: PostgreSQL 18 compose file bound to `127.0.0.1` with a fixed administrator name (a configurable one named like an application role would stay a superuser after a restart on an initialized volume), the provisioning script and a script that exercises it and the resolved compose settings in throwaway containers.
- CI: `api.yml` and `web.yml` (informational, never run on GitHub), `tools/assert_test_reports.py` (recounts real results, requires named suites), TF-3 in `tools/validate_docs.py` (the application exception is derived from `authorized_stories` in `_bmad/custom/config.toml`, bounded to a tracked-path allowlist).

## 4. Verification of the behavior checks (packet section 6)

All tests run on a real PostgreSQL 18 (Testcontainers) through real HTTP. Total at `79cfe7a`: 117 tests, 0 failures, 0 skipped (my own run; the per-class counts printed by the code agent were repeatedly wrong and are not used).

| Check | Proof |
|---|---|
| C1 | `PatientCarrierOperationsHttpTest#c1_*`: 201, quoted ETag, one row in each of the five tables filtered by practice. |
| C2 | `#c2_*`: failure injected in `beforeCommit` after a flush, with in-transaction insert counts read from `pg_stat_xact_user_tables` proving the five writes happened, then all five tables unchanged. |
| C3 | `#c3_*`, `#b1_practiceInvisibility_*`: the whole 404 body equals the unknown-id body in five permutations, no identifier echoed. |
| C4 | `#c4_*`: administrator-only and read-only memberships get 403 `FORBIDDEN`, distinct from the CSRF refusal code `CSRF_REJECTED`; no audit or outbox row. |
| C5 | `PracticeIsolationTest#checkC5_*`: positive control, then no row and an RLS violation without context. |
| C6 | `PracticeIsolationTest#checkC6_*` and `HttpConnectionReuseIsolationTest`: pool of one, same `pg_backend_pid`, empty context before each set, error then real capability refusal then success for another practice. |
| C7 | `#c7_*`, `#concurrent*`, `#differentValidBodies*`: replay, conflict, two concurrent identical requests give two identical 201 and one row, two concurrent different bodies give one 201 and one 409; the test waits for an ungranted advisory lock so it cannot pass without it. |
| C8 | `PracticeIsolationTest#checkC8_*`: from the runtime datasource, current user, role closure, ownership, schema-qualified RLS flags, table and column privileges, no TRUNCATE. |
| C9 | `#c9_*`, `#csrf_*`: valid, absent, invalid, parameter-only and another session's token. |
| C10 | `#c10_*`, `#b1_*`, `#a1_*`: `application/problem+json`, code, correlationId, no stack trace or SQL; 400, 404, 405 with `Allow`, 406, 415, 422, 503. |
| C11 | `ArchitectureTest`: 11 rules including per-module privacy of domain and repository packages; a deliberate violation turns them red (matrix below). |
| C12 | Mutation matrices in section 5, backed by committed tests. |
| C13 | `apps/web/tests/stubbed-probe.spec.ts` (8 cases, the API stubbed) and `apps/web/e2e/outage.spec.ts` (real browser, API and PostgreSQL, stop, start, pause, unpause). |

## 5. Verification actually run (2026-11-19)

| Command | Result |
|---|---|
| `cd apps/api && ./mvnw -B -ntp verify` (the clone at `79cfe7a`; 102 tests at `c6003e8`, where the real stack below was run) | 117 tests, BUILD SUCCESS. |
| `corepack pnpm install --frozen-lockfile`, `corepack pnpm exec tsc -b`, `corepack pnpm build`, `corepack pnpm test:smoke` in `apps/web` | Pass; 9 smoke tests. |
| Real stack: compose PostgreSQL 18 with the real init script, Flyway as owner, API jar as runtime role, Vite dev server, `ETAYE_E2E_DB_CONTAINER=etaye-local-postgres corepack pnpm test:e2e-real` | 1 passed (9.3 s) on `c6003e8`, after the fixes below. |
| `infra/local/verify-init-roles.sh` | 5 PASS: attributes and schema owner, reserved administrator name refused, password with special characters accepted, compose keeps the administrator name fixed and the port on the loopback even with an override, and the health check goes unhealthy for a single privileged attribute or an inherited membership and healthy again when it is removed. |
| `python3 -m unittest discover -s tools -p 'test_*.py'` | 79 tests OK. |
| GitHub Actions on PR #33 | First run: `web.yml` green, `api.yml` red (PostgreSQL closes connections with SQLSTATE 57P01 when it stops, the classifier no longer recognised it, and the exception became a 401 at the error dispatch; fixed with unit tests). Next runs: Planning package checks, Analyze tooling, CodeQL, API (117 tests) and Web all green. The web job is slow on some runners because of the Chromium install (9 min 20 s once), so its timeout is 25 minutes. |
| The exact commands of `api.yml` and `web.yml` replayed on real reports | Surefire guard `PASS surefire: tests=102` with the 14 required suites; Playwright JSON guard `PASS playwright: tests=9` with the required spec file. The first replay on real Surefire reports failed (see below) and was fixed. |
| `python3 tools/validate_docs.py` | `ok: true`, scope mode `story-10.1`. Documentary check only. |
| `git diff --check 0fc825f..HEAD` | Silent. |
| Anonymous probes on the running API | Unknown protected routes 401 problem+json, POST without token 403 `CSRF_REJECTED`, `/api/v1/csrf` 200, `/actuator/health` 401 (not exposed), readiness 200/503 with the minimal body. |
| Secrets | No generated password in any log; the three role passwords and the admin password searched in the API, web and compose logs: 0 matches. |
| Screenshots | `docs/test-artifacts/story-10-1-screenshots/` at 1440, 834 and 390 px, READY and with PostgreSQL stopped; no horizontal overflow. Opened and read by the orchestrator. |

Defects found only by running the real thing, not by the stubbed or in-process tests: the provisioning script failed on a real container (psql does not interpolate variables inside a dollar-quoted body); the web page left the body of a non-OK response unread, which kept the request pending in the browser and hung the real outage spec; and the CI report guard rejected a real green API run because the ArchUnit engine writes simple class names in its testcase elements (the guard now matches the suite name).

Mutation matrices, each mutant shown red then restored (commands kept in the orchestrator's session, outputs read):

- Request path and layers, at `f248190` and again at `f6db2bd` (all eight red as listed): RLS policy opened on `patient` (direct SQL test red; the HTTP cross-practice test stays green because the application predicate still filters, which is the documented defense in depth), FORCE RLS removed, runtime granted SELECT on `practice`, practice context never set, capability guard always true, audit write removed, receipt write removed, a service importing the web package.
- Role mutations at `f6db2bd`: the runtime role created WITH BYPASSRLS turns C5, C6 and C8 red; the runtime role made a member of the owner role turns C8 red. The HTTP cross-practice test C3 stays green under BYPASSRLS because `findByIdAndPracticeId` still filters by practice: the isolation is defended twice on purpose, and the database layer is proven by the per-table direct SQL tests (`checkDirectSqlIsolation_*`), which go red when the policy is opened. The packet's wording "C3, C5 and C6 must go red" assumed a single layer; this is a documented deviation, not a missing test.
- Fix 1 (`ba174f3`): old `|` hash, `_csrf` parameter fallback, C2 hook neutralised, advisory lock removed.
- Fix 2 (`aae2fa7`): lock, account context, transaction guard, strict JSON, session deletion, membership policy opened, no socket timeout (red by timeout, then bounded).
- Round 2 of the final review at `c6003e8` (nine mutants, all red): writer ignores a committed response, classifier accepts a bare `SocketException`, `/csrf` continues after a failed session deletion, date deserializer trims again, practice marker stays active after commit, DELETE granted on `audit_event` (C8 privilege matrix), constant advisory lock key (scope test), a `practices` class depending on a `records` controller (ArchUnit allowlist), `patient_record` write check opened. The compose administrator override is covered by `verify-init-roles.sh` (it fails on the previous compose file).
- Final review fixes: `produces` removed, request cache and logout re-enabled, probe chain removed, generated password allowed, `/csrf` special case removed, error classifier disabled, Throwable logged again, transaction guard removed, strict coercion removed, strict date removed, empty email accepted, `Allow` dropped, outbox write removed, lock removed, 403 instead of 404, TRUNCATE granted, audit WITH CHECK removed, forbidden cross-module import, non-Secure cookie.

## 6. Decisions and assumptions made while building

User decisions applied: UD-38 to UD-41 (React Native with Expo later, demo-only mobile apps, ADR-011 order and budgets, authorization of this story only).

[ASSUMPTION] marks a choice made during implementation that Aymen has not confirmed:

- [ASSUMPTION] AT-038-B is followed literally: a failed attempt commits no audit or outbox row, so denials are not recorded although BR-017 lists them as auditable. This ambiguity is still open and needs a decision.
- [ASSUMPTION] A practice where the caller has no membership is answered 404, a member lacking the capability 403, with fixed public details (spine HTTP conventions).
- [ASSUMPTION] A receipt is replayed whatever its `expires_at` until a purge exists (D-34).
- [ASSUMPTION] The CSRF token lives in the session, so anonymous `GET /api/v1/csrf` creates a session row; issuance is not bounded yet (D-35).
- [ASSUMPTION] The runtime datasource uses 3-second connect and socket timeouts for every query (D-39).
- [ASSUMPTION] JSON scalars are not coerced: a number for a string field or an array for a date is a 400; an empty `contactEmail` is a 400 while an empty `phone` is accepted.
- [ASSUMPTION] Java 21 and Node 22 were used because Java 25 and Node 26 (ADR-002 proposals) are not installed on Aymen's machine; Spring Boot 4.1.2 matches ADR-002. ADR-002 has not been amended.
- `PracticeTransaction` has an optional `ConnectionLifecycleListener` bean seam used only by a test, next to a production leaked-context check. It is a test-support seam in production code; remove it if a better observation point is found.

Effect on acceptance: AT-038-A is met for the membership path; the "portal link" branch of the sentence is not implemented because portal identities are out of scope. AT-038-B is met (absent context, pooled connection after an error, missing capability).

## 7. Reviews

- Codex reviewed the phase 0 plan, the first backend slice (4 blockers: a test hook in production code, a concurrent idempotency 500, an ambiguous body hash, a CSRF parameter fallback) and the whole branch (2 blockers: a create that could commit then answer 500 on a content negotiation error, and provisioning that could keep a superuser application role). All were fixed and proven by mutations. A targeted re-review of the fixes followed and found one more blocker (the administrator name could survive a container restart) and ten important items, mostly CI guards, the outage classifier, response handling on late failures and tests that would pass on a broken implementation; all were fixed and proven as listed in section 5.
- Items judged not worth blocking the story are deferred with a trigger in the register: D-34 to D-40.

## 8. Not verified, and limits

- `api.yml` and `web.yml` ran on GitHub for the first time on 2026-11-20 and are green after one fix; they stay informational. Making them required checks is Aymen's decision (D-40).
- No test pins Spring Session's own transaction: re-adding `withoutTransaction()` (a change that would make a session and its attributes non-atomic) does not turn any test red.
- CodeQL and the documentation checks passed on the pull request; Gitleaks ran as part of the documentation job.
- No Windows or macOS run. No load, soak or memory measurement (story 10.3).
- HawkScan was not run: no account, key or paid plan exists, and D-33 keeps it deferred.
- Expo alignment: React 19.3.1 matches the peer range recorded in ADR-010 from the official documentation; `apps/mobile` does not exist, so nothing was built against it.
- The smoke page is a technical page of a story without screens; it is not a Figma-bound design.

## 9. Running it locally

Passwords are generated per run and never committed. Run the whole block in one shell (or re-export the four variables in every terminal that starts the API): a second terminal without them would pass empty runtime and identity passwords.

```bash
export POSTGRES_ADMIN_PASSWORD=$(openssl rand -hex 12) ETAYE_OWNER_PASSWORD=$(openssl rand -hex 12) \
       ETAYE_RUNTIME_PASSWORD=$(openssl rand -hex 12) ETAYE_IDENTITY_PASSWORD=$(openssl rand -hex 12)
docker compose -f infra/local/docker-compose.yml up -d --wait   # waits for the health check, which also verifies the role attributes
(cd apps/api && FLYWAY_PASSWORD="$ETAYE_OWNER_PASSWORD" ./mvnw -B -ntp flyway:migrate \
   -Dflyway.url=jdbc:postgresql://localhost:5432/etaye -Dflyway.user=etaye_owner)
(cd apps/api && ./mvnw -B -ntp verify && SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/etaye \
   SPRING_DATASOURCE_USERNAME=etaye_runtime SPRING_DATASOURCE_PASSWORD="$ETAYE_RUNTIME_PASSWORD" \
   ETAYE_IDENTITY_DATASOURCE_PASSWORD="$ETAYE_IDENTITY_PASSWORD" java -jar target/etaye-api-0.0.1-SNAPSHOT.jar)
corepack pnpm install --frozen-lockfile && (cd apps/web && corepack pnpm dev --port 5173)   # http://localhost:5173
(cd apps/web && ETAYE_E2E_DB_CONTAINER=etaye-local-postgres corepack pnpm test:e2e-real)   # stops and pauses the database
docker compose -f infra/local/docker-compose.yml down -v
```

Run the API command from its own terminal; the web and the real spec need the API and the compose database running.
