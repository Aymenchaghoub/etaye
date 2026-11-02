# Étaye

[![Documentation](https://github.com/Aymenchaghoub/etaye/actions/workflows/documentation.yml/badge.svg)](https://github.com/Aymenchaghoub/etaye/actions/workflows/documentation.yml)
[![CodeQL](https://github.com/Aymenchaghoub/etaye/actions/workflows/codeql.yml/badge.svg)](https://github.com/Aymenchaghoub/etaye/actions/workflows/codeql.yml)

Evidence-supported consultation preparation for general medical practices with multiple practitioners. A clinician can inspect dated sources, correct AI drafts, validate an exact version and explicitly publish selected information to a patient.

**Current state: planning and design. Application development has not started.** This repository contains specifications, tooling and design evidence, not a running medical application. All demonstration people and data are fictional. No clinical validation, certification, regulatory compliance or readiness for real patient data is claimed.

- Start with the [documentation index](docs/README.md).
- Continue complete Figma design with the [Claude handoff](docs/design/handoff-claude.md) and [coverage tracker](docs/design/coverage.csv).
- Read the [product requirements](docs/planning-artifacts/prd/prd.md), [business rules](docs/planning-artifacts/business-rules.md), [architecture](docs/planning-artifacts/architecture/ARCHITECTURE-SPINE.md) and [documentary API](docs/contracts/README.md).
- Resume work from the [continuation note](docs/continuation.md).
- Shared agent policy: [AGENTS.md](AGENTS.md); [setup and verified tooling](docs/agent-setup.md).

The intended foundation is Spring Boot, React with TypeScript and PostgreSQL. Versions and alternatives are evaluated in [ADRs](docs/decisions/README.md); no application dependencies or build commands are asserted as installed/tested. BMAD core/BMM **6.12.4** and pinned **Unslop** are installed as shared project skills. [Third-party notices](THIRD_PARTY_NOTICES.md).

The existing [Figma identity](https://www.figma.com/design/vfh4BSnbRAbaDE1q0CyssL/Etaye?node-id=0-1), original logos and French interface copy are preserved. Repository documentation and future technical identifiers/commits use English; conversations with Aymen use French. An English-facing demo is a separate product decision.

Sequence: **planning → complete design and review → Aymen's explicit development authorization → implementation**. Completing the documents does not authorize development. There is no application scaffold, migration, application pipeline or deployment in this repository. Aymen subsequently authorized the initial documentation/tooling CI and free GitHub security setup; see [actual security/quality state](docs/operations/github-security.md).

Repository: [Aymenchaghoub/etaye](https://github.com/Aymenchaghoub/etaye). Aymen explicitly authorized public visibility on 2026-11-02. Former project/working-name context is recorded only in the [source register and legacy audit](docs/references/source-register.md).
