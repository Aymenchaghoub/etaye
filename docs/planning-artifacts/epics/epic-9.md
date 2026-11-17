# Epic 9: Credible synthetic demonstration and delivery

Status: backlog. Design: Ready for design, not designed or approved. Source versions: user mission 2026-11-02; Figma 2417265104370398313; stable BMAD 6.12.4; legacy audit 83d0439920d598775d4f424229a913b125b52217. Read current authoritative documents again before refinement.

Allocation and story text refreshed on 2026-11-17 after the user decisions of that date ([decision register](../decisions-assumptions.md), UD-12 to UD-37) and aligned with the [roadmap](../roadmap.md). Sources consulted in full for this refresh: PRD, business rules, access matrix, functional specification, UX, architecture spine, data model and dictionary, API contract and README, AI specification, security/privacy, test strategy, deferred register, traceability and the dependency stories.

These are planning-level slices, not implementation authorization. Shared gate: complete reviewed required screen/interaction/print coverage, confirmed blocking assumptions, Aymen's explicit development authorization, then installed BMAD readiness check. Validation of code and visual fidelity remain separate.

## Story 9.1: Enter and reset bounded synthetic journeys

As a demo visitor, I want to choose a named fictional actor and reset to a known fixture so that I can follow a complete journey without real data or unbounded provider calls.

- Requirements: FR-019. Rules: BR-018. Journey: J-08.
- Design: S-01, S-37; interactions I-26. Acceptance test design: AT-035.
- Dependencies: Story 10.1 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 1, Red-thread slice (web).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-035-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then visitor chooses a named fictional actor/practice and sees demo limits; authorized reset restores fixture version. The demo package runs with docker compose and a scripted Playwright journey that doubles as the test and the video recorder; a static warm-up page covers cold starts (UD-34).

**AT-035-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then no real data or unlimited paid calls; active sessions receive reset/version invalidation notice.

**AT-035-C:** Given long content, keyboard-only navigation and a failed or loading state on S-01, S-37, when the surface is reviewed under the evidence level below, then no essential source, date, unit or action is clipped and focus, error and recovery behavior match the catalog. Evidence level CORE for S-01: open captures at desktop 1440×1024, tablet 1024×768 and mobile 390×844, with 320 CSS px reflow, long French content, keyboard focus and the failed, loading and restricted states of the family, compared with the approved design reference and preserved as separate visual evidence. Evidence level SAMPLED for S-37: functional assertions and automated axe-core checks for every state, plus opened desktop and mobile captures for the subset sampled by risk (8–10 screens across all sampled families, at 2 widths; confirmed UD-37), preserved as separate visual evidence.

## Story 9.2: Produce real-stack quality and release evidence

As the maintainer, I want real-stack quality evidence for every change so that a release is blocked when a required suite fails or does not execute.

- Requirements: FR-020. Rules: BR-001, BR-017–018. Journey: J-08.
- Design: Operational evidence; interactions I-23. Acceptance test design: AT-036.
- Dependencies: Story 10.1 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 1, Red-thread slice (web). Deferred parts: Promotion pipeline, SBOM and provenance signing, SonarQube Cloud analysis → Increment 4 (D-29).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-036-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then future checks execute required nonzero suites against real PostgreSQL/API/UI and promote the tested digest. Increment 1 covers build, tests on real PostgreSQL, CodeQL, secret scan and evidence reports; promotion, signing and Sonar follow in increment 4 (D-29).

**AT-036-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then missing reports/nonexecuted suites/failed quality gate block release; untrusted PRs cannot access deployment credentials.

**AT-036-C:** Given long content, keyboard-only navigation and a failed or loading state on the story's operational outputs, when the surface is reviewed under the evidence level below, then no essential source, date, unit or action is clipped and focus, error and recovery behavior match the catalog. Not applicable: operational evidence only (reports, logs and recorded measurements); no visual review is required.

## Story 9.3: Recover unavailable services and releases

As a user of the demo, I want honest unavailable, cold-start and expired-session states so that a failure never looks like success and my work is not lost.

- Requirements: FR-019, FR-020. Rules: BR-018. Journey: J-08.
- Design: S-37, S-38; interactions I-22, I-23. Acceptance test design: AT-037.
- Dependencies: Stories 10.1, 1.2 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 1, Red-thread slice (web). Deferred parts: Release rollback rehearsal → Increment 4 (D-29).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-037-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then API/AI failures are visible and recoverable; rollback follows compatible schema/artifact plan. Increment 1 covers the user-visible states; rollback rehearsal follows in increment 4 (D-29).

**AT-037-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then cold start does not claim completed generation; destructive migration rollback is never assumed safe.

**AT-037-C:** Given long content, keyboard-only navigation and a failed or loading state on S-37, S-38, when the surface is reviewed under the evidence level below, then no essential source, date, unit or action is clipped and focus, error and recovery behavior match the catalog. Evidence level SAMPLED for S-37, S-38: functional assertions and automated axe-core checks for every state, plus opened desktop and mobile captures for the subset sampled by risk (8–10 screens across all sampled families, at 2 widths; confirmed UD-37), preserved as separate visual evidence.
