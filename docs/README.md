# Documentation index

The planning, design and implementation records cover different stages of Étaye. Stories 10.1 and 10.2 provide the runnable platform foundation, the generated client and the token bridge; stories 1.1 to 1.4, 3.1, 3.2 and 9.1 add access, patient and record screens and the local demonstration. See [implementation records](implementation-artifacts/README.md) for the verified scope and [design coverage](design/coverage.csv) for approved surfaces. Development authorization is granted per story.

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
| Architecture diagrams, built and planned | [Interactive views and source files](architecture-views/README.md) |
| Data ownership and shape | [Data model](planning-artifacts/data-model.md), [data dictionary](planning-artifacts/data-dictionary.md) |
| Transport contract and validation | [API guide](contracts/README.md), [OpenAPI](contracts/openapi.yaml), [operation index](contracts/operation-index.json) |
| AI behavior and evaluation | [AI specification](planning-artifacts/ai-specification.md) |
| Security/privacy and measurable quality | [Security/privacy](planning-artifacts/security-privacy.md), [NFRs](planning-artifacts/non-functional-requirements.md) |
| Actual initial CI and GitHub security | [Repository controls](operations/github-security.md), [CI/CD and quality audit](operations/ci-cd-quality-audit.md), [CI observation](operations/ci-observation.md), [SonarQube Cloud](operations/sonarqube-cloud.md) |
| Engineering baseline | [Measured quality baseline](operations/quality-baseline.md) |
| Future delivery and recovery | [CI/CD/deployment](operations/cicd-deployment-strategy.md), [deployment readiness](operations/deployment-readiness.md), [GitHub delivery rules](operations/delivery-rules.md) |
| Dated compatibility/cost evidence | [Hosting assessment](operations/hosting-cost-assessment.md), [official source evidence](operations/technology-and-hosting-sources.md) |
| Verification design | [Test strategy](test-artifacts/test-strategy.md), [acceptance scenarios](test-artifacts/acceptance-scenarios.md) |
| Planning backlog and roadmap | [Story delivery order](planning-artifacts/story-delivery-order.md), [10 epics / 45 stories](planning-artifacts/epics/README.md), [roadmap by increment](planning-artifacts/roadmap.md), [implementation stopping point](implementation-artifacts/README.md) |
| UX spines | [DESIGN](planning-artifacts/ux/DESIGN.md), [EXPERIENCE](planning-artifacts/ux/EXPERIENCE.md) |
| Existing visual source and assets | [Figma inventory](design/figma-inventory.md), [tokens/assets](design/token-reference.md), [snapshot/export manifest](design/figma/manifest.json), [offline design-system reference](design/design-system.md) |
| Design surfaces | [Screens](design/screen-catalog.md), [interactions/modals](design/interaction-catalog.md), [PDFs/emails](design/pdf-email-specifications.md) |
| Offline mockups and version evidence | [Committed HTML reference and latest native comparisons](../design/offline-reference/README.md) |
| Shared fictional content | [Narrative](design/synthetic-data.md), [dataset](design/synthetic-data.json) |
| Design progress and next phase | [Coverage](design/coverage.csv), [tracking guide](design/coverage-guide.md), [Claude handoff](design/handoff-claude.md), [session recovery](design/session-recovery.md), [short continuation prompt](design/continuation-prompt.md) |
| Received and corrected HTML design | [Codex corrections/evidence](design/reviews/2026-11-03-corrections.md), [original reception](design/reviews/2026-11-03-reception.md), [decisions](design/reviews/2026-11-03-decisions.md), [separate HTML tracker](design/html-reception.csv), [preserved sources](../design/received/2026-11-03/README.md), [reconstruction/verification](../design/corrections/README.md) |
| Earlier local editable Figma import | [Plugin/export guide](../design/figma-local-import/README.md); Windows queue execution and closure recorded separately below |
| Isolated native MCP import test | [2026-11-17 pilot](design/reviews/2026-11-17-figma-mcp-import.md); one editable S-06/nora state, fidelity reconciliation pending |
| Windows Figma Desktop import | [Native run](design/reviews/2026-11-17-windows-native-run.md), [closure](design/reviews/2026-11-18-figma-native-closure.md), [queue plugin](../design/figma-windows-import/README.md); 456 desktop states imported; 52 targeted states corrected/exported; broader review and Aymen approval pending |
| Background layer study (paper and blueprint) | [Background study](design/background-study/README.md), [calm-zones report](design/reviews/2026-11-17-background-calm-zones.md); Proposal: working direction accepted by Aymen, applied to the offline copies of S-01, S-02 and S-25, not a design approval |
| Functional motion for state feedback | [Motion study](design/motion-study/README.md), [ADR-012](decisions/ADR-012-functional-motion.md); Proposal validated by Aymen on 2026-11-20 (UD-42), playable prototype; Figma variants not drawn |
| Design-system closure | [ADR-013](decisions/ADR-013-web-component-behaviour-layer.md), [ADR-014](decisions/ADR-014-design-system-closure.md), [type roles](design/typography-closure.md), [review record](design/reviews/2026-11-20-design-system-review.md); Aymen's seven decisions on 2026-11-20 (UD-43); nothing applied to tokens or CSS yet (D-50) |
| End-to-end mapping | [Traceability](traceability.md), [review](review-report.md), [documentary validation](test-artifacts/document-validation.json) |
| Agent installation and policy | [Setup/troubleshooting](agent-setup.md), [workflow execution record](workflow-record.md), [AGENTS](../AGENTS.md), [CLAUDE](../CLAUDE.md) |

Each authority owns its definition. Other documents link to it; generated catalogs/indexes and exported chat bundles are distribution/lookup aids. Design status belongs in coverage.csv; implementation status belongs in future compatible BMAD tracking supported by story/code/history/evidence. Neither is copied into root instruction files.
