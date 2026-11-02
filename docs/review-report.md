# Planning package review

Date:2026-11-02. Verdict: **Ready for complete design work; development is not authorized.** This is a documentary consistency review, not application testing, clinical validation, user design approval or an independently completed security audit.

## Method and review provenance

Read current requirements/rules/journeys, access, catalogs/fictional content, architecture/data/API, AI/security/test/delivery and handoff authorities. Resolved the installed BMAD6.12.4 review customization. Applied edge-case path tracing, verification-gap perspective and structure→prose review sequentially after authoring agents hit their usage limit. No independent completed review is claimed. No build workflow or application test ran. Mechanical architecture lint and OpenAPI validation are distinct from this semantic review.

The persistent future-development perspectives are silent failures (partial/after-commit/async outcomes), edge cases (missing/stale/concurrent/denied states) and AC compliance (actual observed result vs acceptance text). Editorial structure kept canonical authorities separate and removed exported duplicates; prose preserved English technical docs and French product copy.

## Findings and disposition

The machine-readable [findings](test-artifacts/review-findings.json) retain lens, location, trigger, guard and consequence without invented rankings. They include issues found during progressive authoring and their final disposition; a fixed historical finding is not an unresolved defect.

| Lens | Location / finding | Resolution |
|---|---|---|
|edge-case-hunter|docs/contracts/openapi.yaml: patient sources: Portal source response originally reused internal Source schema|Fixed: Use PublishedSource/Page projection with approved excerpt only|
|edge-case-hunter|docs/design/synthetic-data.json: measurement lineage: Corrected weight and earlier measurement lacked immutable dated source objects|Fixed: Add SRC-A-007-v1/v2 correction lineage and SRC-A-008; attach every measurement source|
|edge-case-hunter|docs/design/pdf-email-specifications.md: P-01: Weight example referred to patient allergy source|Fixed: Reference SRC-A-007-v2 and original correction source|
|edge-case-hunter|docs/design/synthetic-data.json: patient explanation v1: Published explanation claimed an internal report was available|Fixed: Say shared consultation information, not an unpublished report|
|edge-case-hunter|docs/contracts/openapi.yaml: visit preparation: Single conditional PUT conflated first creation with versioned editing|Fixed: Separate idempotent POST creation and If-Match PATCH edit|
|edge-case-hunter|docs/contracts/openapi.yaml: context discovery: Practitioner and own portal-link lists were missing|Fixed: Add typed practitioner and own PortalLink endpoints|
|verification-gap|docs/planning-artifacts/prd/prd.md: Generated requirement headings and cross references retained placeholders|Fixed: Resolve all20 headings/refs and fail documentary validation on unresolved authoring placeholders|
|verification-gap|docs/design/figma/resolved-tokens.json: typography: Initial extraction selected the role label instead of the sample|Fixed: Read applied sample node/style, preserve exact14 node references|
|verification-gap|docs/operations/technology-and-hosting-sources.md: Direct Sonar monorepo documentation was inaccessible|Recorded limitation: Distinguish linked official setup from unverified plan binding; require account check before activation|
|structure|docs/design/claude-chat-kit and coordination copies: Derived chat distribution duplicated canonical repository documents|Fixed: Export to requested Desktop folder; remove superseded coordination/lookup copies|
|prose|docs/planning-artifacts/epics/ and docs/README.md: Planning completeness might be mistaken for implementation readiness|Fixed: Use backlog/Ready for design and explicit approval/authorization gate|
|verification-gap|docs/workflow-record.md: independent reviews: Authoring subagents stopped at usage limit|Recorded limitation: Record coordinator sequential review and do not claim independent completed review|

## Verification evidence

- `python3 tools/validate_docs.py`: link/anchor integrity,20 FR requirements covered by37 backlog stories,76 S/I/P/M families, contract local refs/permissions/path parameters, synthetic relationships and publication safeguards,15 exported asset hashes, shared-source import/link, no application scaffold/workflow and project-secret pattern scan. Exact current results: [document-validation.json](test-artifacts/document-validation.json).
- `uvx openapi-spec-validator docs/contracts/openapi.yaml`: **OK**, full OpenAPI3.1 structural validation with validator0.9.0 in an isolated tool environment. No live API was called.
- Installed BMAD `lint_spine.py --workspace docs/planning-artifacts/architecture`: **ok,0 findings**. This is a mechanical spine check, not validation of runtime architecture.
- Native Codex skill discovery returned30 project skills, zero errors. Claude initialization returned all29 BMAD names plus Unslop. No model inference request needed for discovery.
- Seven source screenshots and the eight-asset rasterized SVG contact sheet were opened and inspected. Existing foundation geometry/token values preserved. No application design frame was created, reviewed or approved in this phase.
- GitHub authentication/repository existence and remote state checked. Initial private setup was superseded by Aymen's explicit public-visibility authorization; publication requires the recorded source/secret checks. No proprietary professional material is included. No application CI badges, deployed URLs or coverage numbers fabricated.

## Consistency conclusions and remaining decisions

Validation/publication/task/freshness remain distinct throughout domain rules, API, data, stories and design. Practitioner, patient and administrator projections have explicit boundaries; source access does not grant attachment access. Scheduling uses a separate aggregate with conflict/version rules. Source lineage and correction history are coherent; missing input never becomes reassuring normality. Each FR maps to rules/journey/surface/data/API/story/test, with operational evidence instead of an invented FR-020 product page.

Not user-approved: multi-membership account baseline, practice-wide practitioner access, portal-link identity procedure, adult-only demo, self-validation and patient booking-window defaults. Hosting/model/storage providers and paid reliability budget are proposals; exact dependency patches locked only after development authorization. Optional English demo and follow-up PDF have explicit resumption triggers. All design coverage rows still require real Figma nodes/state/responsive/A4 review and Aymen approval; documentation review is not design review.

Access: current planning access is sufficient. Shared conversation fetch and professional CI attachment remain unavailable; full Figma variable collections/history and Sonar account monorepo binding have precise limitations/next steps in source/setup/cost records. No deployment or paid AI secret is requested now. Claude already has access to the source Figma chat, according to Aymen; repository carries no credential.

## Handoff and gate

Proceed only with [Claude complete design](design/handoff-claude.md). Complete all40 screen,28 interaction,3 PDF and5 email families, required variants and navigable prototypes; open exports and record review evidence in coverage.csv. Design exceptions need Aymen's specific acceptance. After complete design/review, obtain explicit development authorization and run the installed readiness gate before generating implementation tracking. The present review does not pass that gate.

## Editorial review detail

Purpose: help an international contributor understand the product and help Claude complete the whole design without inventing rules. The handoff uses the task-definition model (constraints→reading→work order→review); PRD uses strategic context plus a stable reference catalog. Installed word_metrics.py measured827 words in the handoff and1375 in PRD before this reporting addition. No arbitrary length target was imposed.

| Pass | Original section | Disposition / revised treatment | Impact |
|---|---|---|---|
| structure | Handoff reading order and scope gate | PRESERVE before work instructions; protects context and stopping point |0word reduction; comprehension retained|
| structure | Derived chat bundle inside canonical repository | MOVE to requested external Desktop distribution; keep canonical sources |Removed redundant copies, no source content lost|
| prose | Publication wording in historical fictional example | Refer to shared information instead of an internal available report |Avoids implying patient access to an unpublished report|

No further editorial rewrite was applied merely to shorten the complete reference catalogs. Repeated per-entry fields support random access and explicit acceptance coverage.

## First-PR automated review and additional feedback

[CodeRabbit review5741890622](https://github.com/Aymenchaghoub/etaye/pull/1#pullrequestreview-5741890622) examined the PR through ed96cabb065ce3b5c32daac1cbe2c0eca6f34b2c. Its nine inline findings and one manifest nit were checked by the coordinator against current files, not accepted as vulnerability proof. All were valid documentary/tooling improvements; the manifest self-hash had already been fixed, then its current-run file set was tightened. The review is automated external advice, not a clinical/runtime audit or Aymen approval. The original BMAD authoring-agent independence limitation above remains accurate.

| Finding | Verified disposition |
|---|---|
| CI whitespace check examined empty worktree | Compare HEAD first parent to HEAD, covering the PR merge diff; workflow policy checks the exact command. |
| Scheduler patient lookup/registration lacked a nonclinical contract | Add minimal SchedulingPatient lookup and separately granted PATIENT_REGISTER creation (PD-001 proposal), preserving clinical search permissions. Update rules/access/journey/screens/interactions/data dictionary/traceability and AT-015-D/E. |
| ADR file cache wording contradicted no-store policy | Require explicit Cache-Control: no-store for protected preview/download. This fixes a proposed requirement, not an observed runtime exposure. |
| French document samples had fused words/IDs | Correct spacing and exact source revision identifiers; regenerate chat attachments. |
| Screen interaction IDs referred to unrelated actions | Reconcile all explicit screen references, including lab entry and brief editing; align inventory/trigger surfaces and add reciprocal mapping checks. |
| Capability aliases and missing scheduling fixture grants | Use canonical API/access-matrix vocabulary; assign explicit scheduling and optional registration grants without any clinical permission to Mila. Validate fixture capabilities. |
| Spine metadata referenced removed/misnamed files | Replace stale sources/companion paths and validate metadata references. |
| Chat export claimed all linked content was attached | List bundled/unbundled groups, give public repository access path and require missing necessary files instead of guessing; no filesystem access is implied. |
| Plural fixture references did not check practice ownership | Enforce practice equality for each referenced ID as for singular references; retain intentional denied-access scenario exemption only for attemptedRecordId. |
| Manifest included unrelated/stale external files | Hash only the seven attachments generated by that run; retain user extras without claiming them as generated output. |

Aymen also relayed Gemini commentary on 2026-11-02. It is user-supplied external feedback, not independently verified evidence or approval. Its context-size, production-artifact and server-authorization cautions informed focused future story context packets, an allowlisted production-artifact boundary and AT-036-D. Its suggestion to proceed from merge to implementation is rejected: full design/review and Aymen's explicit authorization remain mandatory.

Four negative documentary fixtures were exercised in temporary isolated copies: cross-practice plural source references, noncanonical fixture capability, missing spine metadata source and a mismatched screen interaction. All were rejected, with a clean baseline; [actual evidence](test-artifacts/validator-negative-checks.json). These are tests of documentation tooling, not application security tests.

The first run of the corrected committed-diff whitespace check blocked PR1 on CRLF endings in coverage.csv (run 41508825572). The tracker was normalized to LF and given a scoped Git attribute; values/statuses were preserved except explicit scheduling trace links. This failure demonstrates the gate executed; only subsequent green runs support final delivery.
