# Epic 3: Longitudinal patient evidence

Status: backlog. Design: Ready for design, not designed or approved. Source versions: user mission 2026-11-02; Figma 2417265104370398313; stable BMAD 6.12.4; legacy audit 83d0439920d598775d4f424229a913b125b52217. Read current authoritative documents again before refinement.

Allocation and story text refreshed on 2026-11-17 after the user decisions of that date ([decision register](../decisions-assumptions.md), UD-12 to UD-28) and aligned with the [roadmap](../roadmap.md). Sources consulted in full for this refresh: PRD, business rules, access matrix, functional specification, UX, architecture spine, data model and dictionary, API contract and README, AI specification, security/privacy, test strategy, deferred register, traceability and the dependency stories.

These are planning-level slices, not implementation authorization. Shared gate: complete reviewed required screen/interaction/print coverage, confirmed blocking assumptions, Aymen's explicit development authorization, then installed BMAD readiness check. Validation of code and visual fidelity remain separate.

## Story 3.1: Find and register a local patient

As a practitioner or an authorized scheduler, I want to find and register practice-local patients so that appointments and records attach to the right identity without cross-practice matching (PD-001 accepted, UD-17).

- Requirements: FR-004, FR-005. Rules: BR-004. Journey: J-02.
- Design: S-07, S-08; interactions I-04. Acceptance test design: AT-009.
- Dependencies: Story 1.3 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 1, Red-thread slice (web).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-009-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then a practitioner searches local patients, distinguishes no-results from empty list and creates valid identity.

**AT-009-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then likely duplicates prompt human selection and no cross-practice matching occurs.

**AT-009-C:** Given long content, keyboard-only navigation and a failed or loading state on S-07, S-08, when the surface is reviewed under the evidence level below, then no essential source, date, unit or action is clipped and focus, error and recovery behavior match the catalog. Evidence level SAMPLED for S-07, S-08: functional assertions and automated axe-core checks for every state, plus opened desktop and mobile captures for the subset sampled by risk (proposed default: 8–10 screens across all sampled families, adjustable by Aymen), preserved as separate visual evidence.

## Story 3.2: Explore chronology and explicit assertions

As a practitioner, I want to explore a patient's chronology, history, allergies and reported treatments with explicit assertion states so that missing information is never mistaken for a normal finding.

- Requirements: FR-005. Rules: BR-008. Journey: J-02.
- Design: S-09, S-10; interactions I-05. Acceptance test design: AT-010.
- Dependencies: Story 3.1 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 1, Red-thread slice (web).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-010-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then the rich record exposes dated history, allergies and reported treatments with source types.

**AT-010-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then minimal history shows unknown/not-asked rather than absence; conflicting reports remain visible.

**AT-010-C:** Given long content, keyboard-only navigation and a failed or loading state on S-09, S-10, when the surface is reviewed under the evidence level below, then no essential source, date, unit or action is clipped and focus, error and recovery behavior match the catalog. Evidence level CORE for S-09, S-10: open captures at desktop 1440×1024, tablet 1024×768 and mobile 390×844, with 320 CSS px reflow, long French content, keyboard focus and the failed, loading and restricted states of the family, compared with the approved design reference and preserved as separate visual evidence.

## Story 3.3: Enter and correct measurements

As a practitioner, I want to see measurements and trends with their correction lineage (increment 1) and to enter or correct them (increment 2) so that values keep unit, provenance and history.

- Requirements: FR-006. Rules: BR-008–009. Journey: J-02.
- Design: S-11; interactions I-06. Acceptance test design: AT-011.
- Dependencies: Story 3.2 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 1, Red-thread slice (web). Deferred parts: Entering and correcting measurements (I-06; appendMeasurement, correctMeasurement) → Increment 2 (D-24).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-011-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then a dated measurement has valid value/unit/provenance and appears in a labeled trend. Increment 1 verifies this on seeded measurements and the weight correction lineage; entry and correction through the interface are verified in increment 2 (D-24).

**AT-011-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then correction retains old revision/reason; incompatible-unit series and missing values are not joined silently.

**AT-011-C:** Given long content, keyboard-only navigation and a failed or loading state on S-11, when the surface is reviewed under the evidence level below, then no essential source, date, unit or action is clipped and focus, error and recovery behavior match the catalog. Evidence level SAMPLED for S-11: functional assertions and automated axe-core checks for every state, plus opened desktop and mobile captures for the subset sampled by risk (proposed default: 8–10 screens across all sampled families, adjustable by Aymen), preserved as separate visual evidence.

## Story 3.4: Enter and correct laboratory results

As a practitioner, I want to see laboratory results with their supplied intervals (increment 1) and to enter or correct them manually (increment 2) so that no normality is inferred.

- Requirements: FR-007. Rules: BR-009. Journey: J-02.
- Design: S-12; interactions I-07. Acceptance test design: AT-012.
- Dependencies: Story 3.2 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 1, Red-thread slice (web). Deferred parts: Entering and correcting laboratory results (I-07; appendLabResult, correctLabResult) → Increment 2 (D-24).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-012-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then manual qualitative/numeric result preserves comparator, unit and supplied interval. Increment 1 verifies this on seeded results; manual entry and correction are verified in increment 2 (D-24).

**AT-012-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then absent interval is explicitly absent, correction keeps lineage and no intelligent PDF import is implied.

**AT-012-C:** Given long content, keyboard-only navigation and a failed or loading state on S-12, when the surface is reviewed under the evidence level below, then no essential source, date, unit or action is clipped and focus, error and recovery behavior match the catalog. Evidence level SAMPLED for S-12: functional assertions and automated axe-core checks for every state, plus opened desktop and mobile captures for the subset sampled by risk (proposed default: 8–10 screens across all sampled families, adjustable by Aymen), preserved as separate visual evidence.

## Story 3.5: Upload and inspect authorized documents

As a practitioner, I want to upload and inspect record documents so that files are stored safely and shown only to authorized users.

- Requirements: FR-008. Rules: BR-010. Journey: J-02.
- Design: S-13; interactions I-08, I-09. Acceptance test design: AT-013.
- Dependencies: Stories 3.2, 10.1 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 2, Web completion.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-013-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then a permitted file moves through inspection to an authorized exact-version preview. Increment 1 only shows the seeded document read-only through story 5.3; upload and inspection states belong to this story in increment 2 (D-25).

**AT-013-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then invalid/quarantined files never become facts; guessed download IDs cannot bypass record access.

**AT-013-C:** Given long content, keyboard-only navigation and a failed or loading state on S-13, when the surface is reviewed under the evidence level below, then no essential source, date, unit or action is clipped and focus, error and recovery behavior match the catalog. Evidence level SAMPLED for S-13: functional assertions and automated axe-core checks for every state, plus opened desktop and mobile captures for the subset sampled by risk (proposed default: 8–10 screens across all sampled families, adjustable by Aymen), preserved as separate visual evidence.
