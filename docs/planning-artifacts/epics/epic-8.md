# Epic 8: Branded documents and communications

Status: backlog. Design: Ready for design, not designed or approved. Source versions: user mission 2026-11-02; Figma 2417265104370398313; stable BMAD 6.12.4; legacy audit 83d0439920d598775d4f424229a913b125b52217. Read current authoritative documents again before refinement.

These are planning-level slices, not implementation authorization. Shared gate: complete reviewed required screen/interaction/print coverage, confirmed blocking assumptions, Aymen's explicit development authorization, then installed BMAD readiness check. Validation of code and visual fidelity remain separate.

## Story 8.1: Export a practitioner-only brief

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-017, FR-011. Rules: BR-016. Journey: J-02.
- Design: S-39, P-01; interactions I-19. Acceptance test design: AT-032.
- Dependencies: Access/data/source boundaries in E1–E3 as applicable; explicit publication in E7 for patient exports.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-032-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then export snapshot uses exact brief version/sources and clear clinician-only draft/validated labeling.

**AT-032-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then patient export request denies access; long/incomplete content preserves units, accents and page references.

**AT-032-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.

## Story 8.2: Export reports and patient explanations

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-017, FR-015, FR-016. Rules: BR-013, BR-016. Journey: J-06.
- Design: S-39, P-02, P-03; interactions I-19. Acceptance test design: AT-033.
- Dependencies: Access/data/source boundaries in E1–E3 as applicable; explicit publication in E7 for patient exports.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-033-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then exports reflect requesting audience, version/publication state and dated source references.

**AT-033-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then withdrawal revokes later download and a draft PDF never masquerades as published.

**AT-033-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.

## Story 8.3: Deliver minimal transactional notifications

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-001, FR-009, FR-015, FR-017. Rules: BR-005, BR-006, BR-016. Journey: J-01, J-05.
- Design: M-01–05; interactions I-01, I-17, I-23. Acceptance test design: AT-034.
- Dependencies: Access/data/source boundaries in E1–E3 as applicable; explicit publication in E7 for patient exports.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-034-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then committed invitation/recovery/appointment/publication events produce minimal branded safe-link messages.

**AT-034-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then rolled-back action sends nothing; retry does not duplicate business change or disclose clinical text.

**AT-034-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.
