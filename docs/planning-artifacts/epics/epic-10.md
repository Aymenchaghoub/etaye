# Epic 10: Platform foundation and multi-client delivery

Status: backlog. Design: operational stories, no new screen families; the surfaces they carry are covered by epics 1–9. Source versions: user decisions of 2026-11-17 (UD-12 to UD-28); stable BMAD 6.12.4; ADR-008 and ADR-009 (Proposed).

Allocation and story text refreshed on 2026-11-17 after the user decisions of that date ([decision register](../decisions-assumptions.md), UD-12 to UD-28) and aligned with the [roadmap](../roadmap.md). Sources consulted in full for this refresh: PRD, business rules, access matrix, functional specification, UX, architecture spine, data model and dictionary, API contract and README, AI specification, security/privacy, test strategy, deferred register, traceability and the dependency stories.

These are planning-level slices, not implementation authorization. Shared gate: Aymen's explicit development authorization, the core design families (UD-16), accepted ADR-008 and ADR-009 for stories 10.2 and 10.4 to 10.8, then the installed BMAD readiness check. Validation of code and visual fidelity remain separate.

## Story 10.1: Walking skeleton and secure request path

As the maintainer, I want a walking skeleton with the secure request path so that every later story inherits authorization, practice context, audit and outbox the same way.

- Requirements: FR-002, FR-020. Rules: BR-001, BR-017–018. Journey: J-08.
- Design: Operational evidence. Acceptance test design: AT-038.
- Dependencies: No story dependency. Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 1, Red-thread slice (web).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md), [ADR-008](../../decisions/ADR-008-multi-client-access.md), [ADR-009](../../decisions/ADR-009-api-compatibility.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-038-A:** Given a clean checkout and Aymen's development authorization, when the skeleton is built and run, then one request path resolves session, membership or portal link, capability and resource ownership, opens a transaction with practice context, and commits domain change, audit and outbox together.

**AT-038-B:** Given absent context, a pooled connection reused after an error or a missing capability, when the same path runs, then access is denied and no audit or outbox row is committed for the failed attempt.

**AT-038-C:** Not applicable: operational evidence only (reports, logs and recorded measurements); no visual review is required.

## Story 10.2: Generated client, token bridge and multi-client foundations

As the maintainer, I want a generated typed client, a design-token bridge and multi-client foundations so that the web app, the installed web app and the native shell share one contract and one visual source (UD-12).

- Requirements: FR-016, FR-020. Rules: BR-002, BR-013, BR-016. Journey: J-06, J-08.
- Design: Operational evidence. Acceptance test design: AT-039.
- Dependencies: Story 10.1 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 1, Red-thread slice (web).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md), [ADR-008](../../decisions/ADR-008-multi-client-access.md), [ADR-009](../../decisions/ADR-009-api-compatibility.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-039-A:** Given the cleaned OpenAPI contract, when the web client is built, then every call goes through the generated client, design tokens are generated from the verified token file, and the foundations exist: transport abstraction behind a flag, empty CORS allowlist, no-op push port, client-version endpoint and no inline-PDF dependency.

**AT-039-B:** Given contract drift, an unlisted origin or a push payload containing clinical text, when the checks run, then the build fails or the request is refused.

**AT-039-C:** Not applicable: operational evidence only (reports, logs and recorded measurements); no visual review is required.

## Story 10.3: Hosting benchmark and decision

As the maintainer, I want a measured hosting benchmark and a recorded decision so that the demo's reliability and cost are chosen on evidence (UD-25).

- Requirements: FR-019, FR-020. Rules: BR-018. Journey: J-08.
- Design: Operational evidence. Acceptance test design: AT-040.
- Dependencies: Stories 7.4, 9.1, 9.3 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 1, Red-thread slice (web).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md), [ADR-008](../../decisions/ADR-008-multi-client-access.md), [ADR-009](../../decisions/ADR-009-api-compatibility.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-040-A:** Given the increment 1 application, when the benchmark runs, then peak memory, startup, cold start and PDF memory are recorded with commit, host and fixture, and Aymen records the hosting decision with its cost.

**AT-040-B:** Given an unmeasured claim or a paid option, when the decision is prepared, then it is refused or marked To verify, and no paid service is activated without Aymen's explicit consent.

**AT-040-C:** Not applicable: operational evidence only (reports, logs and recorded measurements); no visual review is required.

## Story 10.4: Installable web app (PWA)

As a patient or practitioner on a phone, I want to install the web app so that I can open it like an app without a store.

- Requirements: FR-016. Rules: BR-002, BR-013. Journey: J-06.
- Design: Operational evidence. Acceptance test design: AT-041.
- Dependencies: Stories 10.2, 10.3 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 3, Mobile (installable web app, then Android shell).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md), [ADR-008](../../decisions/ADR-008-multi-client-access.md), [ADR-009](../../decisions/ADR-009-api-compatibility.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-041-A:** Given a stable HTTPS origin, when the app is opened on Android Chrome and iOS Safari, then it installs in standalone mode with manifest and icons and the service worker caches only the application shell.

**AT-041-B:** Given a clinical response, an offline moment or an incompatible old shell, when the app runs, then no clinical body is ever cached, offline shows an honest unavailable state and the minimum-version check forces a refresh.

**AT-041-C:** Not applicable: operational evidence only (reports, logs and recorded measurements); no visual review is required.

## Story 10.5: Native session transport and device registry

As a native-app user, I want a session transport that works outside the browser cookie model, with device management, so that sign-in is revocable server-side and no clinical data is kept on the device.

- Requirements: FR-001, FR-016. Rules: BR-003, BR-005. Journey: J-01, J-06.
- Design: Operational evidence. Acceptance test design: AT-042.
- Dependencies: Stories 1.2, 10.2 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 3, Mobile (installable web app, then Android shell).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md), [ADR-008](../../decisions/ADR-008-multi-client-access.md), [ADR-009](../../decisions/ADR-009-api-compatibility.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-042-A:** Given an allowed native origin, when a client signs in, then the same opaque server-side session is carried in an Authorization header and kept only in platform secure storage, and the account can list and revoke its devices.

**AT-042-B:** Given logout, suspension, password reset or an unlisted origin, when the next request arrives, then access is refused immediately and cookie and header transports cannot be mixed.

**AT-042-C:** Not applicable: operational evidence only (reports, logs and recorded measurements); no visual review is required.

## Story 10.6: Native Android shell

As a patient on Android, I want a native shell around the same web app so that I can install and open it from the launcher with native sharing.

- Requirements: FR-016. Rules: BR-002, BR-013, BR-016. Journey: J-06.
- Design: Operational evidence. Acceptance test design: AT-043.
- Dependencies: Stories 10.4, 10.5 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 3, Mobile (installable web app, then Android shell).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md), [ADR-008](../../decisions/ADR-008-multi-client-access.md), [ADR-009](../../decisions/ADR-009-api-compatibility.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-043-A:** Given the bundled web assets, when the shell runs on an Android emulator, then the patient signs in, reads a published explanation and shares or downloads its PDF.

**AT-043-B:** Given navigation outside the allowlist, a stored clinical payload or an unavailable backend, when the shell is inspected, then no remote navigation occurs, no clinical data is at rest and the unavailable state is honest.

**AT-043-C:** Not applicable: operational evidence only (reports, logs and recorded measurements); no visual review is required.

## Story 10.7: Content-free push notifications

As a patient, I want a content-free notification when something new is available so that I can return to the portal without clinical text on my lock screen.

- Requirements: FR-009, FR-015. Rules: BR-006, BR-013, BR-016. Journey: J-05, J-06.
- Design: Operational evidence. Acceptance test design: AT-044.
- Dependencies: Stories 10.5, 10.6, 8.3 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 3, Mobile (installable web app, then Android shell).
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md), [ADR-008](../../decisions/ADR-008-multi-client-access.md), [ADR-009](../../decisions/ADR-009-api-compatibility.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-044-A:** Given a committed publication or appointment event and a registered device, when delivery runs, then one content-free notification is delivered per device after re-authorizing the destination.

**AT-044-B:** Given a withdrawn publication, a revoked device or a retry, when delivery runs, then nothing is sent for revoked access, no silent push is used and the payload carries no clinical text.

**AT-044-C:** Not applicable: operational evidence only (reports, logs and recorded measurements); no visual review is required.

## Story 10.8: iOS build and store-readiness gate

As the maintainer, I want an iOS build and a store-readiness decision gate so that costs, policies and review risks are accepted explicitly before any store submission.

- Requirements: FR-016, FR-020. Rules: BR-016, BR-018. Journey: J-08.
- Design: Operational evidence. Acceptance test design: AT-045.
- Dependencies: Story 10.6 (see [roadmap](../roadmap.md)). Gates: Aymen's development authorization and the core design families (UD-16).
- Delivery: Increment 4, Release engineering, iOS, stores and quality extensions.
- Read before refinement: [PRD](../prd/prd.md), [rules](../business-rules.md), [access](../access-matrix.md), [architecture](../architecture/ARCHITECTURE-SPINE.md), [data](../data-model.md), [contracts](../../contracts/openapi.yaml), [screen catalog](../../design/screen-catalog.md), [test strategy](../../test-artifacts/test-strategy.md), [ADR-008](../../decisions/ADR-008-multi-client-access.md), [ADR-009](../../decisions/ADR-009-api-compatibility.md).
- Assumptions: apply relevant A-01–A-12 from the decision register; never treat them as verified facts. Record any changed source/version and resulting AC change in the future story.

**AT-045-A:** Given Aymen's decision on the Apple program and its cost, when the iOS build runs, then it compiles and passes a simulator smoke test with no signing material in the repository.

**AT-045-B:** Given no explicit store decision or no privacy policy, when submission is considered, then it is blocked and the costs, review policies and risks are presented to Aymen first.

**AT-045-C:** Not applicable: operational evidence only (reports, logs and recorded measurements); no visual review is required.
