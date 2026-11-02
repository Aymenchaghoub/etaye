# Epic 5: Sourced clinician preparation

Status: backlog. Design: Ready for design, not designed or approved. Source versions: user mission 2026-11-02; Figma 2417265104370398313; stable BMAD 6.12.4; legacy audit 83d0439920d598775d4f424229a913b125b52217. Read current authoritative documents again before refinement.

These are planning-level slices, not implementation authorization. Shared gate: complete reviewed required screen/interaction/print coverage, confirmed blocking assumptions, Aymen's explicit development authorization, then installed BMAD readiness check. Validation of code and visual fidelity remain separate.

## Story 5.1: Read a daily preparation workspace

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-009, FR-011. Rules: BR-006, BR-011. Journey: J-02.
- Design: S-06, S-18; interactions I-09. Acceptance test design: AT-019.
- Dependencies: Access/data/source boundaries in E1–E3 as applicable; explicit publication in E7 for patient exports.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-019-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then upcoming encounters link to source-aware brief and changes since last visit.

**AT-019-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then empty day/partial panel errors are distinct and missing brief does not become normal findings.

**AT-019-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.

## Story 5.2: Generate a bounded clinician brief

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-011, FR-013. Rules: BR-011. Journey: J-02.
- Design: S-18, S-40; interactions I-14. Acceptance test design: AT-020.
- Dependencies: Access/data/source boundaries in E1–E3 as applicable; explicit publication in E7 for patient exports.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-020-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then a scoped job records source snapshot and returns a new clinician-only draft.

**AT-020-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then quota, duplicate, timeout and cancellation states do not overwrite existing corrected content.

**AT-020-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.

## Story 5.3: Inspect dated statement support

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-008, FR-011, FR-013. Rules: BR-008, BR-010–011. Journey: J-02.
- Design: S-13, S-18, S-21; interactions I-09. Acceptance test design: AT-021.
- Dependencies: Access/data/source boundaries in E1–E3 as applicable; explicit publication in E7 for patient exports.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-021-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then selecting a marker opens exact supporting version/locator and returns focus to that marker.

**AT-021-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then unavailable or non-supporting evidence is explicit and cannot pass review merely because a citation exists.

**AT-021-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.

## Story 5.4: Handle changed sources and failed tasks

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-013, FR-014. Rules: BR-011, BR-014. Journey: J-04.
- Design: S-24, S-37, S-40; interactions I-23. Acceptance test design: AT-022.
- Dependencies: Access/data/source boundaries in E1–E3 as applicable; explicit publication in E7 for patient exports.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-022-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then relevant source correction marks dependent versions STALE with an explanation.

**AT-022-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then retry uses a bounded new attempt/candidate and late worker results cannot overwrite human corrections.

**AT-022-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.
