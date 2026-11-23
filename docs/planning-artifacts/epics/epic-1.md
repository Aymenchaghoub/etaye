# Epic 1: Safe access and isolated practice work

Status: backlog. Design: Ready for design, not designed or approved. Source versions: user mission 2026-11-02; Figma 2417265104370398313; stable BMAD 6.12.4; legacy audit 83d0439920d598775d4f424229a913b125b52217. Read current authoritative documents again before refinement.

Allocation and story text refreshed on 2026-11-17 after the user decisions of that date ([decision register](../decisions-assumptions.md), UD-12 to UD-37) and aligned with the [roadmap](../roadmap.md). Sources consulted in full for this refresh: PRD, business rules, access matrix, functional specification, UX, architecture spine, data model and dictionary, API contract and README, AI specification, security/privacy, test strategy, deferred register, traceability and the dependency stories.

These are planning-level slices, not implementation authorization. Shared gate: complete reviewed required screen/interaction/print coverage, confirmed blocking assumptions, Aymen's explicit development authorization, then installed BMAD readiness check. Validation of code and visual fidelity remain separate.

## Story 1.1: Activate an intended invitation

As an invited team member or patient, I want to activate exactly the grant my invitation names so that I gain access to the intended practice or to my own record and to nothing else.

- Requirements: FR-001, FR-004. Rules: BR-004–005. Journey: J-01.
- Design: S-03; interactions I-01, I-03. Acceptance test design: AT-001.
- Dependencies: Story 10.1 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 1, Red-thread slice (web). Deferred parts: Issuing, resending and revoking invitations (issueInvitation, revokeInvitation, resendInvitation) and the invitation emails M-01 and M-02; increment 1 activates seeded pending, expired, revoked and recipient-mismatched invitations → Increment 2 (D-23).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-001-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then an unexpired intended invitation activates exactly its membership or portal link once.

**AT-001-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then expired, revoked, already-used or recipient-mismatched tokens grant nothing and show the recovery route.

**AT-001-C:** Given long content, keyboard-only navigation and a failed or loading state on S-03, when the surface is reviewed under the evidence level below, then no essential source, date, unit or action is clipped and focus, error and recovery behavior match the catalog. Evidence level SAMPLED for S-03: functional assertions and automated axe-core checks for every state, plus opened desktop and mobile captures for the subset sampled by risk (8–10 screens across all sampled families, at 2 widths; confirmed UD-37), preserved as separate visual evidence.

## Story 1.2: Sign in, recover and expire safely

As an account holder, I want to sign in, recover access and return safely after my session expires so that I reach only what my current grants allow without revealing whether an account exists.

- Requirements: FR-001. Rules: BR-003, BR-005. Journey: J-01.
- Design: S-02, S-04, S-38; interactions I-22. Acceptance test design: AT-002.
- Dependencies: Story 10.1 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 1, Red-thread slice (web).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-002-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then successful credentials resolve current grants and a safe return route.

**AT-002-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then invalid/recovery responses do not enumerate accounts; expiry preserves safe in-memory work without saving falsely.

**AT-002-C:** Given long content, keyboard-only navigation and a failed or loading state on S-02, S-04, S-38, when the surface is reviewed under the evidence level below, then no essential source, date, unit or action is clipped and focus, error and recovery behavior match the catalog. Evidence level CORE for S-02: open captures at desktop 1440×1024, tablet 1024×768 and mobile 390×844, with 320 CSS px reflow, long French content, keyboard focus and the failed, loading and restricted states of the family, compared with the approved design reference and preserved as separate visual evidence. Evidence level SAMPLED for S-04, S-38: functional assertions and automated axe-core checks for every state, plus opened desktop and mobile captures for the subset sampled by risk (8–10 screens across all sampled families, at 2 widths; confirmed UD-37), preserved as separate visual evidence.

## Story 1.3: Select and switch practice context

As an account holder with several memberships or portal links, I want to choose and switch the active practice context so that the screens and caches I see belong to exactly one practice at a time (UD-19).

- Requirements: FR-002, FR-016. Rules: BR-001–004. Journey: J-01.
- Design: S-05; interactions I-20. Acceptance test design: AT-003.
- Dependencies: Story 1.2 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 1, Red-thread slice (web).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-003-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then a multi-membership account selects only active contexts and clears prior-practice cached resources.

**AT-003-B:** Given an invalid, missing, stale or unauthorized condition, when the same action is attempted, then a guessed or suspended context and stale deep link expose no other-practice payload.

**AT-003-C:** Given long content, keyboard-only navigation and a failed or loading state on S-05, when the surface is reviewed under the evidence level below, then no essential source, date, unit or action is clipped and focus, error and recovery behavior match the catalog. Evidence level CORE for S-05: open captures at desktop 1440×1024, tablet 1024×768 and mobile 390×844, with 320 CSS px reflow, long French content, keyboard focus and the failed, loading and restricted states of the family, compared with the approved design reference and preserved as separate visual evidence.

## Story 1.4: Demonstrate server-side resource isolation

As the portfolio author, I want a visible, repeatable demonstration of server-side isolation so that a reviewer sees cross-practice, administrator and patient access attempts refused without disclosure.

- Requirements: FR-002, FR-019. Rules: BR-001–002. Journey: J-08.
- Design: S-36; interactions I-22. Acceptance test design: AT-004.
- Dependencies: Stories 10.1, 1.3 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 1, Red-thread slice (web). Deferred parts: Extending the adversarial matrix to each resource group added later (uploads, availability, audit, devices); every later story adds its resource to the matrix → Increment 2 (10.1).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-004-A:** Given valid current permission and the documented prerequisite state, when the actor completes this action, then practitioner A reads record A through the intended resource guard. The adversarial matrix is generated and published as a report by CI, and an interactive isolation journey in the demo replays the same refusals (UD-33).

**AT-004-B (UD-59):** Given an invalid, missing, stale or unauthorized condition, when the same read is attempted on the implemented record route, then inaccessible record identifiers, including record B, are denied under the documented existence-hiding policy. The 1.4 proof covers the current practitioner, administrator and patient boundaries. The corresponding source, task and export refusal cases join the same matrix when their real guarded operations ship, under [D-55](../deferred-work.md).

**AT-004-C:** Given long content, keyboard-only navigation and a failed or loading state on S-36, when the surface is reviewed under the evidence level below, then no essential source, date, unit or action is clipped and focus, error and recovery behavior match the catalog. Evidence level CORE for S-36: open captures at desktop 1440×1024, tablet 1024×768 and mobile 390×844, with 320 CSS px reflow, long French content, keyboard focus and the failed, loading and restricted states of the family, compared with the approved design reference and preserved as separate visual evidence.
