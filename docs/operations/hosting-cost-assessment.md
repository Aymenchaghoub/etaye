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

## Update 2026-11-17 (UD-25, UD-12)

Option D, added with the mobile direction: one low-cost VPS running the application, PostgreSQL and a file volume on one origin, without sleeping. Published entry prices observed on 2026-11-17: OVHcloud VPS-1 from €3.81 per month excluding VAT; Hetzner CX23 between €3.49 and €6.49 per month excluding VAT depending on the page (To verify at purchase). Hidden costs: system maintenance (about one to two hours per month, an estimate), backups, a domain. Installed and native clients need a stable always-on HTTPS origin before any store step, so the story 10.3 benchmark compares option A (free, best effort) with option D before Aymen decides; nothing is provisioned and no payment method is added until he does.

## Update 2026-11-26 (Empirical capacity benchmark & Azure for Students comparison)

Clean isolated cgroup capacity measurements (`--memory-swap == --memory`) and read-only Azure CLI inspection added the following verified facts (full methodology and tables in [deployment-readiness.md](deployment-readiness.md)):

1. **JVM container footprint (`512 MiB` vs `1–2 GiB`)**: Inside a real `512 MiB` / `0.25 vCPU` cgroup, default JVM peaked at `276.4 MB` (`61.03 s` startup, `21.5 RPS`), while tuning (`-Xmx192m -XX:MaxMetaspaceSize=96m -Xss256k -XX:+UseSerialGC -XX:TieredStopAtLevel=1`, Hikari max `5`) reduced peak cgroup memory to **`218.6 MB`** (`36.47 s` startup, `32.5 RPS`, `0` OOM kills or HTTP errors across 190 load probes). Under `0.5 vCPU / 1 GiB` tuned, cold startup dropped to `27.95 s` (`262.9 MB` peak, `43.1 RPS`). Memory is therefore manageable inside `512 MiB` for Sprint 3 scope (before PDFBox generation), making **CPU quota** the main cold-start bottleneck on Render Free (~`0.1` CPU).
2. **Azure for Students (`USD 100`, expires `2027-11-26`, `USD 0` spent)**: Subscription policy `sys.regionrestriction` allows only five EU regions (`norwayeast`, `belgiumcentral`, `polandcentral`, `italynorth`, `germanywestcentral`) and marks all `Standard_B*` burstable VMs `NotAvailableForSubscription`. Always-on `D2s v4`/`D2as v4` VMs (`~75.34 EUR/month`) would drain credits in ~5 weeks, and Azure PostgreSQL Flexible Server `B1ms` runs `~13.07 EUR/month` + storage after the 12-month student grant.
3. **Recommended sustainable EUR 0 demonstration pair (Option A-Azure, unprovisioned)**: **Azure Container Apps Consumption** (`180,000 vCPU-s`, `360,000 GiB-s`, `2M requests` monthly free grant with scale-to-zero, equivalent to `100 active hours/month` at `0.5 vCPU / 1 GiB` or `200 hours/month` at `0.25 vCPU / 0.5 GiB` at `EUR 0/month` even after Nov 2027) paired with **Neon Free** (`100 CU-h/month`, `1 GB` project storage, `5 min` scale-to-zero, requiring non-superuser `init-roles.sh` + managed demo seed fixes). Render Free + Neon Free remains a fallback with slower cold starts (~`0.1` CPU). No Azure, Neon or Render service was provisioned or funded.
