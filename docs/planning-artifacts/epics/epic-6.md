# Epic 6: Consultation and human-reviewed reports

Status: backlog. Design: Ready for design, not designed or approved. Source versions: user mission 2026-11-02; Figma 2417265104370398313; stable BMAD 6.12.4; legacy audit 83d0439920d598775d4f424229a913b125b52217. Read current authoritative documents again before refinement.

Allocation and story text refreshed on 2026-11-17 after the user decisions of that date ([decision register](../decisions-assumptions.md), UD-12 to UD-28) and aligned with the [roadmap](../roadmap.md). Sources consulted in full for this refresh: PRD, business rules, access matrix, functional specification, UX, architecture spine, data model and dictionary, API contract and README, AI specification, security/privacy, test strategy, deferred register, traceability and the dependency stories.

These are planning-level slices, not implementation authorization. Shared gate: complete reviewed required screen/interaction/print coverage, confirmed blocking assumptions, Aymen's explicit development authorization, then installed BMAD readiness check. Validation of code and visual fidelity remain separate.

## Story 6.1: Conduct an encounter with or without appointment

As a practitioner, I want to conduct an encounter with or without an appointment, save notes and complete it separately so that clinical notes are never lost or published by side effect.

- Requirements: FR-012. Rules: BR-007. Journey: J-04.
- Design: S-19; interactions I-05. Acceptance test design: AT-023.
- Dependencies: Story 3.2 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 1, Red-thread slice (web).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-023-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then practitioner starts an authorized encounter, saves notes and completes it independently.

**AT-023-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then invalid appointment relationship, unsaved exit and permission revocation cannot silently lose notes or create publication.

**AT-023-C:** Given long content, keyboard-only navigation and a failed or loading state on S-19, when the surface is reviewed under the evidence level below, then no essential source, date, unit or action is clipped and focus, error and recovery behavior match the catalog. Evidence level CORE for S-19: open captures at desktop 1440×1024, tablet 1024×768 and mobile 390×844, with 320 CSS px reflow, long French content, keyboard focus and the failed, loading and restricted states of the family, compared with the approved design reference and preserved as separate visual evidence.

## Story 6.2: Draft a consultation report

As a practitioner, I want a consultation report draft that separates generated baseline, source facts and my edits so that I remain the author of what is kept.

- Requirements: FR-012, FR-013. Rules: BR-007, BR-011. Journey: J-04.
- Design: S-20, S-21; interactions I-14, I-15. Acceptance test design: AT-024.
- Dependencies: Stories 6.1, 5.2 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 1, Red-thread slice (web).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-024-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then AI or human draft separates generated baseline, source facts and human-edited text.

**AT-024-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then malformed/unsupported AI output remains failed or reviewable candidate, never approved content.

**AT-024-C:** Given long content, keyboard-only navigation and a failed or loading state on S-20, S-21, when the surface is reviewed under the evidence level below, then no essential source, date, unit or action is clipped and focus, error and recovery behavior match the catalog. Evidence level CORE for S-21: open captures at desktop 1440×1024, tablet 1024×768 and mobile 390×844, with 320 CSS px reflow, long French content, keyboard focus and the failed, loading and restricted states of the family, compared with the approved design reference and preserved as separate visual evidence. Evidence level SAMPLED for S-20: functional assertions and automated axe-core checks for every state, plus opened desktop and mobile captures for the subset sampled by risk (proposed default: 8–10 screens across all sampled families, adjustable by Aymen), preserved as separate visual evidence.

## Story 6.3: Review, correct and compare versions

As a practitioner, I want to review, correct and compare versions so that every change keeps actor, time and exact text and concurrent edits cannot overwrite each other.

- Requirements: FR-014. Rules: BR-012, BR-015. Journey: J-04.
- Design: S-21, S-22; interactions I-15, I-21. Acceptance test design: AT-025.
- Dependencies: Story 6.2 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 1, Red-thread slice (web).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-025-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then corrections preserve history and comparison shows actor/time and exact changed text.

**AT-025-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then concurrent write returns 412 with local text retained; approved content is edited only through new draft.

**AT-025-C:** Given long content, keyboard-only navigation and a failed or loading state on S-21, S-22, when the surface is reviewed under the evidence level below, then no essential source, date, unit or action is clipped and focus, error and recovery behavior match the catalog. Evidence level CORE for S-21, S-22: open captures at desktop 1440×1024, tablet 1024×768 and mobile 390×844, with 320 CSS px reflow, long French content, keyboard focus and the failed, loading and restricted states of the family, compared with the approved design reference and preserved as separate visual evidence.

## Story 6.4: Validate the exact reviewed version

As a practitioner holding the validation capability, I want to validate one exact reviewed version so that validation is a deliberate, recorded attestation that grants no patient visibility.

- Requirements: FR-014. Rules: BR-012. Journey: J-04.
- Design: S-23; interactions I-16. Acceptance test design: AT-026.
- Dependencies: Story 6.3 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 1, Red-thread slice (web).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-026-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then reviewer checks facts/citations/missing data and explicitly validates a current exact version.

**AT-026-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then invalid or stale preconditions prevent validation; success grants no patient visibility. Validation applies to consultation reports and patient explanations only; a clinician brief is rejected with 422 BRIEF_NOT_REVIEWABLE.

**AT-026-C:** Given long content, keyboard-only navigation and a failed or loading state on S-23, when the surface is reviewed under the evidence level below, then no essential source, date, unit or action is clipped and focus, error and recovery behavior match the catalog. Evidence level CORE for S-23: open captures at desktop 1440×1024, tablet 1024×768 and mobile 390×844, with 320 CSS px reflow, long French content, keyboard focus and the failed, loading and restricted states of the family, compared with the approved design reference and preserved as separate visual evidence.
