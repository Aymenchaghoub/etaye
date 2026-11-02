# ADR-003 — Practice isolation in a shared schema

Status: Proposed. Date: 2026-11-02. Related: BR-001–004, AD-3.

| Approach | Strength | Cost / limitation |
|---|---|---|
| Database per practice | Strong operational boundary, separate restore | Provisioning, migrations, connection pools and demo reset multiply |
| Schema per practice | Namespace separation | Search-path/pooling hazards and repeated schema maintenance |
| Shared schema + application predicates | Small operational surface | A forgotten predicate can expose another practice |
| Shared schema + explicit predicates + RLS | Small deployment, database defense in depth | Correct transaction context, role configuration and negative tests essential |

**Choice:** shared schema with `practice_id` on every practice-owned row, composite unique keys/FKs, application authorization and PostgreSQL RLS. RLS is a safety net, not the role/resource permission engine.

Authenticate first; load active membership from a narrowly privileged identity lookup. Authorize the selected practice. Begin the domain transaction; use transaction-local context on that physical connection; run resource/capability checks and repository statements; commit/rollback clears context. The runtime role cannot own tables or bypass RLS; use FORCE RLS where applicable. No session-level `SET`, detached lazy load or database operation outside a context-bound transaction. Disable open-session-in-view. Verify connection reuse after errors and nested transactions. Administrative capability never implies clinical read.

Global accounts, sessions and token digests are not practice clinical tables. Bootstrap membership/portal-link discovery uses a separate, restricted identity repository/role limited to the authenticated account or a token digest; it does not allow clinical reads. Cross-practice support queries and scheduled job discovery use explicitly restricted service access to metadata, then establish the selected practice transaction. Migration credentials are unavailable to HTTP runtime. No general bypass endpoint exists.

Patient portal methods require both practice context and active record-specific `PortalLink`; RLS practice matching alone is insufficient. Publication/file source projections enforce audience and content grants. Existence errors for another practice are indistinguishable from missing resources. Test UUID swapping, list counts, joins, export jobs, signed URLs, audit, workers and pool reuse in both directions.

Consequence: a coding error inside the authorized practice is still possible; combine tests, access service and least privilege. RLS does not guarantee anonymization, prevent malicious database administrators or establish regulatory compliance.
