# Story 3.4 implementation record — Read laboratory results

**State.** Increment 1 implementation and review. The baseline is `4b3ee192ce298bdf398376585f6a745f5f847da6`. Scope, S-12 design and story authorization are recorded in UD-78, UD-79 and UD-80. UD-81 selects the review tool. UD-82 records Aymen's running-UI approval and authorization to merge PR #105. [Story and AT-012](../planning-artifacts/epics/epic-3.md#story-34-enter-and-correct-laboratory-results) · [Context packet](story-3-4-context-packet.md) · [Running-UI and merge decisions](../planning-artifacts/decisions-assumptions.md#story-34-running-interface-and-merge-decisions--2026-11-28)

## Implementation boundary

- The contract was committed first at `55d40dc95873e2f877ad2160d6d1d398d5ec061b` and refined for explicitly supplied point-comparison context at `c04b31e`. It defines three no-store, grant-scoped reads for the list, bounded lineage and independent exact analyte options. Results, dependent reads and page cursors share the view instant. The generated SDK is checked for drift. [API contract notes](../contracts/README.md)
- V13 adds immutable lab revisions with record-local source and predecessor references, forced RLS and runtime `SELECT` only. Live membership and clinical capability are verified at the route. V14 introduces synthetic seeded results and integrates them with local reset and the managed-demo database bootstrap. The managed bootstrap upgrade was exercised on a non-superuser PostgreSQL test instance. [Data dictionary](../planning-artifacts/data-dictionary.md)
- The Biologie tab preserves the shared context shell, patient banner, historical control and four record tabs. It shows both supplied date fields, comparator, decimal scale, unit, interval and text. Sources disclose in place with keyboard focus return. Corrections expose the earlier revision and its evidence. The chart requires explicitly comparable numeric points and retains the table.
- Claire has the three canonical results. SRC-A-003 revision 2 adds the two supplied intervals to the exact supporting synthetic excerpt. Marie carries the read-side proof of correction, comparator, qualitative text and comparable points. Louis has an empty laboratory panel. D-24 retains I-07 and both write operations, including stale-write and missing-precondition tests. The protected document viewer belongs to stories 5.3/3.5. [Deferred register](../planning-artifacts/deferred-work.md)

## Local verification

| Check | Executed evidence |
| --- | --- |
| API on Java 25 and PostgreSQL Testcontainers | `./mvnw -B -ntp clean verify` passed 306 tests, zero failures or skips, with the JaCoCo bundle floors at `e3c9c0b`. This includes 8 lab HTTP and database cases, reset, managed V11→V13 bootstrap, RLS, runtime read-only, restricted access and anchor-scoped lineage. The changed-line gate passed 173/177 (97.7%). |
| Web types, build and units | `corepack pnpm verify` and `corepack pnpm build` passed for the application changes at `c121abc`. Vitest ran 170 tests. The changed-line gate passed 46/46 measured TypeScript lines. |
| S-12 Chromium with fault injection | 19 tests passed at `c121abc`, with axe-core on the exercised S-12 states. The additional cases cover non-comparable units, exact large decimals, and the 1,000-character correction reason with 500-character source text. These include loading and recovery of results, source, history and later pages, comparator, interval absence, source-stated notices, date ranges, invalid historical dates, keyboard focus and long-content reflow at 320, 390, 768 and 1440 CSS px. |
| Integrated loopback package | `tools/run_package_smoke.sh` executed 14 tests on the local package (3 record, 3 measures, 3 laboratory and 5 patients). The three laboratory cases read Claire, Louis and Marie with a real session and verify both sides of the correction and the restricted and hidden states. Security headers passed on five routes and a foreign Host rejection. Application revision `c121abc`. The three laboratory cases also passed after the added axe checks for filtered, empty, historical and hidden views. |
| Browser / database outage | The existing real `e2e/outage.spec.ts` passed against local package `etaye-demo-story34`. A temporary loopback proxy mapped only `/` to the compiled React probe entry; API calls used the package proxy. The test stopped, restarted, paused and unpaused the isolated database and verified both API and browser recovery. |
| Document, token and workflow validators | `tools/validate_docs.py`, `tools/design_system_reference.py --check` and `tools/check_workflow_policy.py` passed on `2c69bd3`. These checks are documentary or configuration evidence. |

### Acceptance evidence

| Criterion | Evidence |
| --- | --- |
| AT-012-A | HTTP and real-browser assertions read seeded numbers and qualitative text, including `< 0,060`, exact source versions and supplied intervals. |
| AT-012-B | HTTP and browser coverage show explicit absence and immutable correction lineage at 10:14 and 10:15 practice time, current-grant checks, record and revision existence hiding, and unavailable source recovery. Source-backed reading has no PDF extraction or lab write entry. The write-side scenarios resume with D-24. |
| AT-012-C | Stubbed browser assertions and axe scans exercise UI states independently of the real browser tests. The captured risk sample is kept separate from the functional reports. The application uses the approved shell, tokens and offline visual references under the named exception. |

## Opened running comparisons

The local [review page](../design/reviews/2026-11-27-story-3-4-runtime.html) presents the matched specimen and running capture for each state. The evidence directory is the local sibling `../etaye-design-receipts/2026-11-27-story-3-4-runtime/`. Each pair is named `<state>-<width>-<theme>-{specimen,application}.png`. The state list maps to the approved specimen IDs. Real data are synthetic. Three rows of this sample use a single browser interception to reproduce a transport or display state reliably.

| Opened state | Widths in CSS px | Notes |
| --- | --- | --- |
| `claire` | 1440×1024, 768×1024, 390×844 | Shared shell, page background, exact three values and units. |
| `source` | 1440×1024, 390×844 | Focus on the disclosure, version 2 and locator `DOC-A-001:page-1` in its body. |
| `vide` | 1440×1024, 390×844 | Louis keeps the neutral allergy banner and a truthful empty panel. |
| `correction` | 1440×1024, 390×844 | Marie shows the real lineage and a source per version. |
| `avant-correction` | 1440×1024, 390×844 | Shared historical banner, original 7,40 and its exact source. |
| `tendance` | 1440×1024, 390×844 | Two seeded Analyse D points. The wider dataset remains available in the table. |
| `chargement` | 1440×1024, 390×844 | Lab request held in the browser. Identity and navigation stay visible. |
| `erreur` | 1440×1024, 390×844 | Injected laboratory 503. Shared attention notice with a retry button. |
| `long` | 1440×1024, 768×1024, 390×844 | Injected long text and value. The source opens and focus returns to the result. |

The opened visual sample contains 40 pairs, with 9 cases at the matched desktop and mobile widths in both themes plus 4 pairs for default and long content at 768 px. The sample was recaptured on the local package at application revision `c121abc`, and the changed chart and comparison notice were reopened. Both themes were opened. Desktop navigation and the surface background persist through the selected states. The application uses the existing shared alert, empty-state and historical components. That accounts for the alert tone, the Louis allergy treatment and the placement of the historical banner. The correction proof and points appear in Marie's record. The live source label initially shows its reference. Claire’s table explains the context required for comparison. A tablet comparison exposed folding in the approved specimen too. The running application corrected the shared banner and switched the S-12 table to cards at the narrower tablet width in `77f4619`. The review page shows this change for Aymen's runtime approval.

The full Chromium smoke run passed 225 cases on `80420c2` in the quiet serial run. A complete instrumented run of the same application first passed 224 cases and timed out waiting for the sign-in heading on one pre-existing test under concurrent local load. Three isolated repeats passed. GitHub `Web verify` also passed on `80420c2`.

The local runtime review page resolved all 40 pairs. Its own axe-core checks passed at 390 and 1440 CSS px in both themes after links were set to switch colors immediately with the theme.

## Three-lens BMAD review

Zcode CLI `4.0.0-50`, runtime `0.18.0`, model `GLM-5.4`. The single review used revision `2c69bd322eea09e68f2033cf83cc090a417b6d38` and the complete diff from the recorded baseline, attached with the epic excerpt, context packet and test evidence. It ran in an isolated directory in guarded mode with execution, editing and delegation disabled. The generation stalled after the diff reads. The same Zcode session was resumed to obtain one report. The original report is in the local sibling receipts as `bmad-zcode-glm-5.4-review.md`. Its SHA-256 is `4ebb4fa0abce0451013b96ef6aea270ddd986a57477b39adce566d0492cc8f37`. A pre-UD-81 Codex worktree contains unused partial edits. The primary agent implemented the branch, triaged this report and preserved the isolated worktree.

| Lens / finding | Verdict and disposition |
| --- | --- |
| Silent failure · large decimals rounded in chart | Confirmed at the reviewed revision. `c121abc` preserves exact axis labels and computes differences using integer micro-units before converting only the drawing ratio. Near-limit points one micro-unit apart have distinct axes and positions. |
| Silent failure · textual interval hid supplied numeric bounds | Confirmed. `c121abc` shows the original interval text and separately supplied bounds and unit. |
| Silent failure · no time zone after successful membership access | Invariant under the active schema and identity resolution. V1 makes `practice.timezone` non-null. V11 applies the IANA validator and joins the practice to each current membership context. The identity repository derives current membership IDs from those same context rows, which `requireCapability` requires. The route tests cover missing and inactive clinical grants at the authorization boundary. |
| Design note · cursor checksum has no secret key | The checksum is a consistency mechanism. The independent authorization check and scoped row-position lookup gate every request. HTTP tests exercise another record, another actor, another filter and malformed tokens. |
| Silent failure · recovery and focus | Reviewer found the source and list recovery paths and typed refusal branches in the diff. Browser cases cover focus return, preserving visible rows on a later-page failure, retry and removal of protected content. |
| Edge cases · lineage size, ties, date boundary and SQL invariants | Covered by `c121abc` in the real HTTP and database suite. The 105-revision case checks the original plus newest 99, a 1,000-character reason and either endpoint anchor. Pagination exercises an exact date/recorded-time tie. The date-only boundary uses Paris midnight on a daylight-saving transition. SQL rejection checks inconsistent value kinds, reversed bounds, wrong-record and later source and an invalid predecessor. |
| Edge cases · equivalent bound scales | Confirmed. The comparator matches bounds by exact numeric value and keeps the supplied decimal scale in the table. Free-text context, structured context, precision, unit and reported laboratory remain part of compatibility. A foreign opaque key is an authorized empty filter. |
| Acceptance evidence | The initial review attachment identified in-flight tests and missing opened captures. The implementation record includes the subsequently executed real tests and sampled images. Added a browser check for a 1,000-character correction reason and 500-character source text at 320 CSS px, with keyboard interaction and axe-core. Packet text labels the baseline's 101 operations and the implementation contract's 103 operations explicitly. |

## Draft PR checks

[PR #105](https://github.com/Aymenchaghoub/etaye/pull/105) opened as a draft at `80420c2`. `Package verify`, `Web verify`, `CodeQL`, dependency review and the planning checks passed there. `API verify` stopped at Maven Central HTTP 429 in the compiler-debt step, before Testcontainers. The missing matrix upload followed that failure. [First API job](https://github.com/Aymenchaghoub/etaye/actions/runs/46073367226/job/140755297356)

SonarQube flagged two dynamically assembled API queries under S2077. `e3c9c0b` uses fixed, parameter-bound statements for both pages and cursor checks. The local API suite and changed-line gate were rerun on that revision. The Web report flagged two reductions without an initial value and insufficient new-code coverage. `2fb0014` initializes both reductions and adds four component scenarios for independent errors, throttling and scoped cursor recovery, bounded lineage and equal dated values. `f6d8cab` clears the elapsed retry delay when starting the next request, so a stale cursor can immediately restart the list after a prior throttle. Web verification passed 174 Vitest tests plus the production build at that revision. The pipeline reruns these checks on the updated head. CodeRabbit completed its first requested review on `80420c2` with no actionable comments. [First review](https://github.com/Aymenchaghoub/etaye/pull/105#issuecomment-7228872058)

The hosted API, Web, package, CodeQL and SonarQube checks all passed at `feca4f8`. The rebuilt local package passed 14 real-stack cases, and local Chromium passed the full 225-case suite before the follow-up regression test. [Draft PR checks](https://github.com/Aymenchaghoub/etaye/pull/105/checks)

### CodeRabbit follow-up on `feca4f8`

| Finding | Resolution |
| --- | --- |
| Browser evidence floor did not grow with the laboratory suite | `Web verify` requires 226 smoke cases, including the added historical return case. The Vitest and Surefire report guards also match the new totals. |
| A correction could reference its own row | V15 adds a validated constraint and requires an existing same-lineage predecessor at insertion. Existing migration files are unchanged. SQL tests cover absent and self-referential predecessors. Managed bootstrap accepts the new schema history. |
| Lab test fixtures could affect the demo reset setup count | The reset test checks its one-row increment against the observed baseline and still checks the exact seeded count after reset. |
| Returning from history could keep the old current window | The query anchor tracks the selected snapshot. The browser test advances the clock during the historical view and checks both bounds on return. |

## Running-UI approval and final application evidence

The four findings were resolved at application revision `ef9afa9`. GitHub passed 306 API tests, 174 Vitest tests, 226 Chromium smoke scenarios, 14 real-stack scenarios, CodeQL and all three SonarQube quality gates on that head. Changed-line coverage passed at 173/177 on the API and 46/46 on Web logic. During simultaneous local verification, three Chromium scenarios exceeded their wait. Each passed twice in an isolated rerun. A Java 25 run with reversed lab and reset test order passed 13 tests, including the managed V11 → V13 → V15 upgrade. The three laboratory cases passed on the rebuilt local package. [API verification](https://github.com/Aymenchaghoub/etaye/actions/runs/46136961117) · [Web verification](https://github.com/Aymenchaghoub/etaye/actions/runs/46136961175) · [Package verification](https://github.com/Aymenchaghoub/etaye/actions/runs/46136961073) · [SonarQube](https://github.com/Aymenchaghoub/etaye/actions/runs/46136961126)

Claire at 768×1024 and Marie's correction at 390×844 were recaptured on `ef9afa9` in both themes. The matched background and shell checks and images are in the `ef9afa9/` subdirectory of the local runtime receipts. Aymen approved the running S-12 interface on 2026-11-28 and authorized the squash merge of PR #105 under UD-82. The first follow-up review request for `ef9afa9` reached the included CodeRabbit quota. [Quota response](https://github.com/Aymenchaghoub/etaye/pull/105#issuecomment-7238146869) · [PR checks and discussion](https://github.com/Aymenchaghoub/etaye/pull/105/checks)
