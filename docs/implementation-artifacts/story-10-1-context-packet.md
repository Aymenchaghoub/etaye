# Story 10.1 — implementation context packet

Status: Prepared for a future implementation. **This packet does not authorize development** (UD-28). Date: 2026-11-19. Prepared by Claude Code at Aymen's request, following the context-packet rule of [delivery rules](../operations/delivery-rules.md). The story text and acceptance designs stay in [epic 10](../planning-artifacts/epics/epic-10.md); where this packet adds detail it says so, and a [Proposal] is not a decision.

## 1. Objective and traceability

Build the walking skeleton and the secure request path, so that every later story inherits authorization, practice context, audit and outbox the same way. Requirements FR-002, FR-020. Rules BR-001, BR-017, BR-018. Journey J-08. Design: operational evidence, no screen. Acceptance: AT-038-A and AT-038-B (AT-038-C does not apply). Milestone M1 of [ADR-011](../decisions/ADR-011-delivery-order-and-milestones.md); the story contains the spike whose exit criteria are in ADR-011 point 4.

## 2. Preconditions (check before any code)

1. Written development authorization naming story 10.1: `development_authorized = true` in `_bmad/custom/config.toml` and a dated row in the decision register. It is false at the time of writing.
2. ADR-011 accepted by Aymen (it is Proposed at the time of writing).
3. A clean checkout of main without `apps/`.
4. Toolchain, observed on Aymen's machine on 2026-11-19: Java 21 (OpenJDK 21.0.12), Maven 3.9.11, Node 22.23.2, Docker 29.8.2, **no** Java 25, **no** Node 26, **no** pnpm. ADR-002 proposes Java 25 and Node 26 and keeps Java 21 and Spring Boot 3.5 as fallbacks. Installing a toolchain is allowed only in the user's home directory from the official distribution with its checksum verified, never with `sudo`, and is recorded in the spike report; otherwise use the fallbacks and say so.

## 3. Scope

In scope: the request path of AD-3 and AD-7 proved on two carrier operations (section 4); the data slice (section 5); the spike (section 7); CI jobs for the new apps; the TF-3 change of `tools/validate_docs.py` (its no-scaffold invariant, roadmap section 12).

Out of scope, with the story that owns it: sign-in and sessions endpoints (1.2; tests create sessions with a test-only helper), invitations (1.1), practice selection and switching (1.3), isolation demonstration across all resources (1.4), duplicate detection, patient search and PD-001 (3.1), the record timeline (3.2), generated client and token bridge (10.2), demo dataset and reset (9.1), every screen, `apps/mobile`, HawkScan (D-33).

## 4. Carrier operations [Proposal, confirm in the phase 0 plan]

Two existing contract operations, implemented minimally and extended later by 3.1 and 3.2:

- `createPatient`, POST `/api/v1/practices/{practiceId}/patients`, CLINICAL_WRITE. CSRF header and `Idempotency-Key` (16 to 128 characters, scoped to account, practice, operation and body hash). Body `PatientEdit`: firstName, lastName, dateOfBirth, optional contactEmail and phone. One transaction creates the Patient and its empty PatientRecord and commits the audit event, the outbox event and the idempotency receipt with them. 201 with `ETag`. The same key and body return the prior outcome and create nothing; the same key with another body is 409 `IDEMPOTENCY_CONFLICT`. Duplicate detection (`DUPLICATE_PATIENT`) is story 3.1.
- `getPatientRecord`, GET `/api/v1/practices/{practiceId}/records/{recordId}`, CLINICAL_READ. 200 with `ETag`. A record of another practice or an unknown id is a 404 indistinguishable from a missing one; a visible resource with a missing capability is 403 (spine HTTP conventions).
- Also `getCsrfToken` (GET `/api/v1/csrf`), because the write path needs it. Errors follow the contract (RFC 9457, stable code, correlationId); declare and return only what the contract declares for each operation.

## 5. Data and isolation slice

Tables, minimal: practice, account, membership (state and capabilities), patient, patient_record, audit_event, outbox_event, idempotency_receipt, plus the session tables of Spring Session JDBC. Every practice-owned row carries `practice_id`; references between practice-owned tables are composite `(practice_id, id)`. UUID keys, `timestamptz`, snake_case. Flyway versioned SQL only; an applied migration is immutable, so do not merge throwaway migrations.

Roles (ADR-003, AD-3): a migration role that owns the objects, and a runtime role that owns nothing, has no BYPASSRLS and is not a superuser. RLS enabled and forced on practice-owned tables. The practice context is set with a transaction-local setting on the same physical connection as every repository statement, and a missing context denies. No session-level `SET`, no open-session-in-view, no database call outside a context-bound transaction. Membership and token lookups before the context exists go through a separate restricted identity repository limited to the authenticated account, as ADR-003 describes. Test fixtures (two practices, accounts with distinct capabilities, an administrator without clinical grant) are test-only and are not the demo dataset.

## 6. Behavior checks (each one becomes a test on a real PostgreSQL through real HTTP)

| # | Check | Source |
|---|---|---|
| C1 | Authorized create gives 201 and exactly one row each in patient, patient_record, audit_event, outbox_event and idempotency_receipt, with the right practice_id | AT-038-A |
| C2 | A failure injected after the domain write rolls back everything: no patient, audit, outbox or receipt row | AT-038-A, AD-7 |
| C3 | Practitioner of practice B reads a record of practice A by UUID: 404, identical to an unknown id | AT-038-B, ADR-003 |
| C4 | Missing capability (including an administrator-only membership) gives 403 and no domain audit or outbox row for the failed attempt | AT-038-B |
| C5 | A statement on the runtime role without context returns no row or fails closed | AD-3 |
| C6 | Pool of one connection: a request that fails, then a request of another practice, never sees leaked context; context A then context B never mix | AT-038-B |
| C7 | Same key and body: one patient, same outcome; same key and another body: 409 | contract, AD-7 |
| C8 | Catalog query proves the runtime role is not the owner, has no BYPASSRLS, and that RLS is forced | AD-3 |
| C9 | Unsafe request without a valid CSRF token is refused | ADR-004 |
| C10 | Errors are RFC 9457 with code and correlationId, never a stack trace | AD-10 |
| C11 | An ArchUnit or Spring Modulith rule really fails when a boundary is crossed (shown on a deliberate violation) | UD-35 |
| C12 | Mutation checks: remove the context, then give the runtime role ownership or BYPASSRLS; C3, C5 and C6 must go red, then green again | quality rule 4 of the agent prompt |
| C13 | One Playwright smoke on the real stack: browser, React page, API, PostgreSQL | ADR-011 point 4 |

Open point to flag, not to decide: AT-038-B says no audit or outbox row is committed for the failed attempt, while BR-017 lists denials among auditable events. Follow AT-038-B literally for the failed mutation, add no separate security-event row, and report the ambiguity.

## 7. Phases and tasks

**Phase 0, plan, then wait for Aymen's go.** Deliver the plan of the agent prompt: modules, tables, roles, carrier confirmation, test list, CI jobs, risks.

**Phase 1, spike, at most 3 work sessions, then stop for review.** Tasks: (1) minimal Maven project with the verified versions and the ADR-002 fallbacks applied if needed; (2) Testcontainers PostgreSQL and the two roles; (3) the context mechanism and checks C5, C6, C8 with their mutation checks; (4) the module rule C11; (5) a one-page React app and the Playwright smoke C13; (6) the workspace decision: package manager (pnpm is a candidate, verify), `apps/*` and `packages/*`, TypeScript project references, one CI job per app, React version alignment with the Expo SDK (SDK 58, verify its React version); (7) `docs/test-artifacts/spike-10-1-report.md` with commands, real outputs, versions, decisions, fallbacks and sessions spent.

**Phase 2, the story, only after the report is approved.** Branch `feat/10.1-walking-skeleton`, PR to main. Tasks: (1) migrations; (2) identity lookup and membership resolution; (3) session resolution and CSRF; (4) context-bound transaction wrapper; (5) `createPatient` and `getPatientRecord` with audit, outbox and idempotency; (6) checks C1 to C12; (7) CI jobs with pinned action SHAs; (8) TF-3 in `tools/validate_docs.py`; (9) the verified commands in AGENTS.md "Running and verifying"; (10) implementation record and continuation note.

## 8. Review and validation before merge

Independent reviews: `codex review` on the diff and a Claude Code review, covering silent failures, edge cases and acceptance criteria, as the delivery rules require. Findings are fixed or answered before merge. **Validation by Aymen:** before merging any change with something visible, the reviewing agent starts the application locally (Docker Compose for PostgreSQL, the API and the web app), opens real screenshots, and tells Aymen what to look at and how to run it. For this story the visible part is only the smoke page and the API probes. The merge waits for Aymen's reply. Required GitHub checks stay the three existing ones; proposing a new required check is Aymen's decision.

## 9. Risks to watch

Spring Boot 4.1.x with Spring Modulith or ArchUnit and with Hibernate under pooled connections; the transaction-local context with Hikari (never a session-level setting); Spring Session tables against RLS; JDK 25 and Node 26 availability; React version difference between web and Expo; idempotency pulling more scope than the story budget (flag it, do not silently drop it); throwaway migrations leaking into main.

## 10. Source versions

Last commit that changed each file, on the branch carrying this packet (main at `469c7dc`, branch head `6c1d1f8`). Re-read the current files before coding; a summary is not a substitute.

| File | Last commit |
|---|---|
| `docs/planning-artifacts/epics/epic-10.md` | `86019eb` |
| `docs/decisions/ADR-011-delivery-order-and-milestones.md` | `d273b4d` |
| `docs/decisions/ADR-002-stack-and-tooling.md` | `86019eb` |
| `docs/decisions/ADR-003-practice-isolation.md` | `48a3acf` |
| `docs/decisions/ADR-004-authentication-sessions.md` | `5ba958c` |
| `docs/decisions/ADR-007-delivery-and-hosting.md` | `13565c4` |
| `docs/decisions/ADR-009-api-compatibility.md` | `86019eb` |
| `docs/planning-artifacts/architecture/ARCHITECTURE-SPINE.md` | `86019eb` |
| `docs/planning-artifacts/data-model.md` | `48a3acf` |
| `docs/planning-artifacts/data-dictionary.md` | `5ba958c` |
| `docs/planning-artifacts/business-rules.md` | `5ba958c` |
| `docs/planning-artifacts/access-matrix.md` | `86019eb` |
| `docs/contracts/README.md` | `5ba958c` |
| `docs/contracts/openapi.yaml` | `5ba958c` |
| `docs/planning-artifacts/security-privacy.md` | `86019eb` |
| `docs/planning-artifacts/non-functional-requirements.md` | `86019eb` |
| `docs/test-artifacts/test-strategy.md` | `86019eb` |
| `docs/planning-artifacts/roadmap.md` | `6c1d1f8` |
| `docs/operations/delivery-rules.md` | `48a3acf` |
