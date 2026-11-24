# Epic 4: Scheduling and preparation

Status: backlog. Design: Ready for design, not designed or approved. Source versions: user mission 2026-11-02; Figma 2417265104370398313; stable BMAD 6.12.4; legacy audit 83d0439920d598775d4f424229a913b125b52217. Read current authoritative documents again before refinement.

Allocation and story text refreshed on 2026-11-17 after the user decisions of that date ([decision register](../decisions-assumptions.md), UD-12 to UD-37) and aligned with the [roadmap](../roadmap.md). Sources consulted in full for this refresh: PRD, business rules, access matrix, functional specification, UX, architecture spine, data model and dictionary, API contract and README, AI specification, security/privacy, test strategy, deferred register, traceability and the dependency stories.

These are planning-level slices, not implementation authorization. Shared gate: complete reviewed required screen/interaction/print coverage, confirmed blocking assumptions, Aymen's explicit development authorization, then installed BMAD readiness check. Validation of code and visual fidelity remain separate.

## Story 4.1: Define availability and exceptions

As a practitioner or practice manager, I want to define availability and exceptions so that offered slots reflect real working time.

- Requirements: FR-009. Rules: BR-006. Journey: J-03.
- Design: S-14, S-15; interactions I-24. Acceptance test design: AT-014.
- Dependencies: Story 1.3 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 2, Web completion.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-014-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then a practitioner publishes eligible intervals and dated exceptions in practice timezone. Increment 1 offers a seeded fixed slot grid instead (UD-21); this story replaces it (D-20).

**AT-014-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then DST ambiguity requires an explicit offset and impacted bookings are disclosed before changes.

**AT-014-C:** Given long content, keyboard-only navigation and a failed or loading state on S-14, S-15, when the surface is reviewed under the evidence level below, then no essential source, date, unit or action is clipped and focus, error and recovery behavior match the catalog. Evidence level SAMPLED for S-14, S-15: functional assertions and automated axe-core checks for every state, plus opened desktop and mobile captures for the subset sampled by risk (8–10 screens across all sampled families, at 2 widths; confirmed UD-37), preserved as separate visual evidence.

## Story 4.2: Book a real appointment

As a scheduler, practitioner or linked patient, I want to book an offered slot so that exactly one appointment is created and a concurrent attempt on the same slot is refused with my input preserved.

- Requirements: FR-009. Rules: BR-006. Journey: J-03.
- Design: S-14, S-16, S-26; interactions I-10. Acceptance test design: AT-015.
- Dependencies: Story 3.1 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 1, Red-thread slice (web). Under [UD-63](../decisions-assumptions.md) this story delivers the restricted scheduler screens (S-07 lookup and S-08 create-only variants), the I-10 patient lookup with its combobox ([D-47](../deferred-work.md)) and the return to the retained slot (AT-015-D/E); the server operations and their refusal tests are delivered by [story 3.1](epic-3.md).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-015-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then an eligible slot creates one BOOKED appointment distinct from a consultation. Increment 1 offers the seeded fixed slot grid with atomic booking (UD-21); the overlap rule is forbidden for practitioner and patient record alike (UD-23).

**AT-015-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then concurrent identical-slot attempts produce one booking and a preserved-input conflict for the other.

**AT-015-C:** Given long content, keyboard-only navigation and a failed or loading state on S-14, S-16, S-26, when the surface is reviewed under the evidence level below, then no essential source, date, unit or action is clipped and focus, error and recovery behavior match the catalog. Evidence level SAMPLED for S-14, S-16, S-26: functional assertions and automated axe-core checks for every state, plus opened desktop and mobile captures for the subset sampled by risk (8–10 screens across all sampled families, at 2 widths; confirmed UD-37), preserved as separate visual evidence.

## Story 4.3: Reschedule or cancel an appointment

As a scheduler, practitioner or linked patient, I want to cancel (increment 1) or reschedule (increment 2) an eligible appointment so that history, reasons and old times are kept.

- Requirements: FR-009. Rules: BR-006. Journey: J-03.
- Design: S-16, S-26; interactions I-11, I-12. Acceptance test design: AT-016.
- Dependencies: Story 4.2 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 1, Red-thread slice (web). Deferred parts: Rescheduling and patient move (I-11; rescheduleAppointment, moveOwnAppointment) → Increment 2 (D-22).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-016-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then authorized eligible changes preserve appointment identity and old/new times with reason. Increment 1 verifies cancellation; rescheduling is verified in increment 2 (D-22).

**AT-016-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then stale version, cutoff or occupied target slot leaves original booking intact and reports the reason.

**AT-016-C:** Given long content, keyboard-only navigation and a failed or loading state on S-16, S-26, when the surface is reviewed under the evidence level below, then no essential source, date, unit or action is clipped and focus, error and recovery behavior match the catalog. Evidence level SAMPLED for S-16, S-26: functional assertions and automated axe-core checks for every state, plus opened desktop and mobile captures for the subset sampled by risk (8–10 screens across all sampled families, at 2 widths; confirmed UD-37), preserved as separate visual evidence.

## Story 4.4: Record arrival, completion or no-show

As a scheduler or practitioner, I want to record arrival and completion (increment 1) or a no-show (increment 2) so that attendance stays separate from the consultation and its report.

- Requirements: FR-009, FR-012. Rules: BR-006–007. Journey: J-03.
- Design: S-16; interactions I-13. Acceptance test design: AT-017.
- Dependencies: Story 4.2 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 1, Red-thread slice (web). Deferred parts: No-show marking (appointmentNoShow) → Increment 2 (D-22).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-017-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then staff records permitted attendance transitions after checking current version. Increment 1 verifies check-in and completion; no-show marking is verified in increment 2 (D-22).

**AT-017-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then no-show cannot imply an encounter; completing attendance does not validate/publish a report.

**AT-017-C:** Given long content, keyboard-only navigation and a failed or loading state on S-16, when the surface is reviewed under the evidence level below, then no essential source, date, unit or action is clipped and focus, error and recovery behavior match the catalog. Evidence level SAMPLED for S-16: functional assertions and automated axe-core checks for every state, plus opened desktop and mobile captures for the subset sampled by risk (8–10 screens across all sampled families, at 2 widths; confirmed UD-37), preserved as separate visual evidence.

## Story 4.5: Prepare a visit and inspect patient reports

As a linked patient, I want to prepare my visit, and as a practitioner I want to read and acknowledge that submission, so that the patient's own words stay attributable and are never turned into clinical facts silently (UD-18).

- Requirements: FR-010, FR-016. Rules: BR-008. Journey: J-06.
- Design: S-17, S-27; interactions I-05. Acceptance test design: AT-018.
- Dependencies: Story 4.2 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 1, Red-thread slice (web).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-018-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then linked patient submits dated concerns/questions and practitioner reads original provenance. The practitioner acknowledgement is an explicit append-only action on the exact submission (acknowledgePreparation) and never changes authorship.

**AT-018-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then a different patient cannot edit it; encounter-start lock or concurrency preserves safe comparison/recovery.

**AT-018-C:** Given long content, keyboard-only navigation and a failed or loading state on S-17, S-27, when the surface is reviewed under the evidence level below, then no essential source, date, unit or action is clipped and focus, error and recovery behavior match the catalog. Evidence level SAMPLED for S-17, S-27: functional assertions and automated axe-core checks for every state, plus opened desktop and mobile captures for the subset sampled by risk (8–10 screens across all sampled families, at 2 widths; confirmed UD-37), preserved as separate visual evidence.
