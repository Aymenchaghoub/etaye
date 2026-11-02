# Epic 3: Longitudinal patient evidence

Status: backlog. Design: Ready for design, not designed or approved. Source versions: user mission 2026-11-02; Figma 2417265104370398313; stable BMAD 6.12.4; legacy audit 83d0439920d598775d4f424229a913b125b52217. Read current authoritative documents again before refinement.

These are planning-level slices, not implementation authorization. Shared gate: complete reviewed required screen/interaction/print coverage, confirmed blocking assumptions, Aymen's explicit development authorization, then installed BMAD readiness check. Validation of code and visual fidelity remain separate.

## Story 3.1: Find and register a local patient

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-004, FR-005. Rules: BR-004. Journey: J-02.
- Design: S-07, S-08; interactions I-04. Acceptance test design: AT-009.
- Dependencies: User-approved design gate; current access and identity assumptions.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-009-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then a practitioner searches local patients, distinguishes no-results from empty list and creates valid identity.

**AT-009-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then likely duplicates prompt human selection and no cross-practice matching occurs.

**AT-009-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.

## Story 3.2: Explore chronology and explicit assertions

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-005. Rules: BR-008. Journey: J-02.
- Design: S-09, S-10; interactions I-05. Acceptance test design: AT-010.
- Dependencies: User-approved design gate; current access and identity assumptions.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-010-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then the rich record exposes dated history, allergies and reported treatments with source types.

**AT-010-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then minimal history shows unknown/not-asked rather than absence; conflicting reports remain visible.

**AT-010-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.

## Story 3.3: Enter and correct measurements

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-006. Rules: BR-008–009. Journey: J-02.
- Design: S-11; interactions I-06. Acceptance test design: AT-011.
- Dependencies: User-approved design gate; current access and identity assumptions.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-011-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then a dated measurement has valid value/unit/provenance and appears in a labeled trend.

**AT-011-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then correction retains old revision/reason; incompatible-unit series and missing values are not joined silently.

**AT-011-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.

## Story 3.4: Enter and correct laboratory results

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-007. Rules: BR-009. Journey: J-02.
- Design: S-12; interactions I-07. Acceptance test design: AT-012.
- Dependencies: User-approved design gate; current access and identity assumptions.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-012-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then manual qualitative/numeric result preserves comparator, unit and supplied interval.

**AT-012-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then absent interval is explicitly absent, correction keeps lineage and no intelligent PDF import is implied.

**AT-012-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.

## Story 3.5: Upload and inspect authorized documents

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-008. Rules: BR-010. Journey: J-02.
- Design: S-13; interactions I-08, I-09. Acceptance test design: AT-013.
- Dependencies: User-approved design gate; current access and identity assumptions.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-013-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then a permitted file moves through inspection to an authorized exact-version preview.

**AT-013-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then invalid/quarantined files never become facts; guessed download IDs cannot bypass record access.

**AT-013-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.
