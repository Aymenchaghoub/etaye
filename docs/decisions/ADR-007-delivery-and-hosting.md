# ADR-007 — Immutable delivery and conditional minimal-cost hosting

Status: Proposed. Date: 2026-11-02. Related: FR-019/020, AD-10.

**Choice:** GitHub Actions validates a release candidate once; GHCR stores the exact backend image digest, with frontend artifact hash recorded. Promotion deploys those validated artifacts. Two CI-based SonarQube Cloud projects cover backend and frontend. Quality gate result, analysis commit, tests and deployment manifests must refer to the same SHA.

Prefer an initial same-origin application on a single backend host with PostgreSQL hosted separately. Render Free and Neon Free are candidates for a low-traffic synthetic demo; viability is conditional on measured JVM/PDF memory, cold starts, account quotas and persistent file storage. Cloudflare Pages is suitable for the optional independent showcase, not automatically the authenticated app. See the dated [hosting assessment](../operations/hosting-cost-assessment.md).

Alternatives: a paid small always-on instance improves readiness but needs explicit budget authorization; self-hosting lowers vendor cost but adds maintenance; multiple free services maximize fragmentation and cold-start failure points. No choice is justified by portfolio decoration.

Rollback means redeploying a prior compatible image and static artifact, not automatically reversing migrations. Expand/contract changes preserve the prior application compatibility window; destructive changes require a verified backup and recovery drill. Forward fix or restore into a separate database is preferred over editing applied migrations. No service, billing plan, CI workflow or deployment is activated during planning.
