# Hosting and cost assessment

Status: Conditional proposal, not provisioned or budget-approved. 2026-11-02. [Official evidence](technology-and-hosting-sources.md) owns dated vendor observations. Aymen explicitly authorized public repository visibility after the initial private setup; current state is recorded in github-security.md. No free tier is a guarantee of production suitability or real patient-data compliance.

| Option | Composition | Expected baseline cost / trade-off |
|---|---|---|
| A: zero paid runtime target | Single same-origin Spring/static app on Render Free; Neon Free DB; private persistent object store within verified free allowance; deterministic AI; email preview/sink | $0 target within quotas, not guaranteed.512MB fit unmeasured, cold starts and suspension possible; external DB/storage traffic may trigger limits. No timely reminders while sleeping; persistent documents cannot use local host disk. |
| B: more reliable demo | Same architecture with paid always-on compute and free DB/storage where suitable | Render published512MB$7/month or2GB$25/month plus any DB/storage/egress/domain tax/currency effects.512MB remains conditional even when paid. Budget approval and measured RSS/startup/PDF behavior required. |
| C: optional independent showcase | Prebuilt static showcase on Cloudflare Pages/Workers static hosting | Free-tier candidate; separate public content only. Authenticated app remains one origin with `/api`; default pages.dev/onrender.com split does not satisfy cookie architecture. |

Recommendation is conditional A for synthetic experimentation, B if reliable recruiter access is needed and benchmarks justify spend. Do not choose a paid host or add a payment method without explicit discussion. A custom domain is optional and has a separate recurring cost; no fake live URL. Serving the validated frontend inside the backend image simplifies same-origin sessions and exact artifact promotion; a same-origin proxy is an alternative with extra operational checks.

## Capacity and dependency assessment

Measure Java heap, metaspace, native/thread/database pool and PDF peak RSS together; do not allocate full512MB as heap. Start with bounded thread/pool/task concurrency; p95 warm response and startup/memory evidence decide viability. Sleep means DB-backed jobs resume later rather than vanish; it does not provide an always-on worker. Show a service-warming/unavailable state, bounded retries and deterministic AI fallback only when explicitly labeled as demo response.

Neon1GB DB quota includes schema/history growth; retain bounded synthetic rows and reset fixture version safely. Pool remote connections with TLS and verify RLS transaction context through chosen pooling mode.6h restore window is not a backup strategy: export versioned synthetic fixture plus scheduled encrypted DB/byte backup where necessary, restore-test separately. Object storage needs private ACL, region, durable bytes and explicit guarded download; assess R2 versus Neon object service before selecting adapter. No large binaries in relational free storage by default.

## Build, analysis and storage costs

Public standard GitHub runners are free under current terms; private usage depends on actual account allowance. Use bounded artifacts (e.g. failure traces7days, release reports30days proposed), cancel superseded runs and avoid unbounded browser matrices. GHCR currently free differs from generic Actions/Packages pooled quotas. Keep only justified retained release digests and never delete the active/rollback image. Sonar Free limits50k private LOC across organization and restricts branches/PR target; two frontend/backend projects must fit and bind correctly. No custom-quality-gate entitlement assumed; supplement built-in gate with explicit documented checks if needed.

## AI and email

Default demo/CI uses deterministic provider adapter: no paid model calls. Real evaluation budget is a later explicit decision. Cost per generation equals dated model input/output rates times actual token counts plus any provider fees; proposed ceilings/quotas in AI spec. No pricing number for an unselected provider is invented. Email preview/sink is sufficient for synthetic demo; real outbound provider/allowlisted recipients and quotas must be selected before activation. Render SMTP/egress restrictions must be checked for the selected plan; an HTTP email adapter is the proposed boundary.

## Decision gate before deployment

Choose budget/reliability requirement, benchmark real app footprint, confirm account entitlements/remaining quotas/regions, persistent storage/backup and exact artifact deployment support. If candidate host cannot deploy a validated external image digest, select a supported method/provider; never silently rebuild from source during promotion. Document paid limits and set budget alerts. No deployment credentials are needed now.
