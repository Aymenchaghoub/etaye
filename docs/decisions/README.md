# Architecture decision records

Date: 2026-11-02. Status: Draft / internally reviewed when recorded in the review report. **No ADR is approved by Aymen.** The user selected the product boundaries and three core technologies; the concrete choices here are proposals for design continuity.

| ADR | Decision |
|---|---|
| [001](ADR-001-modular-monolith.md) | Domain ownership and asynchronous work |
| [002](ADR-002-stack-and-tooling.md) | Supported stack and tooling |
| [003](ADR-003-practice-isolation.md) | Shared schema with server authorization and RLS |
| [004](ADR-004-authentication-sessions.md) | Opaque browser sessions and CSRF |
| [005](ADR-005-document-lineage-publication.md) | Immutable versions, evidence and explicit publication |
| [006](ADR-006-files-pdf-storage.md) | Guarded files and reproducible PDF export |
| [007](ADR-007-delivery-and-hosting.md) | Immutable delivery with a benchmarked hosting choice |

The [architecture spine](../planning-artifacts/architecture/ARCHITECTURE-SPINE.md) owns cross-domain constraints; ADRs explain the tradeoffs. Update both when an invariant changes, preserving IDs and supersession history.
