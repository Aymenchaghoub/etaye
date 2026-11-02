# Epic 7: Explicit publication and patient access

Status: backlog. Design: Ready for design, not designed or approved. Source versions: user mission 2026-11-02; Figma 2417265104370398313; stable BMAD 6.12.4; legacy audit 83d0439920d598775d4f424229a913b125b52217. Read current authoritative documents again before refinement.

These are planning-level slices, not implementation authorization. Shared gate: complete reviewed required screen/interaction/print coverage, confirmed blocking assumptions, Aymen's explicit development authorization, then installed BMAD readiness check. Validation of code and visual fidelity remain separate.

## Story 7.1: Prepare a separately reviewed patient explanation

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-013, FR-014, FR-015. Rules: BR-011–013. Journey: J-05.
- Design: S-20, S-21; interactions I-15, I-16. Acceptance test design: AT-027.
- Dependencies: Access/data/source boundaries in E1–E3 as applicable; explicit publication in E7 for patient exports.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-027-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then a practitioner produces audience-appropriate explanation from allowed evidence and validates it.

**AT-027-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then a clinician brief cannot be relabeled or directly published; unsupported clinical certainty is corrected.

**AT-027-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.

## Story 7.2: Publish selected validated content

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-015. Rules: BR-013. Journey: J-05.
- Design: S-23; interactions I-17. Acceptance test design: AT-028.
- Dependencies: Access/data/source boundaries in E1–E3 as applicable; explicit publication in E7 for patient exports.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-028-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then publication names patient, exact version, audience and attachments, then commits one active grant.

**AT-028-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then missing capability/stale version/ineligible brief fails without exposing body or sending premature email.

**AT-028-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.

## Story 7.3: Replace or withdraw a publication

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-015. Rules: BR-013–014. Journey: J-05.
- Design: S-24; interactions I-18. Acceptance test design: AT-029.
- Dependencies: Access/data/source boundaries in E1–E3 as applicable; explicit publication in E7 for patient exports.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-029-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then replacement activates a new validated version and replaces the old publication atomically.

**AT-029-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then withdrawal requires reason and blocks future body/download access while acknowledging unretractable downloaded copies.

**AT-029-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.

## Story 7.4: Read only own published information

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-016. Rules: BR-002, BR-013. Journey: J-06.
- Design: S-25, S-28, S-29; interactions I-09. Acceptance test design: AT-030.
- Dependencies: Access/data/source boundaries in E1–E3 as applicable; explicit publication in E7 for patient exports.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-030-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then linked patient opens current published explanation and understandable source excerpts.

**AT-030-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then internal validated-but-unpublished report, other patient link and clinician-only source all deny access.

**AT-030-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.

## Story 7.5: Manage patient appointments and authorized files

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-009, FR-016. Rules: BR-006, BR-010, BR-013. Journey: J-06.
- Design: S-26, S-30; interactions I-10, I-11, I-12. Acceptance test design: AT-031.
- Dependencies: Access/data/source boundaries in E1–E3 as applicable; explicit publication in E7 for patient exports.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-031-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then patient manages own eligible appointments and authorized publication attachments.

**AT-031-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then other patient names, disallowed source attachments and expired grants never appear through previews or download URLs.

**AT-031-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.
