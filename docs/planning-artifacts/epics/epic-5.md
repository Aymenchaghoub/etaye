# Epic 5: Sourced clinician preparation

Status: backlog. Design: Ready for design, not designed or approved. Source versions: user mission 2026-11-02; Figma 2417265104370398313; stable BMAD 6.12.4; legacy audit 83d0439920d598775d4f424229a913b125b52217. Read current authoritative documents again before refinement.

Allocation and story text refreshed on 2026-11-17 after the user decisions of that date ([decision register](../decisions-assumptions.md), UD-12 to UD-37) and aligned with the [roadmap](../roadmap.md). Sources consulted in full for this refresh: PRD, business rules, access matrix, functional specification, UX, architecture spine, data model and dictionary, API contract and README, AI specification, security/privacy, test strategy, deferred register, traceability and the dependency stories.

These are planning-level slices, not implementation authorization. Shared gate: complete reviewed required screen/interaction/print coverage, confirmed blocking assumptions, Aymen's explicit development authorization, then installed BMAD readiness check. Validation of code and visual fidelity remain separate.

## Story 5.1: Read a daily preparation workspace

As a practitioner, I want a daily workspace listing my appointments with their brief and preparation status so that I can prepare each visit without opening every record.

- Requirements: FR-009, FR-011. Rules: BR-006, BR-011. Journey: J-02.
- Design: S-06, S-18; interactions I-09. Acceptance test design: AT-019.
- Dependencies: Stories 4.2, 5.2 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 1, Red-thread slice (web).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-019-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then upcoming encounters link to source-aware brief and changes since last visit.

**AT-019-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then empty day/partial panel errors are distinct and missing brief does not become normal findings.

**AT-019-C:** Given long content, keyboard-only navigation and a failed or loading state on S-06, S-18, when the surface is reviewed under the evidence level below, then no essential source, date, unit or action is clipped and focus, error and recovery behavior match the catalog. Evidence level CORE for S-06, S-18: open captures at desktop 1440×1024, tablet 1024×768 and mobile 390×844, with 320 CSS px reflow, long French content, keyboard focus and the failed, loading and restricted states of the family, compared with the approved design reference and preserved as separate visual evidence.

## Story 5.2: Generate a bounded clinician brief

As a practitioner, I want a bounded clinician brief of changes, gaps and contradictions that I can acknowledge version by version so that preparation is fast and sourced and the brief is never mistaken for validated or publishable content (UD-22).

- Requirements: FR-011, FR-013. Rules: BR-011. Journey: J-02.
- Design: S-18, S-40; interactions I-14. Acceptance test design: AT-020.
- Dependencies: Stories 3.2, 3.3, 10.1 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 1, Red-thread slice (web). Deferred parts: Optional AI rephrasing of the brief and any real model provider → Increment 4 (D-26).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-020-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then a scoped job records source snapshot and returns a new clinician-only draft. The server derives the source manifest from the record watermark, and the practitioner can acknowledge the exact version (acknowledgeBrief); there is no validation or publication path for a brief. The brief content is composed by a deterministic composer and every statement carries its citation by construction (UD-30).

**AT-020-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then quota, duplicate, timeout and cancellation states do not overwrite existing corrected content. A client-supplied source subset is rejected for a brief, and review or validation requests return 422 BRIEF_NOT_REVIEWABLE.

**AT-020-C:** Given long content, keyboard-only navigation and a failed or loading state on S-18, S-40, when the surface is reviewed under the evidence level below, then no essential source, date, unit or action is clipped and focus, error and recovery behavior match the catalog. Evidence level CORE for S-18: open captures at desktop 1440×1024, tablet 1024×768 and mobile 390×844, with 320 CSS px reflow, long French content, keyboard focus and the failed, loading and restricted states of the family, compared with the approved design reference and preserved as separate visual evidence. Evidence level SAMPLED for S-40: functional assertions and automated axe-core checks for every state, plus opened desktop and mobile captures for the subset sampled by risk (8–10 screens across all sampled families, at 2 widths; confirmed UD-37), preserved as separate visual evidence.

## Story 5.3: Inspect dated statement support

As a practitioner, I want to open the exact dated source behind any statement so that I can judge whether it really supports the claim.

- Requirements: FR-008, FR-011, FR-013. Rules: BR-008, BR-010–011. Journey: J-02.
- Design: S-13, S-18, S-21; interactions I-09. Acceptance test design: AT-021.
- Dependencies: Story 5.2 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 1, Red-thread slice (web).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-021-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then selecting a marker opens exact supporting version/locator and returns focus to that marker. Each citation shows its mechanical check result (UD-31); the check is never labeled verified.

**AT-021-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then unavailable or non-supporting evidence is explicit and cannot pass review merely because a citation exists.

**AT-021-C:** Given long content, keyboard-only navigation and a failed or loading state on S-13, S-18, S-21, when the surface is reviewed under the evidence level below, then no essential source, date, unit or action is clipped and focus, error and recovery behavior match the catalog. Evidence level CORE for S-18, S-21: open captures at desktop 1440×1024, tablet 1024×768 and mobile 390×844, with 320 CSS px reflow, long French content, keyboard focus and the failed, loading and restricted states of the family, compared with the approved design reference and preserved as separate visual evidence. Evidence level SAMPLED for S-13: functional assertions and automated axe-core checks for every state, plus opened desktop and mobile captures for the subset sampled by risk (8–10 screens across all sampled families, at 2 widths; confirmed UD-37), preserved as separate visual evidence.

## Story 5.4: Handle changed sources and failed tasks

As a practitioner, I want changed sources and failed tasks to be explicit so that nothing stale or failed is shown as current or successful.

- Requirements: FR-013, FR-014. Rules: BR-011, BR-014. Journey: J-04.
- Design: S-24, S-37, S-40; interactions I-23. Acceptance test design: AT-022.
- Dependencies: Story 5.2 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 1, Red-thread slice (web).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-022-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then relevant source correction marks dependent versions STALE with an explanation. For a brief, staleness is a freshness banner with a regeneration suggestion and does not block acknowledgement.

**AT-022-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then retry uses a bounded new attempt/candidate and late worker results cannot overwrite human corrections.

**AT-022-C:** Given long content, keyboard-only navigation and a failed or loading state on S-24, S-37, S-40, when the surface is reviewed under the evidence level below, then no essential source, date, unit or action is clipped and focus, error and recovery behavior match the catalog. Evidence level SAMPLED for S-24, S-37, S-40: functional assertions and automated axe-core checks for every state, plus opened desktop and mobile captures for the subset sampled by risk (8–10 screens across all sampled families, at 2 widths; confirmed UD-37), preserved as separate visual evidence.
