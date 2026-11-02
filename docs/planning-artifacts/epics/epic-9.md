# Epic 9: Credible synthetic demonstration and delivery

Status: backlog. Design: Ready for design, not designed or approved. Source versions: user mission 2026-11-02; Figma 2417265104370398313; stable BMAD 6.12.4; legacy audit 83d0439920d598775d4f424229a913b125b52217. Read current authoritative documents again before refinement.

These are planning-level slices, not implementation authorization. Shared gate: complete reviewed required screen/interaction/print coverage, confirmed blocking assumptions, Aymen's explicit development authorization, then installed BMAD readiness check. Validation of code and visual fidelity remain separate.

## Story 9.1: Enter and reset bounded synthetic journeys

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-019. Rules: BR-018. Journey: J-08.
- Design: S-01, S-37; interactions I-26. Acceptance test design: AT-035.
- Dependencies: Access/data/source boundaries in E1–E3 as applicable; explicit publication in E7 for patient exports.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-035-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then visitor chooses a named fictional actor/practice and sees demo limits; authorized reset restores fixture version.

**AT-035-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then no real data or unlimited paid calls; active sessions receive reset/version invalidation notice.

**AT-035-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.

## Story 9.2: Produce real-stack quality and release evidence

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-020. Rules: BR-001, BR-017–018. Journey: J-08.
- Design: Operational evidence; interactions I-23. Acceptance test design: AT-036.
- Dependencies: Access/data/source boundaries in E1–E3 as applicable; explicit publication in E7 for patient exports.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-036-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then future checks execute required nonzero suites against real PostgreSQL/API/UI and promote the tested digest.

**AT-036-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then missing reports/nonexecuted suites/failed quality gate block release; untrusted PRs cannot access deployment credentials.

**AT-036-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.

## Story 9.3: Recover unavailable services and releases

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-019, FR-020. Rules: BR-018. Journey: J-08.
- Design: S-37, S-38; interactions I-22, I-23. Acceptance test design: AT-037.
- Dependencies: Access/data/source boundaries in E1–E3 as applicable; explicit publication in E7 for patient exports.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-037-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then aPI/AI failures are visible and recoverable; rollback follows compatible schema/artifact plan.

**AT-037-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then cold start does not claim completed generation; destructive migration rollback is never assumed safe.

**AT-037-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.
