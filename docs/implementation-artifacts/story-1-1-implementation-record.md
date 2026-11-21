# Story 1.1 implementation record

On 2026-11-21, UD-50 authorized team and patient invitation activation. Aymen approved the six S-03 and four I-03 HTML references under the recorded Figma exception. Issuance, listing, revocation, resending, I-01 and invitation email delivery belong to increment 2 under D-23. [Story and AT-001](../planning-artifacts/epics/epic-1.md#story-11-activate-an-intended-invitation) · [Scope and C1–C10](story-1-1-context-packet.md) · [Design approval](../design/reviews/2026-11-21-story-1-1-review.md)

## Runtime behavior

- `/invitation#<token>` inspects the token in a POST body with CSRF and request admission. The browser removes the fragment from history and retains the token in component memory during activation or sign-in. The preview exposes the masked recipient hint, the practice name and invitation timing.
- V3 forces practice RLS on invitations. Restricted identity functions take an exact digest, derive practice context, and grant one membership or one record-specific portal link, consume the invitation and append one practice audit event in a single database operation. Patient birth-date verification precedes writes. Existing grants and account suspension are checked during acceptance. V1 and V2 remain the migration history for existing installations.
- The winning key replays the same outcome under the original identity conditions. A changed body conflicts. Anonymous replay is bounded by invitation expiry and credential revision. Account access and grants are re-resolved before the session cookie and CSRF value rotate.
- The team destination is `/compte`. Patient acceptance returns its exact current portal-link ID, which the browser checks against the active grant list before opening `/compte/patient/{id}`. Practice selection and the full patient home are delivered in their own stories. [Contract](../contracts/README.md)

## Evidence on 2026-11-21

| Check | Observed result |
|---|---|
| API, Java 25 and PostgreSQL 18 | `./mvnw -B -ntp verify` and the Surefire report gate passed with 157 tests, zero skips, including all 7 invitation HTTP cases. C1–C8 cover exact grants, safe refusals, suspension, concurrency, signed-in and anonymous replays, credential revision, role privileges, transaction context, CSRF and admission bounds. |
| V2 upgrade | A disposable PostgreSQL 18.7 database migrated to V2, received a synthetic marker account, migrated to V3, and retained that account. Testcontainers also applied V1 through V3 on a fresh database. |
| Mutation checks | C6 failed when the accepted-token guard was removed and its expected replay became a refusal. C7 failed when the digest predicate was removed and the preview selected the wrong practice. Each check produced one assertion failure in a disposable checkout. |
| Web, Node 26 | `tsc -b`, the production build and SDK drift checks passed. The Chromium smoke gate passed all 30 tests, including 11 invitation tests. The ten approved states have functional assertions and axe scans. Keyboard focus, generic refusal, throttling, retry and existing-recipient sign-in were exercised. |
| Real Spring/React/PostgreSQL | All three invitation browser tests passed. A team member activated and signed back in, a patient corrected a rejected birth date and activated the exact portal link, and expired, revoked and unknown tokens reached their recovery states. |
| Visual evidence | The real stack and matching HTML reference produced 200 captures for ten states, five widths and two themes. The run asserted no horizontal overflow at those widths. The opened comparisons document the sampled results. [Visual review](../design/reviews/2026-11-21-story-1-1-rendered.md) |

Candidate `5ec9e4a483785301f9742c2c4026e35e9e7d43a9` incorporates the approved contract and the exact portal destination. The local review and remote checks are tracked in the continuation note and pull request.

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
