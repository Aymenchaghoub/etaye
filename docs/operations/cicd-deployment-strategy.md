# Future CI/CD and deployment strategy

Status: API and Web validation run through GitHub Actions. The promotion, deployment and rollback stages below are planned. [GitHub security](github-security.md), [delivery rules](delivery-rules.md), ADR-007 and the [test strategy](../test-artifacts/test-strategy.md) provide the repository and acceptance evidence.

## Verification gates and local commands

Updated 2026-11-26 after the [CI/CD quality audit](ci-cd-quality-audit.md#14-targeted-hardening-on-pr-73). Use the commands below to reproduce the required gates. A coverage gate can pass with zero changed instrumented lines once the source inventory, report counters and test-run marker have been verified.

| Gate | Enforced by | Fails when |
|---|---|---|
| Which jobs a change needs | `tools/ci_scope.py` (rules and hygiene test in `tools/test_ci_scope.py`) | never skips on a failed diff, a missing parent, a manual or scheduled run, or a path no rule names; the Documentation job fails when a tracked file has no rule |
| Build environment | Maven Enforcer in `apps/api/pom.xml` | Maven below 3.9.11, a JDK below 25, duplicate dependency versions, divergent transitive versions |
| API warnings | `-Xlint:all,-serial,-deprecation -Werror` on production compilation, plus a clean diagnostic compile of main and test sources under `-Pcompiler-debt` | a production warning outside the two tracked categories, any new or stale serial/deprecation diagnostic, or an incomplete diagnostic build |
| API coverage floors | JaCoCo `check` in `mvnw verify`: 90 % lines and 85 % branches overall, 90 % lines and 80 % branches in five critical packages (security, transaction, identity service, record service, record domain) | a floor is missed |
| Web lint | `corepack pnpm --filter etaye-web lint`: correctness, Hooks, accessibility and promise rules. CI checks the exact suppressed diagnostics | a new or stale diagnostic, an inline suppression, or a bulk-suppression entry which ESLint can prune |
| Web coverage floors | Vitest `thresholds` (`unit-only` and `unit+component`): 80 % lines, statements and branches and 70 % functions in logic modules (`.ts`); 95 % lines and statements, 90 % functions and 87 % branches in nine pure rule modules; plus `Web verify` combined LCOV (`unit-only` + `component` + stubbed `smoke`): >= 90 % lines and >= 85 % branches | a floor is missed |
| Tooling coverage floors | Cobertura native check in `Planning package checks` (`coverage run --rcfile=tools/pyproject.toml`): >= 90 % lines and >= 85 % branches | a floor is missed or branch counters omitted |
| Changed-line coverage | `tools/assert_changed_line_coverage.py`, pull requests only | fewer than 80 % of the executable lines a pull request adds or changes run (aggregate over the changed files: API production code, Web logic modules `.ts`); a changed file with ten or more such lines is under 50 %; a tracked in-scope source is omitted, a reported file or line is absent from the checkout, counters or the independent serialization disagree, or the report predates the test run for that revision |
| Test and coverage evidence | `tools/assert_test_reports.py`, `tools/assert_coverage_reports.py` | a report is missing, empty, malformed, skipped, below its configured line/branch floor, or a required suite is absent |
| Workflow policy | `tools/check_workflow_policy.py`, `actionlint`, `zizmor` in `Planning package checks` | an unpinned action, a dangerous trigger, an expression in a shell script, a missing timeout, a required job that can be skipped (`if`, `needs`) or a required workflow that filters pull requests, a run on push that cancels unconditionally, an orphaned required check name, a syntax or security finding |
| Pinned floors | `tools/test_coverage_policy.py` | a floor above is lowered, a gate removed, the lint baseline grows, or a workflow switches a gate off from the command line (`-Djacoco.skip`, `--passWithNoTests`, a lower `--min-percent`) without editing this test |

The changed-line measure uses coverage-tool source locations. A multiline expression may map to a single physical line. The floors were calibrated against measured coverage baselines. Keep the current floors as minimums and raise them as coverage grows. The combined Web report measures pages and components (`.tsx`) through Vitest and stubbed Chromium source maps. The changed-line gate uses the separate Vitest report for `.ts` logic. The combined Web and Python Cobertura reports enforce the overall line and branch floors listed above.

`Package verify` (`.github/workflows/package.yml`) is required on pull requests to `main`. For in-scope changes, it provisions PostgreSQL roles in throwaway containers, builds the four demo images, starts the package and runs 11 tests in `record`, `measurements` and `patients`. The tests cover selected cross-practice denials and stale-version handling. The separate outage spec, story 9.1 demonstration and full story 1.4 isolation replay remain deferred (D-56). Trivy reports all HIGH and CRITICAL findings with per-image JSON on relevant pull requests, pushes to `main`, weekly runs and manual runs. Vulnerability findings remain report-only. Failure to scan all four images fails the step.

The three other images build once. The API image starts against Maven Central, with one retry via a fixed HTTPS mirror only after an explicit Maven download HTTP 429, 502, 503 or 504. Startup and browser tests run once. The wrapper validates its pinned distribution SHA-256, and Maven uses strict checksums on both routes. Repository-supplied artifact checksums verify transfer integrity. Release build design must separately establish its artifact trust policy. Docker base tags include pinned digests, with targeted OS upgrades from signed distro repositories where the pinned bases lack a shipped fix.

Operational dependencies worth knowing when a required check turns red without a repository change: `Planning package checks` downloads the pinned Gitleaks and actionlint releases and runs `zizmor` against GitHub (a new advisory on a pinned action fails the step until Dependabot or a manual bump updates the action); `actionlint` uses the ShellCheck of the runner image; `API verify` resolves the latest Temurin 25 patch, compiles production code with `-Werror` and downloads the checksum-verified Maven distribution from Maven Central, which answered HTTP 429 to the package image build in three of seven completed runs on 2026-11-25 (rerun the job; only the package build retries and uses a mirror for its Maven downloads). A pull request that adds a tracked file no rule of `tools/ci_scope.py` names fails the hygiene test until the file is classified: add it to the scopes it affects, or to the ignored set if no verification reads it.

Run before opening a pull request (Java 25, Node 26 with Corepack, Docker):

```bash
git rev-parse HEAD > /tmp/etaye-api-coverage-started
(cd apps/api && ./mvnw -B -ntp clean verify)              # tests, Enforcer, -Werror, coverage floors
corepack pnpm install --frozen-lockfile
corepack pnpm --filter etaye-web verify                   # lint, SDK drift, types, unit tests and floors
corepack pnpm --filter etaye-web build && corepack pnpm --filter etaye-web test:smoke
python3 -m unittest discover -s tools -p 'test_*.py'      # tooling tests, run from the repository root
python3 tools/validate_docs.py && python3 tools/check_workflow_policy.py && bash tools/run_actionlint.sh
git diff --check "$(git merge-base origin/main HEAD)" HEAD
# Coverage of the lines this branch changes (API shown; the Web form is in .github/workflows/web.yml)
python3 tools/assert_changed_line_coverage.py jacoco apps/api/target/site/jacoco/jacoco.xml \
  --prefix apps/api/src/main/java/ --include 'apps/api/src/main/java/**/*.java' \
  --started /tmp/etaye-api-coverage-started --base "$(git merge-base origin/main HEAD)" --head HEAD
# Compiler debt check (run after the coverage gate, since clean removes its reports)
(cd apps/api && ./mvnw -B -ntp -Dstyle.color=never -Pcompiler-debt clean test-compile) > /tmp/etaye-compiler-debt.log 2>&1 &&
  python3 tools/check_quality_debt.py javac /tmp/etaye-compiler-debt.log --base "$(git merge-base origin/main HEAD)"
# ESLint debt check
corepack pnpm --filter etaye-web lint --format json --output-file /tmp/etaye-eslint.json &&
  python3 tools/check_quality_debt.py eslint /tmp/etaye-eslint.json --base "$(git merge-base origin/main HEAD)"
# Delivery package, when infrastructure or either application changed
export COMPOSE_PROJECT_NAME=etaye-demo-ci ETAYE_DEMO_WEB_PORT=4179
bash tools/build_demo_package.sh && bash tools/run_package_smoke.sh && bash tools/run_image_scan.sh
bash infra/local/verify-init-roles.sh
```

## Coverage and dependency checks

Aymen authorized CI quality improvements on 2026-11-22. `API verify` generates JaCoCo XML and HTML when the API suite runs. Run `./mvnw -B -ntp clean verify` in `apps/api` to reproduce the report locally under `target/site/jacoco/`. GitHub retains the `api-coverage` artifact.

`Web verify` keeps the Chromium smoke suite and generates a separate Vitest unit coverage report. Run `corepack pnpm --filter etaye-web test:coverage` from the repository root. `apps/web/reports/unit-coverage/lcov.info` includes eligible handwritten TypeScript sources even when unit tests never import them. Generated SDK and test files are excluded. GitHub retains `web-unit-coverage`. These results measure unit coverage. Browser journey evidence is recorded separately.

`Dependency review` checks new dependency advisories exposed by GitHub's dependency graph at high or critical severity. When the pnpm graph changes, the job also runs `corepack pnpm audit --audit-level high` against the complete locked graph. Aymen authorized promoting it to the eighth required check in ruleset `26390376` on 2026-11-26 after PR #74 passed on `ee5257e` and `tools/check_workflow_policy.py` / `.github/workflows/dependency-review.yml` were aligned. Maven build-resolved transitive coverage depends on dependency submission or a dedicated scanner.

`API verify` also publishes the adversarial isolation matrix of story 1.4 (UD-33, UD-59 C1) as the `api-isolation-matrix` artifact (`isolation-matrix.json` and `.md`, retained 30 days) and appends it to the job summary. `tools/assert_isolation_matrix.py` fails the job for a zero-case report, a missing, skipped or failing mandatory case in `apps/api/src/test/resources/isolation/matrix-cases.json`, refusals whose responses can be told apart, an identifier or request material in the report, or a missing upload. Source, task and export rows stay `PENDING` until their routes ship (D-55). The scripted browser replay of the matrix (`apps/web/demo-e2e/isolation.spec.ts`) runs locally with the story and joins CI at M6.

The baselines became the floors above. SonarQube Cloud imports Java XML (`apps/api/target/site/jacoco/jacoco.xml`), Web LCOV (`apps/web/reports/combined-coverage/lcov.info`) and Python Cobertura XML (`reports/tooling-coverage.xml`) into three independent monorepo projects (`aymenchaghoub_etaye-api`, `aymenchaghoub_etaye-web`, `aymenchaghoub_etaye-tooling`) via `.github/workflows/sonarqube.yml` using `SONAR_TOKEN`. See [SonarQube Cloud integration](sonarqube-cloud.md) for Free plan constraints, combined Web coverage and the optional observation plan. [SonarQube Cloud setup](https://docs.sonarsource.com/sonarqube-cloud/getting-started/github)

## Enforced and evaluated anti-regression thresholds

Native build/CI gates (JaCoCo, LCOV, Cobertura) and SonarQube Cloud denominators are deliberately kept distinct: SonarQube uses its own executable-line and condition inventory (for example excluding unexecuted declaration overhead or re-computing conditions across `.ts`/`.tsx` and Python), so native floors are calibrated against the exact native measurements recorded at `efb29e6` rather than comparing incompatible metrics across tools.

| Area | Native / CI gate | Measured baseline (`efb29e6`) | Evaluation vs target & anti-fragility rationale |
| --- | --- | --- | --- |
| API bundle (JaCoCo XML) | **`90 %` lines / `85 %` branches** enforced in `apps/api/pom.xml` (`check-coverage`) | `93.48 %` lines (`2166/2317`), `86.67 %` branches (`780/900`) | Meets `90 % / 85 %` with headroom (`+3.48` / `+1.67` pts). SonarQube API measures `91.6 %` overall (`93.5 %` L / `86.7 %` B). |
| API critical packages (JaCoCo `PACKAGE`) | **`90 %` lines / `80 %` branches** across the 5 security/transaction/identity/records packages | `identity.service` is `91.76 %` L / `83.33 %` B (`45/54`); the other 4 packages exceed `96.7 %` L / `86.3 %` B | `0.85` per critical package would be artificially fragile on `identity.service`'s 54-branch denominator; `0.80` tightens protection cleanly (`0.75 → 0.80`). |
| Web combined (LCOV union) | **`90 %` lines / `85 %` branches** enforced in `web.yml` via `assert_coverage_reports.py lcov` on `combined-coverage/lcov.info` | `97.80 %` lines (`2360/2413`), `88.09 %` branches (`2382/2704`), `42` files (`.ts` + `23` `.tsx`) | Reliably measures the exact union of unit, component and stubbed Chromium journeys (SonarQube Web reports `92.9 %` overall / `97.5 %` L / `88.1 %` B). |
| Web logic `.ts` (Vitest) | **`80 %` lines / statements / branches, `70 %` functions** in `vitest.config.ts` (`src/**/*.ts`), keeping 9 pure rule modules at `95 / 95 / 90 / 87` | `unit-only` `.ts`: `86.99 %` L / `87.88 %` B / `72.8 %` F (`unit+component` `.ts`: `99.02 %` L / `95.96 %` B) | Applied in both `test:unit:coverage` and `test:coverage`; setting `90 %` here would break `test:unit:coverage` on `.ts` helpers exercised via `.tsx`/Playwright. |
| Web standalone `.tsx` | No standalone Vitest numeric floor | `0 %` in `unit-only`; `78.34 %` L / `67.54 %` B in `component-only`; `97.05 %` L / `85.26 %` B in `combined-coverage` | A standalone `.tsx` Vitest floor or `85 %` `.tsx`-only branch gate (`+0.26` pt margin on JSX ASTs) would be fragile; `.tsx` is protected cleanly through `combined-coverage`. |
| Python tooling (Cobertura) | **`90 %` lines / `85 %` branches** enforced in `documentation.yml` via `assert_coverage_reports.py cobertura` on `reports/tooling-coverage.xml` | `93.34 %` lines (`3067/3286`), `88.34 %` branches (`1371/1552`) | Uses `coverage==7.17.5` already locked in `tools/requirements.txt` (SonarQube Tooling reports `91.2 %` overall / `93.3 %` L / `86.8 %` B). |
| New code (`pull_request`) | Keep **`80 %` aggregate / `50 %` per-file (>=10 lines)** in `api.yml` & `web.yml` (`AGENTS.md`); track **`90 %` SonarQube `new_coverage`** observationally | `100.0 %` API / `92.3 %` Web / `93.2 %` Tooling on `b4e8ff9` main period | Raising the hard native PR gate to `90 %` would be brittle on small defensive diffs; `.tsx` changed lines stay outside native `assert_changed_line_coverage.py` without an independent AST line map. |

## Validation and promotion graph

1. Untrusted PR validation: checkout exact SHA with read-only token; pinned tool/action versions; dependency lockfile install; secret detection (Gitleaks candidate), lint/type checks/compile; dependency/SBOM/container scan (Trivy candidate); no deployment secrets. No `pull_request_target` execution of contributor code with secrets.
2. Backend suites: domain business-rule tests, Spring HTTP/integration tests against real PostgreSQL Testcontainers; verify nonzero expected unit/integration counts, reports and failures. JaCoCo XML produced and checked, not only file existence.
3. Database: clean installation, checksum validation, relevant previous-release→candidate upgrade paths, integrity/constraint tests under runtime role and compatibility window. Never substitute H2 for PostgreSQL isolation/concurrency evidence.
4. Frontend: strict TypeScript, lint, production build, Vitest/Testing Library interaction tests, coverage LCOV; fail when selected required suites do not execute. No test omission hidden by a green aggregate.
5. SonarQube Cloud CI-based API, Web and Tooling projects analyze the exact commit with JaCoCo, combined LCOV and Cobertura. Wait for each eligible project gate. Missing evidence, timeout or a failed gate fails the scanner job. All three checks remain optional after the ten-PR observation by owner decision.
6. Isolated real-stack Playwright: unique database/schema/store/network/project namespace, immutable candidate API image + validated frontend, migrated/seeded two-practice fixture. Real session/auth/API/PostgreSQL; deterministic AI only at external boundary. Exercise practitioner, patient, admin, publication/source and denial paths plus booking/edit/failure states. Fail if required project/journey suite is empty or skipped unexpectedly; preserve traces/screenshots/logs safely. Cleanup only the isolated environment.
7. Required review: silent failures, edge cases and AC compliance; inspect actual diff/test assertions; compare running UI with approved Figma at matching viewports and A4 samples. Build/test pass does not prove visual match.
8. Trusted release build/promote: artifacts that underwent validation are signed/checksummed/identified by commit+digest and frontend hash; SBOM/provenance/report manifest links every result. If image is built before real-stack tests, that tested image is the release image. Do not rebuild during deploy. Fork artifacts are not trusted for promotion without trusted revalidation.
9. Deploy selected environment using least-privilege repository/environment credentials unavailable to untrusted contributions; serialize promotions and migration runs, explicit environment separation. Apply compatible migration once, deploy exact digest/static artifact, record previous compatible version. No configuration/secret value in artifact logs.
10. Post-deploy smoke: liveness/readiness, synthetic login/context, permitted record read and wrong-practice denial, current published content and safe download, DB migration version, deterministic task health. Verify deployed SHA/digest against release manifest. Failed smoke halts promotion and triggers documented recovery.

## GHCR candidate rehearsal

The [candidate workflow](../../.github/workflows/release.yml) is manual and runs only for protected `main`. It uses a read-only repository token and pinned GitHub-owned actions. Its evidence upload contains dry-run reports. Publication and signing require a separately reviewed activation change and owner approval.

Run the same rehearsal from a clean checkout on Linux amd64 with Docker Engine 28 or later, Buildx, Compose, Node 26, Corepack, locked pnpm dependencies and Chromium from the repository's Playwright version. Use a new evidence directory outside the checkout. The script creates a disposable loopback Compose project, builds it once and archives the actual container images for the real-stack tests. It verifies the OCI manifest plus compressed and uncompressed layer contents, runs Trivy and generates CycloneDX reports from the same archives with one database snapshot. The evidence covers API, Web, Flyway, the project's local demo PostgreSQL image and its digest-pinned upstream base. The upstream base is an external dependency and the local demo PostgreSQL image is a test fixture for a managed-database deployment.

```bash
corepack pnpm install --frozen-lockfile
corepack pnpm --filter etaye-web exec playwright install chromium
candidate_evidence=$(mktemp -d)/candidate
bash tools/prepare_release_candidate.sh "$candidate_evidence" "$(git rev-parse HEAD)"
python3 tools/release_artifacts.py verify "$candidate_evidence" --git-sha "$(git rev-parse HEAD)"
```

The manifest records the full source SHA, image and archive checksums, scanner database evidence, validated Playwright results and an unsigned provenance state. The Docker local image ID identifies the engine's object. The OCI platform manifest digest identifies the archive subject that can be transferred without rebuilding. If the engine exposes no runtime manifest descriptor, the verified config digest and uncompressed layer checksums establish runtime content identity. That fallback supplies no runtime manifest observation. A published registry digest exists only after reading the exact response manifest back from the registry and checking its bytes. There is no prior known-good published release to record in the rehearsal. Archive checksums detect changes against the trusted manifest from the same run. They provide authenticity only when a trusted signer attests that manifest.

### Future activation

1. Obtain publication authorization, confirm package visibility, and establish a separate owner-controlled publishing environment with main-only deployment rules and an independent approval if the plan supports it. Keep production deployment as a separate decision. Fail closed if the protection is unavailable. A same-repository manual input alone cannot authorize the job.
2. Preserve the exact archives and manifest from the trusted protected-main build. Grant only `contents: read`, `packages: write`, `id-token: write` and `attestations: write` to the isolated, approved publishing job. Validate the run ID, exact source SHA, checked evidence and candidate artifact digests before it copies the images. PR workflow artifacts and contributor branches are ineligible. Review scanner results against the release policy before any write.
3. For the API, Web and Flyway images, plan a staging tag scoped to the full commit SHA and preserve OCI digests with `skopeo copy --preserve-digests` from the verified OCI archives. Authenticate using a short-lived repository token. Fetch the registry manifest by digest and compare its raw hash to the archive digest. Reject any registry conversion or mismatch. Track both PostgreSQL images in the release evidence, including the external base index and platform digest.
4. Use full-SHA-pinned GitHub-owned attestation actions for SLSA build provenance and CycloneDX SBOMs. Bind each attestation to the staged image's exact repository and manifest digest, and attest the final manifest and report checksums. Record the trusted build workflow identity and source SHA. Verify provenance with `gh attestation verify` restricted to the expected repository, signer workflow and committing SHA. Repeat verification for the SBOM predicate. Check signatures, predicate types and image identities before creating any release record or consumer-facing tag. [GitHub artifact attestations](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations)
5. Write a separate published manifest only after all verification passes. Record immutable registry references, verified attestation references and the previous known-good release manifest/digests when one exists. A deployer consumes only that signed, verified record. A failed verification leaves the current release unchanged and any staged tags ineligible for deployment. [Skopeo copy semantics](https://github.com/containers/skopeo/blob/main/docs/skopeo-copy.1.md)

## Meaningful blocking results

Proposed baseline: zero failing required tests, nonzero expected suites, no unexplained skip, no unresolved relevant secret/critical exploitable dependency finding, all applicable Sonar gates pass and human review of detected security hotspots, no broken migration/contract, complete critical journey evidence, no unresolved blocking visual mismatch. Coverage target new business code≥80% line and meaningful branch cases, not an excuse to omit risk-based tests; exact gates reviewed when app exists. Account plan may limit custom Sonar gates, so enforce additional thresholds explicitly in CI rather than falsely claiming a paid setting is available.

The initial public-repository main ruleset is active and recorded in [GitHub security](github-security.md). Its nine required contexts enforce the planning, CodeQL, dependency review, application (`API verify`, `Web verify`) and full-stack delivery package (`Package verify`) suites. The [observation record](ci-observation.md#dependency-review-and-delivery-scope) keeps all three SonarQube jobs optional after completing the 10-PR observation.

## Production artifact boundary

Planning/design evidence remains outside runtime packaging. Future backend/container contexts and frontend build inputs must allowlist application code, locked runtime dependencies and explicitly promoted assets; exclude docs/, the raw Figma snapshot/captures, tools/, _bmad/, .agents/, .claude/, Git metadata and personal settings. Promote only reviewed logo/token/font assets needed at runtime, with source hash and license attribution; do not copy the entire design evidence folder into a public web root. Inspect the actual built image/static artifact manifest before release (AT-036-D). This is a future build requirement, not a container configuration created now.

## Mobile artifacts (increments 3 and 4)

The installable web app is part of the validated web artifact. The Expo Android app is built in increment 3, as a local development build or a package file, from the same validated commit. An iOS simulator build follows in increment 4 and only on a Mac (Linux cannot produce it); no iOS device build exists without the Apple program (ADR-010). Signing keys and store credentials never enter the repository or the logs, and no Apple or Google developer account is created or paid without Aymen's decision (D-15, not planned, UD-39). Native smoke evidence is recorded only for runs that actually happened (an emulator is not a real device).

## Rollback and recovery

Retain previous known-good image/static digest and release manifest. Prefer expand/contract migrations that permit old/new app versions during rollback window. On failure: stop promotion, inspect safe smoke/log evidence, choose compatible app rollback or forward fix. Never automatically reverse a destructive migration or edit an applied checksum. If data restore is necessary, restore into isolated database/store first, verify lineage/attachments, then switch deliberately; account for new writes since backup and document loss. Rehearse recovery against synthetic state. Proposed RPO/RTO are NFR targets, not measured guarantees.

Deployment, registry and store credentials require explicit setup authorization and minimum permissions. Keep promotion separate from untrusted PR validation.
