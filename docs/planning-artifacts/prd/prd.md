---
title: "Étaye — Product requirements"
status: draft
created: 2026-11-02
updated: 2026-11-02
review_status: ready-for-design
user_approval: not-approved
---

# Product requirements

## Purpose and evidence

Étaye supports consultation preparation and clinician-reviewed documentation for multi-practitioner general medical practices. Important statements must be supported by identifiable dated sources that a reviewer can inspect. The portfolio hypothesis is improved inspectability, not a measured clinical outcome. Read the [brief](../product-brief/brief.md), [source register](../../references/source-register.md) and [legacy assessment](../../references/existing-system-audit.md).

## Actors and success

The primary actor is the practitioner. Patients manage visits and read selected publications. Practice administrators manage operational access, with no implicit clinical grant. Proposed success is a demonstrated end-to-end practitioner, patient and administration journey with exact source inspection, preserved corrections and negative isolation cases. No adoption, clinical effectiveness or usability study result exists.

## Functional requirements

Each requirement is in the initial V2 planning scope unless explicitly marked future implementation. Its numbered acceptance tests are in [traceability](../../traceability.md); the linked business rules own transition guards.

### FR-001 — Account access
An intended recipient can activate only the invited grant; existing accounts can sign in, recover access and return safely from expiry. Recovery never enumerates accounts.

Rule: BR-003, BR-005. Journey: J-01. Design surface: S-02–05, S-38. Verification: successful authorized journey plus relevant denied, missing-data, failed and concurrent variants; no false success.

### FR-002 — Practice isolation
Every protected query, write, source, task and download resolves current server authorization in one practice; at least two practices demonstrate denied cross-access.

Rule: BR-001–004. Journey: J-01. Design surface: S-05, S-36. Verification: successful authorized journey plus relevant denied, missing-data, failed and concurrent variants; no false success.

### FR-003 — Team administration
An authorized administrator manages scoped invitations and membership capabilities without gaining clinical access; final-administrator protection prevents lockout.

Rule: BR-002–005. Journey: J-07. Design surface: S-31–34. Verification: successful authorized journey plus relevant denied, missing-data, failed and concurrent variants; no false success.

### FR-004 — Patient identity
Practitioners register and correct practice-local identity; duplicate warnings require human resolution; portal linking is verified per record, never inferred from email.

Rule: BR-004. Journey: J-02. Design surface: S-07–08. Verification: successful authorized journey plus relevant denied, missing-data, failed and concurrent variants; no false success.

### FR-005 — Longitudinal record
Chronology combines dated sources, history, allergies and reported treatments while distinguishing patient report, observation, unknown and explicit negative assertions.

Rule: BR-008. Journey: J-02. Design surface: S-09–10. Verification: successful authorized journey plus relevant denied, missing-data, failed and concurrent variants; no false success.

### FR-006 — Measurements
Practitioners append and correct dated measurements with units, source lineage and inspectable trends; gaps and incompatible units cannot masquerade as normal observations.

Rule: BR-008–009. Journey: J-02. Design surface: S-11. Verification: successful authorized journey plus relevant denied, missing-data, failed and concurrent variants; no false success.

### FR-007 — Laboratory results
Manual numeric or qualitative results preserve supplied intervals, units, comparators and dates; ordinary lab PDFs do not imply extracted record facts.

Rule: BR-009. Journey: J-02. Design surface: S-12. Verification: successful authorized journey plus relevant denied, missing-data, failed and concurrent variants; no false success.

### FR-008 — Documents and sources
Authorized users upload, inspect, preview and retrieve versioned safe documents; source access uses exact page/excerpt/revision and never bypasses an audience boundary.

Rule: BR-010. Journey: J-02. Design surface: S-13, S-30. Verification: successful authorized journey plus relevant denied, missing-data, failed and concurrent variants; no false success.

### FR-009 — Real scheduling
Availability and exceptions produce offered slots; atomic booking/rescheduling prevents overlaps. Cancellations, check-in, completion and no-shows follow the appointment lifecycle independently of consultations.

Rule: BR-006. Journey: J-03. Design surface: S-14–16, S-26. Verification: successful authorized journey plus relevant denied, missing-data, failed and concurrent variants; no false success.

### FR-010 — Visit preparation
Patients prepare their own eligible visit; practitioners inspect the dated report without silently promoting it to an observation or overwriting it.

Rule: BR-008. Journey: J-02, J-06. Design surface: S-17, S-27. Verification: successful authorized journey plus relevant denied, missing-data, failed and concurrent variants; no false success.

### FR-011 — Clinician brief
A clinician-only sourced brief explains changes, missing information and contradictions since the previous visit, with dated inspectable supporting evidence.

Rule: BR-011–014. Journey: J-02. Design surface: S-18. Verification: successful authorized journey plus relevant denied, missing-data, failed and concurrent variants; no false success.

### FR-012 — Consultations and reports
A practitioner conducts and completes an encounter with or without a linked appointment, preserving structured notes and independently versioned report content.

Rule: BR-007. Journey: J-04. Design surface: S-19–20. Verification: successful authorized journey plus relevant denied, missing-data, failed and concurrent variants; no false success.

### FR-013 — AI drafting
A bounded asynchronous task creates a draft candidate with statement citations and source manifest; processing, failure, retry, duplication and unavailable-provider states are explicit.

Rule: BR-011. Journey: J-04. Design surface: S-21, S-37, S-40. Verification: successful authorized journey plus relevant denied, missing-data, failed and concurrent variants; no false success.

### FR-014 — Review, versions and freshness
A human corrects and reviews exact versions, validates explicitly and inspects history/diffs. Changed relevant sources mark dependencies stale; approved text is never overwritten.

Rule: BR-012, BR-014–015. Journey: J-04. Design surface: S-21–24. Verification: successful authorized journey plus relevant denied, missing-data, failed and concurrent variants; no false success.

### FR-015 — Explicit publication
Authorized practitioner selects an eligible validated report/explanation and patient audience. Publish, atomic replacement and withdrawal remain independent from validation.

Rule: BR-013. Journey: J-05. Design surface: S-23–24. Verification: successful authorized journey plus relevant denied, missing-data, failed and concurrent variants; no false success.

### FR-016 — Patient portal
A verified linked patient sees own appointments/preparation and only published content/authorized attachments; internal drafts, briefs and clinical sources stay inaccessible.

Rule: BR-002, BR-013. Journey: J-06. Design surface: S-25–30. Verification: successful authorized journey plus relevant denied, missing-data, failed and concurrent variants; no false success.

### FR-017 — PDFs and emails
Audience-safe A4 exports identify status, version, author, dates and sources. Minimal transactional emails follow committed events and reauthorize at their destination.

Rule: BR-016. Journey: J-05, J-06, J-07. Design surface: S-39; P-01–03; M-01–05. Verification: successful authorized journey plus relevant denied, missing-data, failed and concurrent variants; no false success.

### FR-018 — Audit, settings and preferences
Permission-appropriate audit explains consequential changes; settings/profile edits cannot escalate clinical grants or leak sensitive log bodies.

Rule: BR-017. Journey: J-07. Design surface: S-33–35. Verification: successful authorized journey plus relevant denied, missing-data, failed and concurrent variants; no false success.

### FR-019 — Synthetic demonstration
Showcase and bounded role journeys use exclusively coherent fictional data; reset and unavailable services have honest recoverable states.

Rule: BR-018. Journey: J-08. Design surface: S-01, S-37. Verification: successful authorized journey plus relevant denied, missing-data, failed and concurrent variants; no false success.

### FR-020 — Delivery evidence
Future releases require meaningful isolated-stack checks, blocking quality evidence and traceable immutable artifacts with smoke/recovery procedures; no pipeline is implemented now.

Rule: BR-001, BR-017–018. Journey: J-08. Design surface: Operational artifacts, no extra product page. Verification: successful authorized journey plus relevant denied, missing-data, failed and concurrent variants; no false success.

## Scope boundaries and release gates

Billing, payments, commercial subscriptions, prescriptions, audio transcription, autonomous diagnosis/treatment and an unsupported global medical risk score are excluded. Conversational search and intelligent laboratory PDF extraction are deferred. Upload/view/manual result entry remain initial scope. Follow-up PDF value must be agreed before adding a fourth template. [Deferred register](../deferred-work.md) owns rationale and resumption triggers.

Planning and documentation → complete design and review → Aymen's explicit development authorization → implementation. No scaffold, domain implementation, migrations, application CI/CD or deployment in this phase. All stories remain backlog/planning. A successful documentary review is not authorization to build.

## Product decisions still open

[Decision register](../decisions-assumptions.md) owns multi-practice account/linking assumptions, shared-practice clinical access, self-validation, patient booking windows, adults-only demo and optional English-facing demo. These are usable design baselines, not Aymen-approved product decisions. Confirm affected flows in design review before implementation.

## Quality, safety and internationalization

[NFRs](../non-functional-requirements.md), [AI specification](../ai-specification.md), [security/privacy](../security-privacy.md) and [test strategy](../../test-artifacts/test-strategy.md) define measurable proposed targets. Synthetic data only; no claim of certification/compliance or readiness for real records. French interface copy remains authoritative during design; message keys, locale-aware formats and separation of content from labels prepare later localization without duplicating every screen.

## Design and acceptance authority

The screen, interaction, PDF/email catalogs and coverage tracker specify all states. Figma owns verified existing visual properties; business rules own behavior. Resolve a conflict explicitly and update traceability; neither a legacy behavior nor a clinical Figma example may silently create a medical rule.
