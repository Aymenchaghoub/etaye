# Story 1.1 — implementation context packet

Status: Phase-0 preparation only; this packet authorizes nothing (delivery rules). Date: 2026-11-21. Prepared by ZCode at Aymen's request from `main` at `447fe57`. Story text and acceptance designs stay in [epic 1](../planning-artifacts/epics/epic-1.md); where this packet adds detail it says so, and a [Proposal] is not a decision. No application change, migration, contract edit or service activation accompanies this packet. **Arbitration outcome (2026-11-21, UD-50):** Aymen validated points 2–4 below (the six contract points, the dataset fixtures and the development authorization); Aymen subsequently approved both reference state sets. See the [design review](../design/reviews/2026-11-21-story-1-1-review.md) and the [implementation record](story-1-1-implementation-record.md) for delivery evidence.

## 1. Objective and traceability

An invited team member or patient activates exactly the grant a seeded invitation names — its membership or its portal link, once — and every expired, revoked, already-used or recipient-mismatched token grants nothing and shows the recovery route.

- Requirements: FR-001 (account access), FR-004 (patient identity/portal linking). Rules: BR-004 (portal linking), BR-005 (invitations, activation, recovery). Journey: J-01. Acceptance design: AT-001-A/B/C.
- Design families: S-03 (Invitation and account activation) and I-03 (Activate patient portal) are in scope; I-01 (Invite team member) is cited by the epic but its issuance dialog is deferred by D-23 — see section 3.
- Milestone M1 of [ADR-011](../decisions/ADR-011-delivery-order-and-milestones.md); increment 1. Dependencies ([story index](../planning-artifacts/epics/story-index.json)): 10.1 only — merged as `bfc4706` (PR #33). [Verified observation] Story 1.2 is merged too (`05ae963`, PR #45, plus PR #47) although the index lists no edge; 1.1 builds directly on its session, CSRF, request-budget, recovery-digest and audit foundations, and on its local inbox.
- AT-001-C evidence level for S-03: SAMPLED (not CORE) — functional assertions and automated axe-core checks for every state, plus opened desktop and mobile captures for the risk-sampled subset (8–10 screens across all sampled families at 2 widths; UD-37).

## 2. Preconditions (all currently unmet or pending — check again before any code)

1. **Written development authorization naming story 1.1: ABSENT.** `_bmad/custom/config.toml` has `development_authorized = false` and `authorized_stories = ["10.1", "10.2", "1.2"]` (default deny). A new dated UD row and the config encoding are required (section 12).
2. **Design status of the cited families: not approved.** `docs/design/coverage.csv`: S-03, I-01 and I-03 are `Ready for design`, `aymen_approval = Not approved`, under the 2026-11-18 named exception (exact Figma nodes replaced by the corrected HTML reference snapshot `d2f0800e…` plus the tokens and recipes of [design-system.md](../design/design-system.md) section 14). [Verified observation] Under UD-16, SAMPLED families are "built desktop-first in code with sampled visual evidence", so pre-build approval is not the UD-16 gate for S-03; the CORE gate is already satisfied (UD-46 approved the 19 CORE families on 2026-11-20). The story 1.2 precedent nevertheless had Aymen approve the story's families before code — section 12 asks him to choose.
3. **Reference states exist.** The committed offline reference contains S-03 (6 states: `equipe`, `expiree`, `deja-utilisee`, `revoquee`, `invalide`, `erreur`) and I-03 (4 states: `ouvert`, `deja-active`, `expiree`, `erreur`), all structurally verified and none visually approved (`html-reception.csv`).
4. **Seeded invitations: insufficient.** The epic requires seeded pending, expired, revoked and recipient-mismatched invitations; the synthetic dataset references only one narratively (Louis Bernard's expired portal invitation) and none exist as machine-readable fixtures — section 9, item 7.
5. Toolchain and running/verifying commands: AGENTS.md (Java 25, Node 26/Corepack pnpm, Docker for Testcontainers; `API verify`/`Web verify` required on PRs).

## 3. Scope

In scope (once authorized):

- Contract-first additive changes for the activation flow (section 4), committed before application changes as in story 10.2.
- `V3` migration: the practice-owned `invitation` table (id, practice_id, purpose, intended_recipient, token_digest, expires_at, state, issuer_id, accepted_at, version) with RLS, plus the narrow identity lookup by token digest (section 5). V1/V2 stay untouched (applied migrations are immutable).
- API: anonymous `acceptInvitation` (CSRF, request budget, atomic single-use acceptance creating the intended membership or portal link and the account when eligible, audit; BR-005 transition `PENDING → ACCEPTED`), plus the preview path decided in section 4.
- Web: S-03 surface with all catalogued states, patient variant per I-03, French copy from the reference, token carried in the page fragment (never a query parameter), safe return to an allowlisted route.
- Synthetic-dataset extension for the seeded invitations and their recipients (section 9, item 7), reviewed with Aymen.
- SAMPLED visual evidence per AT-001-C; coverage tracker updated without marking anything approved.

Out of scope, with the owning story/register entry: issuing, resending, revoking and listing invitations (`issueInvitation`, `revokeInvitation`, `resendInvitation`, `listInvitations`; the I-01 dialog states `ouvert`, `envoye`, `existant`, `combinaison`, `renvoyer`) and the invitation emails M-01/M-02 → increment 2 (D-23; emails also 8.3); practice selection and switching (1.3); the practitioner/patient homes S-06/S-25 (M2/M5); native invitation landing (DF-9, increment 3+); branded mail (8.3); demo reset journeys (9.1); suspension management UI (2.2; only the suspension conflict guard is in scope, check C5).

## 4. Contract position and required additive changes

[Verified observation] `POST /api/v1/invitations/accept` (`acceptInvitation`) exists in the contract (version 0.2.3-planning, 98 operations): anonymous, `x-required-permissions: [INTENDED_RECIPIENT]` (a policy predicate), CSRF + `Idempotency-Key` parameters, body `Activation {token (32–512, writeOnly), recipientVerification (1–512, required), password (12–1024, optional, writeOnly)}`, `200 Acknowledgement`, errors 400/401/403/404/409/422/429/503. The contracts README requires: single-use token digest and expiry, intended-recipient verification, generic wording where enumeration matters, and "never cache passwords/raw tokens in idempotency storage".

Gaps found against S-03/I-03 and BR-005 — each needs an additive contract decision before code (contract precedes code; 10.2 precedent):

1. **No preview operation.** S-03 must show purpose, practice, recipient hint and expiry *before* acceptance (screen catalog), but no operation returns invitation metadata from a token. [Proposal] add an anonymous, rate-budgeted `POST /invitations/inspect` (body `{token}`; the token never in a URL or query) returning a masked projection: purpose, practice display name, masked recipient hint, `expiresAt`, eligibility state — without consuming or altering anything.
2. **`Activation` has no `displayName`.** The reference team form requires "Nom affiché" and `Account.displayName` is required; increment 1 has no profile screen (D-31). [Proposal] add `displayName` (conditional: required for TEAM_MEMBERSHIP, forbidden-irrelevant for PATIENT_PORTAL, which derives identity from the record), mirroring the conditional style of `InvitationCreate`.
3. **`recipientVerification` semantics are undeclared.** The reference fixes them: patient portal → the record's date of birth (I-03 `ouvert`: "Votre date de naissance"); team → [To verify] token possession plus email match, or email match alone (S-03 shows a masked recipient hint and no verification field). The README row must state the exact per-purpose check and its generic failure wording ("stops without saying which field differs", I-03 `erreur`).
4. **Session after acceptance.** BR-005 says activation yields a "new authenticated session subject to current grants", but the operation returns a bare `Acknowledgement` and the contract's session rules live on `signIn`. [Proposal, decision needed] (a) keep `Acknowledgement` and route to `/connexion` after acceptance, or (b) additive change: success returns `Session` (like `signIn`, rotating the cookie and CSRF) so J-01's "activate … → permitted home" holds without a second authentication. Recommendation: (b), closer to the rule and the journey.
5. **Refusal codes.** S-03/I-03 render distinct terminal states (expired, revoked, already used, invalid) plus the generic identity stop, while the codes catalog has none of them. [Proposal] add `INVITATION_EXPIRED`, `INVITATION_REVOKED`, `INVITATION_ALREADY_USED` (422, legitimate holder may know the history), keep an unknown/garbled token at `404` indistinguishable from any other unknown token, and use a single generic `RECIPIENT_MISMATCH`-style 422 whose field errors never say which verification input differed.
6. **Anonymous idempotency scope.** 10.1 binds keys to (account, practice, operation, body hash); an anonymous recipient has no account. [Proposal] scope the key to (invitation digest, operation, body hash) or document the anonymous scope in the README; the receipt never stores the raw token or password (contract rule).
7. Runtime-coverage and error-table rows of the contracts README, and the OpenAPI version bump, are updated in the same contract commit.

## 5. Data and isolation slice

- The invitation row is practice-owned (`practice_id`, composite references, RLS under the non-owner runtime role) per the [data dictionary](../planning-artifacts/data-dictionary.md). The anonymous token lookup happens *before* any practice context exists: [Verified observation] ADR-003's bootstrap exception covers exactly this — a restricted identity lookup limited to a token digest (the V2 recovery implementation and its narrow `etaye_identity` functions are the working precedent). The acceptance transaction then opens `PracticeTransaction` in the invitation's practice and commits, atomically: invitation `PENDING → ACCEPTED` (+`accepted_at`), the account (create `ACTIVE` when none matches the normalized recipient email; BR-005 `PENDING_ACTIVATION → activate → ACTIVE`), exactly one membership (TEAM) or one ACTIVE `portal_link` (PATIENT_PORTAL; table exists since V2 with the one-active-link-per-record unique index), the audit event, and the idempotency receipt. No outbox email in increment 1 (M-01/M-02 deferred).
- `portal_link.verified_by/verified_at` are set from the seeded issuer context; a team invitation never creates a portal link and a portal invitation never grants capabilities (BR-005; check C1/C2).
- Raw token and password never appear in audit, receipts or logs; only the digest is stored (BR-005, BR-017). Anonymous refused attempts have no determinate actor practice → operational log only, no practice audit row (BR-017 UD-47 scoping).

## 6. Design evidence and review path (S-03, I-03; I-01 deferred)

- Build from the committed reference states under the 2026-11-18 exception: `design/offline-reference/site/ecrans/S-03/` and `…/I-03/`, the `tokens.css` custom properties and the recipes of design-system sections 4–7, both themes, original logo files. The named reference aliases inside those states (`INV-TEAM-01` "Alex Rivière", `INV-PORTAL-01`, `INV-PORTAL-02` Louis Bernard) are the natural seed identities — currently absent from the dataset (section 9, item 7).
- States required by the catalog beyond the six delivered S-03 files: the patient acceptance form is I-03 `ouvert`; "already active", throttled and loading/submitting states come from SP-STATE/SP-SAVE and the shared patterns; a recipient-mismatched presentation exists only as I-03 `erreur` (generic identity stop) — no separate "mismatch" frame is required by the catalog, the mismatch is the generic stop.
- Keyboard, error summary, focus restoration and announcements follow SP-A11Y and EXPERIENCE.md; the auth shell (`.etaye-auth`, card ≤ 440 px on `papier`) is the layout; React Aria Components per ADR-013 with the D-49/D-52 reservations.
- Review sequence [Proposal]: assemble the S-03/I-03 state set from the reference (captures at the SP-RESPONSIVE widths), present it to Aymen with this packet's open points, then either his explicit family approval (1.2 precedent) or his reliance on UD-16's sampled path, recorded in the tracker without touching `aymen_approval` except by his answer. The SAMPLED evidence of AT-001-C is delivered with the story.

## 7. Behavior checks (each becomes a test on real PostgreSQL through real HTTP; sources cited)

| # | Check | Source |
|---|---|---|
| C1 | Seeded PENDING team invitation accepted once: `ACCEPTED`+`accepted_at`, one ACTIVE membership with exactly the seeded capabilities, account ACTIVE, audit row, no portal link; second accept refused | AT-001-A, BR-005 |
| C2 | Seeded PENDING portal invitation + correct birth date + password: exactly one ACTIVE `portal_link` to the invited record and account, no membership, no capabilities | AT-001-A, BR-004/005 |
| C3 | Expired, revoked and already-used tokens each refuse with their distinct typed state and grant nothing; unknown token → 404 byte-identical to any other unknown token | AT-001-B, spine HTTP conventions |
| C4 | Recipient mismatch (wrong verification) → generic single wording that never names the differing field, no grant, no record/patient disclosure | AT-001-B, BR-004, I-03 `erreur` |
| C5 | Suspension conflict: a suspended account/membership is not silently reactivated by acceptance (no grant beyond current states) | BR-005 recovery rule |
| C6 | Two concurrent accepts of one token → exactly one success; same key+body replays the same outcome; same key+other body → 409; raw token/password absent from receipts, audit and logs | BR-005 atomic single use, AD-7, contract |
| C7 | The invitation of practice B grants nothing in practice A; portal link only to the invited record; RLS/context and identity-role narrowing verified, including a pooled-connection reuse case | AD-3, ADR-003, AT-001-B |
| C8 | Anonymous budget: repeated accept/inspect submissions → 429 with `Retry-After`; CSRF required on accept | 1.2 budget pattern, ADR-004 |
| C9 | Web S-03/I-03: every state renders the reference copy, keyboard-complete, focus restored, axe-core clean, token read from the fragment, never in a query/URL | AT-001-C, SP-A11Y |
| C10 | Real-stack journey: open link → inspect → accept (team and patient) → authenticated destination (per section 4 decision) on the isolated stack | J-01, ADR-011 |

## 8. Test and CI impact

- `apps/api` (Testcontainers, Java 25): new suite(s) beside `AccountAccessHttpTest`/`PracticeIsolationTest` covering C1–C8, with at least two mutation checks (single-use guard removed → C6 red; identity digest lookup widened → C7 red). Expected count grows from 150.
- `apps/web`: `tsc -b`, `build`, smoke suite extended from 19 tests with the S-03/I-03 states and their axe scans; the stubbed probe is untouched; the real outage spec stays as is, and the invitation journey joins the real-stack evidence (C10).
- Documentary: `tools/validate_docs.py`, coverage/hybrid evidence rows, this packet's file, implementation record, continuation note.
- GitHub: `API verify`, `Web verify`, `Planning package checks`, `Analyze tooling`, `CodeQL` required; one local Codex CLI three-lens review; manual CodeRabbit request per delivery rules — [Verified observation] CodeRabbit's free-OSS quota was exhausted on 2026-11-20, so request once, record the outcome, and do not loop.

## 9. Gaps and open questions (each qualified)

1. **Authorization absent** — User decision required (section 12).
2. **Design approval of S-03/I-03** — User decision required: explicit approval (1.2 precedent) or UD-16 sampled path without pre-approval; I-01 stays deferred (D-23). User decision.
3. **Preview operation** (`POST /invitations/inspect` or equivalent) — Verified observation (no such operation exists) + Proposal (shape above).
4. **`displayName` on `Activation`** — Verified observation (schema lacks it; reference form and `Account` require it) + Proposal (conditional add).
5. **`recipientVerification` semantics** (team vs portal; generic failure wording) — To verify with Aymen; reference fixes the portal side as date of birth.
6. **Session on acceptance** (`Acknowledgement` + manual sign-in vs additive `Session` response) — Proposal; recommendation (b); User decision required.
7. **Synthetic dataset extension** — Verified observation: `synthetic-data.json` has no invitation fixtures; the epic needs pending, expired, revoked and recipient-mismatched seeds and the HTML reference already names `INV-TEAM-01` (Alex Rivière) and `INV-PORTAL-01/02`; new personas/records (e.g., a not-yet-linked patient record for `INV-PORTAL-01`) are [Proposal] for Aymen's review — the dataset is a frozen baseline, so the extension is dated and reviewed, never silently rewritten.
8. **Refusal code set and existence-hiding split** (typed 422s for the legitimate holder; 404 for unknown; generic mismatch) — Proposal.
9. **Anonymous idempotency scope** — Proposal (section 4.6).
10. **Post-activation destination in increment 1** (before 1.3): `/compte` is the only allowlisted generic return path today — [Proposal] with the seeded single-membership/team case ending there; patient activation ends on `/compte/patient/{portalLinkId}` if the link exists at once, else `/compte`. To verify during spec writing.
11. **Story index edge** — [Verified observation] `dependsOn` lists only 10.1 while the implementation reuses 1.2's foundations; no change requested, recorded here so the reviewer is not surprised.
12. **DeferredRegister check** — D-23 matches the epic's deferral sentence exactly; no conflict found. Verified observation.
13. **Native landing (DF-9)** — out of scope (increment 3+); invitation links open in the browser in increment 1 (D-32 also notes links work on the application origin). Verified observation.

## 10. Risks to watch

Token digest lookup opened wider than ADR-003 allows; single-use race (advisory lock/row lock on the invitation row); account enumeration through inspect/accept wording (mask everything, generic refusals); the 72-hour seeded expiry drifting relative to test clock (seed expiry relative to fixture load, UD-26 default 72 h); anonymous session rows on `/csrf` reusing D-35 budgets; fragment handling so the token never reaches server logs or referrers; CodeRabbit quota; sample-budget creep of the visual evidence.

## 11. Source versions

Last commit touching each file on `main` at `447fe57` (2026-11-21). Re-read current files before any code; a summary is not a substitute.

| File | Last commit |
|---|---|
| `docs/planning-artifacts/epics/epic-1.md` | `5ba958c` 2026-11-17 |
| `docs/planning-artifacts/prd/prd.md` | `86019eb` 2026-11-19 |
| `docs/planning-artifacts/business-rules.md` | `13f6d12` 2026-11-20 |
| `docs/planning-artifacts/access-matrix.md` | `86019eb` 2026-11-19 |
| `docs/planning-artifacts/architecture/ARCHITECTURE-SPINE.md` | `86019eb` 2026-11-19 |
| `docs/planning-artifacts/data-model.md` | `48a3acf` 2026-11-02 |
| `docs/planning-artifacts/data-dictionary.md` | `5ba958c` 2026-11-17 |
| `docs/planning-artifacts/functional-specification.md` | `86019eb` 2026-11-19 |
| `docs/planning-artifacts/decisions-assumptions.md` | `372fda6` 2026-11-21 |
| `docs/planning-artifacts/deferred-work.md` | `372fda6` 2026-11-21 |
| `docs/planning-artifacts/story-delivery-order.md` | `05ae963` 2026-11-20 |
| `docs/planning-artifacts/roadmap.md` | `04a2910` 2026-11-20 |
| `docs/planning-artifacts/ux/EXPERIENCE.md` | `48a3acf` 2026-11-02 |
| `docs/contracts/openapi.yaml` (0.2.3-planning, 98 operations) | `05ae963` 2026-11-20 |
| `docs/contracts/README.md` | `05ae963` 2026-11-20 |
| `docs/design/design-system.md` | `05dad6f` 2026-11-20 |
| `docs/design/screen-catalog.md` | `05dad6f` 2026-11-20 |
| `docs/design/interaction-catalog.md` | `05dad6f` 2026-11-20 |
| `docs/design/coverage.csv` / `docs/design/html-reception.csv` | `05ae963` 2026-11-20 |
| `docs/design/synthetic-data.md` / `.json` | `48a3acf` 2026-11-02 |
| `docs/test-artifacts/test-strategy.md` | `86019eb` 2026-11-19 |
| `docs/decisions/ADR-003-practice-isolation.md` | `48a3acf` 2026-11-02 |
| `docs/decisions/ADR-004-authentication-sessions.md` | `5ba958c` 2026-11-17 |
| `docs/decisions/ADR-011-delivery-order-and-milestones.md` | `0fc825f` 2026-11-19 |
| `docs/operations/delivery-rules.md` | `04a2910` 2026-11-20 |
| `docs/implementation-artifacts/story-10-1-implementation-record.md` | `8afd51c` 2026-11-20 |
| `docs/implementation-artifacts/story-1-2-implementation-record.md` | `046e8d5` 2026-11-21 |
| `_bmad/custom/config.toml` | `05ae963` 2026-11-20 |

Also consulted: ADR-013/ADR-014, `apps/api/src/main/resources/db/migration/V1–V2`, `apps/web/src/auth/*`, `design/offline-reference/site/ecrans/{S-03,I-01,I-03}/`, `docs/design/coverage-guide.md`, `docs/design/session-recovery.md`, `docs/test-artifacts/acceptance-scenarios.md`, `docs/planning-artifacts/epics/story-index.json`, `docs/planning-artifacts/roadmap-assignments.json` (`acceptInvitation` → increment 1; `issueInvitation`/`revokeInvitation`/`resendInvitation`/`listInvitations` → increment 2).

## 12. Arbitration request (nothing starts before Aymen's answers)

1. **Design**: approve S-03 and I-03 explicitly after the state set is presented (1.2 precedent), or rely on UD-16's SAMPLED path (built desktop-first with sampled evidence) — I-01 stays deferred (D-23).
2. **Contract points** (section 4): preview operation; `displayName`; `recipientVerification` semantics; session-on-acceptance (recommend the additive `Session` response); refusal codes; anonymous idempotency scope.
3. **Dataset**: accept the proposed invitation fixtures (pending/expired/revoked/recipient-mismatched; `INV-TEAM-01` Alex Rivière, `INV-PORTAL-01`, `INV-PORTAL-02` Louis Bernard and any new persona/record they need).
4. **Authorization**: on the above, give (or withhold) the written development authorization for story 1.1; it is recorded as a dated UD row and encoded as `authorized_stories = ["10.1", "10.2", "1.2", "1.1"]` (or the agreed list) with `development_authorized` staying false, exactly as UD-41/UD-45/UD-48 did.
