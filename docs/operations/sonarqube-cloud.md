# SonarQube Cloud integration

Updated: 2026-11-26. Owner: Aymen Chaghoub. Organization: [`aymenchaghoub`](https://sonarcloud.io/organizations/aymenchaghoub/projects) (Free plan).

SonarQube Cloud complements Étaye's existing native and GitHub controls (JaCoCo, Vitest, ESLint, `-Werror`, ArchUnit, CodeQL, Gitleaks, Dependency Review, Trivy) with cross-file maintainability, reliability, duplication and security-hotspot tracking across the Java, TypeScript/React and Python codebases. It does **not** replace or relax any existing gate.

## 1. Free plan capabilities and limitations

Verified against the official SonarQube Cloud documentation and the live `sonarcloud.io` organization/quality-gate API on 2026-11-26:

| Capability | Free plan status | Impact on Étaye |
|---|---|---|
| Public repository analysis | Included, unlimited projects and LOC | All three projects (`aymenchaghoub_etaye-api`, `aymenchaghoub_etaye-web`, `aymenchaghoub_etaye-tooling`) are `public`; private LOC caps (50k) do not apply. |
| Monorepo projects | Included via CI-based analysis | Automatic Analysis cannot be used for monorepos or coverage import; CI runs each project with its own key and working directory. |
| Branch analysis | `main` branch only | Feature/topic branches are analyzed as pull requests targeting `main`, not as standalone branches. |
| Pull request analysis & decoration | Supported for PRs targeting `main` | Observed on PR #82: SonarQube Cloud posts per-project PR comments and check runs (`[<Name>] SonarCloud Code Analysis`). |
| Quality gate | Read-only built-in `Sonar way` (`id=9`) | Custom quality gates and custom quality profiles require Team, Enterprise or OSS plans and are **rejected** here to stay on Free. |
| AI / Advanced SAST / SCA / Webhooks / SOT | Not available on Free | AI CodeFix, Advanced SAST, Sonar SCA, Webhooks and Scoped Organization Tokens are disabled/not used. |

### Conflict between read-only `Sonar way` and unmeasured `.tsx` files

The built-in `Sonar way` gate enforces six read-only conditions on New Code: Reliability A (`new_reliability_rating <= 1`), Security A (`new_security_rating <= 1`), Maintainability A (`new_maintainability_rating <= 1`), Coverage `>= 80%` (`new_coverage >= 80`), Duplication `<= 3%` (`new_duplicated_lines_density <= 3`), and Security Hotspots Reviewed `100%`.

In Étaye, Vitest measures `.ts` logic/rule modules (`71%` / `95%` floors), while `.tsx` pages and components are driven by Playwright Chromium smoke and real-stack specs that collect no V8 coverage (D-70). Removing `.tsx` files from `sonar.sources` or adding `**/*.tsx` to `sonar.coverage.exclusions` would hide UI code from static analysis or artificially inflate SonarQube Web coverage above the truthful ~`17–19%` baseline. Therefore, `.tsx` files remain in both quality analysis and coverage reporting, and `SonarQube Web` stays **non-required** on the Free plan: a future UI story touching `.tsx` files may turn `SonarQube Web` red on `new_coverage` without blocking merge, while `Web verify` remains the authoritative required check.

## 2. Monorepo projects, scopes and scanners

| Project key | Display name | Scope & exclusions | Coverage input | Pinned scanner |
|---|---|---|---|---|
| `aymenchaghoub_etaye-api` | `Étaye — API` | `apps/api` (`pom.xml`: Maven default `src/main/java` + `src/test/java` + compiled `target/classes`; no security/record/domain exclusions) | `apps/api/target/site/jacoco/jacoco.xml` | `org.sonarsource.scanner.maven:sonar-maven-plugin:5.9.0.7382` via `./mvnw` |
| `aymenchaghoub_etaye-web` | `Étaye — Web` | `apps/web/sonar-project.properties`: `sonar.sources=src`; tests in `**/*.test.ts,**/*.test.tsx,tests/**,e2e/**,demo-e2e/**`; excludes `src/api/generated/**`, build/report/cache dirs | `apps/web/reports/unit-coverage/lcov.info` | `@sonar/scan@5.1.0` (`sonar-scanner-npm`, locked in `pnpm-lock.yaml`) |
| `aymenchaghoub_etaye-tooling` | `Étaye — Tooling` | `tools/pyproject.toml`: `sonar.sources=tools,design/corrections`, `sonar.inclusions=tools/**/*.py,design/corrections/**/*.py` (Python only, aligned with `.github/codeql-config.yml`); tests `tools/test_*.py`; excludes fixtures, caches and dependency manifests | `reports/tooling-coverage.xml` (`coverage==7.17.5`, Cobertura XML) | `pysonar==1.11.0.6012` (hash-locked in `tools/sonar-requirements.txt`) |

## 3. Architecture decision

Three integration options were compared:

1. **Extend `API verify`, `Web verify`, and `Planning package checks` directly:** Rejected because those jobs are required status checks in ruleset `26390376`. Coupling them to `sonarcloud.io` and `SONAR_TOKEN` violates required-check independence, exposes a repository secret in required PR workflows, and breaks fork/Dependabot PR behavior.
2. **Dedicated `.github/workflows/sonarqube.yml` workflow (Selected):** Isolates `SONAR_TOKEN` to one non-required workflow (`pull_request` on `branches: [main]`, `push` on `branches: [main]`, `workflow_dispatch`), keeps all seven required checks unchanged and secret-free, uses `tools/ci_scope.py` (`sonar-api`, `sonar-web`, `sonar-tooling`) in an unprivileged `preflight` job, checks out `fetch-depth: 0` inside each active job, generates and verifies fresh revision-matched coverage on the exact checkout, and enforces `-Dsonar.qualitygate.wait=true` (`300s` timeout). Because `SonarQube Web` runs only Vitest (`~2s`) instead of Playwright (`~3m`), it finishes faster than `Web verify`.
3. **Separate artifact-consuming jobs inside existing workflows / `workflow_run`:** Rejected because placing Sonar jobs in `api.yml`/`web.yml`/`documentation.yml` spreads `SONAR_TOKEN` across all three required workflows (and `api.yml`/`web.yml` lack `branches: [main]` on `pull_request`), while `workflow_run` is forbidden by `tools/check_workflow_policy.py` and runs outside the PR check context.

## 4. Security, secret handling and untrusted PRs

- **Allowlist compliance:** Repository `selected-actions` allows only GitHub-owned actions and `astral-sh/setup-uv@*`, with SHA pinning required. By using official Maven, npm (`@sonar/scan`) and PyPI (`pysonar`) distributions pinned by version and lockfile/hashes, **zero** third-party GitHub Actions were added and the allowlist remains unchanged.
- **Narrow workflow policy exception:** `tools/check_workflow_policy.py` permits only `sonarqube.yml` to read `SONAR_TOKEN` on `pull_request` (covering dot, bracket and `toJSON(secrets)` forms), and enforces that `pull_request` targets `[main]`, no other context reference is read, neither workflow-level nor job-level `env` exposes secrets, `preflight` stays secret-free, and every secret-reading job gates strictly on `needs.preflight.outputs.trusted == 'true'` without `||`/`always()`.
- **Forks and Dependabot PRs:** `preflight` (which has no secret access) sets `trusted=false` when `pull_request` comes from a fork or missing/external head repository (`head.repo.fork != false` or `head.repo.full_name != github.repository`), when `github.event.pull_request.user.login == 'dependabot[bot]'` or `github.actor == 'dependabot[bot]'`, or on unsupported events/`workflow_dispatch` off `refs/heads/main`. Downstream Sonar jobs are skipped by GitHub (neutral `skipped`, never fake green), and `preflight` logs the explicit reason in `$GITHUB_STEP_SUMMARY`.
- **Missing credentials vs intentional skip:** On trusted runs, each Sonar job explicitly verifies `secrets.SONAR_TOKEN != ''` before installing dependencies and fails (`exit 1`) if the secret is missing. `SONAR_TOKEN` is passed exclusively via `env:` on the final scan step (never via CLI flags, shell interpolation, artifacts or logs).

## 5. Coverage validation and initial observed baseline (PR #82)

Before invoking each scanner, `tools/assert_coverage_reports.py` checks measured source rows. JaCoCo and LCOV use the same readers as the changed-line gate. The readers reconcile JaCoCo instruction, line and branch summaries and LCOV line and branch totals. The Cobertura reader rejects unsafe XML declarations, duplicate file and line records, and aggregate line counts that disagree with the rows. Fresh generation in each CI job ties the evidence to its checkout. See the [engineering quality baseline](quality-baseline.md) for scanner-to-native comparisons.

Observed on [PR #82](https://github.com/Aymenchaghoub/etaye/pull/82) ([workflow run](https://github.com/Aymenchaghoub/etaye/actions/runs/45800748673), `2026-11-26`, prior to `main` baseline push):

| Project | Quality Gate (PR #82) | Imported line / branch coverage | Native baseline comparison | Scan step / total job |
|---|---|---|---|---|
| `aymenchaghoub_etaye-api` | `OK` (`0` new issues) | `89.5%` lines (`2064/2307`), `78.8%` branches (`717/910`), `86.4%` Sonar combined | Exact match with JaCoCo (`89.47%` lines, `78.8%` branches) | `45s` / `3m00s` |
| `aymenchaghoub_etaye-web` | `OK` (`0` new issues) | `17.1%` lines (`478/2790`), `16.1%` branches (`434/2697`), `16.6%` Sonar combined | Vitest LCOV reports `18.78%` (`447/2380` lines, `434/2697` branches); Sonar counts additional executable lines across unmeasured `.tsx`/`.ts` files | `36s` / `1m01s` |
| `aymenchaghoub_etaye-tooling` | `OK` (`93.2%` new coverage on 59 lines, `0` new issues) | `51.1%` lines (`1550/3033`) | `coverage.py` reports `52.70%` (`1550/2941` statements); same `1,550` covered lines against Sonar's executable-line count | `43s` / `59s` |

Full `main` branch project totals and historical issue triage will populate on the first `push` to `main` after PR #82 merges.

## 6. Failure investigation, token rotation and observation

- **Distinguishing failures:** `preflight` summary + GitHub status distinguish intentional skips (`skipped` jobs) from missing credentials (`Require SONAR_TOKEN secret` step failure), test/coverage integrity errors (`mvnw verify`, `test:coverage`, `coverage run`, `assert_coverage_reports.py`), scanner/network errors, and quality gate failures (`sonar.qualitygate.wait=true` non-zero exit).
- **Token rotation procedure:** On the Free plan (Scoped Organization Tokens require Team/Enterprise), generate a new personal token in SonarQube Cloud (`My account > Access tokens > Personal Tokens`) for an account with `Execute Analysis` permission on the project (note that personal tokens inherit that user's other permissions), update the GitHub repository secret `SONAR_TOKEN` (`Settings > Secrets and variables > Actions`), rerun `SonarQube Cloud` on a trusted PR or `main`, and revoke the previous token in SonarQube Cloud.
- **Observation period:** Keep all three `SonarQube` checks non-required for at least 10 trusted PRs and `main` merges. Only `SonarQube API` and `SonarQube Tooling` are candidates for future required-check promotion unless D-70 (Playwright V8 coverage) or a custom quality gate resolves the `.tsx` `new_coverage` conflict on `SonarQube Web`.

## 7. Rollback

Remove `.github/workflows/sonarqube.yml`, `apps/web/sonar-project.properties`, `tools/pyproject.toml`, `tools/sonar-requirements.*`, the `sonar-*` properties/pluginManagement in `apps/api/pom.xml`, `@sonar/scan` from `package.json`/`pnpm-lock.yaml`, `coverage` from `tools/requirements.*`, the Cobertura parser and test additions in `tools/assert_coverage_reports.py`/`tools/test_assert_coverage_reports.py`, `.gitignore` Sonar entries, and revert the `sonar-*` scopes, workflow exception and test expectations in `tools/ci_scope.py`, `tools/test_ci_scope.py`, `tools/check_workflow_policy.py`, `tools/test_check_workflow_policy.py`, `tools/validate_docs.py` and `tools/test_validate_docs_scope.py`.

## 8. Review and hardening log (PR #82)

- **CodeQL finding (`py/clear-text-logging-sensitive-data` on `1f88ca7`):** Fixed in `tools/check_workflow_policy.py` by removing sensitive-named policy variables flowing into stdout diagnostics while preserving exact policy failure reporting.
- **Independent read-only review findings (0 Critical, 0 High, 1 Medium, 4 Low, 2 Info):**
  - *Medium — non-Python files under `aymenchaghoub_etaye-tooling`:* Added `inclusions = "tools/**/*.py,design/corrections/**/*.py"` in `tools/pyproject.toml` and aligned `tools/ci_scope.py` to `tools/**/*.py` and `design/corrections/**/*.py`.
  - *Low — `preflight` fork/null-repo edge case:* Tightened `.github/workflows/sonarqube.yml` to fail closed if `head.repo.fork != false` or `head.repo.full_name != github.repository` and check `github.ref != 'refs/heads/main'` on `workflow_dispatch`.
  - *Low — `check_workflow_policy.py` secret parsing & `||`/`always()` bypass:* Extended detection to `secrets['...']`, `toJSON(secrets)`, `preflight` job references, `GITHUB_TOKEN`-tolerant env checks, and strict `&&` gating with unit tests.
  - *Low — Cobertura duplicate line numbers & Vitest JSON output:* Added duplicate `<line number>` rejection in `tools/assert_coverage_reports.py` (`cobertura_counts`) and removed the unused `--reporter=json` override in `SonarQube Web`.
  - *Low — over-broad Sonar scopes:* Removed unnecessary `sonar-api`/`sonar-web` triggers on `tools/assert_isolation_matrix.py` and `tools/generate_web_tokens.py` in `tools/ci_scope.py`.
  - *Info — `codecov`/Web baseline note & `dependabot.yml` scope:* Documented intentionally non-required `SonarQube Web` (`17.1%` baseline due to `.tsx` Playwright coverage D-70) and existing Dependabot ecosystem coverage (`pip` intentionally absent while `tools/requirements.txt` and `tools/sonar-requirements.txt` stay hash-pinned).
- **CodeRabbit review (`606609b1-b771-4aaf-b8f0-120c7e53bccb` on `1f88ca7`, 4 findings):** (1) Strict `&&` trust gating in `tools/check_workflow_policy.py` (fixed in `c28108d`), (2) Free-plan Personal Token rotation wording (`My account > Access tokens > Personal Tokens`), (3) complete test-file rollback list including `tools/test_validate_docs_scope.py`, and (4) adding `apps/web/sonar-project.properties` to `test_authorized_scope_rejects_unlisted_tracked_entries`. All 4 findings addressed and threads resolved.
