# Role and resource-access matrix

Status: Draft for design. Date: 2026-11-02. Authorization rules: [BR-001–004](business-rules.md). Matrix entries describe server-enforced capabilities, not merely hidden buttons.

## Actors and capability bundles

| Actor | Proposed capabilities | Boundary |
|---|---|---|
| Unauthenticated visitor | Public showcase, synthetic demo entry, login/recovery requests | No practice, patient or membership enumeration. |
| Practice administrator | TEAM_MANAGE, PRACTICE_SETTINGS, ADMIN_AUDIT | Membership and operational settings only. No implicit clinical, patient-document or patient-record access. |
| Practitioner | CLINICAL_READ, CLINICAL_WRITE, SCHEDULE_MANAGE, CLINICAL_VALIDATE, PATIENT_PUBLISH, CLINICAL_AUDIT | Active membership and practitioner profile in selected practice required. Bundling is [ASSUMPTION] A-03/A-06; capabilities remain distinct. |
| Scheduler (capability assignment, not a mandatory new user persona) | SCHEDULE_MANAGE; optional separately assigned PATIENT_REGISTER (PD-001 proposal) | Minimum patient identity/contact and appointment information; no clinical notes, results or AI drafts. An administrator needs this explicitly to schedule. |
| Linked patient account | SELF_APPOINTMENTS, SELF_PREPARATION, SELF_PUBLICATIONS | Only separately verified record links; selected practice and linked record. Cannot browse the clinical record or internal sources. |
| Background service | Named job execution grants tied to practice/resource snapshot | Never a broad interactive superuser; rechecks task scope before retrieval/output. |
| Portfolio operator | Synthetic demo reset and service operations | Operational access is not a product role granting routine unrestricted clinical browsing. |

An account can combine administrator and practitioner capabilities only through explicit assignment; display the active role/context. Revoking one practice membership does not revoke unrelated memberships. Server checks use current grants, not stale UI state.

## Resource matrix

“Own” means a verified portal link to that record; “practice” means current active membership plus the listed capability. No actor has cross-practice access through a guessed identifier.

| Resource/action | Administrator only | Practitioner | Scheduler only | Linked patient | Scope and disclosure |
|---|---|---|---|---|---|
| Practice profile/settings | Read/change operational fields | Read identity; change only with PRACTICE_SETTINGS | Read scheduling-relevant settings | Read public patient-facing fields | Never reveal secret configuration or provider credentials. |
| Team/membership/invitation | Read/create/revoke/deactivate | Only if TEAM_MANAGE | No | No | Protect last active team administrator; self-escalation forbidden. |
| Patient demographic registration | No | Create/read/correct | Minimal lookup with SCHEDULE_MANAGE; new registration additionally requires PATIENT_REGISTER. No existing-identity correction or clinical access | Own displayed identity; correction request/profile fields only | Clinical-team identity reconciliation; no automatic record merging. |
| Longitudinal clinical record | No | Practice clinical read/write | No | No direct record access | A-03 shared-practice model; no global patient search. |
| Measurements/laboratory/history | No | Read/append/correct | No | Only snapshots explicitly included in a publication | Original values and correction lineage retained internally. |
| Patient-reported preparation | No | Read; acknowledge/link to consultation | No clinical body access | Create/read/edit own before encounter begins | Author/source remains PATIENT_REPORTED; no automatic conversion to clinical fact. |
| Appointment/availability | No unless SCHEDULE_MANAGE | Practice manage own/all authorized schedules | Practice scheduling scope | Own eligible offered slots and appointments | Other patient names/details never shown to patients. |
| Consultation/report draft | No | Create/read/edit within practice | No | No | Draft/IN_REVIEW/VALIDATED states all internal until publication. |
| Clinician brief | No | Read/generate/review | No | Never | Not directly publishable, even if validated. |
| AI task/input/output | No | In-scope clinical processing/result | No | No raw task access | Patient portal may see an unavailable published-content page, never draft fragments. |
| Validation | No | CLINICAL_VALIDATE for exact version | No | No | Explicit review and version guard; validator/date recorded. |
| Publication/replacement/withdrawal | No | PATIENT_PUBLISH plus clinical read and validated eligible version | No | Read current own PUBLISHED projection | Publication does not grant access to all underlying source documents. |
| Record attachment | No | Read/upload by clinical grant | No | Only explicitly shared attachment snapshot/reference | Same authorization at preview, download and exported URL issuance. |
| PDF export | No | In-scope clinician templates | Appointment confirmation only if specified separately | Own published report/explanation only | Export authorization rechecked; internal brief cannot leak through a guessed export ID. |
| Administrative audit | Practice membership/security events | Only with ADMIN_AUDIT | No | Own relevant access/security notices, not staff log | Redact patient/clinical details from admin view. |
| Clinical audit | No | CLINICAL_AUDIT within practice | No | No general staff audit | Patient identifiers shown only with clinical grant; no full text or secrets. |
| Account profile/preferences | Own account only | Own account only | Own account only | Own account only | Identity changes do not silently relink portal records. |

A scheduler uses searchSchedulingPatients rather than the clinical searchPatients endpoint. The minimal projection contains local patient/record selectors, name, birth date and contact fields only. registerSchedulingPatient requires both grants, atomically creates Patient plus empty PatientRecord and audit, and returns that same projection. Duplicate candidates require human selection. It creates no appointment, clinical fact, portal invitation or publication. Only the clinical workflow can correct an existing identity. An administrator has neither scheduling nor registration rights unless explicitly assigned.

## Negative authorization examples

- A practitioner from practice B requests a practice A record, source, AI task, export or attachment by ID: deny without disclosing its existence. Log a scoped security event without copying clinical content.
- An administrator opens a clinical deep link: show the forbidden state, not a disabled clinical page containing data.
- A linked patient changes a record ID in an appointment/publication route: deny; do not use a matching email as authorization.
- A patient opens a source marker in a published explanation: show only the publication's authorized source excerpt/document; internal source IDs are not discoverable.
- A membership is suspended while a tab or download session is open: recheck server authorization on each request; existing cached content needs clear session/context handling and must not be transferred across practice switches.

See the API contract for transport error policy. UI forbidden/not-found variants must preserve the server's existence-hiding decision.
