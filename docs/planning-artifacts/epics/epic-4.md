# Epic 4: Scheduling and preparation

Status: backlog. Design: Ready for design, not designed or approved. Source versions: user mission 2026-11-02; Figma 2417265104370398313; stable BMAD 6.12.4; legacy audit 83d0439920d598775d4f424229a913b125b52217. Read current authoritative documents again before refinement.

These are planning-level slices, not implementation authorization. Shared gate: complete reviewed required screen/interaction/print coverage, confirmed blocking assumptions, Aymen's explicit development authorization, then installed BMAD readiness check. Validation of code and visual fidelity remain separate.

## Story 4.1: Define availability and exceptions

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-009. Rules: BR-006. Journey: J-03.
- Design: S-14, S-15; interactions I-24. Acceptance test design: AT-014.
- Dependencies: Access/data/source boundaries in E1–E3 as applicable; explicit publication in E7 for patient exports.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-014-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then a practitioner publishes eligible intervals and dated exceptions in practice timezone.

**AT-014-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then dST ambiguity requires an explicit offset and impacted bookings are disclosed before changes.

**AT-014-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.

## Story 4.2: Book a real appointment

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-009. Rules: BR-006. Journey: J-03.
- Design: S-14, S-16, S-26; interactions I-10. Acceptance test design: AT-015.
- Dependencies: Access/data/source boundaries in E1–E3 as applicable; explicit publication in E7 for patient exports.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-015-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then an eligible slot creates one BOOKED appointment distinct from a consultation.

**AT-015-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then concurrent identical-slot attempts produce one booking and a preserved-input conflict for the other.

**AT-015-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.

## Story 4.3: Reschedule or cancel an appointment

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-009. Rules: BR-006. Journey: J-03.
- Design: S-16, S-26; interactions I-11, I-12. Acceptance test design: AT-016.
- Dependencies: Access/data/source boundaries in E1–E3 as applicable; explicit publication in E7 for patient exports.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-016-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then authorized eligible changes preserve appointment identity and old/new times with reason.

**AT-016-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then stale version, cutoff or occupied target slot leaves original booking intact and reports the reason.

**AT-016-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.

## Story 4.4: Record arrival, completion or no-show

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-009, FR-012. Rules: BR-006–007. Journey: J-03.
- Design: S-16; interactions I-13. Acceptance test design: AT-017.
- Dependencies: Access/data/source boundaries in E1–E3 as applicable; explicit publication in E7 for patient exports.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-017-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then staff records permitted attendance transitions after checking current version.

**AT-017-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then no-show cannot imply an encounter; completing attendance does not validate/publish a report.

**AT-017-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.

## Story 4.5: Prepare a visit and inspect patient reports

As the authorized actor in the linked journey, I want this capability so that the journey can complete without inventing clinical facts or access grants.

- Requirements: FR-010, FR-016. Rules: BR-008. Journey: J-06.
- Design: S-17, S-27; interactions I-05. Acceptance test design: AT-018.
- Dependencies: Access/data/source boundaries in E1–E3 as applicable; explicit publication in E7 for patient exports.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-018-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then linked patient submits dated concerns/questions and practitioner reads original provenance.

**AT-018-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then a different patient cannot edit it; encounter-start lock or concurrency preserves safe comparison/recovery.

**AT-018-C:** Given long content, keyboard-only navigation and relevant failed/loading state, when the approved surface is reviewed at matching desktop/tablet/mobile (and A4 where relevant), then no essential source/date/unit/action is clipped; focus/error/recovery behavior matches the catalog. Preserve opened screenshots as separate visual evidence.
