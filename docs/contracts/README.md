# Documentary API contract

Status: Proposed, ready for design and review. `openapi.yaml` is OpenAPI 3.1 written in JSON syntax (valid YAML 1.2); no endpoint is implemented. [Operation index](operation-index.json) maps all operations. [Business rules](../planning-artifacts/business-rules.md), [access matrix](../planning-artifacts/access-matrix.md), [data dictionary](../planning-artifacts/data-dictionary.md) and ADRs are authoritative companions.

## Common transport and permissions

Future same-origin base `/api/v1`, opaque secure session cookie, CSRF token header on unsafe methods. Obtain `/csrf` before login/mutation. Login rotates session; logout/reset/suspension revokes applicable sessions. `x-required-permissions` describes the resource policy in addition to session authentication. Composite labels such as AUTHORIZED_REQUESTER are policy predicates, not assignable roles; derive them from current capability plus ownership. Never trust a client role or route practice ID. For every practice path, establish active membership or verified own portal link, capability and resource practice/record. A body recordId must match its path/authorized context. One tab's context cannot silently redirect another tab's writes.

404 hides resources outside authorized practice/record; 403 is a forbidden action on a visible resource or CSRF failure. 401 means missing/expired authentication; 400 malformed format; 422 invalid semantic input/transition; 409 scheduling/idempotency/business conflict; 412 stale expected version; 428 absent version precondition; 429 quota/rate limit with Retry-After; 503 unavailable dependency. Problems use RFC 9457 with stable code/correlationId and safe field errors. No stack trace, secret, source body or cross-practice existence detail in errors. Error variants share a schema but status/code must match actual outcome.

## Pagination, filters and ordering

Cursor page `{items,nextCursor}`, default 25, maximum 100. Cursor is opaque and bound to practice/actor/filter/sort; invalid or reused-under-different-context cursor fails safely. Stable `(event time,id)` tie-break order; newly inserted data may require refresh, never imply a frozen snapshot unless the cursor includes one. Search only local patients. Query text is redacted from logs and not copied to public page URLs. Bounded from/to, practitionerId, appointment state and audit eventType filters are allowlisted where declared. Reject unknown sort/filter fields rather than feeding them to SQL. Do not expose unauthorized totals or other-patient identity through offered slots.

## Validation beyond shape

| Group | Required semantic checks |
|---|---|
| Identity/invitations | Normalize email by defined policy, strong supported password policy/hash, single-use token digest and expiry; intended-recipient verification. Portal invitation requires recordId, team invitation explicit permitted capabilities; reject irrelevant grant fields. Recovery response remains generic. |
| Patients/records | Birth date not future; required identity fields; practice-local duplicate suggestions only; stable patient/record identity on corrections. Contact email is not proof of account linkage. |
| Assertions/source dates | PRESENT/EXPLICITLY_ABSENT/UNKNOWN/NOT_ASKED are distinct. UNKNOWN event date forbids fabricated instant/date; DATE carries a date, INSTANT an instant. Recorded time always server-provided. Corrections require reason and prior current revision. |
| Measurements/labs | Finite decimal with concept/unit and actual observed time; retain original values. BP is grouped systolic/diastolic observations. Labs require exactly numericValue or qualitativeValue according to valueKind, comparator only for numeric, supplied unit/interval preserved and ordered bounds. No invented universal medical range or implicit normal result. |
| Upload | Multipart bounded 10MiB proposed cap and PDF/PNG/JPEG signature/MIME allowlist. Quarantine until inspection; no unsafe preview/AI facts before CLEAN. Safe filename/storage key and authorization for content/range request. |
| Scheduling | start<end; timezone valid, offset consistent with intended local time/DST; eligible practitioner/record; booking/cutoff/horizon; active half-open overlap check at commit. Availability changes share locking boundary with booking and disclose impacts. Patient self-booking ignores/rejects body recordId other than own path record. |
| Preparation/encounter | Initial preparation POST; later PATCH with If-Match; no edits after active encounter lock. Patient-reported source never promoted silently. Optional appointment link must match practice/record/practitioner and not be reused. Completion/cancellation are distinct from report state. |
| Documents/review | Edit only DRAFT; immutable validated version edits derive new draft. Review transitions DRAFT→IN_REVIEW→VALIDATED or IN_REVIEW→DRAFT via changes request. Validation verifies current source hash, exact support and required dispositions; purpose and source references belong to same record. |
| AI/task | Source IDs alone do not determine full relevant input; snapshot includes relevant record watermark. Quota/idempotency/purpose/target guard; cancellation and retry limited by state, lease and attempt policy. SUCCEEDED creates new draft candidate only. |
| Publication | VALIDATED CURRENT eligible report/explanation; CLINICIAN_BRIEF rejected. Explicit audienceConfirmed, verified link, curated source/attachment grants and current document token. Replace guards current Publication version plus new eligible version. Withdrawal requires reason, no old body after revocation. |
| Exports | Exactly one applicable documentVersionId or current publicationId for selected template. P-01 clinician-only; patient P-02/P-03 from published projection. Recheck at delivery. Task readiness does not imply validation/publication. |

No operation accepts arbitrary businessStatus or publication state patches. Transitions are named commands and follow lifecycle tables. Attendance commands and cancel/complete preserve historical notes. New-draft creation cannot modify old approved text. Internal source endpoints return full clinician metadata; portal sources return only PublishedSource projection, never internal Source. Patient response must not serialize generated baseline/internal reasons.

Initial recurring availability supports only an explicitly validated weekly pattern with weekday, local start/end, IANA timezone and bounded effective dates; the optional recurrence string must match this supported subset. Reject other recurrence expressions with422. Do not silently expand an arbitrary calendar rule. Date-specific exceptions remain separate and all generated offered instants retain the resolved offset.

## Concurrency and retries

Responses expose quoted ETag. All declared mutable aggregates require exact If-Match; no wildcard. A creation uses an idempotency key with current actor/practice/operation/body hash. Same key/body returns same resource/outcome, changed body 409. Never cache passwords/raw tokens in idempotency storage. After network ambiguity, look up current operation/resource outcome before requesting a fresh business action. An idempotent command still rechecks current access on retrieval; it cannot replay sensitive data after revocation.

Booking constraint conflict is 409; outdated edit is 412. Publication replacement uses one atomic transaction, not two separate publish/unpublish calls. Audit and outbox receipt commit together. Asynchronous acceptance is 202, polling Task shows safe status/failure/result ID. Late cancelled/stale lease output cannot commit. Retry creates bounded attempt/new candidate and preserves human edits. No successful empty summary on timeout.

## Examples and design aliases

Design aliases in `synthetic-data.json` are not UUID wire examples. Future fixture adapter maps them deterministically; contract UUID validation must not be weakened. Example observation: `{"concept":"WEIGHT","value":"68.4","unit":"kg","observedAt":"2026-10-20T09:10:00+02:00","sourceType":"PRACTITIONER_OBSERVED"}`. Unknown allergy history is explicit UNKNOWN, not empty array meaning none. API decimals keep a dot; French UI uses locale formatting. These are synthetic examples, not medical advice.

Contract lint and local-reference checks are documentary checks only. Actual HTTP status, security, database and compatibility behavior remains future implementation evidence.
