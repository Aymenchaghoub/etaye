# Measurable non-functional requirements

Status: Proposed targets; no application measurements exist. 2026-11-02. Baseline load [ASSUMPTION]: 2 synthetic practices, 6 practitioners, 200 patients/practice, 20 concurrent browser sessions and 2 provider tasks/practice; revise after benchmark. Targets do not override correctness/permissions.

| ID | Target | Measurement / pass condition |
|---|---|---|
| NFR-01 Isolation | Zero unauthorized resource disclosure or mutation in defined adversarial matrix | API + PostgreSQL nonowner role, pooled transaction reuse, job/file/export/citation/list/count cases across two practices |
| NFR-02 Integrity | One booking for concurrent occupied interval; no lost versioned edit/publication | Parallel real-DB requests; invariant query; one success, documented conflict/stale error; audit/outbox exactly-once business effect |
| NFR-03 Performance | Warm non-AI list/detail API p95 ≤500ms at baseline; first usable clinician page ≤2.5s on defined test network | Record hardware/network/fixture/count, p50/p95 and errors; separately report cold start, external API and PDF workloads |
| NFR-04 AI | Proposed bounded 30s processing deadline, explicit timeout/failure and quota | Fake slow/failing provider plus opt-in real eval; no unlimited queue/retry; no normality fallback |
| NFR-05 Accessibility | WCAG 2.2 AA target with keyboard-complete key journeys; 320px reflow, 200% zoom | Automated checks + manual keyboard/screen-reader/contrast review; document remaining criteria, never infer conformance from scanner alone |
| NFR-06 Visual fidelity | Every required approved Figma family at matching viewport, no unresolved blocking overflow/source/action issue | Open running-UI screenshots side by side; desktop/tablet/mobile and A4 all pages; functional pass separate |
| NFR-07 Recovery | Synthetic demo target RPO≤24h/RTO≤2h for backed-up state; fixture reset ≤5min | Restore drill to isolated database/store and verify versioned fixture/attachments; free-tier history alone insufficient |
| NFR-08 Resource budget | Process RSS peak <80% selected host limit during concurrent API+single PDF/task; graceful behavior at quotas | Measure startup/steady/peak; reserve native/PDF/thread memory beyond heap. A 512MB host is conditional, not guaranteed |
| NFR-09 Maintainability | Modular dependency direction enforced; immutable applied migrations; documented contracts | Architecture checks, clean/upgrade migration validation, contract lint; release cannot skip required suites |
| NFR-10 Observability | Correlation IDs and safe duration/outcome/error/queue-depth metrics for important paths; no sensitive bodies | Failure injection/log review, secret/text canary checks and scoped audit coverage mapping |
| NFR-11 Compatibility | Latest two stable desktop Chrome/Firefox/Edge; current mobile Safari/Chrome at implementation freeze | Record actual browser versions in Playwright/manual evidence; no current test pass claimed |
| NFR-12 Demo reliability | Show unavailable/cold-start/reset states, bounded calls, no real data | Full journeys with provider/API loss, repeat visitor and reset boundary; scheduled interview readiness check |
| NFR-13 PDF/email | Accents/units/selectable text, no clipped rows, readable grayscale, correct audience/status/version | Short/long/incomplete A4 and narrow email review; extraction and reading-order checks after renderer exists |
| NFR-14 Cost | Hard configured AI/request/storage caps; no paid service activation without explicit discussion | Daily usage ledger and limit tests; dated vendor prices and budget approval, zero paid CI calls by default |

Public demo availability is best-effort if sleeping free services are selected; no SLA is promised. A reliable always-on recruiter demonstration requires budget/benchmark choice. Proposed targets become measured results only with environment, commit, fixture, command and raw evidence recorded in docs/test-artifacts/.
