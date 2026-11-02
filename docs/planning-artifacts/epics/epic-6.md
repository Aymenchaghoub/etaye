# Epic 6: Consultation and human-reviewed reports

Status: backlog. Design: Ready for design, not designed or approved. Source versions: user mission 2026-11-02; Figma 2417265104370398313; stable BMAD 6.12.4; legacy audit 83d0439920d598775d4f424229a913b125b52217. Read current authoritative documents again before refinement.

These are planning-level slices, not implementation authorization. Shared gate: complete reviewed required screen/interaction/print coverage, confirmed blocking assumptions, Aymen's explicit development authorization, then installed BMAD readiness check. Validation of code and visual fidelity remain separate.

## Story 6.1: Conduct an encounter with or without appointment

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-012. Rules: BR-007. Journey: J-04.
- Design: S-19; interactions I-05. Acceptance test design: AT-023.
- Dependencies: Access/data/source boundaries in E1–E3 as applicable; explicit publication in E7 for patient exports.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-023-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then practitioner starts an authorized encounter, saves notes and completes it independently.

**AT-023-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then invalid appointment relationship, unsaved exit and permission revocation cannot silently lose notes or create publication.

**AT-023-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.

## Story 6.2: Draft a consultation report

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-012, FR-013. Rules: BR-007, BR-011. Journey: J-04.
- Design: S-20, S-21; interactions I-14, I-15. Acceptance test design: AT-024.
- Dependencies: Access/data/source boundaries in E1–E3 as applicable; explicit publication in E7 for patient exports.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-024-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then aI or human draft separates generated baseline, source facts and human-edited text.

**AT-024-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then malformed/unsupported AI output remains failed or reviewable candidate, never approved content.

**AT-024-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.

## Story 6.3: Review, correct and compare versions

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-014. Rules: BR-012, BR-015. Journey: J-04.
- Design: S-21, S-22; interactions I-15, I-21. Acceptance test design: AT-025.
- Dependencies: Access/data/source boundaries in E1–E3 as applicable; explicit publication in E7 for patient exports.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-025-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then corrections preserve history and comparison shows actor/time and exact changed text.

**AT-025-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then concurrent write returns 412 with local text retained; approved content is edited only through new draft.

**AT-025-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.

## Story 6.4: Validate the exact reviewed version

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-014. Rules: BR-012. Journey: J-04.
- Design: S-23; interactions I-16. Acceptance test design: AT-026.
- Dependencies: Access/data/source boundaries in E1–E3 as applicable; explicit publication in E7 for patient exports.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-026-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then reviewer checks facts/citations/missing data and explicitly validates a current exact version.

**AT-026-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then invalid or stale preconditions prevent validation; success grants no patient visibility.

**AT-026-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.
