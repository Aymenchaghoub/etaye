# Architecture decision records

Date: 2026-11-02. Status: Draft / internally reviewed when recorded in the review report. **ADR-001 to ADR-007 are not approved by Aymen; ADR-008 and ADR-009 were accepted by him on 2026-11-17 (UD-29) and ADR-010 on 2026-11-19 (UD-38, UD-39); ADR-011 on the same day (UD-40).** The user selected the product boundaries and three core technologies; the concrete choices here are proposals for design continuity.

| ADR | Decision |
|---|---|
| [001](ADR-001-modular-monolith.md) | Domain ownership and asynchronous work |
| [002](ADR-002-stack-and-tooling.md) | Supported stack and tooling |
| [003](ADR-003-practice-isolation.md) | Shared schema with server authorization and RLS |
| [004](ADR-004-authentication-sessions.md) | Opaque browser sessions and CSRF |
| [005](ADR-005-document-lineage-publication.md) | Immutable versions, evidence and explicit publication |
| [006](ADR-006-files-pdf-storage.md) | Guarded files and reproducible PDF export |
| [007](ADR-007-delivery-and-hosting.md) | Immutable delivery with a benchmarked hosting choice |
| [008](ADR-008-multi-client-access.md) | Multi-client access: web, installed web app and native client (amended by ADR-010) |
| [009](ADR-009-api-compatibility.md) | API compatibility and client versioning |
| [010](ADR-010-native-client-react-native-expo.md) | Native client: React Native with Expo, distributed as a demo |
| [011](ADR-011-delivery-order-and-milestones.md) | Delivery order and increment 1 milestones, without calendar dates |
| [012](ADR-012-functional-motion.md) | Functional motion for state feedback |

The [architecture spine](../planning-artifacts/architecture/ARCHITECTURE-SPINE.md) owns cross-domain constraints; ADRs explain the tradeoffs. Update both when an invariant changes, preserving IDs and supersession history.
