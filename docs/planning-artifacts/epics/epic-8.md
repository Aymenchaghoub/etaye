# Epic 8: Branded documents and communications

Status: backlog. Design: Ready for design, not designed or approved. Source versions: user mission 2026-11-02; Figma 2417265104370398313; stable BMAD 6.12.4; legacy audit 83d0439920d598775d4f424229a913b125b52217. Read current authoritative documents again before refinement.

Allocation and story text refreshed on 2026-11-17 after the user decisions of that date ([decision register](../decisions-assumptions.md), UD-12 to UD-28) and aligned with the [roadmap](../roadmap.md). Sources consulted in full for this refresh: PRD, business rules, access matrix, functional specification, UX, architecture spine, data model and dictionary, API contract and README, AI specification, security/privacy, test strategy, deferred register, traceability and the dependency stories.

These are planning-level slices, not implementation authorization. Shared gate: complete reviewed required screen/interaction/print coverage, confirmed blocking assumptions, Aymen's explicit development authorization, then installed BMAD readiness check. Validation of code and visual fidelity remain separate.

## Story 8.1: Export a practitioner-only brief

As a practitioner, I want to export a clinician-only brief as a PDF so that preparation can be printed with its draft or acknowledgement status, version and dated sources.

- Requirements: FR-017, FR-011. Rules: BR-016. Journey: J-02.
- Design: S-39, P-01; interactions I-19. Acceptance test design: AT-032.
- Dependencies: Story 5.2 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 2, Web completion.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-032-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then export snapshot uses exact brief version/sources and clear clinician-only draft/validated labeling. The export labels the draft status and any practitioner acknowledgement; it never carries a validator (UD-22). Scheduled in increment 2 (D-27).

**AT-032-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then patient export request denies access; long/incomplete content preserves units, accents and page references.

**AT-032-C:** Given long content, keyboard-only navigation and a failed or loading state on S-39, P-01, when the surface is reviewed under the evidence level below, then no essential source, date, unit or action is clipped and focus, error and recovery behavior match the catalog. Evidence level SAMPLED for S-39, P-01: functional assertions and automated axe-core checks for every state, plus opened desktop and mobile captures for the subset sampled by risk (proposed default: 8–10 screens across all sampled families, adjustable by Aymen), preserved as separate visual evidence.

## Story 8.2: Export reports and patient explanations

As a practitioner or linked patient, I want audience-correct PDFs of reports and published explanations so that the document I hold matches my right to it and its publication state.

- Requirements: FR-017, FR-015, FR-016. Rules: BR-013, BR-016. Journey: J-06.
- Design: S-39, P-02, P-03; interactions I-19. Acceptance test design: AT-033.
- Dependencies: Story 7.2 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 1, Red-thread slice (web). Deferred parts: P-02 consultation report PDF → Increment 2 (D-27).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-033-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then exports reflect requesting audience, version/publication state and dated source references. Increment 1 delivers P-03 (published patient explanation); P-02 follows in increment 2 (D-27).

**AT-033-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then withdrawal revokes later download and a draft PDF never masquerades as published.

**AT-033-C:** Given long content, keyboard-only navigation and a failed or loading state on S-39, P-02, P-03, when the surface is reviewed under the evidence level below, then no essential source, date, unit or action is clipped and focus, error and recovery behavior match the catalog. Evidence level SAMPLED for S-39, P-02, P-03: functional assertions and automated axe-core checks for every state, plus opened desktop and mobile captures for the subset sampled by risk (proposed default: 8–10 screens across all sampled families, adjustable by Aymen), preserved as separate visual evidence.

## Story 8.3: Deliver minimal transactional notifications

As a recipient of a recovery, appointment or publication event, I want a minimal notification so that I can return to the product without any clinical content leaving it.

- Requirements: FR-001, FR-009, FR-015, FR-017. Rules: BR-005, BR-006, BR-016. Journey: J-01, J-05.
- Design: M-01–05; interactions I-01, I-17, I-23. Acceptance test design: AT-034.
- Dependencies: Stories 1.2, 4.2, 7.2 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 1, Red-thread slice (web). Deferred parts: Team invitation email M-01 and patient activation email M-02 (they need invitation issuance) → Increment 2 (D-23).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-034-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then committed invitation/recovery/appointment/publication events produce minimal branded safe-link messages. Increment 1 delivers M-03, M-04 and M-05 to a local mail sink; M-01 and M-02 follow in increment 2 (D-23).

**AT-034-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then rolled-back action sends nothing; retry does not duplicate business change or disclose clinical text.

**AT-034-C:** Given long content, keyboard-only navigation and a failed or loading state on M-01–05, when the surface is reviewed under the evidence level below, then no essential source, date, unit or action is clipped and focus, error and recovery behavior match the catalog. Evidence level SAMPLED for M-01: functional assertions and automated axe-core checks for every state, plus opened desktop and mobile captures for the subset sampled by risk (proposed default: 8–10 screens across all sampled families, adjustable by Aymen), preserved as separate visual evidence.
