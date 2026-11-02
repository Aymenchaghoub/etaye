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

## Working assumptions and decisions to revisit

| ID | Classification and working choice | Rationale | Consequences and impact if changed | Owner / explicit revisit trigger |
|---|---|---|---|---|
| A-01 | [ASSUMPTION] One account may hold several practice memberships | Avoid duplicate authentication identities for a practitioner working in two practices | Explicit practice selection/switching; server rechecks membership. Changing this affects identity, navigation and session design. | Aymen; confirm during design review before implementation authorization. |
| A-02 | [ASSUMPTION] Patient identity and record are local to a practice; no automatic cross-practice matching | Isolation is clearer without a global clinical identity | A person can have independent records in both practices; no data is copied across them. | Aymen; revisit only when cross-practice coordination is explicitly requested. |
| A-03 | [ASSUMPTION] Active practitioners with clinical capabilities share practice records | Credible shared general-practice demo; no care-team assignment rules supplied | Team-wide visibility inside a practice. Narrower assignments require access policy, query and UI changes. | Aymen; targeted confirmation before clinical-access implementation. |
| A-04 | [ASSUMPTION] Patient portal links are verified separately per record; account email alone never links records | Prevent accidental identity matching or exposure | Invitations prove token possession plus matching intended recipient and clinical-team identity check; ambiguous links need manual resolution. | Aymen; review activation and identity-link screens before design approval. |
| A-05 | [ASSUMPTION] Initial demo involves adults only; no representatives | Guardianship and delegated access need distinct authorization and consent models | Do not quietly support minors through ordinary self-access. | Aymen; resume via D-08 if minors/representatives enter scope. |
| A-06 | [ASSUMPTION] Practitioner may validate their own draft after explicit review | User requires human validation, not two-person approval | Record validator independently from author; a future four-eyes policy can tighten the rule. | Aymen; review validation prototype before development. |
| A-07 | [ASSUMPTION] Patient can book/reschedule/cancel future eligible appointments through offered slots | User explicitly includes patient appointment management; self-service policy details not supplied | Working cutoff and slot-duration policies are practice settings, not hardcoded clinical rules. | Aymen; confirm default booking/cancellation windows during scheduling design. |
| A-08 | [ASSUMPTION] Initial ordinary upload accepts reviewed PDF and common images with finite configured limits | Gives useful record documents without implying intelligent import | Limits, quarantine and supported previews must be explicit; file inspection architecture is technical. | Aymen/technical reviewer; lock supported formats before upload implementation. |
| A-09 | Proposal: English-facing demo remains optional | International audience does not establish product language | Introduce message keys/locale-aware formatting; do not duplicate all Figma frames now. | Aymen; decide after complete French design review, before public demo copy freeze. |
| A-10 | [ASSUMPTION] Follow-up overview is deferred | A consultation report and patient explanation already cover the initial publication journey | No separate follow-up PDF in required design scope until its distinct value is agreed. | Aymen; resume D-07 when a specific cross-visit use case is accepted. |
| A-11 | [ASSUMPTION] No automatic migration of legacy patient records | New demo is synthetic and no real data is permitted | Legacy is evidence only; no code or data copied into the target application. | Aymen; explicit migration request with source rights and synthetic mapping needed. |
| A-12 | To verify: final hosting budget and reliability expectation | Free tiers change and cold starts may impair demos | Hosting assessment must distinguish free feasibility from a reliable recruiter demo. | Aymen; choose deployment option before implementation of deployment work. |

## Permission refinement proposed during PR review

**PD-001 — Proposal, 2026-11-02:** Name the access matrix's previously unspecified demographic-registration grant PATIENT_REGISTER. A scheduler uses SCHEDULE_MANAGE for the minimal local patient lookup; registration requires both grants. It creates only identity plus an empty record boundary and never authorizes clinical read/write, portal linking or identity correction. A scheduler without PATIENT_REGISTER must select an existing patient or ask an authorized team member to register the person. This closes a contract gap rather than introducing another clinical role. Review S-07/S-08/I-04/I-10 restricted variants with Aymen before implementation; changing it affects registration permissions, two scheduling API operations and AT-015-D/E.

## Repository setup working choices

- **User decision, 2026-11-02:** Aymen explicitly authorized public GitHub visibility, superseding the initial private working default after a publication scan.
- **User decision, 2026-11-02:** Initial documentation/tooling CI, first PR and useful free GitHub security/quality features are authorized. No application implementation or paid service activation is authorized.
- [ASSUMPTION] Local Git identity uses the authenticated account’s noreply address to avoid inheriting the global professional email; global settings were preserved.

## Resolution protocol

Record a new dated row or explicit supersession when Aymen resolves a choice; update the authoritative rules, affected designs, contracts, stories and traceability together. Do not label the whole package “Approved” because an agent has reviewed it. The design coverage tracker owns surface review status; future BMAD implementation tracking owns implementation progress.
