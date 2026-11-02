# Business rules and lifecycle definitions

Status: Draft for design. Date: 2026-11-02. This document owns business invariants and domain state transitions. The [PRD](prd/prd.md) owns capabilities; [access matrix](access-matrix.md) owns actor/resource presentation. Technical enforcement is specified in architecture/contracts. Values tagged `[ASSUMPTION]` are not user-approved.

## Canonical state vocabulary

| Axis/entity | Exact values | Meaning |
|---|---|---|
| Invitation | PENDING, ACCEPTED, EXPIRED, REVOKED | Token grant lifecycle; immutable audit trail. |
| Account | PENDING_ACTIVATION, ACTIVE, SUSPENDED | Authentication eligibility, independent of a particular practice membership. |
| Membership | ACTIVE, SUSPENDED | Practice-specific access; invitation is a separate object. |
| Appointment | BOOKED, CHECKED_IN, COMPLETED, CANCELLED, NO_SHOW | Scheduling lifecycle. Rescheduling is an audited change of a BOOKED appointment. |
| Consultation | IN_PROGRESS, COMPLETED, CANCELLED | Clinical encounter lifecycle. |
| Document version business status | DRAFT, IN_REVIEW, VALIDATED | Review status of content. Processing state and patient visibility are separate. |
| Human-review decision | STARTED, CHANGES_REQUESTED, VALIDATED | Append-only review events for exact versions; not a second mutable status field. |
| Publication | PUBLISHED, REPLACED, WITHDRAWN | Patient-facing grant referencing an exact validated version. Absence of a publication means internal only. |
| AI task processing status | QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED | Asynchronous execution only. |
| Freshness | CURRENT, STALE | Relevance of the source snapshot; independent of business status and publication. |

A version can therefore be VALIDATED and STALE with no publication. A successful AI task does not create a validated or published document. A PUBLISHED projection can be flagged STALE without silently changing its text. No single generic “status” pill may collapse these dimensions.

## BR-001 — Practice ownership and isolation

Every patient record, appointment, consultation, source, clinical attachment, generated version, task and clinical audit event belongs to one practice. A request must establish an authenticated account, current active membership or verified portal link, capability and resource ownership. Client-supplied practice IDs and UI filters never establish authority. A background job, search count, file URL, PDF, email recipient and audit query carry the same boundary. No cross-practice joins, patient matching or automatic copy occur in initial scope. [ASSUMPTION A-02] The same person may have independent local records in two practices.

## BR-002 — Capability and resource authorization

An administrator receives no implicit clinical access. Capabilities may coexist only through explicit assignment. Clinical readers see records only in the active practice; a patient sees only their own eligible appointments, preparation and PUBLISHED projections/attachments. A brief is clinician-only. Patient-visible citations expose only authorized excerpts/documents, not an indirect grant to all source material. Recheck permission when retrieving, mutating, processing or exporting. Disabled controls must not be the only protection. Scope inaccessible resource errors according to the API existence-hiding policy.

## BR-003 — Accounts, memberships and practitioner ownership

[ASSUMPTION A-01] An account can have several memberships. The active practice context is explicit and switching it clears resource-specific navigation/cache state. An active account without an active membership or portal link cannot read protected practice information. A practitioner is a professional profile linked to an active membership, not merely an account role label. Suspension of one membership leaves unrelated memberships intact; global account suspension disables every session. Prevent removal/deactivation of the final active practice administrator. Role changes are audited and may never self-escalate without authorized TEAM_MANAGE permission.

## BR-004 — Patient identity and portal linking

A patient and their record are practice-local. Preserve a stable record identifier across demographic corrections. Searching or creating a patient never implies global matching. Warn about likely local duplicates; a human confirms whether to use an existing record. Record merging is deferred. [ASSUMPTION A-04] An account obtains a portal link only through an invitation issued for the intended record after clinical-team identity confirmation and recipient verification. A shared or changed email is not identity proof. Ambiguous identity stops activation/linking and directs the person to the practice without exposing another record. Revoking a portal link removes subsequent access without deleting clinical history. Adults only in the initial synthetic demo (A-05).

## BR-005 — Invitations, activation and recovery

Invitation records identify target practice, intended purpose (TEAM_MEMBERSHIP or PATIENT_PORTAL), intended recipient, explicit grant, issuer, expiry and token digest. Raw tokens appear only in the delivery channel, never audit or application logs. Acceptance must be atomic and single-use; a resend revokes the former active token and creates a new invitation. Accepting a team invitation does not create a portal link. Accepting a portal invitation does not grant staff capabilities. Recovery responses must not enumerate accounts, and recovery must not silently activate suspended access.

| From | Event → To | Actor | Preconditions | Consequences |
|---|---|---|---|---|
| No invitation | issue → PENDING | TEAM_MANAGE or clinical portal-link issuer | Authorized grant, intended recipient checked, expiry set | Token delivered after commit; delivery failure remains visible/retryable. |
| PENDING | accept → ACCEPTED | Intended recipient | Valid unexpired unused token; recipient verification; no suspension conflict | Activate intended membership/link and account if eligible in one operation; audit acceptance. |
| PENDING | time reaches expiry → EXPIRED | System | Stored expiry passed | Acceptance denied; request/resend path preserves no raw token. |
| PENDING | revoke → REVOKED | Authorized issuer/admin | Same practice; current invitation | Deny subsequent acceptance; audit reason. |
| PENDING/EXPIRED/REVOKED | resend → new PENDING invitation | Authorized issuer | Recheck current recipient/grant and rate limit | Old record remains terminal; no token reuse. |
| No account | create pending → PENDING_ACTIVATION | Invitation/recovery workflow as applicable | Verified intended identity path | No protected access yet. |
| PENDING_ACTIVATION | activate → ACTIVE | Intended recipient | Valid activation plus required identity verification | New authenticated session subject to current grants. |
| ACTIVE | suspend → SUSPENDED | Authorized account operator | Explicit reason and protection against locking out final admin | Invalidate access/session eligibility; retain history. |
| SUSPENDED | restore → ACTIVE | Authorized account operator | Suspension issue resolved; grants rechecked | Do not restore revoked membership or publication access implicitly. |

EXPIRED, REVOKED and ACCEPTED are terminal for that invitation. Membership activation/suspension is separate from account activation. Sensitive account changes require the session assurance defined by the authentication ADR.

## BR-006 — Availability, appointments and conflicts

Store an appointment's practitioner, practice-local patient, start/end instants, practice timezone, state and version. End is strictly after start. Proposed intervals use `[start, end)` semantics so adjacent appointments do not overlap. Availability combines recurring working intervals and dated exceptions. Offered slots do not constitute a reservation. Recheck availability and absence of an active conflicting interval at commit. Two concurrent requests for the same slot yield one booking and one explicit conflict, never two confirmations. A patient sees offered slots without other patients' identities. [ASSUMPTION A-07] Self-service booking and changes are permitted within configured future booking/cancellation windows; these windows remain explicit practice policy.

Changing availability must not silently invalidate an existing booking: show impacted appointments and require explicit rescheduling/cancellation. A reschedule preserves appointment identity, retains old time/reason/actor in history, changes version and emits one post-commit notification event. A retry with the same operation identity must not duplicate bookings or notifications. Practitioner leave/blocking exceptions and patient overlap warnings are visible. Practitioner overlap is forbidden; [ASSUMPTION] patient overlap is also forbidden for self-service within that practice. Do not infer availability from absence of a consultation.

| From | Event → To | Actor | Preconditions | Consequences |
|---|---|---|---|---|
| No appointment | book → BOOKED | SCHEDULE_MANAGE or linked patient | Valid offered interval; patient/practitioner same practice; policy and atomic conflict checks | Reserved slot; confirmation event; no consultation created automatically. |
| BOOKED | reschedule → BOOKED | Same authorized actors | Future eligible interval; current version; reason; conflict check | Old/new times retained; affected reminders replaced, one change notification. |
| BOOKED | check in → CHECKED_IN | SCHEDULE_MANAGE | Arrival recorded; current version | Record arrival time; consultation remains a separate explicit action. |
| BOOKED | cancel → CANCELLED | Same authorized actors | Policy allows change; current version; reason | Release slot; retain history and notification. |
| CHECKED_IN | cancel → CANCELLED | SCHEDULE_MANAGE | Consultation not completed; explicit reason | Retain arrival/cancellation history; do not erase any clinical notes. |
| BOOKED | mark no-show → NO_SHOW | SCHEDULE_MANAGE | Appointment start passed; no linked completed encounter; reason/context recorded | Keep reserved history; no invented consultation. |
| BOOKED/CHECKED_IN | complete → COMPLETED | SCHEDULE_MANAGE or practitioner | Encounter finished or attendance explicitly confirmed; current version | Record attendance; report validation/publication unchanged. |

CANCELLED, COMPLETED and NO_SHOW are terminal scheduling states. Administrative correction of a mistaken terminal state creates a reviewed correction event rather than silently rewriting history; design a targeted correction flow before implementation if required. Patient-facing cancellation/reschedule errors preserve the user's intended slot and offer refreshed availability.

## BR-007 — Consultations and reports

A consultation can start with or without an appointment. A linked appointment must belong to the same practice, patient and practitioner; no appointment reuse for unrelated encounters. A consultation has encounter time, responsible practitioner and version. A report is a separate versioned document associated with it. Completing a consultation does not validate its report or publish anything. A cancelled consultation preserves any recorded information and reason; it is not a deletion shortcut.

| From | Event → To | Actor | Preconditions | Consequences |
|---|---|---|---|---|
| No consultation | start → IN_PROGRESS | CLINICAL_WRITE practitioner | Authorized record; optional appointment relationship valid | Create encounter and editable notes; no patient visibility. |
| IN_PROGRESS | complete → COMPLETED | Responsible/authorized practitioner | Required encounter metadata and notes saved; current version | Record completion; report can remain DRAFT/IN_REVIEW. Linked attendance updated explicitly under BR-006. |
| IN_PROGRESS | cancel → CANCELLED | Authorized practitioner | Reason; current version | Preserve saved notes/history; show cancelled encounter. |
| COMPLETED | add correction → COMPLETED | Authorized practitioner | New dated addendum/version with reason | Original encounter/report retained; validated report edits create a new draft. |

No silent reopening, deletion or retrospective date change. Correcting the encounter date retains the previous value and reason. Report/AI processing failures do not undo a completed consultation.

## BR-008 — Provenance, assertions and corrections

Every clinically meaningful fact records source type, original author/reporter, event time or explicit unknown date, recorded time, source identifier/version, and correction lineage. Distinguish PATIENT_REPORTED, PRACTITIONER_OBSERVED and DOCUMENT_REPORTED. An acknowledged patient report retains its original provenance; a practitioner may add a separate assessment. Facts can be PRESENT, EXPLICITLY_ABSENT, UNKNOWN or NOT_ASKED where the field supports assertions. An empty allergy section means unknown, never “no known allergies.” A dated explicit-negative statement does not negate a newer contradictory patient report. Present contradictions with dates and source access for human resolution; no AI overwrite of the record.

Corrections append a superseding revision with correction reason, actor and time. Preserve the original for internal history, mark the current revision clearly and reconsider dependent briefs. Unknown dates are never replaced with the upload date; show both event date and recorded date when they differ. Do not invent clinical recommendations from sample data.

## BR-009 — Measurements, laboratory results and units

A quantitative measurement requires concept, numeric value, unit and observed time; if unavailable, represent a missing observation explicitly rather than using zero. Support source/author/device-or-method when known. A laboratory result also records analyte, specimen/result dates when supplied, comparator (such as `<`), unit, supplied reference interval and interval context/source. Preserve qualitative results as qualitative, not coerced numbers. Store original values/units; any normalized value requires a documented conversion and original retention. Never silently combine incompatible units, intervals, fasting states or time windows. Trend gaps remain gaps; no interpolation presented as an observed result. Flagging outside a supplied interval is informational, not an autonomous diagnosis or global risk score.

Manual result entry is in initial scope. Ordinary uploaded laboratory PDFs remain documents. Intelligent extraction, review of extracted candidates and acceptance into the record belong to deferred increment D-01.

## BR-010 — Documents, attachments and sources

An upload creates a practice-owned document record with original filename, safe storage identifier, media type, size/hash, uploader, recorded time and access state. Inspect/quarantine before preview or use as an AI source; unsupported or rejected files do not become a record fact. User-provided filenames, PDF text and record content are untrusted data, never executable instructions. Source viewing opens the exact version/page/excerpt from which a claim was derived. The same authorization applies to thumbnails, range requests, preview, download and exports. Publishing a report does not publish every attachment or source by implication. Choose patient attachments explicitly, validate their audience and keep an auditable grant.

## BR-011 — AI draft and processing lifecycle

Generation reads an authorized, versioned source snapshot in one practice. Store the task's purpose, input revision identifiers, provider/model configuration reference, prompt/template version, timestamps and bounded attempt metadata. AI output becomes a draft with statement-to-source mappings. Preserve generated text separately from human edits. Only source facts may be attributed as facts; hypotheses, missing data and contradictions must be visibly distinguished. A citation must actually support the associated statement. No invented confidence percentages. Clinician briefs and separately reviewed patient explanations have distinct intended audiences.

| From | Event → To | Actor | Preconditions | Consequences |
|---|---|---|---|---|
| No task | request → QUEUED | Authorized practitioner | Valid purpose/source snapshot; quota; deduplication key | Acknowledge task identity; no draft or success claim yet. |
| QUEUED | claim → RUNNING | Scoped worker | Lease acquired atomically; task still authorized | Record attempt/start; no duplicate active execution. |
| RUNNING | valid result → SUCCEEDED | Worker | Output schema/source references verified; lease current | Create new DRAFT version; never overwrite a reviewed/validated version. |
| RUNNING | timeout/failure → FAILED | Worker/watchdog | Bounded timeout, classified failure | Record safe failure; preserve prior drafts/edits; show retry path. |
| QUEUED/RUNNING | cancel → CANCELLED | Requester/authorized practitioner or system | Still cancellable; race check | Late result discarded; no draft committed after cancellation. |
| FAILED | retry → new QUEUED task | Authorized practitioner | Retryable failure; quota/rate check; user intent | Link prior task; preserve failure history and human corrections. |

SUCCEEDED, FAILED and CANCELLED are terminal for a task attempt record. A retry is a linked new task. Duplicate submissions return the existing logical request/task or conflict when payload differs. Regeneration creates a new draft; it never erases the prior draft's human edits. Failed/incomplete output cannot be presented as success. Proposed latency/cost/evaluation targets live in the AI specification and are not measured results.

## BR-012 — Human review and validation

| From | Event → To | Actor | Preconditions | Consequences |
|---|---|---|---|---|
| No document version | create → DRAFT | Practitioner or successful scoped AI task | Record/purpose/source snapshot authorized | Internal editable content; generated vs human-origin fields traceable. |
| DRAFT | submit for review → IN_REVIEW | CLINICAL_WRITE | Required fields saved; known gaps/contradictions visible; current version | Record review STARTED event; content freeze for explicit review. |
| IN_REVIEW | request changes → DRAFT | Reviewer | Reason; current version | Record CHANGES_REQUESTED; preserve feedback/edits. |
| IN_REVIEW | validate → VALIDATED | CLINICAL_VALIDATE | Exact current version; sources inspected; unsupported statements removed/corrected; missingness acknowledged; no unresolved blocking contradiction | Record validator identity/date/version and attestation; immutable validated content. No publication is created. |
| VALIDATED | revise → new DRAFT version | CLINICAL_WRITE | Explicit new-version action/reason | Original VALIDATED version retained; lineage and corrections available. |

[ASSUMPTION A-06] Self-validation is allowed after an explicit review step; do not imply independent clinical approval. Source relevance and material omissions are checked, not just required-field completion. A clinician can validate incomplete but honestly bounded content only if it does not assert the missing information as fact; blocking conflicts must be resolved or removed from unsupported claims. Validation of a STALE candidate requires a refreshed source review and a new/current snapshot; historical validated versions stay immutable.

## BR-013 — Explicit publication, replacement and withdrawal

Only an eligible VALIDATED consultation report or patient explanation may be published by PATIENT_PUBLISH. A clinician brief is ineligible. Publication is a distinct action showing patient identity, audience, exact version, content preview, selected attachments and consequences. The patient sees a curated projection of that immutable version, not the internal clinical record. A patient-visible source citation includes only an approved label/excerpt or separately authorized document. Internal annotations, draft alternatives and hidden-source URLs never leak through exports.

| From | Event → To | Actor | Preconditions | Consequences |
|---|---|---|---|---|
| No publication | publish → PUBLISHED | PATIENT_PUBLISH practitioner | Eligible VALIDATED CURRENT version; correct verified patient record; audience/attachments checked; explicit confirmation; current version | Create audience grant and publication event; notification queued after commit. |
| PUBLISHED | prepare correction → remains PUBLISHED | CLINICAL_WRITE | New DRAFT descendant, reason | Patient continues to see old approved version unless separately withdrawn; show correction-in-progress only if deliberately designed. |
| PUBLISHED + new eligible version | replace → old REPLACED, new PUBLISHED | PATIENT_PUBLISH | New VALIDATED CURRENT version; audience preview; current publication/version guard | Atomic replacement; history retained; notify replacement; never an interval with two ambiguous current versions. |
| PUBLISHED | withdraw → WITHDRAWN | PATIENT_PUBLISH | Explicit reason and consequences; current publication guard | Remove portal access/download grant for that publication; retain audit; tell patient content withdrawn without exposing internal reason. |
| REPLACED/WITHDRAWN | attempt ordinary read/download | Patient | Former grant no longer current | Show unavailable/replaced notice with authorized replacement link; no old body/download unless a separately designed historical-access policy is accepted. |

REPLACED and WITHDRAWN are terminal for the publication record. Republishing requires a new explicit publication after review; do not flip the old record back silently. Withdrawal cannot revoke a file already downloaded or email already delivered; UI must say so. No notification body contains clinical content. Patient publication failures must not leave content apparently published without an actual grant.

## BR-014 — Source change and obsolescence

When a referenced or relevant record revision changes after generation, mark dependent content STALE with reason/date and affected sources. Preserve text, validation and publication history. A stale draft cannot be validated/published until refreshed review creates or confirms a current source snapshot. A historical published version may remain accessible with a clear dated notice while the practitioner assesses it; a serious error requires explicit withdrawal/replacement, never silent rewriting. New source data does not automatically publish a new result or retract a previous download. Record the dependency rule and source manifest so staleness can be explained and tested.

## BR-015 — Concurrent edits and unsaved changes

Every mutable aggregate has a revision token checked at commit. If another actor changed it, reject the stale write and preserve the local draft for comparison; no last-write-wins on clinical text, schedule or publication. Show current author/time/version and provide reload/copy/compare actions. Do not claim a save occurred before confirmed persistence. Warn before leaving with unsaved changes, including practice switching and session expiry. Recovery of entered content must remain within the account/practice context and retention policy; never persist clinical drafts in a shared demo browser across users. Duplicate clicks disable the initiating control while the request identity remains stable.

## BR-016 — PDFs and transactional emails

Exports recheck the requesting actor, practice, resource version and audience. A practitioner-only brief carries clinician-only labeling; a patient export can contain only its authorized PUBLISHED projection. Draft/validated/published labels must be accurate; VALIDATED without publication is still internal. Include author, relevant dates, version, dated sources, page numbering and synthetic-demo labeling. PDF is selectable text, not an application screenshot. A4, long content, units/accents/fonts, grayscale and page breaks are specified in the print catalog. Email events occur after successful transaction commit, use minimal nonclinical text and require authorization again at the destination. Delivery failure must be visible/retryable without duplicating the underlying business action.

## BR-017 — Audit and accountability

Record actor/service identity, practice, resource, event, timestamp, correlation identifier, result and relevant version before/after reference for important accesses and state changes. Avoid clinical body text, raw tokens, AI prompts/responses and secrets in general logs. Administrative audit contains membership/security/operational information without clinical details; clinical audit requires clinical permission. Audit coverage must be verified against actual actions, including downloads, denials, generation, corrections, validation, publication and job retries; an annotation/aspect alone is not proof. Failed writes must not appear as successful changes. Retention and operational tamper resistance belong in the security design; do not claim compliance from event existence.

## BR-018 — Synthetic demonstration and unavailable services

Use only the shared fictional dataset. At least two isolated practices demonstrate positive and negative authorization. Public visitors cannot submit real patient data, trigger unbounded provider calls or access operational secrets. Demo reset is isolated, rate-limited and returns to a known fixture version without corrupting other active sessions silently. Show reset/session-expiration notices when state changes. Deterministic AI responses exercise the external-provider boundary while the real authorization, persistence, lifecycle and UI remain testable. Unavailable API/AI service states preserve entered work where safe, make no false success claim and offer recovery. No demo feature constitutes medical advice or real-care readiness.
