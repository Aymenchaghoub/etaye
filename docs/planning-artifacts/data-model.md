# Data model and integrity contract

Status: Proposed logical model, ready for design; the schema is implemented incrementally by the stories (V1 to V11 so far, see the end of this document). 2026-11-02. [Business rules](business-rules.md) own state vocabulary; [ADRs](../decisions/) explain choices. [Data dictionary](data-dictionary.md) owns field constraints.

Every practice-owned entity carries `practice_id`; every FK to another practice-owned entity is composite `(practice_id, id)` or is checked by an equivalent database integrity constraint. Clinical resources also retain record ownership. Server repositories require an authorized context; PostgreSQL RLS is defense in depth in the same transaction under a nonowner role. Global Account does not make Patient or PatientRecord global. No client-selected identifier establishes authority.

## Cardinalities

- Account 1→0..N Membership; Practice 1→0..N Membership; unique account/practice pair. Membership 1→0..1 Practitioner. Active account does not imply active membership.
- Practice 1→0..N Patient; Patient 1→1 PatientRecord. Account 1→0..N PortalLink; each adult record 0..1 active self-link [ASSUMPTION]. No automatic cross-practice identity matching.
- Practitioner 1→0..N AvailabilityRule/Exception and Appointment. PatientRecord 1→0..N Appointment/VisitPreparation/Consultation. Consultation links 0..1 Appointment; one appointment can link at most one active encounter.
- PatientRecord 1→0..N SourceRevision, ClinicalAssertion, Measurement, LabResult and RecordDocument. SourceRevision may supersede 0..1 prior revision of same record; one current branch is selected explicitly.
- ClinicalDocument 1→1..N DocumentVersion; each version has 0..N citations and review events. Citation references exactly one immutable source revision with locator; many citations can support one statement.
- DocumentVersion 1→0..N historical Publication records, each for one exact audience link. A Publication has 0..N curated source/attachment grants. At most one current PUBLISHED projection per logical document/audience.
- Domain mutation 1→0..N OutboxEvent and 1..N applicable AuditEvent; task has 0..N attempts internally and 0..1 committed result. ExportArtifact refers to exact version/template and optional publication.

```mermaid
erDiagram
 Account ||--o{ Membership : holds
 Practice ||--o{ Membership : scopes
 Membership ||--o| Practitioner : profiles
 Practice ||--o{ Patient : owns
 Patient ||--|| PatientRecord : identifies
 Account ||--o{ PortalLink : verifies
 PatientRecord ||--o{ PortalLink : grants
 PatientRecord ||--o{ Appointment : schedules
 Practitioner ||--o{ Appointment : attends
 Appointment o|--o| Consultation : optionally_links
 PatientRecord ||--o{ SourceRevision : records
 SourceRevision ||--o{ StatementCitation : supports
 ClinicalDocument ||--|{ DocumentVersion : versions
 DocumentVersion ||--o{ StatementCitation : cites
 DocumentVersion ||--o{ Publication : publishes
 Publication ||--o{ PublicationSourceGrant : curates
```

## Transactions and historical correction

Appointment booking/rescheduling uses exclusion constraints for practitioner and patient active ranges plus serialization shared with availability edits. Guard mutable aggregate version with If-Match. Canonical transport: 412 stale write, 409 business overlap/idempotency conflict, 428 missing precondition. A cancellation/no-show does not delete notes or create a consultation. Half-open ranges allow adjacency. Timezones and DST choices survive rescheduling history.

Commit domain state, required successful audit, idempotency outcome and outbox/job atomically. External AI/email/storage work is outside the request transaction. Worker claims use row locks/leases and fencing token; at most current lease commits. Recheck requester capability/resource scope before input retrieval and result publication. A revoked request never expands access through a worker.

Clinical corrections append immutable revisions with reason/actor/date. Document versions preserve generated baseline, human changes and cited source versions. Reviewed/validated text is never silently replaced; publication references exact immutable content. Relevant source changes mark dependents STALE without overwriting them. Withdrawal/replace revokes future portal/download grants, not already-downloaded files.

## Storage types and deletion

UUID keys, PostgreSQL timestamptz for instants, date for birth/uncertain date-only events, IANA timezone separately, numeric decimals for measurements, text/code for original units. API decimals are strings to avoid binary precision loss; numerical storage and display formatting differ. Unknown event time has explicit precision/status; upload time is separate.

Mutable operational entities have created_at/updated_at/actor and version. Immutable revisions/events have created/occurred time, source/actor and checksum as appropriate. Apply lifecycle status instead of routine destructive deletion of history. Hard-delete belongs only to a documented demo reset/retention operation with referential ordering; no broad cascading deletion of clinical evidence. Applied migrations are immutable Flyway SQL and future clean/upgrade checks validate constraints. Migration V9 (story 3.2) implements the evidence tables `source_revision` and `clinical_assertion` as specified in the [data dictionary](data-dictionary.md): composite practice and record keys, a supersession foreign key that stays inside the record and lineage, forced row-level security, a `SELECT`-only grant for the runtime role and a guard that rejects any update or delete. The demo migration V10 seeds the synthetic evidence and the reset restores it. V9 adds only these two tables to the schema of V1 to V8 (identity, access, invitation and patient tables); the other entities of this model are not implemented yet. Migration V11 (story 3.3) adds `measurement` on the same pattern (immutable revisions that cite their source revision, composite keys, an order guard, forced row-level security, `SELECT` only for the runtime role) constrains `practice.timezone` to an IANA name (`ck_practice_zone`) and replaces the access-context function so that a membership context carries the practice time zone; the demo migration V12 seeds the measurements and extends the reset. Laboratory results (3.4) and documents (3.5) follow the same pattern.
