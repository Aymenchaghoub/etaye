# Claude complete-design handoff

Status: Planning package ready for complete design work. Application development not started. Updated 2026-11-02. Aymen Chaghoub is the product owner and final approver. Speak French with him; write repository specifications, annotations and review reports in English; preserve French interface copy and the name Étaye.

## Mission and stopping point

Complete and review every required Figma screen, state, interaction, modal/drawer, PDF and transactional email from this repository. Do not develop the application, scaffold Spring/React, create migrations, activate application CI/CD or deploy. Sequence: documentation → complete design/review → Aymen's explicit development authorization → implementation. Completing design does not itself authorize development.

## Reading order

1. Root AGENTS.md and CLAUDE.md; docs/continuation.md and docs/README.md.
2. Product brief, PRD, functional-specification.md, business-rules.md, access-matrix.md and decisions-assumptions.md under docs/planning-artifacts/.
3. UX DESIGN.md and EXPERIENCE.md, then this directory's figma-inventory.md and token-reference.md. Inspect the real Figma file before edits.
4. screen-catalog.md, interaction-catalog.md, pdf-email-specifications.md, synthetic-data.md/json and coverage-guide.md/csv.
5. Architecture, data-model/data-dictionary, AI/security specifications and docs/contracts/; consult story/traceability mappings before inventing data fields or business consequences.
6. Review report and deferred-work register. Read current files rather than relying on this handoff's summary.

## Confirmed direction and assumptions

Evidence-supported consultation preparation for multi-practitioner isolated practices. A physician reviews/corrects exact versions; VALIDATED never means automatically PUBLISHED. Missing is never normal. Clinician brief and patient explanation are distinct audiences. Administration has no implied clinical access. Synthetic people/data only, no clinical or compliance claims. Required stack for eventual implementation is Spring Boot, React TypeScript and PostgreSQL; no code now.

A-01–A-12 in the decision register remain working assumptions/proposals, including multiple memberships, shared-practice practitioner access, record-local portal linking, adults-only demo, self-validation and appointment policy defaults. Design these baselines explicitly; surface any structural change to Aymen and update rules/data/contracts/stories together. English-facing demo and follow-up PDF remain deferred decisions, not a translation or extra-screen project.

## Existing Figma and assets

[Existing Étaye file](https://www.figma.com/design/vfh4BSnbRAbaDE1q0CyssL/Etaye?node-id=0-1). Snapshot version 2417265104370398313, 2026-11-02. Cover 0:1; Foundations 2:74; Components 2:75. Colors 3:2/3:87; typography 3:172; spacing/radii 3:216. Logos 4:13/4:19/4:20/4:33; signs/icons 4:37/4:41/4:42/4:47. Buttons 5:22; statuses 5:46; source marker 5:47; annotation 6:2.

Original SVG variants, source screenshots, hashes and resolved tokens live in figma/. Reuse actual logo geometry; no generic replacement. Exact Figma values override generic Unslop defaults. Inspect existing collections/styles/comments and recheck current file revision. MCP Starter read quota was exhausted; REST inspection/export worked with a locally supplied token, never copied into the repository. Full variable metadata remains an editor/connector check; existing property values are available. Empty comments response is not approval.

Missing design assets are the completed component/state library, actual product/prototype frames and A4/email mockups. Font binaries/licenses are not bundled; obtain official files before future rendering/embedding. Actual fictional short/long/incomplete document text is in document-content-samples.md. These gaps are the next design work, not a request to invent a new identity.

## Work order

1. Inspect existing foundations and exports, preserve logo/French copy, reconcile actual token bindings and document any proposed accessibility correction before changing foundations.
2. Complete reusable components and all interactive states, source/chronology/version/publication patterns; consult the component checklist in DESIGN.md. No nested dialogs.
3. Compose J-01/J-02/J-03 and full J-04/J-05 clinician journey from S/I catalogs using the exact shared cast. Show missing data, contradiction, failed AI task and source inspection.
4. Compose J-06 patient mobile-first journey and J-07 administration, including denied clinical access and cross-practice restrictions. Add J-08 showcase/fallback/reset states.
5. Design P-01–03 A4 short/long/incomplete layouts and M-01–05 emails. Do not design prescriptions; follow-up P-04 is deferred D-07.
6. Connect prototypes, review at matching viewports, open every screenshot/export and fix findings. Update coverage with real IDs/evidence; request Aymen's design review after the complete package is concrete.

## Required navigable prototypes

- Practitioner: daily schedule → Claire's chronology/preparation → dated sourced brief → source viewer → consultation → generated draft → correction/version diff → human validation → explicit patient publication → replacement/withdrawal.
- Patient: activation/login/context → appointments/booking/change → preparation → published explanation → authorized source/document → PDF download; unpublished report denied.
- Administration: invite → permission assignment → deactivation/final-admin guard → scoped audit/settings; clinical deep link denied.
- Error branches: two booking attempts, unsaved exit, version conflict, expired session, unavailable API/AI, failed/retried task, stale sources, missing interval, wrong-practice resource, replaced/withdrawn content. Every meaningful action has a defined consequence/back path.

## Content and review criteria

Use synthetic-data.json identifiers/names/dates/values and labeled scenario variants. Existing Figma clinical examples do not establish treatment rules. Important statements show supporting dated sources; a marker must support the exact associated assertion. Patient citations must never open internal records. Show author/validator/version/date and audience; preserve human corrections and distinguish generated text from source facts.

At desktop 1440×1024, tablet 1024×768, mobile 390×844 plus 320px reflow/200% zoom, open and inspect text, units, dates, hierarchy, density, contrast, focus, keyboard order, overflow, sources and action visibility. Review long/partial/error/restricted states. A4: open all pages, including source appendices, test long rows/page breaks, fonts/accents, selectable-text intent and grayscale. Record visual and functional design findings separately. An export request or successful prototype link is not a visual check until opened.

Coverage.csv is authoritative for design progress. Required S-01–40, I-01–28, P-01–03 and M-01–05 must all be designed and reviewed before development, unless Aymen explicitly accepts a specific documented exception. Never mark Approved by Aymen without his actual approval. Do not convert backlog stories to ready-for-dev or start the first epic. Finish with design evidence, unresolved decisions and an explicit development stop.
