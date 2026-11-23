# Story 1.4 implementation record — Demonstrate server-side resource isolation

Status: Implemented on `feat/1.4-demonstrate-server-side-resource-isolation`, started from `origin/main` at `f01288e94c0f23b2e8581fba48b82de69dc081bd` on 2026-11-23. Running-UI approval and merge are pending with Aymen. Authorization: UD-61 (story 1.4 only, global flag false). Scope, design approvals and arbitration are in the [context packet](story-1-4-context-packet.md); this record carries the implementation decisions and the executed evidence. Nothing here claims clinical validation, certification or readiness for real patients.

## Contract review (first commit, before application code)

**Verified observation.** At `f01288e` the OpenAPI document is `0.5.0-planning` with 99 operations. `getPatientRecord` already declares `If-None-Match`, `200`, `304` (with `ETag`, no body), `400`, `401`, `403`, `404`, `406`, `429` and `503`, and the generated SDK exposes it (`apps/web/src/api/generated/sdk.gen.ts`). The observed server behavior of the carrier read matches the AT-004-B cases the packet proposes:

| Condition | Observed answer | Source |
|---|---|---|
| No, expired or revoked session | `401` | `IdentityResolutionFilter`, `SecurityConfig` |
| Membership in the route practice without `CLINICAL_READ`, or suspended membership | `403 FORBIDDEN` | `PracticeSecurityService.requireCapability` |
| No membership in the route practice (including unknown practice and a portal-only account), absent record, record of another practice | `404 NOT_FOUND`, identical body modulo `correlationId` | `PracticeSecurityService`, `PatientService.getPatientRecord`, `GlobalExceptionHandler` |
| Non-UUID identifier | `400 INVALID_PARAMETER` | `GlobalExceptionHandler` |

**Decision.** No transport change is needed for the record-only proof, so `openapi.yaml`, `operation-index.json` and the generated SDK stay untouched and no SDK drift check is affected. The contract guide gains a short "Carrier read refusals" paragraph that states the existence-hiding policy AT-004-B refers to (documentation follows code, ADR-011). The packet's `[ASSUMPTION]` is confirmed: the guided demo entry stays in the existing public showcase and needs no API payload, scenario value or public projection. The synthetic record and practice identifiers the guided read uses are the deterministic fixture identifiers (`md5('etaye-design-v1/' || alias)::uuid`); the browser guards them with the `demoDatasetVersion` it already reads and with the current session contexts, and never renders, stores or puts them in a URL.

**Still verified at the source.** The three guarded resource groups the epic defers (sources, tasks, exports) have no HTTP route yet (`openapi.yaml` lists them as planned operations), so the matrix reports them as pending under D-55 and a router 404 is not evidence.

## Delivered

The hashes below are those of the pushed branch: the local commits were rewritten once before the first push to remove the `Co-Authored-By` trailers that `AGENTS.md` forbids. The tree of the head is identical before and after, so every result in this record applies to it.

| Commit | Content |
|---|---|
| `30c026c` | Contract review: no transport change, carrier read refusals documented in the contract guide. |
| `57732bd` | API: the measured isolation matrix, its report, the CI gate and artifact. Test-only; no production Java changed. |
| `5517805` | Web: guided entry, signed-in journey, I-22 states, Playwright and unit suites, real-stack replay. |
| `dd73073` | Web: fidelity corrections found by the opened running-UI comparison and the focus-ring regression check. |
| `bedddc7` | Fixes for the local review findings, the neutral shell box-sizing and the scoped validator path. |

**API.** `ResourceIsolationMatrixHttpTest` signs in the seeded fictional actors through the real session operation on the `etaye_demo` fixture and exercises `getPatientRecord` over HTTP. With `HttpConnectionReuseIsolationTest` (read refusals on one reused pooled connection) and `CarrierRouteOutageTest` (503 and recovery) it records 24 mandatory cases through `IsolationMatrix`, which writes `target/isolation-matrix/isolation-matrix.json` and `.md` when the run ends: case label, expected and observed outcome, test identity, commit, fixture version, a fingerprint of each refusal (status, media type, caching directive, validator presence and the problem body without its correlation identifier) and the three pending rows. `matrix-cases.json` is the single list of mandatory cases, their expected outcome, the parity group of refusals that must be indistinguishable and the pending rows. `tools/assert_isolation_matrix.py` is the CI gate. `DemoDatabase` was extracted from `DemoHttpTest` so that both suites share the one provisioned demo database of the JVM.

| Group | Cases (expected outcome) |
|---|---|
| Record | A allowed `200`; B control `200`; foreign id, foreign practice, absent id and unknown practice each `404 NOT_FOUND` with one fingerprint |
| Administrator | `403 FORBIDDEN`; one `ACCESS_DENIED` event in practice A without a target identifier |
| Patient | own record and another record through the staff route `404`, same fingerprint; portal context kept (`200`) |
| Current permission | no session and expired session `401`; suspended membership, removed capability `403`; suspended account `401`; conditional authorized `304`; conditional with a revoked grant `403` and no validator; conditional for a hidden record `404` |
| Context, input, transaction | two memberships stay independent (`200, 200, 403, 200`); non-UUID id `400`; reused connection after refusals; `503` then recovery |

**Web.** `/compte/isolation/lecture` and `/compte/isolation/hors-cabinet` are client routes inside the existing account gate: they run the real `getPatientRecord` through the generated SDK for the current account and show the S-36 state that matches the answer. `classify` accepts a success only for the authorized step and the expected fictional record. A different success is a failed verification with nothing rendered. The target identifiers are fictional fixture constants in `isolation-journey.ts`, guarded by the `demoDatasetVersion` the gate already reads and by the account's own contexts; they are never rendered, stored or put in a URL. The journey starts from the existing sign-in with `?retour=` (a client route; the server keeps ignoring it) and continues with Mila and Claire through the same link. The I-22 expiry, recheck and unavailable-return states reuse the 1.2 pause/restore logic for these two steps. `ContextFrame` was extracted from `PracticeDestination` without changing its markup.

## Decisions and assumptions

- **Verified observation.** The packet's `[ASSUMPTION]` held: no API payload, scenario value or operation was needed.
- **Proposal (implemented, needs Aymen's visual OK).** "Mila" and "Claire" are links in step 3 of the guide so that the journey can continue without hidden state; the approved specimen shows plain text there. The alternative was a persistent "journey in progress" marker, rejected because it changes later sign-ins silently.
- **Proposal.** The role label is the shared "Praticien" (1.3), where the specimen reads "Praticienne".
- **Proposal.** The long specimen wording is shown on the second consecutive failure; the first failure shows the short wording.
- **Proposal.** An unknown address under `/compte/isolation/` shows the same generic refusal as a hidden record and sends no request, which implements the specimen "old link".
- **Limit found.** After a first successful sign-in the 9.1 demo marker keeps no account hint, so the reconnection form is empty; unchanged here.

## Verification executed (2026-11-23, Node 26.13.1, OpenJDK 25.0.4 (Red Hat build), Docker 29.8)

| Check | Result |
|---|---|
| `cd apps/api && ./mvnw -B -ntp verify` | 176 tests green, including the 9 matrix tests, `DemoHttpTest` after the extraction and the outage suite. `assert_test_reports.py surefire --min-tests 176` and `assert_isolation_matrix.py --require-commit` passed: 24 cases, 3 pending rows, one fingerprint across the 7 hidden-record cases. |
| Disposable mutations of `PracticeSecurityService` (reverted by `git checkout`) | Ignoring the capability check failed `administratorIsRefusedWithoutDisclosure` and `removedCapabilityIsRefusedImmediately`; ignoring a missing membership failed `hiddenTargetsAreIndistinguishable` (`REC-HIDDEN-FOREIGN-PRACTICE`) and `linkedPatientCannotUseTheStaffRoute`. |
| `python3 -m unittest discover -s tools -p 'test_*.py'` | All tooling tests green, 13 of them for the matrix gate (zero cases, missing, failed and skipped cases, duplicate and unlisted cases, diverging refusals, identifiers, request material, commit, fixture and parity drift, pending rows, malformed input). |
| `corepack pnpm exec tsc -b`, `pnpm build`, `pnpm exec vitest run` | Types, SDK drift check and production build passed; 13 unit tests. |
| `pnpm test:smoke --workers=1` (stubbed API) | 81 Chromium tests, 0 skipped, 21 of them for the journey: both themes, six widths including the 200 % zoom equivalents, axe-core at 390 and 1440, keyboard order, focus on the destination heading, no ring without keyboard, hidden previous result while waiting, failure and retry, unexpected success never rendered, long names at 320 px, expiry, recheck, another account, a cookie changed during the read, a capability withdrawn at the periodic check, a reconnecting patient, a revoked grant. Run serially: a parallel run beside the API suite timed out on unrelated tests, as recorded for 9.1. |
| Real stack: `COMPOSE_PROJECT_NAME=etaye-demo-14 ETAYE_DEMO_WEB_PORT=4194 ./infra/demo/start.sh`, then `ETAYE_E2E_DEMO_URL=http://localhost:4194 corepack pnpm exec playwright test --config=playwright.demo.config.ts` | The replay passed: Nora `200`, her request for the foreign record `404` with a body identical to an absent record, Mila `403`, Claire `404`, every case label equal to its matrix case; an expiry returned to the same step after reconnection. The 9.1 journey passed again. |
| Running-UI comparisons | [Review record](../design/reviews/2026-11-23-story-1-4-runtime.md): 16 sheets opened (15 comparisons and the keyboard focus sheet). |

**Not run or not proven.** The real PostgreSQL outage spec (`test:e2e-real`) was not rerun: no probe code changed. HawkScan was not run because no `HAWK_API_KEY` is configured. Only Chromium is installed (no Firefox or WebKit). The sign-in budget of the package (12 anti-forgery reads per minute per address) throttles back-to-back browser runs, so the two scripted suites were run one minute apart. The published CI artifact can only be observed on the pull request run.

## Local review (Codex CLI, once, read-only)

Run on revision `dd73073` with `codex exec --sandbox read-only --skip-git-repo-check -C "$PWD"`, the story, the diff base `f01288e` and the evidence summary, asking for one three-section report. The first launch blocked on standard input and produced no review (stopped after minutes without output); it was relaunched once with standard input closed, so one review ran. The report listed three major silent failures, one major and two minor edge cases, and partial compliance points. Each finding was checked against the code. Fixes are in `bedddc7`.

| Finding | Verdict | Action |
|---|---|---|
| A withdrawn `CLINICAL_READ` leaves the record on screen (the read only reran when step, audience, grant or attempt changed) | Confirmed from the effect dependencies | The journey re-reads whenever the gate verifies the session again. A test that moves the clock past the 20 s check fails without the change (mutation checked) and passes with it. |
| A result can be shown under another account if the cookie changes between the gate check and the read | Confirmed by reading; the interleaving was not reproduced end to end | After every read the journey reads the session again, refuses the answer when the account differs and shows the unavailable return; a test returns another account on that confirmation read. |
| CI can upload a report holding a raw problem code that failed the privacy check | Confirmed | Observed outcomes are limited to stable codes and UUIDs are masked before they reach the report. The upload keeps running after a failure so a failing matrix stays diagnosable. |
| The 9.1 and 1.4 journeys together need 14 anti-forgery reads in a minute (limit 12) | Confirmed, as the sign-in "Trop de tentatives" seen earlier | Documented in the demo README; the package limiter is product behavior and stays. |
| A patient reconnecting to a step gets the professional sign-in | Confirmed | The reconnection keeps the patient sign-in; a test covers Claire. |
| The report validator accepts any commit, another fixture version, a missing parity block or altered pending rows | Confirmed on an in-memory report | The gate compares the commit (`--expect-commit "$GITHUB_SHA"`), the fixture version named by the manifest, the parity groups and every pending row; unit tests cover each drift. |
| The real replay's record B control only compares two `404` | Confirmed | The replay reads record B as the practitioner of practice B and expects `200`. |
| The denial audit case selects the actor without asserting it and ignores operation and reason; a `Location` leak would pass parity | Confirmed | The case asserts the actor, the reason, the capability and the operation template; the fingerprint covers every validator, location, cookie, retry and disposition header. |
| 200 % browser zoom not verified | Accepted as a limit | The stubbed suite adds the equivalent CSS viewports 720×512 and 512×384 and the 320 px reflow; browser zoom itself is not exercised. |
| The sources pending row omits `readPublishedSources` | Confirmed against `operation-index.json` | Added. |
| Guide links, role label and I-22 shell geometry differ from the specimens; the CI artifact is unverified | Already recorded as open decisions and limits | The artifact can only be observed on the pull request run. |

While preparing these captures the neutral auth shell was found to overflow the viewport by its padding (`min-height: 100dvh` plus padding on a content-box element), which pushed its fictional-data footer below the fold on tall screens; it is now border-box (`bedddc7`).
