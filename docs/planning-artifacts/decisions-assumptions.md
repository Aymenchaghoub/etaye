# Decisions and assumptions register

Status: Draft for design. Date: 2026-11-02. A proposal reviewed by an agent is not user approval. Structural technical rationale belongs in [ADRs](../decisions/), not duplicated here.

## Evidence labels

- **User decision:** explicitly supplied or subsequently accepted by Aymen.
- **Verified observation:** directly inspected evidence with date/version; see [source register](../references/source-register.md).
- **Proposal:** a recommended choice that has not been accepted by Aymen.
- **[ASSUMPTION]:** reversible working choice with rationale and consequences.
- **To verify:** information requiring access, evidence or an explicit decision.

## Confirmed user decisions

| ID | Decision | Consequence |
|---|---|---|
| UD-01 | Public name Étaye; technical identifier etaye | Preserve accents in brand; use ASCII for repository/package identifiers. |
| UD-02 | GitHub and short-lived PR branches targeting main | No GitLab or permanent integration branch. |
| UD-03 | Portfolio project; all demo people/data synthetic | No claim of real care readiness or clinical effectiveness. |
| UD-04 | Spring Boot, React with TypeScript, PostgreSQL | Supported versions and implementation choices need documented evaluation. |
| UD-05 | Several isolated practices; at least two in demo | Every data/API/file/job path must enforce practice boundaries. |
| UD-06 | Physician primary; complementary patient portal | Dense professional work area; simpler patient experience. |
| UD-07 | Human validation and explicit patient publication | Validation and publication remain separate operations. |
| UD-08 | French conversations; English repository documents and commits | Preserve current French Figma copy during this phase. |
| UD-09 | Existing Figma foundations/logo are starting point | Inspect actual values; do not redraw the logo or redesign the file now. |
| UD-10 | Planning → complete design/review → Aymen authorization → implementation | No scaffold, domain code, migration, app pipeline or deployment now. |
| UD-11 | Intelligent laboratory PDF extraction later | Ordinary upload, viewing and manual result entry stay in initial scope. |

### User decisions recorded on 2026-11-17

Source: Aymen's answers in the working session of that date (decision sheet DA-1 to DA-14) and his statement that the platform must also work as an Android and iOS app. Each row supersedes the matching proposal or assumption; nothing else is approved by this record. Numbering note: the assumptions A-13, A-14 and A-16 proposed in the 2026-11-17 report became UD-13, UD-14 and UD-15; A-15 stays open; A-16 is now the increment allocation assumption. UD-29 to UD-37 record Aymen's later validation, the same day, of every recommendation on the open decisions of section 11 of the roadmap; only the development authorization (UD-28) is kept by him.

| ID | Decision | Consequence |
|---|---|---|
| UD-12 | Étaye is available on the web and as an installable Android and iOS app, from one API and as much shared front-end code as possible; the architecture is prepared for it from now on. Publishing in app stores is a separate decision (settled by UD-39: no publication). Refined by UD-38: the shared code is TypeScript packages, not UI components. | ADR-008 and ADR-009 (accepted, UD-29), AD-11, NFR-15, stories 10.2 and 10.4 to 10.8, D-15 to D-19 and D-32. |
| UD-13 | The native app targets the patient first; practitioners use the responsive web app and its installable form. | A native practitioner app is D-16. |
| UD-14 | Packaging order: installable web app (PWA), then a Capacitor shell with bundled assets and an opaque server-side session token. No React Native, Flutter or Kotlin rewrite. **Superseded 2026-11-19 by UD-38 for the shell technology:** the PWA-first order and the opaque server-side session token stand; Capacitor is no longer planned and React Native is now chosen. | Stories 10.4 to 10.6; the reuse of React and TypeScript (UD-04) is preserved. |
| UD-15 | No offline clinical mode and no clinical data at rest on devices in the first versions. | D-17; the service worker caches the application shell only. |
| UD-16 | Named exception to the complete-design gate: only the core families (roadmap section 9) must be designed, reviewed and approved before the increment 1 build; other families are built desktop-first in code with sampled visual evidence. | Evidence levels CORE and SAMPLED; the sampling numbers are confirmed by UD-37; the coverage tracker is not rewritten. |
| UD-17 | PD-001 is accepted: SCHEDULE_MANAGE with the separately assigned PATIENT_REGISTER lets a scheduler look up and register a practice-local patient (identity and empty record only). | BR-004, access matrix, S-07 and S-08 restricted variants, two scheduling operations and AT-015-D/E in increment 1. |
| UD-18 | A practitioner acknowledges a patient's visit preparation through an explicit operation. | `acknowledgePreparation` added to the contract; authorship never changes; a later submission needs a new acknowledgement. |
| UD-19 | An account can hold several practice memberships and switch practice in increment 1 (A-01 confirmed). | Story 1.3 in increment 1; S-05 and I-20. |
| UD-20 | A-03 (practice-wide clinical access among active clinical practitioners) and A-06 (self-validation after an explicit review) are confirmed. | Rules unchanged; validator equal to author is shown honestly. |
| UD-21 | Increment 1 books from a fixed seeded slot grid with atomic booking and cancellation; the booking and cancellation window policy is decided later. | A-07 partly decided; D-20, D-21, D-22; O-8. |
| UD-22 | The clinician brief has no review lifecycle: it stays DRAFT and a practitioner records an append-only acknowledgement of one exact version; it is never validated or published. | BR-012, BR-014, `acknowledgeBrief`, data dictionary; design follow-ups DF-1 and DF-2. |
| UD-23 | A practice-local patient record cannot have overlapping active appointments, whoever books. | BR-006 and the database constraint now agree. |
| UD-24 | Demo files live in PostgreSQL or on a persistent volume behind the FileStore port; object storage and real antivirus scanning are deferred. | ADR-006 amended; D-25, D-28. |
| UD-25 | Hosting: €0 first (local compose and free best-effort hosting), then a VPS decision after the story 10.3 benchmark. | A-12 stays To verify until measured; D-15, D-32. |
| UD-26 | The twelve recommendations of the design-review decision document of 2026-11-03 are adopted as written: patient-branded entry URL on the same account system, password length 12, no consent checkbox before sending a preparation, online cancellation kept, visit-reason error kept, the label "Retirer la publication", invitation 72 hours and recovery 15 minutes, explicit administrator assignment kept, per-glyph font fallback, "La tâche a échoué". | Copy and defaults for design and later stories; the acknowledgement gap is closed by UD-18. |
| UD-27 | Specifications are frozen: no documentary scope extension until the first code runs; each change goes through a one-page mini-ADR. | Governance of docs/decisions. |
| UD-28 | Development is not yet authorized. **Partly lifted on 2026-11-19 by UD-41 for story 10.1 only; still in force for every other story.** | AD-1 and D-12 stay in force; Aymen will authorize in writing. |
| UD-29 | ADR-008 (multi-client access) and ADR-009 (API compatibility) are accepted as written. The native session duration (A-15) stays open and is decided in story 10.5; the To verify items of ADR-008 are spike checks. | The five planned operations are added to the contract before story 10.2 (`getApiMeta`) and story 10.5 (the other four). |
| UD-30 | The clinician brief is composed by a deterministic, side-effect-free composer from structured facts at the record watermark; an AI task can only rephrase it later (D-26) and never adds a fact (proposal P-1). | BR-011, AI specification, story 5.2; design follow-up DF-15. |
| UD-31 | Mechanical support checks run on every citation before validation; flags block validation until corrected or dispositioned; the result is never a score and never labeled verified (proposal P-2). | BR-012, data dictionary, contract (`Citation.mechanicalCheck`), stories 5.3, 6.3, 6.4; DF-16. |
| UD-32 | The record can be viewed as known at a past instant through a read-only `asOf` view (proposal P-3). | BR-008, contract (`AsOf` parameter on five read operations), stories 3.2 and 3.3; DF-17. |
| UD-33 | Isolation proof: the adversarial matrix is generated and published by CI, and an interactive journey replays the refusals (proposal P-4). | Story 1.4; DF-18. |
| UD-34 | Reproducible demo package: `docker compose`, a scripted Playwright journey that is also the test and the video recorder, a static warm-up page and a portfolio-first README (proposal P-5). | Stories 9.1 and 10.3; DF-18. |
| UD-35 | Module-boundary verification and axe-core in the Playwright journeys; ArchUnit or Spring Modulith is chosen at the story 10.1 spike (proposal P-6). | Stories 10.1 and 10.2; NFR-05, NFR-09. |
| UD-36 | Real-model evaluation uses 12 to 15 hand-annotated cases with a single annotator stated as a limit (proposal P-7). | AI specification, D-26. |
| UD-37 | Defaults confirmed for the other open decisions: increments in the order 2 then 3 (O-2, replaced on 2026-11-19 by UD-40: 3 then 2); S-07, S-08, S-17, S-26 and S-27 stay SAMPLED (O-3); sampled evidence is 8 to 10 screens at 2 widths (O-4); assumptions A-02, A-04, A-05, A-08, A-09, A-10 and A-11 are confirmed as working baselines with their revisit triggers (O-11). | Decisions that need measurements or costs stay gated and are not blocking: hosting after the benchmark (O-5), booking windows at the start of increment 2 (O-8), stores (O-9), domain and privacy policy (O-10), tool and versions at the spike (O-13), native session duration in story 10.5 (O-1). |

### User decisions recorded on 2026-11-19

Source: Aymen's statements in the working session of that date. He wanted the application to show three skills (an API, a web interface and a mobile app for iOS and Android), said he does not intend to publish the mobile apps and will use them as demos, then chose React Native with Expo among the options presented. He stated the purpose of the demos: fluid, modern screens on a phone that use the real API, a visual identity consistent between web and mobile, and command of the technologies and the architecture. UD-27 applies: the change goes through the one-page mini-ADR 010, and nothing else is approved by this record.

| ID | Decision | Consequence |
|---|---|---|
| UD-38 | The native client is a React Native app built with Expo, in TypeScript. It shares the generated API client, the design tokens and the domain types with the web app, not its UI components. This supersedes the Capacitor shell and the "no React Native" clause of UD-14. | [ADR-010](../decisions/ADR-010-native-client-react-native-expo.md); ADR-008 amended; stories 10.2 and 10.5 to 10.8; DF-19. |
| UD-39 | The mobile apps are not published: they serve as demos. No Apple or Google developer account or store fee is opened. A native iOS demonstration waits for a Mac (simulator) or for an explicit decision on the Apple program; the installed web app serves iOS users for free. | D-15 Not planned; roadmap O-9 closed; story 10.8 re-scoped; ADR-010 point 5. |
| UD-40 | ADR-011 is accepted as written: the delivery order is increment 1, then 3, then 2; increment 1 is built in six milestones with effort budgets instead of dates; the story 10.1 spike has the exit criteria of the ADR; the cut rule applies. The budgets stay placeholders recalibrated after M1, and the cut candidates stay a proposal until a milestone gate. | [ADR-011](../decisions/ADR-011-delivery-order-and-milestones.md); roadmap section 1 and O-2; replaces the order of UD-37. |
| UD-41 | Development is authorized for **story 10.1 only** (spike, then walking skeleton), as a named exception to the core design gate of UD-16, because the story has no screen. UD-28 stays in force for every other story. Recorded as `authorized_stories = ["10.1"]` in `_bmad/custom/config.toml`; `development_authorized` stays false. | [Context packet](../implementation-artifacts/story-10-1-context-packet.md); epic 10 story 10.1; AGENTS.md; the eleven BMAD overrides. A new story needs its own written authorization. |

### User decisions recorded on 2026-11-20

| ID | Decision | Consequence |
|---|---|---|
| UD-42 | Functional motion is added to the design system as proposed: four state-carrying patterns (save button states, the file drop target, the one-shot trace of the sign on S-01, an overflow fade on bounded lists), the proposed token `--duree-trace` of 1200 ms, and animating the sign on S-01 only. His words, after he played the interactive proposal: "je valide". [ASSUMPTION] read as acceptance of the whole proposal; he can amend any point. | [ADR-012](../decisions/ADR-012-functional-motion.md) (the mini-ADR UD-27 requires); [motion study](../design/motion-study/README.md) with its playable prototype; design-system sections 3, 4.5, 7 and 12; I-08, S-01, S-13 and the save pattern. Figma variants are still to be drawn; no application code. |
| UD-43 | The design-system review is closed with seven decisions, after Aymen read two interactive pages built with the real tokens and CSS. (1) React Aria Components with `@internationalized/date` is the web behaviour layer, Base UI the documented fallback, Radix not retained, and the product has a calendar (his words: "Calendrier : oui"). (2) The type roles follow the mockups (14 to 29 roles). (3) Six corrections of CSS and documents are decided, not applied. (4) The web theme follows the system, with a manual override and `color-scheme`. (5) A token generator is written in the repository at story 10.6; `tokens.json` stays the source. (6) ADR-010 is confirmed with five execution precisions and no native component library. (7) Missing interaction specifications are deferred, not written (his option "(b) différer"), so UD-27 is not lifted. He answered "OK" to each point on the preview page. [ASSUMPTION] "OK" reads as acceptance of the details inside each card, including the typography table. He then reviewed a specimen page of the 29 type roles and answered OK to the 15 added roles, "garder (29 rôles)" for the two touch-target mono values (the preview label said 27), OK to the 9 normalisations and OK to the A4 exception. | [ADR-013](../decisions/ADR-013-web-component-behaviour-layer.md); [ADR-014](../decisions/ADR-014-design-system-closure.md) (the mini-ADRs UD-27 requires); [type roles](../design/typography-closure.md); [review record](../design/reviews/2026-11-20-design-system-review.md); design-system section 15; D-42 to D-52. No token file, CSS file or code changes; UD-28 stays in force. |

### Corrections applied on 2026-11-17 under Aymen's authorization to correct the package

| ID | Correction | Where |
|---|---|---|
| C-01 | The source manifest of a CLINICIAN_BRIEF is derived by the server from the record watermark; a client subset is accepted only for reports and explanations, and omissions are recorded with their reason. | BR-011, AI specification, contract (`Generation`) |
| C-02 | Contract hygiene: errors declared per operation, ETag only on versioned aggregates, one `/tasks` collection, `operationId` names, one `Capability` schema, machine-checkable conditional schemas, the `listPractitioners` description. | Contract 0.2.0-planning and README |
| C-03 | Story text, dependencies, delivery increment and visual-evidence wording replaced by story-specific content; epic 10 added. | Epics, story index, roadmap |
| C-04 | Validator checks for roadmap completeness, story dependencies and the contract policies. | tools/validate_docs.py |

These are reviewable in the pull request that carries them; Aymen can revert any of them. The remaining open decisions are listed in section 11 of the [roadmap](roadmap.md); UD-29 to UD-37 settled the others.

## Working assumptions and decisions to revisit

| ID | Classification and working choice | Rationale | Consequences and impact if changed | Owner / explicit revisit trigger |
|---|---|---|---|---|
| A-01 | [ASSUMPTION] One account may hold several practice memberships | Avoid duplicate authentication identities for a practitioner working in two practices | Explicit practice selection/switching; server rechecks membership. Changing this affects identity, navigation and session design. | Aymen; confirm during design review before implementation authorization. **Status 2026-11-17:** confirmed by Aymen (UD-19): practice switching is in increment 1. |
| A-02 | [ASSUMPTION] Patient identity and record are local to a practice; no automatic cross-practice matching | Isolation is clearer without a global clinical identity | A person can have independent records in both practices; no data is copied across them. | Aymen; revisit only when cross-practice coordination is explicitly requested. **Status 2026-11-17:** confirmed as a working baseline (UD-37); revisit trigger unchanged. |
| A-03 | [ASSUMPTION] Active practitioners with clinical capabilities share practice records | Credible shared general-practice demo; no care-team assignment rules supplied | Team-wide visibility inside a practice. Narrower assignments require access policy, query and UI changes. | Aymen; targeted confirmation before clinical-access implementation. **Status 2026-11-17:** confirmed by Aymen (UD-20). |
| A-04 | [ASSUMPTION] Patient portal links are verified separately per record; account email alone never links records | Prevent accidental identity matching or exposure | Invitations prove token possession plus matching intended recipient and clinical-team identity check; ambiguous links need manual resolution. | Aymen; review activation and identity-link screens before design approval. **Status 2026-11-17:** confirmed as a working baseline (UD-37); revisit trigger unchanged. |
| A-05 | [ASSUMPTION] Initial demo involves adults only; no representatives | Guardianship and delegated access need distinct authorization and consent models | Do not quietly support minors through ordinary self-access. | Aymen; resume via D-08 if minors/representatives enter scope. **Status 2026-11-17:** confirmed as a working baseline (UD-37); revisit trigger unchanged. |
| A-06 | [ASSUMPTION] Practitioner may validate their own draft after explicit review | User requires human validation, not two-person approval | Record validator independently from author; a future four-eyes policy can tighten the rule. | Aymen; review validation prototype before development. **Status 2026-11-17:** confirmed by Aymen (UD-20). |
| A-07 | [ASSUMPTION] Patient can book/reschedule/cancel future eligible appointments through offered slots | User explicitly includes patient appointment management; self-service policy details not supplied | Working cutoff and slot-duration policies are practice settings, not hardcoded clinical rules. | Aymen; confirm default booking/cancellation windows during scheduling design. **Status 2026-11-17:** partly decided (UD-21): increment 1 uses a fixed seeded slot grid with atomic book and cancel; the window policy stays open (D-21, O-8). |
| A-08 | [ASSUMPTION] Initial ordinary upload accepts reviewed PDF and common images with finite configured limits | Gives useful record documents without implying intelligent import | Limits, quarantine and supported previews must be explicit; file inspection architecture is technical. | Aymen/technical reviewer; lock supported formats before upload implementation. **Status 2026-11-17:** confirmed as a working baseline (UD-37); revisit trigger unchanged. |
| A-09 | Proposal: English-facing demo remains optional | International audience does not establish product language | Introduce message keys/locale-aware formatting; do not duplicate all Figma frames now. | Aymen; decide after complete French design review, before public demo copy freeze. **Status 2026-11-17:** confirmed as a working baseline (UD-37); revisit trigger unchanged. |
| A-10 | [ASSUMPTION] Follow-up overview is deferred | A consultation report and patient explanation already cover the initial publication journey | No separate follow-up PDF in required design scope until its distinct value is agreed. | Aymen; resume D-07 when a specific cross-visit use case is accepted. **Status 2026-11-17:** confirmed as a working baseline (UD-37); revisit trigger unchanged. |
| A-11 | [ASSUMPTION] No automatic migration of legacy patient records | New demo is synthetic and no real data is permitted | Legacy is evidence only; no code or data copied into the target application. | Aymen; explicit migration request with source rights and synthetic mapping needed. **Status 2026-11-17:** confirmed as a working baseline (UD-37); revisit trigger unchanged. |
| A-12 | To verify: final hosting budget and reliability expectation | Free tiers change and cold starts may impair demos | Hosting assessment must distinguish free feasibility from a reliable recruiter demo. | Aymen; choose deployment option before implementation of deployment work. **Status 2026-11-17:** UD-25: €0 first, VPS decision after the story 10.3 benchmark; stays To verify until measured. |
| A-15 | To verify: native session duration and refresh policy | ADR-004's 30-minute inactivity and 8-hour absolute limits suit a browser, not an installed app | Server-side revocation stays immediate; a longer native lifetime needs device registration and a re-authentication rule | Aymen; decide in story 10.5 (D-19, O-1); ADR-008 was accepted without a number (UD-29) |
| A-16 | [ASSUMPTION] The increment allocation in docs/planning-artifacts/roadmap.md | Gives long-term visibility and a smaller first slice | Reordering changes story dependencies, exit criteria and the deferred register | Aymen; may reorder at any time, recorded as a dated row |
| A-17 | [ASSUMPTION] Aymen can borrow an Android phone and a Mac from friends for the mobile demos (his statement of 2026-11-19) | Gives real-device evidence on Android and the iOS Simulator at no cost | A borrowed Mac gives the iOS Simulator only: any iPhone build still needs the paid Apple program (ADR-010). The Mac must run an Xcode version that supports the chosen Expo SDK (To verify before borrowing). Every evidence report records the device model and OS version; only synthetic data and no personal account are used on a borrowed device | Aymen; revisit when a device is no longer available or when story 10.6 starts |

## Permission refinement proposed during PR review

**PD-001 — Proposal, 2026-11-02:** Name the access matrix's previously unspecified demographic-registration grant PATIENT_REGISTER. A scheduler uses SCHEDULE_MANAGE for the minimal local patient lookup; registration requires both grants. It creates only identity plus an empty record boundary and never authorizes clinical read/write, portal linking or identity correction. A scheduler without PATIENT_REGISTER must select an existing patient or ask an authorized team member to register the person. This closes a contract gap rather than introducing another clinical role. Review S-07/S-08/I-04/I-10 restricted variants with Aymen before implementation; changing it affects registration permissions, two scheduling API operations and AT-015-D/E. **Status 2026-11-17:** accepted by Aymen (UD-17).

## Repository setup working choices

- **User decision, 2026-11-02:** Aymen explicitly authorized public GitHub visibility, superseding the initial private working default after a publication scan.
- **User decision, 2026-11-02:** Initial documentation/tooling CI, first PR and useful free GitHub security/quality features are authorized. No application implementation or paid service activation is authorized.
- [ASSUMPTION] Local Git identity uses the authenticated account’s noreply address to avoid inheriting the global professional email; global settings were preserved.

## Resolution protocol

Record a new dated row or explicit supersession when Aymen resolves a choice; update the authoritative rules, affected designs, contracts, stories and traceability together. Do not label the whole package “Approved” because an agent has reviewed it. The design coverage tracker owns surface review status; future BMAD implementation tracking owns implementation progress.
