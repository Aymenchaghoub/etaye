# Documentation index

Status: Planning package ready for complete design work. Documents are reviewed for planning consistency, not user-approved designs or implemented features. The [review report](review-report.md) records checks and limits. Aymen's explicit development authorization is still required after complete design review.

| Authority / purpose | Artifact |
|---|---|
| Project orientation | [README](../README.md), [continuation](continuation.md) |
| Evidence and legacy | [Sources](references/source-register.md), [existing-system audit](references/existing-system-audit.md) |
| Product intent | [Brief](planning-artifacts/product-brief/brief.md), [PRD](planning-artifacts/prd/prd.md) |
| Functional journeys | [Functional specification](planning-artifacts/functional-specification.md) |
| Domain invariants and state machines | [Business rules](planning-artifacts/business-rules.md) |
| Actors and permissions | [Access matrix](planning-artifacts/access-matrix.md) |
| Terminology and French–English translation | [Glossary](planning-artifacts/glossary.md) |
| Decisions, assumptions and work outside increment | [Decision register](planning-artifacts/decisions-assumptions.md), [deferred work](planning-artifacts/deferred-work.md) |
| Cross-module decisions | [Architecture spine](planning-artifacts/architecture/ARCHITECTURE-SPINE.md), [runtime/module design](planning-artifacts/architecture/runtime-architecture.md), [ADRs](decisions/README.md) |
| Data ownership and shape | [Data model](planning-artifacts/data-model.md), [data dictionary](planning-artifacts/data-dictionary.md) |
| Transport contract and validation | [API guide](contracts/README.md), [OpenAPI](contracts/openapi.yaml), [operation index](contracts/operation-index.json) |
| AI behavior and evaluation | [AI specification](planning-artifacts/ai-specification.md) |
| Security/privacy and measurable quality | [Security/privacy](planning-artifacts/security-privacy.md), [NFRs](planning-artifacts/non-functional-requirements.md) |
| Actual initial CI and GitHub security | [Repository controls](operations/github-security.md) |
| Future delivery and recovery | [CI/CD/deployment](operations/cicd-deployment-strategy.md), [GitHub delivery rules](operations/delivery-rules.md) |
| Dated compatibility/cost evidence | [Hosting assessment](operations/hosting-cost-assessment.md), [official source evidence](operations/technology-and-hosting-sources.md) |
| Verification design | [Test strategy](test-artifacts/test-strategy.md), [acceptance scenarios](test-artifacts/acceptance-scenarios.md) |
| Planning backlog | [9 epics / 37 stories](planning-artifacts/epics/README.md), [implementation stopping point](implementation-artifacts/README.md) |
| UX spines | [DESIGN](planning-artifacts/ux/DESIGN.md), [EXPERIENCE](planning-artifacts/ux/EXPERIENCE.md) |
| Existing visual source and assets | [Figma inventory](design/figma-inventory.md), [tokens/assets](design/token-reference.md), [snapshot/export manifest](design/figma/manifest.json) |
| Design surfaces | [Screens](design/screen-catalog.md), [interactions/modals](design/interaction-catalog.md), [PDFs/emails](design/pdf-email-specifications.md) |
| Shared fictional content | [Narrative](design/synthetic-data.md), [dataset](design/synthetic-data.json) |
| Design progress and next phase | [Coverage](design/coverage.csv), [tracking guide](design/coverage-guide.md), [Claude handoff](design/handoff-claude.md), [session recovery](design/session-recovery.md), [short continuation prompt](design/continuation-prompt.md) |
| End-to-end mapping | [Traceability](traceability.md), [review](review-report.md), [documentary validation](test-artifacts/document-validation.json) |
| Agent installation and policy | [Setup/troubleshooting](agent-setup.md), [workflow execution record](workflow-record.md), [AGENTS](../AGENTS.md), [CLAUDE](../CLAUDE.md) |

Each authority owns its definition. Other documents link to it; generated catalogs/indexes and exported chat bundles are distribution/lookup aids. Design status belongs in coverage.csv; implementation status belongs in future compatible BMAD tracking supported by story/code/history/evidence. Neither is copied into root instruction files.
