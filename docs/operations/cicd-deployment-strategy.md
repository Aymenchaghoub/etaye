# Future CI/CD and deployment strategy

Status: API and Web validation run through GitHub Actions. The promotion, deployment and rollback stages below are planned. [GitHub security](github-security.md), [delivery rules](delivery-rules.md), ADR-007 and the [test strategy](../test-artifacts/test-strategy.md) provide the repository and acceptance evidence.

## Coverage and dependency checks

Aymen authorized CI quality improvements on 2026-11-22. `API verify` generates JaCoCo XML and HTML when the API suite runs. Run `./mvnw -B -ntp clean verify` in `apps/api` to reproduce the report locally under `target/site/jacoco/`. GitHub retains the `api-coverage` artifact.

`Web verify` keeps the Chromium smoke suite and generates a separate Vitest unit coverage report. Run `corepack pnpm --filter etaye-web test:coverage` from the repository root. `apps/web/reports/unit-coverage/lcov.info` includes eligible handwritten TypeScript sources even when unit tests never import them. Generated SDK and test files are excluded. GitHub retains `web-unit-coverage`. These results measure unit coverage. Browser journey evidence is recorded separately.

`Dependency review` checks new dependency advisories exposed by GitHub's dependency graph at high or critical severity. When the pnpm graph changes, the job also runs `corepack pnpm audit --audit-level high` against the complete locked graph. Branch protection can require `Dependency review` after a successful run is observed on the repository. Maven build-resolved transitive coverage depends on dependency submission or a dedicated scanner.

`API verify` also publishes the adversarial isolation matrix of story 1.4 (UD-33, UD-59 C1) as the `api-isolation-matrix` artifact (`isolation-matrix.json` and `.md`, retained 30 days) and appends it to the job summary. `tools/assert_isolation_matrix.py` fails the job for a zero-case report, a missing, skipped or failing mandatory case in `apps/api/src/test/resources/isolation/matrix-cases.json`, refusals whose responses can be told apart, an identifier or request material in the report, or a missing upload. Source, task and export rows stay `PENDING` until their routes ship (D-55). The scripted browser replay of the matrix (`apps/web/demo-e2e/isolation.spec.ts`) runs locally with the story and joins CI at M6.

The first release of these checks publishes baselines. SonarQube Cloud imports Java XML and Web LCOV into separate projects once the owner links those projects and supplies analysis credentials. Store an analysis-only token in GitHub repository Actions secrets as `SONAR_TOKEN`. The proposed threshold is 80% on new or changed code after verifying the report inputs and the exact quality gate in CI. [SonarQube Cloud setup](https://docs.sonarsource.com/sonarqube-cloud/getting-started/github)

## Validation and promotion graph

1. Untrusted PR validation: checkout exact SHA with read-only token; pinned tool/action versions; dependency lockfile install; secret detection (Gitleaks candidate), lint/type checks/compile; dependency/SBOM/container scan (Trivy candidate); no deployment secrets. No `pull_request_target` execution of contributor code with secrets.
2. Backend suites: domain business-rule tests, Spring HTTP/integration tests against real PostgreSQL Testcontainers; verify nonzero expected unit/integration counts, reports and failures. JaCoCo XML produced and checked, not only file existence.
3. Database: clean installation, checksum validation, relevant previous-release→candidate upgrade paths, integrity/constraint tests under runtime role and compatibility window. Never substitute H2 for PostgreSQL isolation/concurrency evidence.
4. Frontend: strict TypeScript, lint, production build, Vitest/Testing Library interaction tests, coverage LCOV; fail when selected required suites do not execute. No test omission hidden by a green aggregate.
5. SonarQube Cloud CI-based Java and TypeScript projects: analyze the exact commit and actual JaCoCo/LCOV paths; wait for both quality gates (`sonar.qualitygate.wait=true` or explicit exact-analysis check). Scanner upload success alone is insufficient. Missing analysis/report, timeout or red gate blocks delivery. Free main/PR-to-main limits and account LOC verified before setup.
6. Isolated real-stack Playwright: unique database/schema/store/network/project namespace, immutable candidate API image + validated frontend, migrated/seeded two-practice fixture. Real session/auth/API/PostgreSQL; deterministic AI only at external boundary. Exercise practitioner, patient, admin, publication/source and denial paths plus booking/edit/failure states. Fail if required project/journey suite is empty or skipped unexpectedly; preserve traces/screenshots/logs safely. Cleanup only the isolated environment.
7. Required review: silent failures, edge cases and AC compliance; inspect actual diff/test assertions; compare running UI with approved Figma at matching viewports and A4 samples. Build/test pass does not prove visual match.
8. Trusted release build/promote: artifacts that underwent validation are signed/checksummed/identified by commit+digest and frontend hash; SBOM/provenance/report manifest links every result. If image is built before real-stack tests, that tested image is the release image. Do not rebuild during deploy. Fork artifacts are not trusted for promotion without trusted revalidation.
9. Deploy selected environment using least-privilege repository/environment credentials unavailable to untrusted contributions; serialize promotions and migration runs, explicit environment separation. Apply compatible migration once, deploy exact digest/static artifact, record previous compatible version. No configuration/secret value in artifact logs.
10. Post-deploy smoke: liveness/readiness, synthetic login/context, permitted record read and wrong-practice denial, current published content and safe download, DB migration version, deterministic task health. Verify deployed SHA/digest against release manifest. Failed smoke halts promotion and triggers documented recovery.

## Meaningful blocking results

Proposed baseline: zero failing required tests, nonzero expected suites, no unexplained skip, no unresolved relevant secret/critical exploitable dependency finding, both Sonar gates pass and human review of security hotspots, no broken migration/contract, complete critical journey evidence, no unresolved blocking visual mismatch. Coverage target new business code≥80% line and meaningful branch cases, not an excuse to omit risk-based tests; exact gates reviewed when app exists. Account plan may limit custom Sonar gates, so enforce additional thresholds explicitly in CI rather than falsely claiming a paid setting is available.

The initial public-repository main ruleset is active and recorded in [GitHub security](github-security.md). Extend its actual required checks to the implemented application suites and quality results before feature delivery. Revalidate capability if account plan or visibility changes; an operational convention alone is not an enforced branch rule.

## Production artifact boundary

Planning/design evidence remains outside runtime packaging. Future backend/container contexts and frontend build inputs must allowlist application code, locked runtime dependencies and explicitly promoted assets; exclude docs/, the raw Figma snapshot/captures, tools/, _bmad/, .agents/, .claude/, Git metadata and personal settings. Promote only reviewed logo/token/font assets needed at runtime, with source hash and license attribution; do not copy the entire design evidence folder into a public web root. Inspect the actual built image/static artifact manifest before release (AT-036-D). This is a future build requirement, not a container configuration created now.

## Mobile artifacts (increments 3 and 4)

The installable web app is part of the validated web artifact. The Expo Android app is built in increment 3, as a local development build or a package file, from the same validated commit. An iOS simulator build follows in increment 4 and only on a Mac (Linux cannot produce it); no iOS device build exists without the Apple program (ADR-010). Signing keys and store credentials never enter the repository or the logs, and no Apple or Google developer account is created or paid without Aymen's decision (D-15, not planned, UD-39). Native smoke evidence is recorded only for runs that actually happened (an emulator is not a real device).

## Rollback and recovery

Retain previous known-good image/static digest and release manifest. Prefer expand/contract migrations that permit old/new app versions during rollback window. On failure: stop promotion, inspect safe smoke/log evidence, choose compatible app rollback or forward fix. Never automatically reverse a destructive migration or edit an applied checksum. If data restore is necessary, restore into isolated database/store first, verify lineage/attachments, then switch deliberately; account for new writes since backup and document loss. Rehearse recovery against synthetic state. Proposed RPO/RTO are NFR targets, not measured guarantees.

Deployment, registry and store credentials require explicit setup authorization and minimum permissions. Keep promotion separate from untrusted PR validation.
