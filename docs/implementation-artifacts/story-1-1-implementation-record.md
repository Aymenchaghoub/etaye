# Story 1.1 implementation record

On 2026-11-21, UD-50 authorized team and patient invitation activation. Aymen approved the six S-03 and four I-03 HTML references under the recorded Figma exception. Issuance, listing, revocation, resending, I-01 and invitation email delivery belong to increment 2 under D-23. [Story and AT-001](../planning-artifacts/epics/epic-1.md#story-11-activate-an-intended-invitation) · [Scope and C1–C10](story-1-1-context-packet.md) · [Design approval](../design/reviews/2026-11-21-story-1-1-review.md)

## Runtime behavior

- `/invitation#<token>` inspects the token in a POST body with CSRF and request admission. The browser removes the fragment from history and retains the token in component memory during activation or sign-in. The preview exposes the masked recipient hint, the practice name and invitation timing.
- V3 forces practice RLS on invitations. Restricted identity functions take an exact digest, derive practice context, and grant one membership or one record-specific portal link, consume the invitation and append one practice audit event in a single database operation. Patient birth-date verification precedes writes. The transaction checks practice and account state, preserves existing grants and rechecks invitation expiry after acquiring the activation locks. V1 and V2 remain the migration history for existing installations.
- The winning key replays the same outcome under the original identity conditions. A changed body conflicts. Anonymous replay is bounded by invitation expiry and credential revision. Account access and grants are re-resolved before the session cookie and CSRF value rotate.
- The team destination is `/compte`. Patient acceptance returns its exact current portal-link ID, which the browser checks against the active grant list before opening `/compte/patient/{id}`. Practice selection and the full patient home are delivered in their own stories. [Contract](../contracts/README.md)

## Evidence on 2026-11-21

| Check | Observed result |
|---|---|
| API, Java 25 and PostgreSQL 18 | `./mvnw -B -ntp verify` and the Surefire report gate passed with 159 tests, zero skips, including all 9 invitation HTTP cases. C1–C8 cover exact grants, safe refusals, practice and account suspension, expiry while waiting on a row lock, concurrency, signed-in and anonymous replays, credential revision, role privileges, transaction context, CSRF and admission bounds. |
| V2 upgrade | A disposable PostgreSQL 18.7 database migrated to V2, received a synthetic marker account, migrated to V3, and retained that account. Testcontainers also applied V1 through V3 on a fresh database. |
| Mutation checks | C6 failed when the accepted-token guard was removed and its expected replay became a refusal. C7 failed when the digest predicate was removed and the preview selected the wrong practice. Each check produced one assertion failure in a disposable checkout. |
| Web, Node 26 | `tsc -b`, the production build and SDK drift checks passed. The Chromium smoke gate passed all 33 tests, including 14 invitation tests. The ten approved states have functional assertions and axe scans. Keyboard focus, generic refusal, an existing recipient’s direct sign-in, expired-session recovery and the pending, retry and throttled states were exercised. |
| Real Spring/React/PostgreSQL | All three invitation browser tests passed. A team member activated and signed back in, a patient corrected a rejected birth date and activated the exact portal link, and expired, revoked and unknown tokens reached their recovery states. |
| Visual evidence | The real stack and matching HTML reference produced 200 captures for ten states, five widths and two themes. The run asserted no horizontal overflow at those widths. The opened comparisons document the sampled results. [Visual review](../design/reviews/2026-11-21-story-1-1-rendered.md) |

## Delivery review

The read-only Codex CLI reviewed candidate `5ec9e4a483785301f9742c2c4026e35e9e7d43a9` against `447fe579af1fdc43b5a4e16a0e0565be82c350d0`. The implementation follow-up `54921b1fd2696cf010b56c326316257e9040e8e9` closes its seven findings. Follow-up `528cfa402d429e7c213e7375fe3f6ef7f8bd9de3` also refreshes the preview when the same link is opened again in the current tab. Chromium and the isolated running UI verified that refresh. Visual candidate `75c69dc3b0fb65631f5ba2433429cb722e9a00fb` keeps automatic heading focus without drawing an outline around static text. Chromium verified the title and the visible Tab focus on controls in the shared auth shell. Aymen approved the running UI after this correction.

| Review lens | Disposition and regression evidence |
|---|---|
| Silent failures | A suspended practice prevents consumption, account creation and audit. The PostgreSQL HTTP test verifies the preview and acceptance refusal. |
| Edge cases | Acceptance rechecks time after the record and recipient locks. The lock-expiry test verifies that the invitation and grants remain intact. The web restores account-creation fields when the prior session ends, offers direct sign-in and sends conflicting portal links to practice contact. |
| Acceptance criteria | Invitation idempotency accepts the OpenAPI header length range. HTTP tests exercise a key with punctuation. Chromium verifies loading, pending submission, throttling, retry, restrictions and focus with axe. |

CodeRabbit reviewed `a40c4683cc3bf36ccf2d1d3d73fd541e132a5963` and returned five findings. The final candidate `f615a1b4f4562f6bbe33bf7e4d9d1b98a67e40c7` bounds the derived account name while preserving the original patient names, verifies both concurrent HTTP requests wait on the invitation, clears stale form errors after sign-in, aligns the display-name requirement and links historical verification instructions to the current review rules. CodeRabbit reported a rate limit on the later request. [CodeRabbit review](https://github.com/Aymenchaghoub/etaye/pull/49#pullrequestreview-6004835578) · [Rate-limit response](https://github.com/Aymenchaghoub/etaye/pull/49#issuecomment-7061766049)

The final review and integration status are tracked in the [continuation note](../continuation.md) and [PR #49](https://github.com/Aymenchaghoub/etaye/pull/49).

## Review follow-up on main

On 2026-11-22 the seven original findings were rechecked against `88e88d53bfb058af0eff3078452816be5eea5f86`, the approved UD-50 scope, BR-004/005, ADR-003/004, the OpenAPI contract and AT-001/C1–C10. The original practice-suspension refusal, neutral portal-conflict message and up-front sign-in action remain covered. The follow-up closes residual deadline, recovery, focus and replay-contract gaps within the same scope.

- The existing grant check precedes the first write. A portal grant insert could still wait on the issuer-account foreign key, allowing acceptance after expiry. V7 retains the digest-scoped entry point and uses a subtransaction to roll back a newly accepted result whose recorded acceptance reached or passed the deadline. The late-lock case failed with `200` before V7 and now returns `422 INVITATION_EXPIRED` with no account, grant, receipt or audit. The comparison at the exact timestamp is explicit in SQL. The contention test exercises acceptance after the deadline.
- PostgreSQL 18.7 upgrade checks applied V7 to isolated ordinary V4 and demo V6 schemas. V3 checksum `276969359`, execution privileges, the ordinary synthetic marker account and the demo fixture generation were preserved. V1–V6 remain immutable.
- The OpenAPI header describes the existing invitation-scoped receipt and validation order, with the same length and required header. HTTP tests cover length boundaries, punctuation, concurrent requests using both distinct and identical keys, same-body replay, validated-body conflict and proof that a portal conflict leaves the original grant and the second invitation intact.
- The Web form treats an inconclusive session read as retryable. Keyboard focus survives preview retry, submission, throttling and sign-out failure. A successful explicit sign-out moves focus from the removed action to the first account-creation field. The recovery preserves entered birth dates and display names. Verification refusals clear password fields.
- One read-only Codex CLI review identified the explicit sign-out focus gap, request-validation precedence in the replay description and the exact-timestamp test limit. The first two were corrected and retested. The full Java 25 Maven run passed 166 tests, including 12 invitation HTTP cases. Node 26 SDK drift, type check, build and all 60 serial Chromium smoke cases passed. Browser evidence includes axe, 320 CSS px reflow and a Chromium accessibility-tree check at 1440 and 390 px, with a 200% CSS zoom simulation at 1440 px. The complete isolated Spring/React/PostgreSQL Playwright run passed all seven cases, including four invitation journeys and real cross-tab sign-out. The 200 matching reference and runtime captures stay local. Opened samples include S-03 on desktop in light theme and I-03 error on mobile in dark theme.

## Reproduce the browser journeys

Start the isolated PostgreSQL, API and Web stack using the [local stack instructions](story-10-1-implementation-record.md#9-running-it-locally). Run the invitation spec from `apps/web` with an isolated PostgreSQL container. The spec seeds disposable records and generates tokens in memory.

```sh
ETAYE_E2E_INVITE_DB_CONTAINER=etaye-local-postgres \
ETAYE_E2E_BASE_URL=http://localhost:5173 \
corepack pnpm test:e2e-real invitation-access.spec.ts
```

For matching captures, serve [the committed HTML reference](../../design/offline-reference/README.md) and set `ETAYE_E2E_REFERENCE_ORIGIN` and `ETAYE_E2E_INVITE_CAPTURES` to its origin and a local output directory. The team form uses a display name and a password confirmation. The patient form uses the synthetic birth date 12/03/1990.

## Sources and scope

- The implementation follows the planning snapshot at `447fe579af1fdc43b5a4e16a0e0565be82c350d0` and the per-source revisions in the [context packet](story-1-1-context-packet.md#11-source-versions), including PRD, business rules, access matrix, journeys, epic, architecture, data, security, deferred work and test strategy. The AI specification at `5ba958c` and ADR-003/004 identify adjacent boundaries. Each test traces to the existing AT-001 acceptance criteria.
- Authorization and visual approval are recorded at `f1d3d6f49a7288325c28bed00c6b697d67a132a4`. Contracts precede application changes in `acd3b37713cd7db130d964ee38e9190e3a340245` through `5d16687f4ae5295959bb7a42415487facb118446`. The additive acceptance field disambiguates the patient destination for accounts with several links. The additional conflict uses the existing 409 response and preserves any current team grant.
