# End-to-end traceability

Status: Reviewed planning coverage, not implementation or design completion.2026-11-02. Requirement definitions live in PRD; rules in business-rules.md. Screen/interaction catalogs and coverage.csv own design surface definitions/status. API operation IDs are in [contract index](contracts/operation-index.json). Every acceptance test below is a future test design.

| Requirement | Rule | Journey | Required screen families | Data | API operations / boundary | Planning stories | Acceptance test design |
|---|---|---|---|---|---|---|---|
| [FR-001](planning-artifacts/prd/prd.md#fr-001--account-access) | BR-003, BR-005 | J-01 | S-02, S-03, S-04, S-36, S-38 | Account, Session, Invitation | signIn, acceptInvitation, requestRecovery | 1.1, 1.2, 8.3 | AT-001, AT-002, AT-034 |
| [FR-002](planning-artifacts/prd/prd.md#fr-002--practice-isolation) | BR-001–004 | J-01 | S-05, S-36 | Practice, Membership, PortalLink | listAuthorizedPractices, listOwnPortalLinks | 1.3, 1.4 | AT-003, AT-004 |
| [FR-003](planning-artifacts/prd/prd.md#fr-003--team-administration) | BR-002–005 | J-07 | S-31, S-32 | Membership, Invitation | listMemberships, editMembershipCapabilities, suspendMembership | 2.1, 2.2 | AT-005, AT-006 |
| [FR-004](planning-artifacts/prd/prd.md#fr-004--patient-identity) | BR-004 | J-02, J-03 | S-03, S-07, S-08 | Patient, PatientRecord, PortalLink | searchPatients, createPatient, editPatient, searchSchedulingPatients, registerSchedulingPatient | 1.1, 3.1, 4.2 | AT-001, AT-009, AT-015-D/E |
| [FR-005](planning-artifacts/prd/prd.md#fr-005--longitudinal-record) | BR-008 | J-02 | S-07, S-09, S-10 | ClinicalAssertion, SourceRevision | getTimeline, appendAssertion, correctAssertion | 3.1, 3.2 | AT-009, AT-010 |
| [FR-006](planning-artifacts/prd/prd.md#fr-006--measurements) | BR-008–009 | J-02 | S-11 | Measurement, SourceRevision | listMeasurements, appendMeasurement, correctMeasurement | 3.3 | AT-011 |
| [FR-007](planning-artifacts/prd/prd.md#fr-007--laboratory-results) | BR-009 | J-02 | S-12 | LabResult, SourceRevision | listLabResults, appendLabResult, correctLabResult | 3.4 | AT-012 |
| [FR-008](planning-artifacts/prd/prd.md#fr-008--documents-and-sources) | BR-010 | J-02 | S-09, S-13, S-30 | RecordDocument, SourceRevision | uploadRecordDocument, getSourceRevision, downloadRecordDocument | 3.5, 5.3 | AT-013, AT-021 |
| [FR-009](planning-artifacts/prd/prd.md#fr-009--real-scheduling) | BR-006 | J-03 | S-06, S-14, S-15, S-16, S-26 | AvailabilityRule/Exception, Appointment | searchSchedulingPatients, registerSchedulingPatient, listOfferedSlots, bookAppointment, rescheduleAppointment, cancelAppointment | 2.3, 4.1, 4.2, 4.3, 4.4, 5.1, 7.5, 8.3 | AT-007, AT-014, AT-015, AT-016, AT-017, AT-019, AT-031, AT-034 |
| [FR-010](planning-artifacts/prd/prd.md#fr-010--visit-preparation) | BR-008 | J-02, J-06 | S-10, S-16, S-17, S-27 | VisitPreparation, SourceRevision | submitOwnPreparation, editOwnPreparation, getVisitPreparation | 4.5 | AT-018 |
| [FR-011](planning-artifacts/prd/prd.md#fr-011--clinician-brief) | BR-011–014 | J-02 | S-06, S-13, S-18 | ClinicalDocument/Version, StatementCitation | requestGeneration, getDocumentVersion, getSourceRevision | 5.1, 5.2, 5.3, 8.1 | AT-019, AT-020, AT-021, AT-032 |
| [FR-012](planning-artifacts/prd/prd.md#fr-012--consultations-and-reports) | BR-007 | J-04 | S-19, S-20 | Consultation, DocumentVersion | startConsultation, saveConsultationNotes, completeConsultation | 4.4, 6.1, 6.2 | AT-017, AT-023, AT-024 |
| [FR-013](planning-artifacts/prd/prd.md#fr-013--ai-drafting) | BR-011 | J-04 | S-18, S-20, S-21, S-37, S-40 | ProcessingTask, source snapshot | requestGeneration, getProcessingTask, retryProcessingTask | 5.2, 5.3, 5.4, 6.2, 7.1 | AT-020, AT-021, AT-022, AT-024, AT-027 |
| [FR-014](planning-artifacts/prd/prd.md#fr-014--review-versions-and-freshness) | BR-012, BR-014–015 | J-04 | S-06, S-09, S-18, S-21, S-22, S-23, S-24 | DocumentVersion, ReviewEvent | editDraftVersion, deriveNewDraft, validateDocumentVersion | 5.4, 6.3, 6.4, 7.1 | AT-022, AT-025, AT-026, AT-027 |
| [FR-015](planning-artifacts/prd/prd.md#fr-015--explicit-publication) | BR-013 | J-05 | S-23, S-24, S-28, S-29 | Publication, PublicationSourceGrant | publishVersion, replacePublication, withdrawPublication | 7.1, 7.2, 7.3, 8.2, 8.3 | AT-027, AT-028, AT-029, AT-033, AT-034 |
| [FR-016](planning-artifacts/prd/prd.md#fr-016--patient-portal) | BR-002, BR-013 | J-06 | S-05, S-24, S-25, S-26, S-27, S-28, S-29, S-30, S-36 | PortalLink, patient projection | listOwnPublications, readOwnPublication, readPublishedSources | 1.3, 4.5, 7.4, 7.5, 8.2 | AT-003, AT-018, AT-030, AT-031, AT-033 |
| [FR-017](planning-artifacts/prd/prd.md#fr-017--pdfs-and-emails) | BR-016 | J-02, J-05, J-06 | S-30, S-39 | ExportArtifact, OutboxEvent | requestExport, exportOwnPublication, downloadExport | 8.1, 8.2, 8.3 | AT-032, AT-033, AT-034 |
| [FR-018](planning-artifacts/prd/prd.md#fr-018--audit-settings-and-preferences) | BR-017 | J-07 | S-33, S-34, S-35 | AuditEvent, Practice, Account | listScopedAudit, editPractice, editOwnAccount | 2.3, 2.4 | AT-007, AT-008 |
| [FR-019](planning-artifacts/prd/prd.md#fr-019--synthetic-demonstration) | BR-018 | J-08 | S-01, S-37 | Synthetic fixture, IdempotencyReceipt | listDemoScenarios, resetSyntheticDemo | 1.4, 9.1, 9.3 | AT-004, AT-035, AT-037 |
| [FR-020](planning-artifacts/prd/prd.md#fr-020--delivery-evidence) | BR-001, BR-017–018 | J-08 | Operational evidence | Release manifest, safe test evidence | No application API; cicd-deployment-strategy.md and test-strategy.md | 9.2, 9.3 | AT-036, AT-037 |

## Story-level surface and verification map

| Story | Requirement(s) | Rule | Journey | Screen/PDF/email | Interaction | Acceptance design |
|---|---|---|---|---|---|---|
| [1.1](planning-artifacts/epics/epic-1.md) | FR-001, FR-004 | BR-004–005 | J-01 | S-03 | I-01, I-03 | AT-001-A/B/C |
| [1.2](planning-artifacts/epics/epic-1.md) | FR-001 | BR-003, BR-005 | J-01 | S-02, S-04, S-38 | I-22 | AT-002-A/B/C |
| [1.3](planning-artifacts/epics/epic-1.md) | FR-002, FR-016 | BR-001–004 | J-01 | S-05 | I-20 | AT-003-A/B/C |
| [1.4](planning-artifacts/epics/epic-1.md) | FR-002, FR-019 | BR-001–002 | J-08 | S-36 | I-22 | AT-004-A/B/C |
| [2.1](planning-artifacts/epics/epic-2.md) | FR-003 | BR-002–005 | J-07 | S-31, S-32 | I-01 | AT-005-A/B/C |
| [2.2](planning-artifacts/epics/epic-2.md) | FR-003 | BR-003, BR-017 | J-07 | S-32 | I-02 | AT-006-A/B/C |
| [2.3](planning-artifacts/epics/epic-2.md) | FR-018, FR-009 | BR-002, BR-006 | J-07 | S-33 | I-24 | AT-007-A/B/C |
| [2.4](planning-artifacts/epics/epic-2.md) | FR-018 | BR-002, BR-017 | J-07 | S-34, S-35 | I-25 | AT-008-A/B/C |
| [3.1](planning-artifacts/epics/epic-3.md) | FR-004, FR-005 | BR-004 | J-02 | S-07, S-08 | I-04 | AT-009-A/B/C |
| [3.2](planning-artifacts/epics/epic-3.md) | FR-005 | BR-008 | J-02 | S-09, S-10 | I-05 | AT-010-A/B/C |
| [3.3](planning-artifacts/epics/epic-3.md) | FR-006 | BR-008–009 | J-02 | S-11 | I-06 | AT-011-A/B/C |
| [3.4](planning-artifacts/epics/epic-3.md) | FR-007 | BR-009 | J-02 | S-12 | I-07 | AT-012-A/B/C |
| [3.5](planning-artifacts/epics/epic-3.md) | FR-008 | BR-010 | J-02 | S-13 | I-08, I-09 | AT-013-A/B/C |
| [4.1](planning-artifacts/epics/epic-4.md) | FR-009 | BR-006 | J-03 | S-14, S-15 | I-24 | AT-014-A/B/C |
| [4.2](planning-artifacts/epics/epic-4.md) | FR-009 | BR-006 | J-03 | S-14, S-16, S-26 | I-10 | AT-015-A/B/C |
| [4.3](planning-artifacts/epics/epic-4.md) | FR-009 | BR-006 | J-03 | S-16, S-26 | I-11, I-12 | AT-016-A/B/C |
| [4.4](planning-artifacts/epics/epic-4.md) | FR-009, FR-012 | BR-006–007 | J-03 | S-16 | I-13 | AT-017-A/B/C |
| [4.5](planning-artifacts/epics/epic-4.md) | FR-010, FR-016 | BR-008 | J-06 | S-17, S-27 | I-05 | AT-018-A/B/C |
| [5.1](planning-artifacts/epics/epic-5.md) | FR-009, FR-011 | BR-006, BR-011 | J-02 | S-06, S-18 | I-09 | AT-019-A/B/C |
| [5.2](planning-artifacts/epics/epic-5.md) | FR-011, FR-013 | BR-011 | J-02 | S-18, S-40 | I-14 | AT-020-A/B/C |
| [5.3](planning-artifacts/epics/epic-5.md) | FR-008, FR-011, FR-013 | BR-008, BR-010–011 | J-02 | S-13, S-18, S-21 | I-09 | AT-021-A/B/C |
| [5.4](planning-artifacts/epics/epic-5.md) | FR-013, FR-014 | BR-011, BR-014 | J-04 | S-24, S-37, S-40 | I-23 | AT-022-A/B/C |
| [6.1](planning-artifacts/epics/epic-6.md) | FR-012 | BR-007 | J-04 | S-19 | I-05 | AT-023-A/B/C |
| [6.2](planning-artifacts/epics/epic-6.md) | FR-012, FR-013 | BR-007, BR-011 | J-04 | S-20, S-21 | I-14, I-15 | AT-024-A/B/C |
| [6.3](planning-artifacts/epics/epic-6.md) | FR-014 | BR-012, BR-015 | J-04 | S-21, S-22 | I-15, I-21 | AT-025-A/B/C |
| [6.4](planning-artifacts/epics/epic-6.md) | FR-014 | BR-012 | J-04 | S-23 | I-16 | AT-026-A/B/C |
| [7.1](planning-artifacts/epics/epic-7.md) | FR-013, FR-014, FR-015 | BR-011–013 | J-05 | S-20, S-21 | I-15, I-16 | AT-027-A/B/C |
| [7.2](planning-artifacts/epics/epic-7.md) | FR-015 | BR-013 | J-05 | S-23 | I-17 | AT-028-A/B/C |
| [7.3](planning-artifacts/epics/epic-7.md) | FR-015 | BR-013–014 | J-05 | S-24 | I-18 | AT-029-A/B/C |
| [7.4](planning-artifacts/epics/epic-7.md) | FR-016 | BR-002, BR-013 | J-06 | S-25, S-28, S-29 | I-09 | AT-030-A/B/C |
| [7.5](planning-artifacts/epics/epic-7.md) | FR-009, FR-016 | BR-006, BR-010, BR-013 | J-06 | S-26, S-30 | I-10, I-11, I-12 | AT-031-A/B/C |
| [8.1](planning-artifacts/epics/epic-8.md) | FR-017, FR-011 | BR-016 | J-02 | S-39, P-01 | I-19 | AT-032-A/B/C |
| [8.2](planning-artifacts/epics/epic-8.md) | FR-017, FR-015, FR-016 | BR-013, BR-016 | J-06 | S-39, P-02, P-03 | I-19 | AT-033-A/B/C |
| [8.3](planning-artifacts/epics/epic-8.md) | FR-001, FR-009, FR-015, FR-017 | BR-005, BR-006, BR-016 | J-01, J-05 | M-01–05 | I-01, I-17, I-23 | AT-034-A/B/C |
| [9.1](planning-artifacts/epics/epic-9.md) | FR-019 | BR-018 | J-08 | S-01, S-37 | I-26 | AT-035-A/B/C |
| [9.2](planning-artifacts/epics/epic-9.md) | FR-020 | BR-001, BR-017–018 | J-08 | Operational evidence | I-23 | AT-036-A/B/C |
| [9.3](planning-artifacts/epics/epic-9.md) | FR-019, FR-020 | BR-018 | J-08 | S-37, S-38 | I-22, I-23 | AT-037-A/B/C |

## Print/email and event lineage

| Surface | Requirements / rule | Data and permission | Trigger | Story / test |
|---|---|---|---|---|
| P-01 | FR-011/017, BR-016 | Clinician brief exact version/source manifest; CLINICAL_READ only | Authorized export I-19 |8.1 / AT-032|
| P-02 | FR-012/015/017, BR-013/016 | Encounter/report version; clinician grant or exact current patient publication | I-19 guarded export |8.2 / AT-033|
| P-03 | FR-015/016/017, BR-013/016 | Patient explanation/publication/curated grants | I-19 from current published detail |8.2 / AT-033|
| M-01/M-02 | FR-001/003/004/017, BR-005/016 | Intended invitation grant; no clinical text | InvitationIssued committed outbox event |8.3 / AT-034|
| M-03 | FR-001/017, BR-005/016 | Eligible account recovery; token digest/expiry | AccessRecoveryRequested with generic response |8.3 / AT-034|
| M-04 | FR-009/017, BR-006/016 | Own appointment minimal date/practice | AppointmentBooked/Rescheduled/Cancelled after commit |8.3 / AT-034|
| M-05 | FR-015/017, BR-013/016 | Minimal portal notification, current grant on destination | PublicationCreated/Replaced/Withdrawn after commit |8.3 / AT-034|

Detailed I-01–28 mappings are in the interaction catalog and coverage rows. FR-020 has no invented product screen; operations/test/release evidence is its surface. P-04 is deliberately outside scope D-07, not silently untraced required work.
