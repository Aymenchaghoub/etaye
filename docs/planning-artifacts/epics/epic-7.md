# Epic 7: Explicit publication and patient access

Status: backlog. Design: Ready for design, not designed or approved. Source versions: user mission 2026-11-02; Figma 2417265104370398313; stable BMAD 6.12.4; legacy audit 83d0439920d598775d4f424229a913b125b52217. Read current authoritative documents again before refinement.

Allocation and story text refreshed on 2026-11-17 after the user decisions of that date ([decision register](../decisions-assumptions.md), UD-12 to UD-37) and aligned with the [roadmap](../roadmap.md). Sources consulted in full for this refresh: PRD, business rules, access matrix, functional specification, UX, architecture spine, data model and dictionary, API contract and README, AI specification, security/privacy, test strategy, deferred register, traceability and the dependency stories.

These are planning-level slices, not implementation authorization. Shared gate: complete reviewed required screen/interaction/print coverage, confirmed blocking assumptions, Aymen's explicit development authorization, then installed BMAD readiness check. Validation of code and visual fidelity remain separate.

## Story 7.1: Prepare a separately reviewed patient explanation

As a practitioner, I want to prepare a separately reviewed patient explanation so that patient-facing text is written and validated for its audience and never derived by relabeling a brief.

- Requirements: FR-013, FR-014, FR-015. Rules: BR-011–013. Journey: J-05.
- Design: S-20, S-21; interactions I-15, I-16. Acceptance test design: AT-027.
- Dependencies: Story 6.4 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 1, Red-thread slice (web).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-027-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then a practitioner produces audience-appropriate explanation from allowed evidence and validates it.

**AT-027-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then a clinician brief cannot be relabeled or directly published; unsupported clinical certainty is corrected.

**AT-027-C:** Given long content, keyboard-only navigation and a failed or loading state on S-20, S-21, when the surface is reviewed under the evidence level below, then no essential source, date, unit or action is clipped and focus, error and recovery behavior match the catalog. Evidence level CORE for S-21: open captures at desktop 1440×1024, tablet 1024×768 and mobile 390×844, with 320 CSS px reflow, long French content, keyboard focus and the failed, loading and restricted states of the family, compared with the approved design reference and preserved as separate visual evidence. Evidence level SAMPLED for S-20: functional assertions and automated axe-core checks for every state, plus opened desktop and mobile captures for the subset sampled by risk (8–10 screens across all sampled families, at 2 widths; confirmed UD-37), preserved as separate visual evidence.

## Story 7.2: Publish selected validated content

As a practitioner holding the publication capability, I want to publish a validated version to a named patient with selected excerpts so that the patient sees exactly what I confirmed.

- Requirements: FR-015. Rules: BR-013. Journey: J-05.
- Design: S-23; interactions I-17. Acceptance test design: AT-028.
- Dependencies: Story 7.1 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 1, Red-thread slice (web).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-028-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then publication names patient, exact version, audience and attachments, then commits one active grant.

**AT-028-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then missing capability/stale version/ineligible brief fails without exposing body or sending premature email.

**AT-028-C:** Given long content, keyboard-only navigation and a failed or loading state on S-23, when the surface is reviewed under the evidence level below, then no essential source, date, unit or action is clipped and focus, error and recovery behavior match the catalog. Evidence level CORE for S-23: open captures at desktop 1440×1024, tablet 1024×768 and mobile 390×844, with 320 CSS px reflow, long French content, keyboard focus and the failed, loading and restricted states of the family, compared with the approved design reference and preserved as separate visual evidence.

## Story 7.3: Replace or withdraw a publication

As a practitioner holding the publication capability, I want to replace or withdraw a publication so that corrections reach the patient atomically and withdrawn content is no longer reachable.

- Requirements: FR-015. Rules: BR-013–014. Journey: J-05.
- Design: S-24; interactions I-18. Acceptance test design: AT-029.
- Dependencies: Story 7.2 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 1, Red-thread slice (web).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-029-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then replacement activates a new validated version and replaces the old publication atomically.

**AT-029-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then withdrawal requires reason and blocks future body/download access while acknowledging unretractable downloaded copies.

**AT-029-C:** Given long content, keyboard-only navigation and a failed or loading state on S-24, when the surface is reviewed under the evidence level below, then no essential source, date, unit or action is clipped and focus, error and recovery behavior match the catalog. Evidence level SAMPLED for S-24: functional assertions and automated axe-core checks for every state, plus opened desktop and mobile captures for the subset sampled by risk (8–10 screens across all sampled families, at 2 widths; confirmed UD-37), preserved as separate visual evidence.

## Story 7.4: Read only own published information

As a linked patient, I want to read only my current published information and approved excerpts so that internal drafts, briefs and other records are never visible to me.

- Requirements: FR-016. Rules: BR-002, BR-013. Journey: J-06.
- Design: S-25, S-28, S-29; interactions I-09. Acceptance test design: AT-030.
- Dependencies: Stories 7.2, 1.2 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 1, Red-thread slice (web).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-030-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then linked patient opens current published explanation and understandable source excerpts.

**AT-030-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then internal validated-but-unpublished report, other patient link and clinician-only source all deny access.

**AT-030-C:** Given long content, keyboard-only navigation and a failed or loading state on S-25, S-28, S-29, when the surface is reviewed under the evidence level below, then no essential source, date, unit or action is clipped and focus, error and recovery behavior match the catalog. Evidence level CORE for S-25, S-28, S-29: open captures at desktop 1440×1024, tablet 1024×768 and mobile 390×844, with 320 CSS px reflow, long French content, keyboard focus and the failed, loading and restricted states of the family, compared with the approved design reference and preserved as separate visual evidence.

## Story 7.5: Manage patient appointments and authorized files

As a linked patient, I want to manage my own appointments and open authorized attachments so that I act on my care without seeing other patients or unshared documents.

- Requirements: FR-009, FR-016. Rules: BR-006, BR-010, BR-013. Journey: J-06.
- Design: S-26, S-30; interactions I-10, I-11, I-12. Acceptance test design: AT-031.
- Dependencies: Stories 4.2, 4.3, 7.2 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 1, Red-thread slice (web). Deferred parts: Authorized publication attachments (S-30; downloadPublishedAttachment); increment 1 covers the patient appointment screens only → Increment 2 (D-25).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-031-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then patient manages own eligible appointments and authorized publication attachments. Increment 1 verifies the appointment part; attachments are verified in increment 2 (D-25).

**AT-031-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then other patient names, disallowed source attachments and expired grants never appear through previews or download URLs.

**AT-031-C:** Given long content, keyboard-only navigation and a failed or loading state on S-26, S-30, when the surface is reviewed under the evidence level below, then no essential source, date, unit or action is clipped and focus, error and recovery behavior match the catalog. Evidence level SAMPLED for S-26, S-30: functional assertions and automated axe-core checks for every state, plus opened desktop and mobile captures for the subset sampled by risk (8–10 screens across all sampled families, at 2 widths; confirmed UD-37), preserved as separate visual evidence.
