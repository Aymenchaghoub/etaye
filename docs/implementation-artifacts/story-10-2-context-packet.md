# Story 10.2 — Generated client, token bridge and multi-client foundations

Status: Prepared; the authorization is UD-45 (2026-11-20) and covers story 10.2 only. This packet itself authorizes nothing. Date 2026-11-20.

The [phase 0 plan](story-10-2-phase-0.md) records current code and CI evidence and the choices awaiting approval.

## 1. Objective and traceability

Give the web, installed web and future Expo clients one typed API contract and one visual token source (UD-12, UD-38). Story 10.2 is in increment 1, milestone M1 (ADR-011 point 2). It depends on 10.1 and traces to FR-016, FR-020, BR-002, BR-013, BR-016, J-06, J-08 and AD-11. Evidence is operational. [Epic 10](../planning-artifacts/epics/epic-10.md#story-102-generated-client-token-bridge-and-multi-client-foundations)

## 2. Preconditions

- **User decision.** UD-45 grants a named exception to the UD-16 CORE gate for this screenless story. Verify the decision row and `10.2` in `[etaye] authorized_stories` before coding. The global flag stays false. UD-41 covers 10.1 and UD-28 governs any other authorization. ADR-008, ADR-009 and ADR-010 are accepted. Resolve the effective BMAD configuration and run the installed readiness check before implementation. [Decision register](../planning-artifacts/decisions-assumptions.md), [delivery rules](../operations/delivery-rules.md)
- **Verified observation.** Story 10.1 merged at `bfc4706`. The readiness page calls `GET /api/v1/probes/readiness` with raw `fetch`. The OpenAPI 3.1 contract declares 96 operations, including CSRF and the two patient/record carrier operations. It omits the readiness probe and the planned `getApiMeta`. Roadmap CF-2 calls for adding `getApiMeta` before implementation. [10.1 record](story-10-1-implementation-record.md), [contract](../contracts/openapi.yaml), [roadmap](../planning-artifacts/roadmap.md)
- **Verified observation.** The API POM declares Spring Boot 4.1.2, Java 21, ArchUnit 1.5.1 and PostgreSQL Testcontainers. The wrapper selects Maven 3.9.11. API CI uses Temurin 21. Web CI uses Node 22 and Corepack with pnpm 12.21.0. The web manifest pins React 19.3.1, TypeScript 5.8.2, Vite 8.3.8 and Playwright 1.63.0. The workspace already matches `apps/*` and `packages/*`. Web CI runs Chromium smoke tests with a stubbed probe. The real outage spec is separate.

## 3. Scope and deliverables

- Deliver a generated TypeScript client, the flagged transport boundary and a contract-drift check. Keep the browser path same-origin with its existing cookie and CSRF semantics (ADR-008 points 1 and 3 as amended, ADR-004, BR-002). Document supported runtime operations separately from planned contract operations.
- Deliver the empty exact browser-origin allowlist, the no-op push port and its payload check, the version endpoint, and Playwright axe-core checks. Leave PDF handling independent of an inline viewer. Preserve server-side publication and download guards (BR-013, BR-016).
- **User decision.** The sole acceptance extension is ADR-014 point 3 (UD-43 to UD-45, D-50). The bridge reads custom `tokens.json` and also emits CSS for `clair` and `sombre`. Freeze the golden reference after a one-time regeneration that applies four deltas to the received `tokens.css`. Add the 15 roles, per-theme `color-scheme`, the per-theme scrim `rgba(15, 16, 18, 0.48)` and `--duree-trace` (1200 ms). ADR-014 point 3 promotes the ADR-012 motion token when the bridge exists. Keep the received assets intact. The bridge can serve non-CSS outputs. [Design system §§4, 13, 15](../design/design-system.md#4-tokens), [type roles](../design/typography-closure.md)
- **[Proposal]** Keep the client and token output in `apps/web`. Put the Python token generator and its tests in `tools/`. Commit the generated client so CI can regenerate with a pinned tool and fail on differences, including added or removed generated files. Commit the frozen CSS fixture and regenerate the web CSS during the build. Run the golden comparison in CI without rewriting the fixture. Include the contract and generator inputs in the applicable workflow triggers.

## 4. Acceptance designs

**AT-039-A:** Given the cleaned OpenAPI contract, when the web client is built, then every call goes through the generated client, design tokens are generated from the verified token file by a bridge whose output is not tied to CSS (the native token module is added in story 10.6), and the foundations exist: transport abstraction behind a flag, empty CORS allowlist, no-op push port, client-version endpoint and no inline-PDF dependency. axe-core runs in the Playwright journeys (UD-35).

**AT-039-B:** Given contract drift, an unlisted origin or a push payload containing clinical text, when the checks run, then the build fails or the request is refused.

**AT-039-C:** Not applicable: operational evidence only (reports, logs and recorded measurements); no visual review is required.

## 5. Behaviour checks

Each row becomes a test. Record synthetic fixture, commit, environment, exact command, counts and results. Use isolated real HTTP and PostgreSQL for API acceptance evidence. Keep stubbed browser smoke results distinct. [Test strategy](../test-artifacts/test-strategy.md)

| Check | Assertion | Source |
|---|---|---|
| C1 Client | The web build routes every API call, including readiness, through the generated client. Extra response fields and unknown enum values do not break it. | AT-039-A, ADR-009 choices 1 and 5 |
| C2 Drift | Regeneration matches the committed client. A stale client after a contract change that affects generation fails the build. | AT-039-B, ADR-009 choice 5 |
| C3 Transport | The flag gates the transport abstraction. Browser requests retain cookie and CSRF behavior and never mix cookie and bearer credentials. | AT-039-A, ADR-008 points 1 and 3, ADR-010 point 3 |
| C4 CORS | The default allowlist is empty. Cross-origin requests from an unlisted browser origin are refused, including preflight. Each configured entry has a test. | AT-039-A/B, ADR-008 point 4 as amended |
| C5 Push | A content-free payload reaches the no-op port without sending. A synthetic clinical-text payload is refused even with the no-op adapter. | AT-039-A/B, ADR-008 point 7 |
| C6 Version | `X-Client-Version` conveys semantic version and client kind. `GET /api/v1/meta` returns API, minimum and latest client versions. | AT-039-A, ADR-009 choice 2 |
| C7 Token bridge | The bridge reads verified token data and keeps it usable independently of CSS. Both emitted themes match the frozen reference including the decided deltas. | AT-039-A, ADR-014 points 1–4, D-50 |
| C8 PDF | The web build has no inline PDF viewer dependency. | AT-039-A, BR-016, D-28 |
| C9 Accessibility | Playwright journeys execute axe-core with assertions and retain the results. Empty suites cannot pass the evidence check. | AT-039-A, UD-35, NFR-05, test strategy §Evidence and quality gates |

## 6. Phases, tasks and review

1. **[Proposal] Plan.** Recheck the authorization, dependency, contract gaps and token inputs. Confirm the generator, flag and evidence plan against the open points below.
2. **[Proposal] Contract and foundations.** Land the contract preparation first. Update `docs/contracts/openapi.yaml`, `docs/contracts/README.md` and `docs/contracts/operation-index.json` together. In `docs/planning-artifacts/roadmap-assignments.json`, change `getApiMeta` from `planned` to `contract` and assign the readiness probe to increment 1 with status `contract`. Regenerate the tables in `docs/planning-artifacts/roadmap.md` from those assignments. The validator checks the operation index and roadmap operation assignments against the contract. Generate the client and migrate the probe call. Implement metadata, CORS and the push boundary. Run C1–C6 and C8. [Roadmap source](../planning-artifacts/roadmap-assignments.json), [roadmap](../planning-artifacts/roadmap.md), [tools/validate_docs.py](../../tools/validate_docs.py)
3. **[Proposal] Tokens and evidence.** Build the bridge, review the one-time baseline diff and freeze it. Run C7 and C9. Wire drift and report checks into CI. D-40 leaves API and Web informational on GitHub.
4. **Review.** Use a short-lived feature branch and PR to main. Review silent failures, edge cases and acceptance criteria. Record exact-head evidence and limits in the implementation record and continuation. AT-039-C requires operational evidence. If existing visible output changes, follow the separate delivery rule for Aymen's review of the running result.

## 7. Risks and open points

- **To verify.** Pick and pin the OpenAPI generator after proving OpenAPI 3.1 support, unknown enum handling and reproducibility. Confirm the contract addition for the probe, preserving its 200/503 bodies and the existing timeout and failure behavior. Confirm the metadata schema, header format and version values. The update UI belongs to DF-9 and the consuming client stories.
- **Open point.** The protected-route `426 CLIENT_TOO_OLD` guard has no story owner yet. AT-039 does not require it. Decide in the phase 0 plan whether story 10.2 or a later story owns the guard. [ADR-009, choice 2](../decisions/ADR-009-api-compatibility.md), [AT-039](../planning-artifacts/epics/epic-10.md#story-102-generated-client-token-bridge-and-multi-client-foundations)
- **To verify.** Set the flag name, default and disabled behavior, the exact push payload schema and refusal response, and the Playwright journey set and axe-core version. Check that a stub or skipped scan cannot stand in for acceptance evidence.
- **To verify.** The received JSON copies differ. Use the input linked by design-system §3 and check it against the Figma-resolved values and received CSS. Record offline evidence limits under §14. Reconcile the 15 added roles when Figma permits. Resolve the optional layout group and provisional role recount (ADR-014 point 3, D-50). Keep the golden diff limited to the four decided deltas. Apply component-level normalisations with their consumers.
- **[ASSUMPTION]** ADR-009's compatibility window is two previous released versions and at least 90 days. It awaits Aymen's confirmation. It adds no acceptance criterion here.

## 8. Consulted sources

Last commit dates from `git log -1 --format=%cs -- <path>`. UD-45 and its configuration were read in the parallel working-tree change. Re-read the current sources before implementation.

- **Policy and context.** 2026-11-20 for `AGENTS.md`, `docs/README.md`, `docs/continuation.md`. 2026-11-19 for `_bmad/custom/config.toml`, `docs/operations/delivery-rules.md`.
- **Story and delivery.** 2026-11-19 for `docs/planning-artifacts/epics/epic-10.md`, `docs/planning-artifacts/roadmap.md`. 2026-11-17 for `docs/planning-artifacts/epics/epic-2.md`, `docs/planning-artifacts/epics/epic-8.md`. 2026-11-20 for `docs/planning-artifacts/decisions-assumptions.md`, `docs/planning-artifacts/deferred-work.md`.
- **Dependency.** 2026-11-19 for `docs/implementation-artifacts/story-10-1-context-packet.md`. 2026-11-20 for `docs/implementation-artifacts/story-10-1-implementation-record.md`.
- **Product and design.** 2026-11-19 for `docs/planning-artifacts/prd/prd.md`, `docs/planning-artifacts/access-matrix.md`, `docs/planning-artifacts/functional-specification.md`. 2026-11-17 for `docs/planning-artifacts/business-rules.md`. 2026-11-18 for `docs/planning-artifacts/ux/DESIGN.md`. 2026-11-02 for `docs/planning-artifacts/ux/EXPERIENCE.md`. 2026-11-20 for `docs/design/screen-catalog.md`.
- **Architecture and safety.** 2026-11-19 for `docs/planning-artifacts/architecture/ARCHITECTURE-SPINE.md`, `docs/planning-artifacts/security-privacy.md`, `docs/planning-artifacts/non-functional-requirements.md`. 2026-11-02 for `docs/planning-artifacts/data-model.md`. 2026-11-17 for `docs/planning-artifacts/data-dictionary.md`, `docs/planning-artifacts/ai-specification.md`.
- **ADRs.** 2026-11-17 for `docs/decisions/ADR-004-authentication-sessions.md`. 2026-11-19 for `docs/decisions/ADR-008-multi-client-access.md`, `docs/decisions/ADR-009-api-compatibility.md`, `docs/decisions/ADR-011-delivery-order-and-milestones.md`. 2026-11-20 for `docs/decisions/ADR-010-native-client-react-native-expo.md`, `docs/decisions/ADR-012-functional-motion.md`, `docs/decisions/ADR-013-web-component-behaviour-layer.md`, `docs/decisions/ADR-014-design-system-closure.md`.
- **Contract and tests.** 2026-11-17 for `docs/contracts/README.md`, `docs/contracts/openapi.yaml`, `docs/contracts/operation-index.json`. 2026-11-19 for `docs/test-artifacts/test-strategy.md`, `docs/test-artifacts/acceptance-scenarios.md`. 2026-11-20 for `tools/assert_test_reports.py`, `tools/validate_docs.py`.
- **Token evidence.** 2026-11-20 for `docs/design/design-system.md`, `docs/design/typography-closure.md`. 2026-11-02 for `docs/design/figma/resolved-tokens.json`. 2026-11-03 for `design/received/2026-11-03/design-system/tokens.json`, `design/received/2026-11-03/export/etaye-design-system/tokens/tokens.json`, `design/received/2026-11-03/export/etaye-design-system/tokens/tokens.css`. 2026-11-18 for `tools/design_system_reference.py`.
- **API baseline.** 2026-11-20 for `apps/api/pom.xml`, `apps/api/.mvn/wrapper/maven-wrapper.properties`, `apps/api/src/main/java/dev/aymen/etaye/infrastructure/security/SecurityConfig.java`, `apps/api/src/main/java/dev/aymen/etaye/infrastructure/web/ReadinessProbeController.java`, `apps/api/src/main/java/dev/aymen/etaye/infrastructure/web/CsrfController.java`, `apps/api/src/main/java/dev/aymen/etaye/records/web/PatientController.java`.
- **Web baseline.** 2026-11-20 for `package.json`, `pnpm-workspace.yaml`, `apps/web/package.json`, `apps/web/src/App.tsx`, `apps/web/vite.config.ts`, `apps/web/tsconfig.json`, `apps/web/playwright.config.ts`, `apps/web/playwright.real.config.ts`, `apps/web/tests/stubbed-probe.spec.ts`, `apps/web/e2e/outage.spec.ts`.
- **Workflows.** 2026-11-20 for `.github/workflows/api.yml`, `.github/workflows/web.yml`. 2026-11-19 for `.github/workflows/documentation.yml`, `.github/workflows/architecture-pages.yml`. 2026-11-17 for `.github/workflows/codeql.yml`.

## Out of scope

- Native app, typed native token module and shared package extraction (10.6). Native bearer issuance, secure storage and device registry (10.5).
- Hosting benchmark (10.3), PWA manifest, shell caching and refresh (10.4), push delivery and provider credentials (10.7), iOS build and distribution (10.8).
- Product screens and interactions (their owning family stories). Theme preference and storage (S-35, 2.4). Client update screen (DF-9, consuming client stories 10.4 and 10.6). PDF exports (8.1 and 8.2) and inline PDF preview (D-28).
- Component CSS overlay corrections (D-53, first consuming UI story), React Aria spike (D-49, before first UI authorization), required GitHub check changes (D-40, Aymen).
