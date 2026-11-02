# Epic 1: Safe access and isolated practice work

Status: backlog. Design: Ready for design, not designed or approved. Source versions: user mission 2026-11-02; Figma 2417265104370398313; stable BMAD 6.12.4; legacy audit 83d0439920d598775d4f424229a913b125b52217. Read current authoritative documents again before refinement.

These are planning-level slices, not implementation authorization. Shared gate: complete reviewed required screen/interaction/print coverage, confirmed blocking assumptions, Aymen's explicit development authorization, then installed BMAD readiness check. Validation of code and visual fidelity remain separate.

## Story 1.1: Activate an intended invitation

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-001, FR-004. Rules: BR-004–005. Journey: J-01.
- Design: S-03; interactions I-01, I-03. Acceptance test design: AT-001.
- Dependencies: User-approved design gate; current access and identity assumptions.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-001-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then an unexpired intended invitation activates exactly its membership or portal link once.

**AT-001-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then expired, revoked, already-used or recipient-mismatched tokens grant nothing and show the recovery route.

**AT-001-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.

## Story 1.2: Sign in, recover and expire safely

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-001. Rules: BR-003, BR-005. Journey: J-01.
- Design: S-02, S-04, S-38; interactions I-22. Acceptance test design: AT-002.
- Dependencies: User-approved design gate; current access and identity assumptions.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-002-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then successful credentials resolve current grants and a safe return route.

**AT-002-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then invalid/recovery responses do not enumerate accounts; expiry preserves safe in-memory work without saving falsely.

**AT-002-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.

## Story 1.3: Select and switch practice context

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-002, FR-016. Rules: BR-001–004. Journey: J-01.
- Design: S-05; interactions I-20. Acceptance test design: AT-003.
- Dependencies: User-approved design gate; current access and identity assumptions.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-003-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then a multi-membership account selects only active contexts and clears prior-practice cached resources.

**AT-003-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then a guessed or suspended context and stale deep link expose no other-practice payload.

**AT-003-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.

## Story 1.4: Demonstrate server-side resource isolation

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-002, FR-019. Rules: BR-001–002. Journey: J-08.
- Design: S-36; interactions I-22. Acceptance test design: AT-004.
- Dependencies: User-approved design gate; current access and identity assumptions.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-004-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then practitioner A reads record A through the intended resource guard.

**AT-004-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then record B/source/task/export identifiers are denied under the documented existence-hiding policy.

**AT-004-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.
