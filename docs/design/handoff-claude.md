# Complete-design handoff

Status: Corrected HTML design awaiting Aymen's review and Figma synchronization. Application development not started. Updated 2026-11-03. Aymen Chaghoub is the product owner and final approver. The historical filename is retained for existing links; Codex now continues the work here. Speak French with Aymen; write repository specifications, annotations and review reports in English; preserve French interface copy and the name Étaye.

**Current checkpoint, 2026-11-03:** start with the [Codex correction report](reviews/2026-11-03-corrections.md), then the historical [reception](reviews/2026-11-03-reception.md) and [decision reconciliation](reviews/2026-11-03-decisions.md). The bounded Claude brief and interrupted-session note are historical. Preserve the received snapshot and corrected exports; do not reconstruct from an older Desktop kit. [HTML tracking](html-reception.csv) separates received and corrected evidence. No Aymen approval, Figma import or development authorization is recorded. Aymen requested **editable Design layers**, not a runnable Code Layer; the connector is reauthenticated but its Starter/View MCP call quota is exhausted. He selected the [free local import](../../design/figma-local-import/README.md), pending first-batch native execution and visual reconciliation. The local REST token was minimally verified for identity and file read, with owner permission; this does not establish MCP write access or quota.

## Mission and stopping point

Complete and review every required Figma screen, state, interaction, modal/drawer, PDF and transactional email from this repository. Do not develop the application, scaffold Spring/React, create migrations, activate application CI/CD or deploy. Sequence: documentation → complete design/review → Aymen's explicit development authorization → implementation. Completing design does not itself authorize development.

Aymen authorized offline HTML/CSS design and prototype navigation, then asked Codex to correct the independently reviewed delivery. Use the latest verified correction export in docs/continuation.md; the [session recovery note](session-recovery.md) preserves backup/partial-work safeguards. HTML is a design artifact, not application implementation or proof of Figma synchronization. Track it separately under the [coverage guide](coverage-guide.md). For the later requested editable-layer import, preserve existing foundations and use new clearly named pages/batches; inspect real layers/components and navigation after import. An image frame is not an editable design system. No paid upgrade or wholesale replacement is authorized.

For a chat without local repository access, use the generated attachment kit: it bundles the named product/design documents, not every reference below. The exporter lists unbundled policies, technical contracts, stories and review context explicitly. Read required public repository files or request them from Aymen when a design decision depends on them; never assume a filesystem path gives chat access.

## Reading order

1. Root AGENTS.md and CLAUDE.md; docs/continuation.md, docs/README.md and the session recovery note.
2. Product brief, PRD, functional-specification.md, business-rules.md, access-matrix.md and decisions-assumptions.md under docs/planning-artifacts/.
3. UX DESIGN.md and EXPERIENCE.md, then this directory's figma-inventory.md and token-reference.md. Inspect the real Figma file before edits.
4. screen-catalog.md, interaction-catalog.md, pdf-email-specifications.md, synthetic-data.md/json and coverage-guide.md/csv.
5. Architecture, data-model/data-dictionary, AI/security specifications and docs/contracts/; consult story/traceability mappings before inventing data fields or business consequences.
6. Review report and deferred-work register. Read current files rather than relying on this handoff's summary.

## Confirmed direction and assumptions

Evidence-supported consultation preparation for multi-practitioner isolated practices. A physician reviews/corrects exact versions; VALIDATED never means automatically PUBLISHED. Missing is never normal. Clinician brief and patient explanation are distinct audiences. Administration has no implied clinical access. Synthetic people/data only, no clinical or compliance claims. Required stack for eventual implementation is Spring Boot, React TypeScript and PostgreSQL; no code now.

A-01–A-12 in the decision register remain working assumptions/proposals, including multiple memberships, shared-practice practitioner access, record-local portal linking, adults-only demo, self-validation and appointment policy defaults. Design these baselines explicitly; surface any structural change to Aymen and update rules/data/contracts/stories together. PD-001 proposes a separate demographic-registration grant for schedulers without clinical access; include S-07/S-08/I-04/I-10 restricted variants in design review. English-facing demo and follow-up PDF remain deferred decisions, not a translation or extra-screen project.

## Existing Figma and assets

[Existing Étaye file](https://www.figma.com/design/vfh4BSnbRAbaDE1q0CyssL/Etaye?node-id=0-1). Snapshot version 2417265104370398313, 2026-11-02. Cover 0:1; Foundations 2:74; Components 2:75. Colors 3:2/3:87; typography 3:172; spacing/radii 3:216. Logos 4:13/4:19/4:20/4:33; signs/icons 4:37/4:41/4:42/4:47. Buttons 5:22; statuses 5:46; source marker 5:47; annotation 6:2.

Original SVG variants, source screenshots, hashes and resolved tokens live in figma/. Reuse actual logo geometry; no generic replacement. Exact Figma values override generic Unslop defaults. Inspect existing collections/styles/comments and recheck current file revision. MCP Starter read quota was exhausted; REST inspection/export worked with a locally supplied token, never copied into the repository. Full variable metadata remains an editor/connector check; existing property values are available. Empty comments response is not approval.

Required Figma component/state frames and navigable prototypes remain pending. HTML/A4/email mockups and font binaries have since been received in the 2026-11-03 export; see the reception report for preserved sources, supplemental licenses, review and remaining gaps. Actual fictional short/long/incomplete document text is in document-content-samples.md. Delivery is not approval and is not a request to invent a new identity.

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

Coverage.csv owns Figma coverage; a separate HTML tracker records actual HTML design/review evidence while synchronization is pending. Attachment06 is an exported scope baseline: merge by stable ID without erasing newer nodes, reviews or findings. Never put an HTML path in figma_node. Required S-01–40, I-01–28, P-01–03 and M-01–05 must all be designed and reviewed before development, unless Aymen explicitly accepts a specific documented exception. Never mark Approved by Aymen without his actual approval. Do not convert backlog stories to ready-for-dev or start the first epic. Finish with design evidence, unresolved decisions and an explicit development stop.
