# Epic 2: Team and operational administration

Status: backlog. Design: Ready for design, not designed or approved. Source versions: user mission 2026-11-02; Figma 2417265104370398313; stable BMAD 6.12.4; legacy audit 83d0439920d598775d4f424229a913b125b52217. Read current authoritative documents again before refinement.

These are planning-level slices, not implementation authorization. Shared gate: complete reviewed required screen/interaction/print coverage, confirmed blocking assumptions, Aymen's explicit development authorization, then installed BMAD readiness check. Validation of code and visual fidelity remain separate.

## Story 2.1: Invite and manage team permissions

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-003. Rules: BR-002–005. Journey: J-07.
- Design: S-31, S-32; interactions I-01. Acceptance test design: AT-005.
- Dependencies: User-approved design gate; current access and identity assumptions.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-005-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then an administrator issues a bounded invitation with explicit capabilities and sees delivery status.

**AT-005-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then duplicate sends do not duplicate membership; administrator alone cannot open clinical data.

**AT-005-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.

## Story 2.2: Deactivate access without losing accountability

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-003. Rules: BR-003, BR-017. Journey: J-07.
- Design: S-32; interactions I-02. Acceptance test design: AT-006.
- Dependencies: User-approved design gate; current access and identity assumptions.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-006-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then deactivation suspends the selected membership and records actor/reason.

**AT-006-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then the final active administrator cannot be removed and unrelated practice memberships remain unchanged.

**AT-006-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.

## Story 2.3: Maintain operational practice settings

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-018, FR-009. Rules: BR-002, BR-006. Journey: J-07.
- Design: S-33; interactions I-24. Acceptance test design: AT-007.
- Dependencies: User-approved design gate; current access and identity assumptions.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-007-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then authorized settings edits show their timezone/booking effect and version.

**AT-007-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then a concurrent change is rejected; changing availability cannot silently strand existing bookings.

**AT-007-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.

## Story 2.4: Inspect scoped audit and own preferences

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-018. Rules: BR-002, BR-017. Journey: J-07.
- Design: S-34, S-35; interactions I-25. Acceptance test design: AT-008.
- Dependencies: User-approved design gate; current access and identity assumptions.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-008-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then an actor filters allowed event metadata and changes own display preferences.

**AT-008-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then administrative audit contains no clinical body and profile changes cannot escalate permissions.

**AT-008-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.
