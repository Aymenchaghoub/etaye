# Story 3.1 implementation record — Find and register a local patient

Status: Implemented on `feat/3.1-find-register-local-patient` (draft PR #59); the end-of-story review and the CodeRabbit review are triaged and fixed; Aymen approved the running UI and authorized the merge of PR #59 on 2026-11-24 (UD-66). Authorized under UD-65 (scope UD-63, design UD-64). Baseline `origin/main` `02a8c6d`. [Context packet](story-3-1-context-packet.md) · [Design review](../design/reviews/2026-11-23-story-3-1-design-review.md) · [Decision register](../planning-artifacts/decisions-assumptions.md)

## Sequence

| Commit | Content |
|---|---|
| `5ce7ebb` | Phase-0 packet, arbitration, design review with 26 specimens, UD-63 to UD-65, allowlist and scoped validator mode (121 tooling tests, zero documentary errors). |
| `111d87d` | Contract first: search rules, opaque cursor, duplicate rule, typed `candidates` on the 409, `PatientRegistration` with `confirmedDistinctRecordIds`, regenerated SDK (drift check and `tsc -b` pass). |
| `75c7739` | V8 migration, search, registration, correction and scheduling operations, 19 tests. |
| `f88d593` | Control characters and cursor length refused before any statement; concurrent-correction test. |
| `9c20554` | Local-package opt-in for demo patient input; required suites and minimum in the API workflow. |
| `6e22d1c`, `2d5ea31`, `6b6e005` | Web: practitioner shell, S-07, S-08, tests, merged from the Web worktree. |
| `5d31b2c` | Real-stack specs made runnable on the demonstration package. |
| `9b8da2b` | Reviewed revision: running-UI comparison and record. |
| `efe56ad`, `891227a`, `299abab`, `959e77c` | End-of-story review fixes: API and SQL, then Web, with their tests. |

## API and data

- **V8.** One database normalization, `patient_search_key` (lowercase, diacritics removed through an explicit `translate` map for Latin letters, `œ`, `æ` and `ß` expanded, every other non-alphanumeric a separator), declared `IMMUTABLE` for the generated stored columns `first_name_key` and `last_name_key`. Queries pass their words through the same function, so stored keys and query words cannot drift. Indexes serve the keyset order and the birth-date match. The runtime role receives `UPDATE` on the identity columns and `version` and `updated_at` only: never `practice_id`, `id`, `created_at` or the generated keys. The privilege catalog test now states that narrow grant. The `unaccent` extension was not needed. The function is immutable by declaration; it depends on the database `ctype` for the alphanumeric test, which is stable inside one cluster.
- **Search.** `searchPatients` and `searchSchedulingPatients` share one service: tokens are normalized in the database, a `JJ/MM/AAAA` or `AAAA-MM-JJ` token that is a real date matches that birth date, two different dates or a query with no letter, digit or date match nothing, every other token must match the start of a name word. The page order is last-name key, first-name key, id; the cursor encodes the position and a SHA-256 binding to practice, actor and the normalized query. The query never reaches a log, an audit row or an outbox row (a canary test captures the application output). An empty practice and a query without match return the identical 200 page.
- **Registration.** The duplicate rule, a deterministic SQL predicate over the generated keys, runs after the idempotency receipt check inside the same practice transaction. A refusal throws before any write, so it leaves no patient, audit, outbox or receipt row (asserted). `confirmedDistinctRecordIds` must cover every current candidate; the server re-runs the rule at commit. The confirmation joins the canonical body hash only when present, so earlier receipts keep their hash and a confirmed retry needs a new key.
- **Correction.** `editPatient` locks the row, compares the `If-Match` version, updates the identity columns and the version, and writes the audit and outbox rows in the same transaction. A missing header is 428, a malformed one 400, a stale one 412, a foreign or unknown patient 404. Two concurrent corrections on one version yield one 200 and one 412 (tested).
- **Scheduling.** `registerSchedulingPatient` needs both `SCHEDULE_MANAGE` and `PATIENT_REGISTER`, returns the minimal projection and audits `PATIENT_CREATED` with the channel `SCHEDULING`. The same grants still give no clinical route (tested). The screens are story 4.2.
- **Input hardening.** A control character in a query, a name or a phone number, and a cursor above 1,024 characters are 400 before PostgreSQL sees them.

## Assumptions and decisions taken during implementation

1. **[ASSUMPTION] A correction runs no duplicate check.** Written in the contract; the warning protects registration. Recorded for Aymen to confirm or reverse.
2. **[ASSUMPTION] An identity correction does not change `record_revision`.** Identity is not clinical content; nothing reads the watermark yet. The record's optimistic `version` does move with the identity it embeds, so the conditional record read never serves a stale patient (found by the end-of-story review).
3. **Demo patient input (needs Aymen's confirmation).** Story 9.1 closed `POST /patients` for the demo identities (`denyAll`). Story 3.1 cannot be shown otherwise, so `etaye.demo.patient-input-enabled` (default false) reopens `createPatient`, `editPatient` and `registerSchedulingPatient`, and only the loopback Compose package sets it. Both modes are tested. A hosted demo keeps the closed default unless it opts in.
4. **Conflict panel without an author.** The API exposes no author of the last change, so the approved conflict specimen shows the version and time of the current record but not the name of the colleague (single deviation from the specimen; the Web record below states the final wording).

## Web

- **Shell and routes.** `PractitionerShell` (`.etaye-app`, top bar and navigation on `--fond`, work area on `--surface`) with a navigation limited to "Patients", the account name and a sign-out button, and the below-760 px menu of the approved specimen. The routes are `/compte/pratiques/{grant}/patients`, `…/nouveau` and `…/{recordId}/modifier`, mounted behind the existing 1.3 gate; a staff grant may open them and a 403 gives the restricted state because `AccessContext` carries no capability list.
- **S-07 and S-08.** Submit-based search, "Afficher plus" with the cursor and a failure that keeps the loaded rows, empty practice versus no result, stale notice, created-row confirmation, the segmented birth-date field with the "31 février" validation, the duplicate panel with typed candidates and the named confirmation under a new idempotency key, saving with the width-stable button, outcome check after a lost response, failure keeping the input, version conflict, identity-only banner, the I-20 guard on the form, and the restricted state.
- **Memory only.** The query, the form draft and the idempotency attempt keys live in memory scoped to account and practice; they are cleared on practice switch, sign-out, expiry and demo reset, never in the URL, a log or persistent storage (asserted by the smoke suite).
- **Deviations from the approved specimens**, all disclosed in the [running review](../design/reviews/2026-11-23-story-3-1-runtime.md): no author name in the conflict panel (the API has none), one "Ouvrir ce dossier" link per candidate when there are several, an "Enregistré" state for 600 ms and an "Identité enregistrée" notice after a save, and the existing 1.3 role label. The edit URL carries the record identifier because only `getPatientRecord` loads a patient; the PATCH goes to the patient identifier with `If-Match` set to the patient version.
- **Friction.** The 1.3 session re-check remounts the gate on every route change, so each navigation briefly shows "Vérification de l'accès…"; the hide-on-tab-hide behavior unmounts the view, so the form keeps an in-memory draft.

## Verification so far

`./mvnw -B -ntp verify` on Java 25 and PostgreSQL 18.7 Testcontainers: 198 tests, 0 failures, 0 skipped (the CI minimum was 176; 22 tests are new: 20 in `PatientSearchAndRegistrationHttpTest`, the V7-to-V8 upgrade test and the opted-in demo test). `tools/assert_test_reports.py` passes with the three new required suites and a minimum of 198. The V8 migration was also applied by the real Compose package on the seeded demo database, and, signed in as Nora on that stack, the live search answered as expected (listing, word start, birth date, a name of the other practice returning nothing) while the create call was refused with 403 by the story 9.1 closure, which is why the demo opt-in exists.
**Web (Node 26.13.1, pnpm 12.21.0).** `tsc -b` exit 0; `build` including the SDK drift check; Vitest 38 tests (25 new); stubbed Chromium smoke 110 passed, 29 of them new, with an axe-core scan on every state and keyboard, focus, shell-background, overflow and idempotency-key assertions; one of the 29 failed once on an earlier run of the Web agent and passed on every rerun, including mine, and was not diagnosed. **Real stack:** `e2e/patients.spec.ts`, 5 specs, 5 passed in one clean run against the rebuilt Compose package (the earlier failures were test races and the per-address sign-in limit, fixed in `5d31b2c`; I did not repeat the run twice in a row because of that limit). **Visual:** 24 comparison sheets of six screens, two widths and two themes against the specimens, seven opened; see the [running review](../design/reviews/2026-11-23-story-3-1-runtime.md) for the observed differences and what remains unverified.

## End-of-story review (single Codex CLI run, read-only, 2026-11-23)

One run of `codex exec --sandbox read-only` on `9b8da2b`, three lenses, as the delivery rules require. The reviewer could not start Docker or open loopback, so it ran no suite; it opened the 24 comparison sheets and read the code and the Surefire report. Every finding was verified against the code before any change; none was rejected. The fixes are `efe56ad` (API, SQL, proxy, contract), `891227a` and `299abab` (Web) merged in `959e77c`; no second review was run.

| Lens | Finding (severity) | Disposition |
|---|---|---|
| Silent failures | A restored correction draft overwrites a colleague's newer change (P1) | Confirmed. The draft carries its base version; a different saved version opens the conflict panel with the input kept. |
| | A lost PATCH response is shown as a success of someone else's edit (P1) | Confirmed. Success only when the saved identity equals what was sent. |
| | An unresolved registration loses its idempotency key, and a confirmed retry drops the confirmation (P1) | Confirmed. Key and confirmation live in the scoped in-memory draft; a changed body rotates the key. |
| | Cached rows can show after the read grant is removed (P1) | Confirmed in part. Rows are no longer cached, only the query; the periodic check stays the 1.3 gate. |
| | The demo proxy logs the query string (P1) | Confirmed. The access log keeps method and path and the error log keeps critical errors only, because nginx quotes the raw request line, query included, when an upstream call fails. A canary search left no trace in the proxy or API logs, and none with the API stopped (a 502). Trade-off: the demo proxy no longer logs failed upstream calls; the API container's own log carries those diagnostics. CodeRabbit raised the residual error-log path and this is the answer. |
| | The conditional record read serves a stale patient after a correction (P2) | Confirmed. The correction moves the record version (minimal `UPDATE (version, updated_at)` grant); tested. |
| | The cursor holds names and an unauthenticated position (P2) | Confirmed. It holds the last row id and a context binding only; a forged foreign id is 400; tested. |
| | Rollback of a correction on audit or outbox failure unconfirmed (P3) | Test added for both faults: nothing changes, no event row remains. |
| Edge cases | Two concurrent registrations can both miss the warning (P1) | Confirmed. Identity-level transaction locks serialize the check and the insert; two sessions yield one 201 and one 409. |
| | An eleventh match goes unreviewed (P2) | Confirmed. More than ten matches keep the registration refused. |
| | Decomposed accents, non-Latin names and empty keys (P2) | Confirmed. Canonical composition in Java, combining marks folded and a stored-name fallback in SQL; tests include a Cyrillic name. |
| | Paging interrupts a search and repeats a renamed patient (P2) | Confirmed. "Afficher plus" waits for a ready list and rows are de-duplicated. |
| | Back and session expiry bypass the dirty-form decision (P2) | Confirmed. Back is guarded; a draft survives expiry and reconnection of the same account; other changes clear it. |
| | Focus enters content behind the mobile menu (P2) | Confirmed. The main content is `inert` while the menu is open; keyboard test added. |
| | Input boundaries differ from the contract (P3) | Fixed: strict quoted `If-Match`, cursor checked before the empty shortcut, null and repeated confirmations are 400. The 1900 lower bound of the form versus the open API stays and is disclosed. |
| Acceptance criteria | AT-009-B versus the concurrency and ten-candidate exceptions (P1) | Resolved by the two fixes above. |
| | The created row may not be visible on return to S-07 (P2) | Confirmed. A creation outside the first page is pinned first with "Dossier créé" (disclosed). |
| | The S-07 stale notice cannot render (P2) | Confirmed. It now shows only when paging repeats a row (disclosed). |
| | "Réessayer" retries the wrong query (P2) | Confirmed and fixed. |
| | Scheduling foreign-practice refusal not directly tested (P2) | Test added for both routes and an unknown practice, with zero writes. |
| | "Annuler" disabled while saving, unlike the specimens (P3) | Kept and disclosed. |

After the fixes: API 206 tests, Vitest 40, stubbed smoke 133, real-stack specs 5 of 5 on a fresh-volume package, the CI assertion passes with the new required suites and a minimum of 206.

## CodeRabbit review (manual, 2026-11-23)

The PR exceeded CodeRabbit's 100-file limit (130 files), so `.coderabbit.yaml` now excludes the static specimens, the comparison sheets and the generated client and the review covered the remaining 75 files. It left ten comments; each was verified against the code before any change, and all ten were confirmed. The CodeQL annotations from the first run (links built from the address bar, an overflow check on the page size, number parsing, an unused parameter) were fixed earlier in `74c77f0` and the check now passes.

| Comment | Disposition |
|---|---|
| A foreign cursor under an unmatched query answers 200 (API, minor) | Confirmed. The position is resolved before the empty-page shortcut; the test forges the exact case. |
| The registration race test may not overlap the checks (API test) | Confirmed. Replaced by a test that slows the duplicate lookup; it fails when the identity locks are disabled (verified by temporarily removing them). |
| Reconnection clears the patient draft because the server answers `/compte` for a patient path (major) | Confirmed. The client restores the paused patient page and keeps the draft for the same account and an active grant; the stub now applies the real server rule, and the earlier expiry test had passed only through its unfaithful stub. |
| A saved correction rewrites its draft and can show a false conflict | Confirmed. A done flag stops the draft write and the guard after a successful save. |
| The "Dossier créé" pin appears inside a filtered search | Confirmed. The pin only shows in the unfiltered directory. |
| Navigation requested during a save is dropped and the page is blank | Confirmed. The confirmation shows, then the requested navigation completes. |
| The keyboard-order test can pass with a missing stop | Confirmed. Every required control must appear in the order first. |
| Design review still lists the development authorization as pending | Confirmed. Marked as the state at UD-64 with a link to UD-65. |
| UD-63 says the design approval follows the authorization | Confirmed. Reordered: UD-64 came before UD-65. |
| The proxy error log can still quote the query on an upstream failure | Confirmed. The error log keeps critical errors only; a canary search with the API stopped left no trace; the diagnostic trade-off is recorded above. |

After the fixes: API 206 tests, Vitest 40, stubbed smoke 133, real-stack specs 5 of 5; all required checks passed on the previous head and are re-run on the new one.
