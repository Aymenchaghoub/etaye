# Documentary API contract

Status: Versioned contract with implemented and planned operations. `openapi.yaml` is OpenAPI 3.1 written in JSON syntax (valid YAML 1.2). [Operation index](operation-index.json) maps all operations. [Business rules](../planning-artifacts/business-rules.md), [access matrix](../planning-artifacts/access-matrix.md), [data dictionary](../planning-artifacts/data-dictionary.md) and ADRs are authoritative companions. Version 0.5.0-planning (2026-11-24) contains 100 operations.

## Runtime coverage

Story 10.1 serves `getReadiness`, `getCsrfToken`, `createPatient` and `getPatientRecord`. Story 10.2 adds `getApiMeta`. Story 1.2 serves `signIn`, `getSession`, `signOut`, `requestRecovery` and `completeRecovery`. Story 1.1 adds `inspectInvitation` and `acceptInvitation`. Story 3.1 adds `searchPatients`, `editPatient`, `searchSchedulingPatients` and `registerSchedulingPatient` and extends `createPatient` with the duplicate rule. Story 3.2 adds the read operations `getTimeline`, `listRecordSources`, `getSourceRevision`, `listAssertions` and the new `getHistorySummary` (UD-67); `appendAssertion` and `correctAssertion` stay planned for increment 2 (D-60). Story 3.3 adds the read operations `listMeasurements` and the new `listMeasurementRevisions` (UD-71) and the practice time zone on the membership access context; `appendMeasurement` and `correctMeasurement` stay planned for increment 2 (D-24). The local story 9.1 package serves `listDemoScenarios` and `resetSyntheticDemo` and supplies the demo metadata. Remaining operations describe planned behavior. Readiness returns JSON `UP` on 200 and `DOWN` on 503 without opening a session. The versions endpoint is public and stateless. The Web SDK sends `X-Client-Version: web/<semantic-version>` using its package version. This is compatibility metadata. The 426 enforcement belongs to story 10.4.

## Web sign-in and recovery

The sign-in and recovery operations return `Cache-Control: no-store`. A valid sign-in rotates the opaque session cookie and the CSRF value, and returns current active membership and verified portal link IDs. The browser obtains the rotated CSRF value from `GET /csrf` before its next write. The nearer of the 30-minute inactivity deadline and the 8-hour absolute deadline is reported in `expiresAt`. Reading session status rechecks account state and grants without renewing inactivity.

The exact local return paths are `/compte`, `/compte/pratiques/{membershipId}` and `/compte/patient/{portalLinkId}`. A scoped path requires its current grant. Sign-in falls back to `/compte` for any other destination. Native sessions and protected clinical workflows have separate operations.

Account mutations require the session-bound CSRF header, including anonymous sign-in and recovery. Missing, suspended and incorrect credentials share `401 INVALID_CREDENTIALS`. Anonymous CSRF issuance and account submissions have bounded request budgets and return `429 RATE_LIMITED` with a delta-seconds `Retry-After`. Use a 16–128 character recovery key consisting of ASCII letters, digits, underscore or hyphen. `POST /access-recovery` gives the same 202 for all valid addresses and deduplicates the normalized address and key for 15 minutes. The response never confirms a delivery. A reset token has a 15-minute lifetime and can be consumed once. Redemption revokes all of the account's existing sessions. Expired, already-used and ineligible tokens share `422 INVALID_RECOVERY_LINK`. A password under 12 Unicode code points receives `422 PASSWORD_TOO_SHORT`.

For a local synthetic demonstration, set `ETAYE_DEMO_RECOVERY_INBOX=true` on the API process, start the web app at `http://localhost:5173` and open the separate listener at `http://127.0.0.1:8025/` from the address bar. Its in-memory capture is restricted to `@example.test` addresses. The link carries the token in the reset page fragment. Branded mail and external delivery belong to story 8.3.

## Practice context selection

Story 1.3 extends `GET /session` with `Session.contexts` (UD-52). The response maintains `Cache-Control: no-store` and the existing session/grant fields. It returns just the grant kind and ID, practice ID and label, and a display role. Memberships and verified patient links appear only while the account, grant and practice are active. List entries correspond exactly to the respective membership/link IDs in the same response. A valid account with no contexts receives an empty array. Sign-in and invitation acceptance retain their existing response semantics.

The display role summarizes a grant for presentation. A portal link is `PATIENT`. Staff membership categories prefer clinical capabilities, then administrative capabilities, then scheduling, then `TEAM`. The server still checks the actual capability and the ownership of each resource. The selector sends no patient or record details. The exact link ID keeps two portal grants at the same practice distinct.

The web route selects the context in its own tab and rechecks it through `getSession` before rendering the protected destination. It never stores an active practice in the session. A missing or inconsistent projection is an unavailable state for a context-aware client. Unknown kinds cannot be selected. A denied or removed target enters generic recovery without exposing its previous label or payload. The two standalone discovery lists remain planned.

## Invitation activation

Both invitation operations are anonymous and return `Cache-Control: no-store`. `inspectInvitation` returns the masked preview (`purpose`, `practiceName`, `recipientHint`, `expiresAt`, `eligibility`) with an optional `acceptedAt` for an already-used token, without consuming anything or exposing a practice, invitation or record identifier; a well-formed unknown token is `404` byte-identical to any other unknown resource. The raw token travels only in the request body and the page fragment, never in a URL, query, log or audit row; the server stores a digest (72-hour invitation lifetime, UD-26).

`acceptInvitation` is atomic and single-use per BR-005: one transaction marks the invitation `ACCEPTED` (+`acceptedAt`), creates exactly the intended membership with the invitation's capabilities (TEAM_MEMBERSHIP) or exactly one active portal link to the invited record (PATIENT_PORTAL), creates the account when the recipient has none, and writes the audit event and the idempotency receipt. A team invitation never creates a portal link; a portal invitation never grants a staff capability. Success returns `Session` and rotates the opaque cookie and the CSRF value exactly as `signIn` does (`returnTo` stays sign-in-only); the client routes by purpose to `/compte` (team) or `/compte/patient/{activatedPortalLinkId}` when the exact portal link remains active (patient).

Input modes: when the recipient has no account, `password` is required and creates the account (`422 PASSWORD_TOO_SHORT`); a new TEAM account also requires `displayName` (`422 DISPLAY_NAME_REQUIRED`) while a new PATIENT_PORTAL account derives its display name from the record's patient identity; a signed-in account whose normalized email matches the intended recipient may accept without them; an unsigned-in existing account receives `422 SIGN_IN_REQUIRED` and the client offers sign-in within the activation flow. A suspended practice makes the invitation unavailable without consuming it (`422 INVITATION_UNAVAILABLE`). A suspended recipient account or membership never regains access through acceptance; the same `422 SIGN_IN_REQUIRED` applies and sign-in keeps refusing. `recipientVerification` is the record patient's birth date for PATIENT_PORTAL (required); for TEAM_MEMBERSHIP in increment 1 token possession is the verification and the field is absent — the recipient email check returns with issuance in increment 2 (D-23).

Refusals: an expired, revoked or already-used invitation answers `422 INVITATION_EXPIRED`, `422 INVITATION_REVOKED` or `422 INVITATION_ALREADY_USED` (the holder may know the history); a wrong or missing verification answers one generic `422 RECIPIENT_MISMATCH` whose wording never says which input differed; a portal invitation whose record already holds an active link answers `409 PORTAL_LINK_EXISTS`; a team invitation conflicting with the capabilities of an existing active membership answers `409 MEMBERSHIP_EXISTS`; an unknown token stays `404`. Anonymous submissions share the bounded account budgets (`429 RATE_LIMITED` with delta-seconds `Retry-After`). The idempotency key of an anonymous acceptance is scoped to the invitation digest, the operation and the body hash; the replay memory lives on the invitation row and never stores the raw token or the password. Authenticated acceptances can only be replayed by their original account. Anonymous acceptance replay is bounded by the invitation expiry and the credential revision at acceptance.

## Record evidence reads (story 3.2, UD-67)

Story 3.2 serves the read side of the record: `getTimeline`, `listRecordSources`, `getSourceRevision`, `listAssertions` and `getHistorySummary`. All five require `CLINICAL_READ` and follow the carrier refusals above: the actor's current membership in the route practice and the capability are re-resolved on every request, then the record or revision inside that practice; a record or revision of another practice, a guessed identifier and an absent resource share one `404` problem body apart from `correlationId` (a malformed, foreign or mismatched cursor is a `400`), and an administration-only member receives `403`. Every response is `Cache-Control: no-store` and carries no `ETag`. Routine reads are not audited; a denial writes the bounded security event (D-62).

**Evidence items.** A source revision is immutable. Its lineage groups the revisions of one source, and a correction appends a revision with a reason that supersedes the previous one inside the same record. Text, labels and author labels are plain text and a client never renders them as markup. `kind` is a `SourceKind`; the filter `kind` of `getTimeline` accepts those values, repeated, and rejects any other with `400`.

**Timeline text.** A timeline item carries the stored text of its revision (`excerpt`, plain text) so that a note or a patient statement reads in the list without a second request; a client shows it for the kinds that carry text and never renders it as markup. `getSourceRevision` opens the same immutable revision with its full provenance.

**Order and unknown dates.** The timeline orders items by event date descending, then recording time descending, then id. An item whose event date is unknown is never placed at its recording date: it forms a last group, ordered by recording time and id, and shows only its recording date. `from` is inclusive, `to` exclusive; a date-only event counts at 00:00 of its timezone; items with an unknown event date stay listed under every date filter, so a filter never hides a fact. `listRecordSources` uses the same order; `listAssertions` lists oldest event date first with unknown dates last.

**Paging.** Default 25, maximum 100. The opaque cursor is bound to practice, actor, record, view instant and filters and holds only the id of the last item; a malformed, foreign or mismatched cursor is `400`. No total is returned: a client says how many items it shows, never how many exist.

**The `asOf` view (UD-32).** With `asOf`, each lineage contributes the latest revision whose server-provided recording time is at or before the instant. A lineage whose first revision is later is absent, and no count, cursor, label, text or error discloses a later revision. The instant is an absolute date-time; a client shows it in the practice time zone. A future instant is `400`. `getSourceRevision` with `asOf` answers `404` for a revision recorded after the instant. The view is read-only and uses the same authorization. The identity in the record header is always the current one (D-63).

**Assertions and section state (BR-008).** A statement is PRESENT, EXPLICITLY_ABSENT, UNKNOWN or NOT_ASKED. `getHistorySummary` returns for ALLERGY, HISTORY and REPORTED_TREATMENT, in that order, the derived `SectionState` and the current statements oldest first. The server derives the state with one pure function that the clinician brief reuses (UD-30): no statement is NOT_RECORDED and never means absence; a negative and a present statement together are CONFLICTING and read as unknown, in either order, with both statements listed and nothing resolved; a dated negative never negates a newer present report. An UNKNOWN or NOT_ASKED statement beside a present or negative one stays listed as dated history.

**Planning-contract correction (G-17).** In the `0.5.0-planning` contract `Assertion` required `text`, `sourceType`, `correctionReason` and `sourceRevisionId` on output, which contradicts the fixture and the data dictionary (an UNKNOWN statement has no text, no source and no correction, and an original has no reason). These four fields become optional on output and `recordedAt` is added as required. No consumer, generated or manual, existed before story 3.2, so the correction is made before any client depends on the earlier shape. `TimelineItem.kind` becomes the `SourceKind` enumeration for the same reason. Every other change of this section is a new optional field, a new optional query parameter or a new operation (ADR-009).

## Measurements (story 3.3, UD-71)

Story 3.3 serves the read side of the measurements: `listMeasurements` and `listMeasurementRevisions`. Both require `CLINICAL_READ` and follow the carrier refusals and the existence-hiding rules of the record evidence reads above; every response is `Cache-Control: no-store` and carries no `ETag`. `appendMeasurement` and `correctMeasurement` stay planned for increment 2 (D-24) and are not routed.

**Measurement lineage.** A `Measurement` is one immutable revision of a numeric observation. A correction appends the next revision of the same lineage with its reason, and `version` equals the revision number; the original is never edited or removed. Every revision cites the source revision that supports it (`sourceRevisionId`, `sourceReference`) and is recorded no earlier than that source. `value` is a decimal string that the server returns exactly as stored (no rounding, no reformatting), `unit` is returned as entered and is never converted, and `observedAt` is always an instant: an observation without a time does not exist (BR-009, D-65).

**Blood pressure.** A blood-pressure reading is two measurements, the concepts `SYSTOLIC_BLOOD_PRESSURE` and `DIASTOLIC_BLOOD_PRESSURE`, that share a `groupId` and the same `observedAt`. A group with one component is returned as it is; the client says it is incomplete and never completes it. `concept` is a free code up to 100 characters; the catalogue of allowed codes and units belongs to the entry screens (D-66), and a client labels the codes it knows and shows any other code verbatim.

**Reading.** `listMeasurements` returns the current revision of every lineage, newest observation first (observed time, then recording time, then id), 25 per page by default and 100 at most, without a total: a client says how many it shows, never how many exist. The opaque cursor is bound to the practice, the actor, the record, the view instant and the filters; a malformed, foreign or mismatched cursor is a `400`. `from` is inclusive and `to` exclusive on the observed time. `concept` (repeated, at most 10 values) keeps only the measurements of those exact stored codes: two spellings of a unit or a concept are two series and are never merged by the server.

**Lineage read.** `listMeasurementRevisions` returns the revisions of the lineage that contains `measurementId`, oldest first, each with its recording time, author, source reference and, for a correction, the reason.

**The `asOf` view.** The rule of the evidence reads applies: each lineage contributes the latest revision recorded at or before the instant; a lineage whose first revision is later is absent; `listMeasurementRevisions` lists only the revisions recorded at or before the instant, and a `measurementId` recorded after it is a `404` byte-identical to an absent resource, so nothing discloses a later revision. A future instant is a `400`.

**Practice time zone (D-64).** The membership `AccessContext` of `GET /session` carries `timezone`, the IANA zone of the practice. A client reads and shows every instant of the record screens in that zone; the server still exchanges absolute instants.

## Synthetic demonstration

Story 9.1 specifies these operations for the loopback-only synthetic demo package (UD-56). `PUBLIC_SYNTHETIC` and `OPERATOR_DEMO_RESET` are policy predicates. The latter requires the current seeded operator identity, which has no clinical grant. The ordinary application keeps its existing stateless metadata and exposes no demo catalogue or reset.

- `listDemoScenarios` returns the fixed Nora, Claire and Mila journeys in that order, paginated by an opaque cursor. It discloses the fictional actor, role, practice, journey, limits and published synthetic sign-in identifier before entry. Scenario selection supplies presentation context to sign-in. The regular session and current access checks govern entry. The local demo README publishes the shared synthetic password.
- The administration card describes the delivered boundary: « Ouvrir l’espace administratif du cabinet et constater que les dossiers cliniques restent réservés aux personnes habilitées. » The catalogue advertises only the journeys available in this slice. The live package supplies all six disclosure fields. The planning schema keeps the additions optional for client compatibility.
- `resetSyntheticDemo` requires an authenticated operator, valid CSRF, a nonblank reason and an idempotency key. A successful reset atomically restores the supported fixture rows and revokes open sessions, including the initiating session. The operator may sign in again to replay the same key and reason for the original acknowledgement. Reusing that key with a different reason returns `409 IDEMPOTENCY_CONFLICT`. A concurrent reset returns `409 DEMO_RESET_IN_PROGRESS`, and a new reset inside the operator cooldown returns `429 RATE_LIMITED` with `Retry-After`. An invalid input or database failure cannot advance the generation.
- The no-store `getApiMeta` read supplies `demoDatasetVersion` and `demoResetGeneration` together in demo mode. The generation starts at 1 and advances once per committed reset, including a restore of the same fixture version. The client combines the changed marker with the existing session-expiry path. A busy fixture or database failure can return `503`. The `202` response remains a correlation acknowledgement and the marker is the observable completion evidence.

## Common transport and permissions

Future same-origin base `/api/v1`, opaque secure session cookie, CSRF token header on unsafe methods. Obtain `/csrf` before login/mutation. Login rotates session; logout/reset/suspension revokes applicable sessions. `x-required-permissions` entries are conjunctive (all entries must pass) and describe the resource policy in addition to session authentication. Composite labels such as AUTHORIZED_REQUESTER are policy predicates, not assignable roles; derive them from current capability plus ownership. Never trust a client role or route practice ID. For every practice path, establish active membership or verified own portal link, capability and resource practice/record. A body recordId must match its path/authorized context. One tab's context cannot silently redirect another tab's writes.

404 hides resources outside authorized practice/record; 403 is a forbidden action on a visible resource or CSRF failure. 401 means missing/expired authentication; 400 malformed format; 422 invalid semantic input/transition; 409 scheduling/idempotency/business conflict; 412 stale expected version; 428 absent version precondition; 429 quota/rate limit with Retry-After; 503 unavailable dependency. Problems use RFC 9457 with stable code/correlationId and safe field errors. No stack trace, secret, source body or cross-practice existence detail in errors. Error variants share a schema but status/code must match actual outcome.

### Carrier read refusals (story 1.4)

`getPatientRecord` authorizes before it reads: session, then the actor's current membership in the route practice and `CLINICAL_READ`, then the record inside that practice, all re-resolved on every request. A conditional `If-None-Match` is evaluated only after that authorization, so a denied actor never receives a `304` or an `ETag`. The refusals carry no resource data and the same schema:

- `401` — no session, an expired session or a suspended account; the session is closed.
- `403 FORBIDDEN` — the actor holds a membership in the route practice (their context is already visible to them) but it is suspended or lacks `CLINICAL_READ`, for example an administrator without a clinical capability.
- `404 NOT_FOUND` — every other case, with an identical problem body apart from `correlationId`: a practice where the actor holds no membership (including an unknown practice and any practice reached by a portal-only account), a record that is absent, or a record that belongs to another practice. A linked patient's portal link never grants this staff read, not even for the patient's own record.
- `400` — a path identifier that is not a UUID.

Only the actor's own memberships can establish the practice of the security audit event written for a denial, and a failed audit write never changes the response (BR-017). This clarifies the observed behavior of the planned `0.5.0-planning` contract; no operation, schema or status changes.

## Declared error responses

Error responses are declared per operation, never copied onto all of them. A GET declares no 409, 412, 422 or 428. 400 appears when an operation has a body, a query or a path parameter; 401 on every operation except public ones (sign-in keeps it for bad credentials); 403 on authenticated operations and wherever a CSRF header is required; 404 when a path parameter or a token can refer to a hidden resource; 409 on unsafe operations except sign-in, sign-out and recovery; 412 and 428 exactly on operations that require `If-Match`; 422 on operations with a body or a state transition; 429 and 503 are general domain API responses. The stateless metadata and readiness operations declare their observed or specified outcomes. The router responds with 405 and `Allow` for unsupported methods rather than declaring a 405 on the supported operation. The carrier read’s conditional 304 includes `ETag` and has no body. The documentary validator checks contract and index consistency.

## Business error codes

`Problem.code` is a stable uppercase identifier, never reused for another meaning. Proposed initial catalog (additive):

| Code | Status | Meaning |
|---|---|---|
| DUPLICATE_PATIENT | 409 | Likely duplicate local patient; the response lists typed same-practice `candidates`; the registration is repeated with their record IDs in `confirmedDistinctRecordIds` once a human confirmed a different person |
| SLOT_CONFLICT | 409 | The practitioner is already booked on an overlapping interval |
| PATIENT_OVERLAP | 409 | The patient record already has an overlapping active appointment (UD-23) |
| IDEMPOTENCY_CONFLICT | 409 | Same idempotency key with a different body |
| DEMO_RESET_IN_PROGRESS | 409 or 503 | A concurrent reset is in progress or a fixture request arrived during the reset |
| LAST_ADMINISTRATOR | 409 | The change would remove the last active practice administrator |
| STALE_VERSION | 412 | The `If-Match` version is not the current one |
| PRECONDITION_REQUIRED | 428 | A required `If-Match` header is missing |
| BRIEF_NOT_REVIEWABLE | 422 | Review, change request or validation attempted on a CLINICIAN_BRIEF |
| NOT_A_BRIEF | 422 | `acknowledgeBrief` attempted on another document purpose |
| SOURCE_STALE | 422 | Validation or publication of a version whose sources are STALE |
| CITATION_UNSUPPORTED | 422 | A required citation support review is missing or negative |
| PUBLICATION_INELIGIBLE | 422 | The version cannot be published (brief, not validated, not current) |
| TASK_QUOTA_EXCEEDED | 429 | The processing quota is exhausted |
| INVITATION_EXPIRED | 422 | The invitation lifetime (72 hours) has passed |
| INVITATION_UNAVAILABLE | 422 | The practice cannot currently grant access |
| INVITATION_REVOKED | 422 | The invitation was revoked by an authorized issuer |
| INVITATION_ALREADY_USED | 422 | The invitation was already accepted once |
| MEMBERSHIP_EXISTS | 409 | An existing membership holds a different grant and is left intact |
| PORTAL_LINK_EXISTS | 409 | The invited record already holds an active portal link |
| RECIPIENT_MISMATCH | 422 | Intended-recipient verification failed; never says which input differed |
| DISPLAY_NAME_REQUIRED | 422 | A new-account acceptance lacks the display name |
| SIGN_IN_REQUIRED | 422 | The recipient account exists; sign in before accepting |
| CLIENT_TOO_OLD | 426 | The client version is below the minimum (ADR-009; planned, not yet a declared response) |

## Contract conventions

- ETag appears only on a response whose body is a single versioned aggregate (it names the version a later `If-Match` must quote); lists, tokens, acknowledgements, sessions and binary content carry none.
- `Capability` is declared once and referenced.
- Paginated responses keep one explicit wrapper schema per resource. A shared base with `allOf` conflicts with `additionalProperties: false` and explicit wrappers keep generated clients simple; the duplication is deliberate.
- A creation that needs a parent in its body (for example a document draft with its `recordId`) lives at the practice level and is listed under the record; the body `recordId` must match the authorized record.
- Version comparison is computed on the client from two versions in increment 1; no diff operation exists (decision of 2026-11-17).
- Acknowledgements: `acknowledgePreparation` (visit preparation) and `acknowledgeBrief` (CLINICIAN_BRIEF only) append an acknowledgement to the exact current version under `If-Match`. They never change authorship, content or visibility.
- `asOf` (UD-32) is an optional query parameter of the record read operations (timeline, assertions, measurements, laboratory results, sources): it returns each lineage's latest revision recorded at or before a past instant, read-only and under the same authorization; a future instant is a 400.
- `Citation.mechanicalCheck` (UD-31) is computed by the server and read-only; it is a flag, never a score.
- The native operations owned by ADR-008 (`createNativeSession`, `registerDevice`, `listOwnDevices`, `revokeDevice`) are planned for the 10.5 contract preparation (see section 8.3 of the [roadmap](../planning-artifacts/roadmap.md)).

## Pagination, filters and ordering

Cursor page `{items,nextCursor}`, default 25, maximum 100. Cursor is opaque and bound to practice/actor/filter/sort; invalid or reused-under-different-context cursor fails safely. Stable `(event time,id)` tie-break order; newly inserted data may require refresh, never imply a frozen snapshot unless the cursor includes one. Search only local patients. Query text is redacted from logs and not copied to public page URLs. Bounded from/to, practitionerId, appointment state and audit eventType filters are allowlisted where declared. Reject unknown sort/filter fields rather than feeding them to SQL. Do not expose unauthorized totals or other-patient identity through offered slots.

## Patient search and duplicate rule (story 3.1, UD-63)

**Normalization.** Text is first put in canonical composition, so a decomposed accent equals its precomposed letter. A name or query word is then lowercased, stripped of combining marks and Latin diacritics, `œ`, `æ` and `ß` expand to `oe`, `ae` and `ss`, and every other non-alphanumeric character is a word separator (hyphens, apostrophes and spaces). A stored name whose words would be empty keeps its lowercase text as key. The server applies one normalization, in the database, to stored names and to queries.

**Search.** The query is split into tokens. A token written `JJ/MM/AAAA` or `AAAA-MM-JJ` that is a real date matches that exact birth date, and two different dates match nothing; every other token must match the start of a word of the normalized first or last name. All tokens must match. Results are ordered by normalized last name, first name, then id. The clinical `q` is optional (empty lists the practice) and at most 100 characters; the scheduling `q` is required, two to 100 characters. The cursor encodes only the id of the last row and a binding to practice, actor and normalized query, never a name; the position is read from the current row. A cursor that does not decode, does not match, or names a patient of another practice is `400 INVALID_PARAMETER`, even when the query itself cannot match. A page never carries a total, so a count is never an authorization disclosure. The query is never logged or copied to a URL.

**Duplicate rule.** On registration, a candidate is any patient of the same practice whose normalized last name and first name both equal the submitted ones, in either order, or whose birth date equals the submitted one and whose normalized last name equals the submitted last name. At most 10 candidates are returned, ordered like search. When more than ten patients match, the registration stays refused because they cannot all be named. Over-warning is accepted because a human decides; typos are not detected (D-59). Another practice is never searched.

**Confirmation.** `createPatient` and `registerSchedulingPatient` answer `409 DUPLICATE_PATIENT` with the `candidates` and create nothing. The client shows them; the person either opens an existing record or repeats the registration with `confirmedDistinctRecordIds` naming every candidate shown. The server re-runs the rule at commit; a current candidate that is not named yields a fresh 409 with the updated list. The confirmation belongs to the canonical body hash, so a confirmed request needs a new idempotency key; a list with a null or a repeated record is `400`. A correction through `editPatient` runs no duplicate check. Registrations of the same likely identity in one practice are serialized by transaction-scoped locks taken before the check, so two concurrent requests under different keys cannot both pass it: one is created and the other receives the warning. `If-Match` is one quoted numeric version (`"7"`); weak, unquoted and list forms are `400`. A correction also moves the record version, so the record ETag changes with the patient it embeds.

## Scheduling patient projection

`GET /practices/{practiceId}/scheduling/patients` (searchSchedulingPatients) requires SCHEDULE_MANAGE and a query of at least two characters; cursor/limit rules apply. The SchedulingPatient projection exposes only local identity/contact and selectors, never clinical history, source metadata or portal-account linkage. searchPatients retains CLINICAL_READ. Knowing a recordId does not authorize opening that record.

`POST` on the same path (registerSchedulingPatient) requires **both** SCHEDULE_MANAGE and separately assigned PATIENT_REGISTER (PD-001, accepted UD-17). PatientEdit contains only demographic fields; use the same birth/contact/duplicate checks. Atomically create identity plus empty local PatientRecord, audit and idempotency outcome; return201 SchedulingPatient. A likely duplicate returns409 Problem with code DUPLICATE_PATIENT; refresh the authorized minimal lookup for human selection. The problem response contains no clinical data or untyped candidate fields. No clinical facts, portal link, invitation or appointment is created. Without registration permission, show the restricted S-08 variant and involve an authorized team member. Existing identity correction stays clinical-only. See AT-015-D/E and the access matrix.

## Validation beyond shape

| Group | Required semantic checks |
|---|---|
| Identity/invitations | Normalize email by defined policy, strong supported password policy/hash, single-use token digest and expiry; intended-recipient verification. Portal invitation requires recordId, team invitation explicit permitted capabilities; reject irrelevant grant fields. Recovery response remains generic. Activation: PATIENT_PORTAL requires the record patient's birth date as `recipientVerification`; TEAM in increment 1 verifies token possession; `password` for any new account and `displayName` for a new TEAM_MEMBERSHIP account. |
| Patients/records | Birth date not future; required identity fields, each with at least one letter or digit; practice-local duplicate suggestions only; stable patient/record identity on corrections. Contact email is not proof of account linkage. |
| Assertions/source dates | PRESENT/EXPLICITLY_ABSENT/UNKNOWN/NOT_ASKED are distinct. UNKNOWN event date forbids fabricated instant/date; DATE carries a date, INSTANT an instant. Recorded time always server-provided. Corrections require reason and prior current revision. |
| Measurements/labs | Finite decimal with concept/unit and actual observed time; retain original values. BP is grouped systolic/diastolic observations. Labs require exactly numericValue or qualitativeValue according to valueKind, comparator only for numeric, supplied unit/interval preserved and ordered bounds. No invented universal medical range or implicit normal result. |
| Upload | Multipart bounded 10MiB proposed cap and PDF/PNG/JPEG signature/MIME allowlist. Quarantine until inspection; no unsafe preview/AI facts before CLEAN. Safe filename/storage key and authorization for content/range request. |
| Scheduling | start<end; timezone valid, offset consistent with intended local time/DST; eligible practitioner/record; booking/cutoff/horizon; active half-open overlap check at commit. Availability changes share locking boundary with booking and disclose impacts. Patient self-booking ignores/rejects body recordId other than own path record. |
| Preparation/encounter | Initial preparation POST; later PATCH with If-Match; no edits after active encounter lock. Patient-reported source never promoted silently; the practitioner acknowledgement is a separate append-only command. Optional appointment link must match practice/record/practitioner and not be reused. Completion/cancellation are distinct from report state. |
| Documents/review | Edit only DRAFT; immutable validated version edits derive new draft. Review transitions DRAFT→IN_REVIEW→VALIDATED or IN_REVIEW→DRAFT via changes request; a CLINICIAN_BRIEF has none of them and is only acknowledged (422 BRIEF_NOT_REVIEWABLE otherwise). Validation verifies current source hash, exact support and required dispositions; purpose and source references belong to same record. |
| AI/task | Source IDs alone do not determine full relevant input; snapshot includes relevant record watermark. For CLINICIAN_BRIEF the request carries no `sourceRevisionIds` (the server derives the manifest); for reports and explanations it carries the explicit subset. Quota/idempotency/purpose/target guard; cancellation and retry limited by state, lease and attempt policy. SUCCEEDED creates new draft candidate only. |
| Publication | VALIDATED CURRENT eligible report/explanation; CLINICIAN_BRIEF rejected. Explicit audienceConfirmed, verified link, curated source/attachment grants and current document token. Replace guards current Publication version plus new eligible version. Withdrawal requires reason, no old body after revocation. |
| Exports | Exactly one applicable documentVersionId or current publicationId for selected template. P-01 clinician-only; patient P-02/P-03 from published projection. Recheck at delivery. Task readiness does not imply validation/publication. |

No operation accepts arbitrary businessStatus or publication state patches. Transitions are named commands and follow lifecycle tables. Attendance commands and cancel/complete preserve historical notes. New-draft creation cannot modify old approved text. Internal source endpoints return full clinician metadata; portal sources return only PublishedSource projection, never internal Source. Patient response must not serialize generated baseline/internal reasons.

Initial recurring availability supports only an explicitly validated weekly pattern with weekday, local start/end, IANA timezone and bounded effective dates; the optional recurrence string must match this supported subset. Reject other recurrence expressions with422. Do not silently expand an arbitrary calendar rule. Date-specific exceptions remain separate and all generated offered instants retain the resolved offset.

## Concurrency and retries

Single versioned aggregates expose a quoted ETag; lists, tokens, acknowledgements and binary content do not. All declared mutable aggregates require exact If-Match; no wildcard. A creation uses an idempotency key with current actor/practice/operation/body hash. Same key/body returns same resource/outcome, changed body 409. Never cache passwords/raw tokens in idempotency storage. After network ambiguity, look up current operation/resource outcome before requesting a fresh business action. An idempotent command still rechecks current access on retrieval; it cannot replay sensitive data after revocation.

Booking constraint conflict is 409; outdated edit is 412. Publication replacement uses one atomic transaction, not two separate publish/unpublish calls. Audit and outbox receipt commit together. Asynchronous acceptance is 202, polling Task shows safe status/failure/result ID. Late cancelled/stale lease output cannot commit. Retry creates bounded attempt/new candidate and preserves human edits. No successful empty summary on timeout.

## Examples and design aliases

Design aliases in `synthetic-data.json` are not UUID wire examples. Future fixture adapter maps them deterministically; contract UUID validation must not be weakened. Example observation: `{"concept":"WEIGHT","value":"68.4","unit":"kg","observedAt":"2026-10-20T09:10:00+02:00","sourceType":"PRACTITIONER_OBSERVED"}`. Unknown allergy history is explicit UNKNOWN, not empty array meaning none. API decimals keep a dot; French UI uses locale formatting. These are synthetic examples, not medical advice.

Contract lint and local-reference checks are documentary checks only. Actual HTTP status, security, database and compatibility behavior remains future implementation evidence.
