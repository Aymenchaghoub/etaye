# AI assistance and evaluation specification

Status: Proposed, ready for design. No model integration or evaluation result exists. 2026-11-02. Requirements FR-011/013/014/015; [BR-011–014](business-rules.md) own lifecycles. Provider API keys are unnecessary for documentation or deterministic demo/CI.

## Tasks and audiences

CLINICIAN_BRIEF summarizes changes since prior encounter, relevant facts, gaps and contradictions for a professional. CONSULTATION_REPORT drafts from saved encounter notes and authorized record facts. PATIENT_EXPLANATION is separately authored/reviewed for a patient and can only become visible through an eligible explicit publication. A clinician brief cannot be published by changing its display label. AI never decides treatment/diagnosis, validates, publishes, edits source facts or creates a normality/risk classification from missing data.

## Input, output and evidence

Input: purpose, synthetic patient alias (no contact identity), source manifest with immutable revisions/locators/dates/source types, saved encounter data when relevant, explicit unknown/negative/assertion states, and a bounded context budget. Include only the selected relevant synthetic facts; no full account, contact, birth date, password, invitation token or operational credential. Source clinical narrative remains untrusted data. Record language and provenance; never silently translate it into new authoritative evidence.

Output schema: purpose, sections, statements each with stable local statementId/text/kind (SOURCE_FACT/DRAFT_INTERPRETATION/MISSING_INFORMATION/CONTRADICTION), supporting sourceRevisionIds/locators, omissions/warnings and inputSnapshotHash. No confidence percentage. Validate schema, referenced IDs, audience, length and unsupported structured claims before creating DRAFT. Source existence and lexical overlap are insufficient: the exact source must support the associated assertion, relevant date, value and qualifier. Reviewer sees source facts, raw generated baseline and human-edited content separately.

New relevant information marks dependencies STALE, including a newly added allergy/preparation source that was not cited before. Old text/validator/publication metadata stays intact. Regeneration creates a candidate/new draft. It cannot replace a corrected field or validated/published version. A reviewer reconfirms a current snapshot explicitly; validation records reviewer/date/version/source hash. Publication is a later distinct transaction.

Proposed deterministic relevance policy: a clinician brief depends on the record's clinical revision watermark at its generation cutoff, including new history/preparation, not just cited IDs. Any later clinical addition/correction within that record makes it STALE. A historical consultation report depends on its encounter notes and explicitly included source revisions; unrelated later visit preparation does not rewrite or stale an as-of report, but a correction to an included fact does. A patient explanation records its covered topics (for example ALLERGY and CURRENT_TREATMENT) as well as cited revisions; a new or corrected assertion in a covered topic makes it STALE. Unknown relevance defaults to STALE for human assessment. Store the policy version/topic set/watermark in the source manifest and test this policy, including the dataset's current historical report and stale earlier patient explanation. A future policy change requires an ADR/rule/fixture update, not an undocumented heuristic.

## Processing reliability and controls

A task captures account/practice/record/purpose/idempotency key/body hash/input snapshot and bounded attempts. QUEUED→RUNNING→SUCCEEDED/FAILED/CANCELLED is independent from content business state. Return task ID immediately; client polls bounded intervals, handles expiry and recovers after response loss. Duplicate request same key/body returns same task; changed body with same key is a conflict. Provider timeout is a failure, not an empty successful summary. Retry classified transient failures with bounded backoff; explicit retry/cancel preserves prior content. A lease token fences late workers. Check target version and current grants before reading sources and committing a result.

[ASSUMPTION targets] maximum one active AI task per record/purpose, two active provider calls per practice, 20 generation requests/practice/day in public synthetic demo, 30s provider deadline, at most two automatic transient retries; per-request input/output token ceilings and a configurable daily monetary stop. Quota is reserved transactionally, actual usage reconciled; administrative UI displays safe failure/capacity states without clinical bodies. Targets must be tuned with measurements, never presented as validated performance.

Provider port accepts typed minimized request and returns typed draft/usage/failure. Default demo/CI adapter maps fixture scenarios to deterministic responses, including unsupported statement, missing data, timeout and malformed output. It is not a replacement for real record/auth/storage boundaries. Real-model evals are separate opt-in runs with explicit provider/data/retention/cost policy and human review. No mandatory paid call during normal development or CI.

## Outbound-data and logging boundary

Public demo exclusively synthetic. If future real-data work is proposed, stop and re-scope privacy/legal/provider contracts first. Data minimization and pseudonymization do not guarantee anonymity; rare events/free text can identify people. Imported documents can contain prompt injection: delimit data, forbid executing embedded instructions, allow no model tools that modify records/publish/fetch arbitrary URLs, and validate output independently. Do not treat simple regex filtering as a privacy guarantee.

No raw prompts/responses/clinical text/tokens in general logs, analytics, traces or error payloads. Store necessary draft content in authorized record storage with intended retention, not provider debug logs. Log task ID, safe failure class, provider/config version, duration, token usage and estimated cost only. Provider retention/training/regional terms require current official verification before any paid integration; adapter is replaceable.

## Evaluation protocol

Freeze a versioned synthetic corpus with independently prepared source facts and expected assertions. Include rich/minimal records, contradictory dates/allergies, missing treatments, corrected measurements, irrelevant sources, long context, malicious document instructions, source revocation, changed data and concurrent human edits. Review at least 30 cases across these categories before choosing a real model [PROPOSED sample size]. Use two human judgments for disputed source-support cases; do not use an LLM grader as sole clinical truth.

| Dimension | Method | Proposed release target (not measured) |
|---|---|---|
| Factual faithfulness | Sentence-level check against frozen source facts, including negation/date/unit | No material fabricated fact in acceptance corpus |
| Citation validity/relevance | Resolvable exact revision/locator plus human support/qualifier check | 100% of important factual claims supported; unsupported claims block approval |
| Important omissions | Case-specific expected critical-context checklist | No defined critical omission; report all omissions, not a single opaque score |
| Unsupported statements | Count material assertions without support, classify severity/rationale | Zero material unsupported assertions in accepted drafts |
| Missing-data behavior | Minimal/unknown/explicit-negative/contradictory cases | 100% preserve distinction; no reassuring default or risk tier |
| Human corrections | Concurrency/regeneration fixtures compare corrected text/history | 100% preserved; late results never overwrite |
| Latency | Enqueue/queue/provider/complete p50/p95 separately, warm vs cold | p95 provider+processing ≤30s excluding explicitly reported cold start; target may change after benchmark |
| Cost | Provider token usage × dated unit prices, reserved/actual quotas | Configurable hard budget; report currency/date/model per generation, no invented zero-cost real calls |

Evaluation record includes corpus hash, model/provider/template/config versions, source snapshot, run date, reviewers, per-case findings, actual latency/cost and limits. Deterministic fixture tests establish lifecycle behavior; they do not measure real-model quality. No measured results are claimed in this package.
