# Test strategy and future evidence

Status: Planned test design. No application exists or application test has run. 2026-11-02. Requirements→rules→journeys→surfaces→contracts→stories→acceptance tests are linked in [traceability](../traceability.md). AT-001–037 in epic files are test designs, not test results.

## Risk and test layers

Highest risks: cross-practice exposure across API/files/jobs/citations/exports; accidental patient visibility; missing-data reassurance; lost human corrections; wrong appointment/identity association; concurrent booking/publication; unsafe uploads/provider instructions; silent audit/notification failure. Test these invariants in domain rules and real persistence/HTTP journeys. Do not manufacture tests that merely repeat implementation getters.

- Domain tests: every allowed/disallowed lifecycle transition, required actor/guard/effect, missing vs explicit negative, correction/source freshness and audience invariants.
- Deterministic services (UD-30 to UD-32): the brief composer (changes, contradictions and gaps; the same input gives the same output), the mechanical support checks (a labelled corpus including SC-UNSUPPORTED-DRAFT, no false PASS_MECHANICAL on negative cases) and the asOf view (property test: nothing recorded after the instant is visible, same authorization as the current view).
- PostgreSQL integration: composite ownership constraints, exclusion ranges and adjacent slots, DST timestamps, optimistic guards, RLS transaction-local context/absent context/pool reuse, nonowner/non-bypass role, worker fencing, idempotent outcomes and audit/outbox rollback atomicity. Use supported actual PostgreSQL major, not H2.
- API contract: schema/semantics, typed errors, pagination/filter/cursor scoping, current permission revocation, source projection and binary delivery. Test 401/403/404 distinction without existence leaks; malicious/oversize uploads, scan failure/range preview, lost response then retry.
- Frontend behavior: keyboard/focus/error summaries, pending/dirty/failed/read-only/long content, local draft preservation/context clearing, version conflict and partial panels. Use meaningful user interactions, not snapshots alone.
- Native app (ADR-010): component tests with React Native Testing Library; a smoke journey on an Android emulator against the real API stack with deterministic AI, email and scan edges; opened screenshots beside the approved 390×844 web designs; frame timing and any real-device claim only from a physical phone; iOS evidence only on a Mac.
- End-to-end: isolated complete Spring+React+PostgreSQL+private store test environment, actual authentication and HTTP. Stub only external provider/email/scan edges deterministically; do not replace whole API/authorization. Browser tests cover clinician, patient and administration and the negative paths below.
- Visual/PDF/email: open actual running-UI screenshots against approved Figma at equal viewport; inspect all A4 pages and grayscale, font/accents/units/table headers/source references/text extraction/reading order. Functional and visual reports separate.
- Real-model evaluation: opt-in corpus/protocol in AI spec, measured quality/latency/cost, never part of mandatory deterministic CI or claimed measured from fixtures.

## Mandatory acceptance scenarios

1. PRA-A clinician gets R-A-001 but cannot read PRA-B record/source/task/file/export by ID; administration-only Mila cannot obtain clinical data; aggregate counts/search also scoped.
2. Generated DRAFT, IN_REVIEW and VALIDATED internal versions are invisible to Claire until explicit eligible PUBLISHED grant; clinician brief never publishable. Portal citation returns only approved excerpt/attachment.
3. Correction is preserved through regeneration, source changes mark STALE, late timeout/cancelled lease cannot overwrite. Missing treatments/allergy contradictions never become none/normal/low risk.
4. Two concurrent booking attempts yield one committed booking, one409; rescheduling failure retains old appointment; availability race serialized; adjacent intervals allowed; appointment is not consultation.
5. Concurrent draft edit yields412 with recoverable local text, missing If-Match428; replacement atomic; withdrawal denies future download, old downloaded copy limitation explicit.
6. Invitation expiry/revoke/reuse/recipient mismatch and recovery enumeration; membership revocation mid-session/worker rechecks; patient path/body mismatch denied.
7. Upload quarantine/failed scan/type spoof/pathname/oversize and malicious document instructions; no source fact ingestion. Patient export cannot bypass grant with task/export identifier.
8. Audit/outbox failure rolls back required state; email retry not duplicate business effect. API/provider unavailability and reset/version boundary show honest status without data loss or fake success.
9. Multi-client (UD-12): cookie and bearer transports never mix and a bearer request carrying a cookie or browser origin headers is refused; the CORS allowlist rejects unknown browser origins; revocation, suspension and password reset end native sessions on the next request; the service worker and the app sandbox, including a shared PDF copy, hold no clinical body; push payloads carry no clinical text and are re-authorized at delivery; a client below the minimum version gets the update response; the generated client and the contract cannot drift.
10. Deterministic and mechanical services: the composer output is reproducible and every statement is cited; a statement whose value is absent from its excerpt, whose source is superseded or whose negation disagrees is flagged and cannot be validated until corrected or dispositioned; the asOf view of 20 October 2026 shows 86,4 kg, the current view 68,4 kg, and neither exposes a later revision to a user without access.

## Evidence and quality gates

Future reports identify commit, environment, tool versions, fixture version/hash, exact command, suite/test counts, assertions/results, skip rationale, screenshots/traces and limitations. CI fails if required suites/reports do not execute; a zero-test successful process is failure. Import real JaCoCo XML/LCOV into distinct Sonar projects and wait for exact gate result. Coverage is supplementary, not proof of acceptance. Risk-based performance/load/memory/cold-start/restore drills validate NFR targets separately.

Three review perspectives persist: silent failures (especially partial/after-commit/async paths), edge cases (concurrency/expiry/missing/long content), acceptance criteria (exact trace and observed behavior). Independent reviewers were attempted during planning but authoring agents hit account usage limits; final documentary review may proceed sequentially and must state that limitation. No independent code review or application assurance is claimed now.

## Current documentary checks

Validate JSON/YAML/OpenAPI structure and local refs, Markdown links, stable FR/BR/J/S/I/P/M/story/AT coverage, lifecycle terms, synthetic dataset relationships/dates and forbidden-secret/proprietary-copy patterns. Record exact outputs in review/validation reports. These checks validate this package, not the future app.
