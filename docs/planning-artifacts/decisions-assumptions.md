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

Source: Aymen's answers in the working session of that date (decision sheet DA-1 to DA-14) and his statement that the platform must also work as an Android and iOS app. Each row supersedes the matching proposal or assumption; nothing else is approved by this record. Numbering note: the assumptions A-13, A-14 and A-16 proposed in the 2026-11-17 report became UD-13, UD-14 and UD-15; A-15 stays open; A-16 is now the increment allocation assumption.

| ID | Decision | Consequence |
|---|---|---|
| UD-12 | Étaye is available on the web and as an installable Android and iOS app, from one API and as much shared front-end code as possible; the architecture is prepared for it from now on. Publishing in app stores is a separate decision. | ADR-008 and ADR-009 (Proposed), AD-11, NFR-15, stories 10.2 and 10.4 to 10.8, D-15 to D-19 and D-32. |
| UD-13 | The native app targets the patient first; practitioners use the responsive web app and its installable form. | A native practitioner app is D-16. |
| UD-14 | Packaging order: installable web app (PWA), then a Capacitor shell with bundled assets and an opaque server-side session token. No React Native, Flutter or Kotlin rewrite. | Stories 10.4 to 10.6; the reuse of React and TypeScript (UD-04) is preserved. |
| UD-15 | No offline clinical mode and no clinical data at rest on devices in the first versions. | D-17; the service worker caches the application shell only. |
| UD-16 | Named exception to the complete-design gate: only the core families (roadmap section 9) must be designed, reviewed and approved before the increment 1 build; other families are built desktop-first in code with sampled visual evidence. | Evidence levels CORE and SAMPLED; the sampling numbers stay [ASSUMPTION] (O-4); the coverage tracker is not rewritten. |
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
| UD-28 | Development is not yet authorized. | AD-1 and D-12 stay in force; Aymen will authorize in writing. |

### Corrections applied on 2026-11-17 under Aymen's authorization to correct the package

| ID | Correction | Where |
|---|---|---|
| C-01 | The source manifest of a CLINICIAN_BRIEF is derived by the server from the record watermark; a client subset is accepted only for reports and explanations, and omissions are recorded with their reason. | BR-011, AI specification, contract (`Generation`) |
| C-02 | Contract hygiene: errors declared per operation, ETag only on versioned aggregates, one `/tasks` collection, `operationId` names, one `Capability` schema, machine-checkable conditional schemas, the `listPractitioners` description. | Contract 0.2.0-planning and README |
| C-03 | Story text, dependencies, delivery increment and visual-evidence wording replaced by story-specific content; epic 10 added. | Epics, story index, roadmap |
| C-04 | Validator checks for roadmap completeness, story dependencies and the contract policies. | tools/validate_docs.py |

These are reviewable in the pull request that carries them; Aymen can revert any of them. Open decisions after this record are listed in section 11 of the [roadmap](roadmap.md).

## Working assumptions and decisions to revisit

| ID | Classification and working choice | Rationale | Consequences and impact if changed | Owner / explicit revisit trigger |
|---|---|---|---|---|
| A-01 | [ASSUMPTION] One account may hold several practice memberships | Avoid duplicate authentication identities for a practitioner working in two practices | Explicit practice selection/switching; server rechecks membership. Changing this affects identity, navigation and session design. | Aymen; confirm during design review before implementation authorization. **Status 2026-11-17:** confirmed by Aymen (UD-19): practice switching is in increment 1. |
| A-02 | [ASSUMPTION] Patient identity and record are local to a practice; no automatic cross-practice matching | Isolation is clearer without a global clinical identity | A person can have independent records in both practices; no data is copied across them. | Aymen; revisit only when cross-practice coordination is explicitly requested. |
| A-03 | [ASSUMPTION] Active practitioners with clinical capabilities share practice records | Credible shared general-practice demo; no care-team assignment rules supplied | Team-wide visibility inside a practice. Narrower assignments require access policy, query and UI changes. | Aymen; targeted confirmation before clinical-access implementation. **Status 2026-11-17:** confirmed by Aymen (UD-20). |
| A-04 | [ASSUMPTION] Patient portal links are verified separately per record; account email alone never links records | Prevent accidental identity matching or exposure | Invitations prove token possession plus matching intended recipient and clinical-team identity check; ambiguous links need manual resolution. | Aymen; review activation and identity-link screens before design approval. |
| A-05 | [ASSUMPTION] Initial demo involves adults only; no representatives | Guardianship and delegated access need distinct authorization and consent models | Do not quietly support minors through ordinary self-access. | Aymen; resume via D-08 if minors/representatives enter scope. |
| A-06 | [ASSUMPTION] Practitioner may validate their own draft after explicit review | User requires human validation, not two-person approval | Record validator independently from author; a future four-eyes policy can tighten the rule. | Aymen; review validation prototype before development. **Status 2026-11-17:** confirmed by Aymen (UD-20). |
| A-07 | [ASSUMPTION] Patient can book/reschedule/cancel future eligible appointments through offered slots | User explicitly includes patient appointment management; self-service policy details not supplied | Working cutoff and slot-duration policies are practice settings, not hardcoded clinical rules. | Aymen; confirm default booking/cancellation windows during scheduling design. **Status 2026-11-17:** partly decided (UD-21): increment 1 uses a fixed seeded slot grid with atomic book and cancel; the window policy stays open (D-21, O-8). |
| A-08 | [ASSUMPTION] Initial ordinary upload accepts reviewed PDF and common images with finite configured limits | Gives useful record documents without implying intelligent import | Limits, quarantine and supported previews must be explicit; file inspection architecture is technical. | Aymen/technical reviewer; lock supported formats before upload implementation. |
| A-09 | Proposal: English-facing demo remains optional | International audience does not establish product language | Introduce message keys/locale-aware formatting; do not duplicate all Figma frames now. | Aymen; decide after complete French design review, before public demo copy freeze. |
| A-10 | [ASSUMPTION] Follow-up overview is deferred | A consultation report and patient explanation already cover the initial publication journey | No separate follow-up PDF in required design scope until its distinct value is agreed. | Aymen; resume D-07 when a specific cross-visit use case is accepted. |
| A-11 | [ASSUMPTION] No automatic migration of legacy patient records | New demo is synthetic and no real data is permitted | Legacy is evidence only; no code or data copied into the target application. | Aymen; explicit migration request with source rights and synthetic mapping needed. |
| A-12 | To verify: final hosting budget and reliability expectation | Free tiers change and cold starts may impair demos | Hosting assessment must distinguish free feasibility from a reliable recruiter demo. | Aymen; choose deployment option before implementation of deployment work. **Status 2026-11-17:** UD-25: €0 first, VPS decision after the story 10.3 benchmark; stays To verify until measured. |
| A-15 | To verify: native session duration and refresh policy | ADR-004's 30-minute inactivity and 8-hour absolute limits suit a browser, not an installed app | Server-side revocation stays immediate; a longer native lifetime needs device registration and a re-authentication rule | Aymen; decide when ADR-008 is accepted (D-19, O-1) |
| A-16 | [ASSUMPTION] The increment allocation in docs/planning-artifacts/roadmap.md | Gives long-term visibility and a smaller first slice | Reordering changes story dependencies, exit criteria and the deferred register | Aymen; may reorder at any time, recorded as a dated row |

## Permission refinement proposed during PR review

**PD-001 — Proposal, 2026-11-02:** Name the access matrix's previously unspecified demographic-registration grant PATIENT_REGISTER. A scheduler uses SCHEDULE_MANAGE for the minimal local patient lookup; registration requires both grants. It creates only identity plus an empty record boundary and never authorizes clinical read/write, portal linking or identity correction. A scheduler without PATIENT_REGISTER must select an existing patient or ask an authorized team member to register the person. This closes a contract gap rather than introducing another clinical role. Review S-07/S-08/I-04/I-10 restricted variants with Aymen before implementation; changing it affects registration permissions, two scheduling API operations and AT-015-D/E. **Status 2026-11-17:** accepted by Aymen (UD-17).

## Repository setup working choices

- **User decision, 2026-11-02:** Aymen explicitly authorized public GitHub visibility, superseding the initial private working default after a publication scan.
- **User decision, 2026-11-02:** Initial documentation/tooling CI, first PR and useful free GitHub security/quality features are authorized. No application implementation or paid service activation is authorized.
- [ASSUMPTION] Local Git identity uses the authenticated account’s noreply address to avoid inheriting the global professional email; global settings were preserved.

## Resolution protocol

Record a new dated row or explicit supersession when Aymen resolves a choice; update the authoritative rules, affected designs, contracts, stories and traceability together. Do not label the whole package “Approved” because an agent has reviewed it. The design coverage tracker owns surface review status; future BMAD implementation tracking owns implementation progress.
