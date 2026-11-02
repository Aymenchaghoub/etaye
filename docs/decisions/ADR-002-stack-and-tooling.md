# ADR-002 — Current supported stack and tooling

Status: Proposed. Date: 2026-11-02. Related: AD-1/10, FR-020.

User decisions fix Spring Boot, React TypeScript and PostgreSQL. Current official evidence is recorded in [technology and hosting sources](../operations/technology-and-hosting-sources.md).

| Area | Proposed choice | Alternative and consequence |
|---|---|---|
| JVM | Java 25 LTS, open JDK distribution with published security updates | Java 21 LTS remains credible but gives a shorter runway; do not assume Oracle commercial support terms apply to every distribution |
| Backend | Spring Boot 4.1.2 / Spring MVC / Spring Security / Spring Data JPA; BOM controls compatible dependency versions | Boot 3.5 is an alternative whose support window must be rechecked at setup; WebFlux adds a second concurrency model without measured need |
| Build | Maven wrapper on a supported 3.9 release, chosen and checksum-pinned during setup | Gradle also supported; no user value justifies both |
| Database | PostgreSQL 18.6 for CI/local/demo, provider support verified before provisioning | PostgreSQL 17 remains an acceptable documented fallback if a selected provider lacks 18; keep all environments on the same major |
| Browser app | React 19.3.0, TypeScript strict, Vite 8 supported patch, Node 26 LTS | Next.js/SSR evaluated: public showcase can benefit from static pre-render, but clinician workspace needs no second server runtime; do not adopt an app framework only for a landing page |
| UI primitives | Radix Primitives plus CSS tokens/components authored from Figma | MUI/Ant default themes pull identity away from existing foundations; shadcn source snippets are optional references, not a substitute design system |
| State | Local form/UI state, server-query cache (TanStack Query candidate), URL state for safe filters | Global Redux store not justified; never cache clinical data indefinitely across logout/practice changes |
| Migration | Flyway Community SQL, checksums, validate and clean/upgrade test paths | Liquibase supports declarative migrations but adds format/abstraction without a demonstrated need; paid undo is not the rollback strategy |
| Tests | Boot-managed JUnit generation, Testcontainers PostgreSQL, Vitest supported compatible patch at setup, Testing Library, Playwright | H2 and endpoint mocks cannot establish PostgreSQL isolation, constraints or end-to-end behavior |
| PDF | Apache PDFBox 3 supported patch, server-side layout service and embedded licensed fonts | Chromium HTML-to-PDF simplifies CSS but increases memory/startup cost; OpenHTMLToPDF maintenance and accessibility fit require further evaluation before selecting it |

Primary-source validation establishes support and baseline compatibility, **not a successful project build**. Exact tool patches, plugin compatibility, accessibility behavior and PDF layout remain future setup spikes after authorization. Use BOM/lockfiles; do not force independent Spring component versions. Pin base images by digest at release. No starter was run or generated in this phase.
