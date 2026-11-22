# Story 9.1 implementation record

2026-11-22. Branch `feat/9.1-enter-and-reset-bounded-synthetic-journeys`. Baseline `7d46654` (PR #51). Reference approvals are recorded at `d4444ea`. UD-56 authorizes story 9.1 and the story's BMAD readiness gate passed before implementation. Global development authorization stays false. [Context and consulted sources](story-9-1-context-packet.md) · [Story and AT-035](../planning-artifacts/epics/epic-9.md#story-91-enter-and-reset-bounded-synthetic-journeys) · [Draft PR #52 and checks](https://github.com/Aymenchaghoub/etaye/pull/52/checks)

| Revision | Delivered evidence |
|---|---|
| `27abfe2` | Contract 0.5.0 defines the scenario disclosure, no-store optional demo metadata, reset acknowledgement and replay semantics. The index still contains 99 operations and the roadmap now lists all 99. |
| `f08e612` | Demo-only Flyway migration, current operator check, transactionally restored fixture and session revocation. Tests use an isolated database with production roles and the same migration. |
| `4bce77e` | Generated client, showcase, I-26 pages, session notice, browser smoke and real scripted walkthrough. |
| `73f452a` | Four-service Compose package, generated local credentials and static warm-up. This is the revision reviewed by Codex. |
| `4d21f50`, `0939819` | Compatibility and reliability fixes from review, with regression coverage. The recorded Compose application revision is `0939819b9ce637b34103fb40e2d32bde9c0fc401`. |
| `fcfab6a` | Restores normal Vite development dependency discovery for the browser fixtures. The demo build keeps the same inputs as the recorded application revision. |

## Boundaries and decisions

- The demo loader is available only in the dedicated `etaye_demo` PostgreSQL database initialized with the package marker. The owner applies the separate migration and the runtime role executes the bounded reset function. Compose runs one API replica and only publishes the web gateway on loopback.
- The reset uses a server-owned operator identity, normal session and CSRF, a bounded request barrier and a PostgreSQL transaction. All active sessions close atomically with fixture restoration. A 30-second cooldown applies to distinct resets. Successful keys replay for 24 hours after a fresh operator sign-in. The receipt holds digests and the original correlation ID. The same dataset version is detectable through its incremented generation.
- Scenario choice is presentation context. The existing sign-in and current grants give Nora, Claire and Mila their destinations. The seeded team activation accepts the published fictional name. The local demo disables arbitrary patient creation and exercises no external provider. Existing practice grants isolate the two practices.
- The package restores practice, account, membership, patient, record, portal link and invitation rows. The canonical weight correction and publication v2 remain design fixtures until their domain stories supply runtime tables, as agreed under UD-58 (D-54). The packet's provisional description of Mila included scheduling and registration. Its contract-confirmed copy describes her current administrative destination and restricted clinical access. The data preserves the planned capability assignments. [Deferred work](../planning-artifacts/deferred-work.md) · [Synthetic dataset](../design/synthetic-data.md)
- S-37 covers unavailable, maintenance and rate-limit handling at the public demo boundary. In-journey recovery stays with 9.3. The reset notices use the existing inline status and warning banners. D-43 remains open for toast work. Hosted deployment and CI integration of the scripted journey have the owners recorded in UD-56. [Design review](../design/reviews/2026-11-22-story-9-1-runtime.md)

Current source reconciliation checked the epic and PRD, BR-018 and J-08, access matrix, roadmap, design catalog and approvals, architecture, data model and dictionary, contract, AI and security specifications, test strategy, deferred work, and the merged 10.1 dependency. Source versions and the approved design snapshot are in the [phase-0 packet](story-9-1-context-packet.md#10-consulted-source-versions-checks-and-phase-0-review). On resume, `d4444ea` superseded the phase-0 design status for S-37 and I-26. Source decisions and the AT-035 mapping otherwise remained applicable. The 9.1 implementation exercises the persisted carrier and access slice of AT-035-A/B.

## Executed verification

| Evidence | Result |
|---|---|
| `JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./mvnw -B -ntp verify` in `apps/api` | 163 passed with PostgreSQL 18 through Testcontainers. The new suite asserts dedicated-database admission, anonymous catalogue pagination, operator and CSRF checks, two-practice isolation, fixture restoration, concurrent refusal, session closure including a real sign-in held across reset, stale writes, replay and rate limiting. |
| `corepack pnpm exec tsc -b`, `corepack pnpm build` and `ETAYE_SMOKE_PORT=5192 corepack pnpm test:smoke --reporter=json` in `apps/web` | Types, generated SDK drift and production build passed. 54 Chromium smoke cases passed, including keyboard operation and axe-core on the built boundary states. Node 26.13.1, pnpm 12.21.0, Playwright 1.63.0. |
| `COMPOSE_PROJECT_NAME=etaye-demo-91-verify ETAYE_DEMO_WEB_PORT=4189 ./infra/demo/start.sh` | Compose built and ran the web gateway, API and PostgreSQL. Flyway installed V1–V5 in the dedicated database. The API and database ports stayed within the Compose network. |
| `ETAYE_E2E_DEMO_URL=http://localhost:4189 corepack pnpm --filter etaye-web test:demo` | Two cases passed. Real sign-in drove Nora, Claire and Mila to their available destinations. A separately signed-in operator reset the fixture, verified the changed generation and triggered Nora's expired-session notice. Both browser views were recorded. [Videos and running-UI review](../design/reviews/2026-11-22-story-9-1-runtime.md) |
| Cold start and responsive evidence | Chromium received HTTP 504 and 502 while the isolated API container was stopped or coming up. The gateway served the static page throughout. After an HTTP 200 with `UP`, it opened the showcase. [Opened comparisons](../design/reviews/2026-11-22-story-9-1-runtime.md) |

Story 9.1 requested one end-of-story three-lens review. Aymen allowed Zcode for this review on 2026-11-22. Zcode Runtime 0.17.1 reached the provider but returned `429` with usage-limit code `1308` before producing a report. One read-only Codex CLI review then completed on `73f452a401f0f0a26cb3c69bd3ccc7fd0559cacf`, with the exact diff, story and verification summary attached.

| Lens | Disposition |
|---|---|
| Silent failures | The browser now reconciles a lost reset response using the fixture generation and a tab-local reset key. A fresh successful sign-in adopts the current generation. Active tabs capture the marker before reading a session and retain it in memory when browser storage is restricted. The showcase offers a retry when the operator check fails. |
| Edge cases | A two-second JDBC query deadline bounds the reset call. The operator check runs before MVC body binding. The demo restricts the pending team invitation name and keeps the portal's record-derived name, tested with Marie's invitation. |
| Acceptance | Demo metadata and catalogue additions are optional for compatible clients, with full disclosure verified before entry. The demo routes and static warm-up are selected only for the demo production build. Keyboard retry keeps focus on the showcase. Playwright recording and the offline visual comparisons were verified and are linked here. D-54 carries the future clinical anchors. |

GitHub ran the Testcontainers API suite and Surefire count guard, and ran the Web type check, build, browser smoke and Playwright count guard on initial review head `b42e2d4`. CodeQL flagged the reset lock acquisition on that head. The follow-up retains the acquired lock reference through the same release in `finally`. All three focused PostgreSQL demo tests passed after this change. [API run](https://github.com/Aymenchaghoub/etaye/actions/runs/44962147619) · [Web run](https://github.com/Aymenchaghoub/etaye/actions/runs/44962147620) · [CodeQL finding](https://github.com/Aymenchaghoub/etaye/pull/52#discussion_r4655543375)

## CodeRabbit follow-up

CodeRabbit reviewed `0a63feb990e7f400757c8aeadde4e51afc982be2` and raised four minor findings. [Review](https://github.com/Aymenchaghoub/etaye/pull/52#pullrequestreview-6008962546)

| Finding | Disposition |
|---|---|
| Session deadline can hide a reset | The demo rechecks the fixture generation and server session at the deadline while hiding protected content. A changed generation shows the reset notice. Regression cases exercise a reset between polls and ordinary expiry for the same generation. |
| Retry assertion could pass without an idempotency key | The test checks that the first key is present before checking that the retry reused it. |
| Runtime page port differs from the default | The page links to the standard package on 4179 and separately labels the review stack on 4189. Chromium checked both links and the decision controls at 390 CSS px. |
| AT-035-A clinical fixture evidence | UD-58 accepts D-54. The [acceptance map](../traceability.md#story-91-demo-fixture-verification) records the delivered reset scope and its later clinical storage dependencies. |

Aymen approved the running interface separately under [UD-57](../planning-artifacts/decisions-assumptions.md#story-91-approvals-2026-11-22). The deadline regression reproduced the missing reset notice before the fix. A fresh isolated worktree passed the type check, the production build and all 56 Chromium smoke tests after the fix. Both scripted Compose journeys also passed against the rebuilt local package. The [PR checks](https://github.com/Aymenchaghoub/etaye/pull/52/checks) record results for the exact head.

### Second CodeRabbit review

CodeRabbit reviewed `82f5589706640cdaf4bb0eff786f51d19dc02d25` and raised four findings. [Review](https://github.com/Aymenchaghoub/etaye/pull/52#pullrequestreview-6011388203)

| Finding | Disposition |
|---|---|
| Encoded request paths | The demo barrier and the reset authentication check use the container's decoded servlet path. The real HTTP regression verifies that reset waits for in-flight sign-in, closes the resulting session, and responds to an anonymous reset with 401. |
| Failed second metadata read | If a session check returns 401 between metadata reads, an unavailable or invalid follow-up marker keeps the retry action available while protected content stays hidden. The browser tests verify retry into the reset notice and preserve ordinary expiration for the same generation. |
| Documentary scan count | A fresh detached checkout of the reviewed commit returned 216 eligible files, including 194 under `docs/` and 22 Python files under `tools/`. The committed report already matched the validator output from that checkout. |
| Credential generation failure | Startup exits before publishing `.env` when OpenSSL fails. A temporary-directory check verified cleanup, no Compose invocation on failure, and successful retry with four generated values and mode `0600`. |

The encoded reset regression previously returned 409. After the fixes, Maven verified all 163 API tests. The web type check and production build passed, as did all 58 Chromium smoke tests. Both real scripted journeys passed on a fresh Compose project with an isolated synthetic database. The documentary validator and 99 tooling tests also passed.

## Running it

From the repository root run `./infra/demo/start.sh` and open `http://localhost:4179`. The recorded review stack uses `http://localhost:4189`. Credentials and reset instructions are in the [local demo README](../../infra/demo/README.md). The running-UI review is a separate decision from the design approvals and merge authorization. [Review page](../design/reviews/2026-11-22-story-9-1-runtime/index.html)
