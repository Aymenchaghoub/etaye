# Story 3.1 implementation record — Find and register a local patient

Status: In progress on `feat/3.1-find-register-local-patient`, authorized under UD-65 (scope UD-63, design UD-64). Baseline `origin/main` `02a8c6d`. [Context packet](story-3-1-context-packet.md) · [Design review](../design/reviews/2026-11-23-story-3-1-design-review.md) · [Decision register](../planning-artifacts/decisions-assumptions.md)

## Sequence

| Commit | Content |
|---|---|
| `5ce7ebb` | Phase-0 packet, arbitration, design review with 26 specimens, UD-63 to UD-65, allowlist and scoped validator mode (121 tooling tests, zero documentary errors). |
| `111d87d` | Contract first: search rules, opaque cursor, duplicate rule, typed `candidates` on the 409, `PatientRegistration` with `confirmedDistinctRecordIds`, regenerated SDK (drift check and `tsc -b` pass). |
| `75c7739` | V8 migration, search, registration, correction and scheduling operations, 19 tests. |
| `f88d593` | Control characters and cursor length refused before any statement; concurrent-correction test. |
| `9c20554` | Local-package opt-in for demo patient input; required suites and minimum in the API workflow. |

## API and data

- **V8.** One database normalization, `patient_search_key` (lowercase, diacritics removed through an explicit `translate` map for Latin letters, `œ`, `æ` and `ß` expanded, every other non-alphanumeric a separator), declared `IMMUTABLE` for the generated stored columns `first_name_key` and `last_name_key`. Queries pass their words through the same function, so stored keys and query words cannot drift. Indexes serve the keyset order and the birth-date match. The runtime role receives `UPDATE` on the identity columns and `version` and `updated_at` only: never `practice_id`, `id`, `created_at` or the generated keys. The privilege catalog test now states that narrow grant. The `unaccent` extension was not needed. The function is immutable by declaration; it depends on the database `ctype` for the alphanumeric test, which is stable inside one cluster.
- **Search.** `searchPatients` and `searchSchedulingPatients` share one service: tokens are normalized in the database, a `JJ/MM/AAAA` or `AAAA-MM-JJ` token that is a real date matches that birth date, two different dates or a query with no letter, digit or date match nothing, every other token must match the start of a name word. The page order is last-name key, first-name key, id; the cursor encodes the position and a SHA-256 binding to practice, actor and the normalized query. The query never reaches a log, an audit row or an outbox row (a canary test captures the application output). An empty practice and a query without match return the identical 200 page.
- **Registration.** The duplicate rule, a deterministic SQL predicate over the generated keys, runs after the idempotency receipt check inside the same practice transaction. A refusal throws before any write, so it leaves no patient, audit, outbox or receipt row (asserted). `confirmedDistinctRecordIds` must cover every current candidate; the server re-runs the rule at commit. The confirmation joins the canonical body hash only when present, so earlier receipts keep their hash and a confirmed retry needs a new key.
- **Correction.** `editPatient` locks the row, compares the `If-Match` version, updates the identity columns and the version, and writes the audit and outbox rows in the same transaction. A missing header is 428, a malformed one 400, a stale one 412, a foreign or unknown patient 404. Two concurrent corrections on one version yield one 200 and one 412 (tested).
- **Scheduling.** `registerSchedulingPatient` needs both `SCHEDULE_MANAGE` and `PATIENT_REGISTER`, returns the minimal projection and audits `PATIENT_CREATED` with the channel `SCHEDULING`. The same grants still give no clinical route (tested). The screens are story 4.2.
- **Input hardening.** A control character in a query, a name or a phone number, and a cursor above 1,024 characters are 400 before PostgreSQL sees them.

## Assumptions and decisions taken during implementation

1. **[ASSUMPTION] A correction runs no duplicate check.** Written in the contract; the warning protects registration. Recorded for Aymen to confirm or reverse.
2. **[ASSUMPTION] An identity correction does not change `record_revision`.** Identity is not clinical content; nothing reads the watermark yet.
3. **Demo patient input (needs Aymen's confirmation).** Story 9.1 closed `POST /patients` for the demo identities (`denyAll`). Story 3.1 cannot be shown otherwise, so `etaye.demo.patient-input-enabled` (default false) reopens `createPatient`, `editPatient` and `registerSchedulingPatient`, and only the loopback Compose package sets it. Both modes are tested. A hosted demo keeps the closed default unless it opts in.
4. **Conflict panel without an author.** The API exposes no author of the last change, so the approved conflict specimen shows the version and time of the current record but not the name of the colleague (single deviation from the specimen; the Web record below states the final wording).

## Verification so far

`./mvnw -B -ntp verify` on Java 25 and PostgreSQL 18.7 Testcontainers: 198 tests, 0 failures, 0 skipped (the CI minimum was 176; 22 tests are new: 20 in `PatientSearchAndRegistrationHttpTest`, the V7-to-V8 upgrade test and the opted-in demo test). `tools/assert_test_reports.py` passes with the three new required suites and a minimum of 198. The V8 migration was also applied by the real Compose package on the seeded demo database, and, signed in as Nora on that stack, the live search answered as expected (listing, word start, birth date, a name of the other practice returning nothing) while the create call was refused with 403 by the story 9.1 closure, which is why the demo opt-in exists. The Web, browser and visual evidence sections follow when those runs complete.
