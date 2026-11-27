# CI observation after PR #73

Observation started on 2026-11-26 when [PR #73](https://github.com/Aymenchaghoub/etaye/pull/73) landed as `38d3366361a243404403188705f34473bae684d9`. Aymen owns the decision to require new checks. [CI audit and image analysis](ci-cd-quality-audit.md#14-targeted-hardening-on-pr-73)

## Package verify

At least ten distinct pull requests opened after PR #73 must execute the heavy verification before the owner reviews whether to make `Package verify` required. Count a PR once, using the run for its latest assessed head and recording the run link. A successful observation requires the role verifier, four-image build/start, real-stack journeys and Trivy scan to have executed. Also count and diagnose a heavy verification failure, even if it prevents later steps from running (see PR #79). Exclude path-only skips, cancelled runs, reruns of a counted PR, the PR #73 pilot, pushes to `main`, and scheduled or manual runs from the count. Keep the status optional throughout observation. [Package workflow](https://github.com/Aymenchaghoub/etaye/actions/workflows/package.yml)

For each PR, use the run in its Checks tab. Record the head, check conclusion and whether the four steps actually executed. A later head or a changed rerun outcome updates the existing row. Diagnose and document any unexplained failure before proposing a required status.

```bash
gh pr checks <pr-number>
gh run view <package-run-id> --json headSha,event,conclusion,url,jobs --jq '
  {headSha,event,conclusion,url,
   steps:[.jobs[] | select(.name == "Package verify") | .steps[]
          | select(.name | test("role provisioning|Build and start|real-stack journeys|Scan the built images"))
          | {name,status,conclusion}]}'
```

**Current count** 14 assessed PRs against the 10-PR threshold: 13 completed all four heavy steps successfully and one diagnosed application build failure. The threshold is met. The first `main` push after PR #73 passed all four heavy steps and provides baseline evidence separately from the PR count. [Main baseline run](https://github.com/Aymenchaghoub/etaye/actions/runs/45794380012)

<!-- observation-rows -->
| PR | Assessed head and Package run | Role / build / browser / scan | Outcome and counting decision |
| --- | --- | --- | --- |
| [PR #74](https://github.com/Aymenchaghoub/etaye/pull/74) (`apache-maven` 3.10.2) | `ee5257e` · [45794551194](https://github.com/Aymenchaghoub/etaye/actions/runs/45794551194) | pass / pass / pass / pass | **1/10.** Package verify passed (all four heavy steps). Separate `API verify` failed on Maven 3.10.2 HTTP 401 warnings from Flyway metadata repositories. Closed/deferred without relaxing quality gates. |
| [PR #75](https://github.com/Aymenchaghoub/etaye/pull/75) (`pyyaml` 6.0.4 in `/tools`) | `fc5985f` · [45811257116](https://github.com/Aymenchaghoub/etaye/actions/runs/45811257116) | skip / skip / skip / skip | **Excluded.** Path-only skip; heavy steps did not execute. Merged as `a40ebb0`. |
| [PR #76](https://github.com/Aymenchaghoub/etaye/pull/76) (`flyway/flyway` 13.13.0) | `e4bf7c3` · [45809990626](https://github.com/Aymenchaghoub/etaye/actions/runs/45809990626) | pass / pass / pass / pass | **2/10.** Passed all four steps (`migrate` Trivy `16 HIGH / 0 CRITICAL`, `10` distinct). Local PostgreSQL 18 compatibility verified on exact `13.13.0` (`12.11.0 -> 13.13.0` upgrade + fresh DB + forced `etaye_runtime` RLS). Merged as `513b53c`. |
| [PR #77](https://github.com/Aymenchaghoub/etaye/pull/77) (`nginx` 1.31.0-alpine-slim) | `883c698` · [45794628786](https://github.com/Aymenchaghoub/etaye/actions/runs/45794628786) | pass / pass / pass / pass | **3/10.** Passed all four steps. `web` Trivy count increased from 0 to 11 HIGH / 0 CRITICAL (8 distinct); closed/rejected due to security regression. |
| [PR #78](https://github.com/Aymenchaghoub/etaye/pull/78) (`web-tooling` group) | `df07027` · [45812376154](https://github.com/Aymenchaghoub/etaye/actions/runs/45812376154) | pass / pass / pass / pass | **4/10.** Passed all four heavy steps on final rebased head. Merged as `0cba3f5`. |
| [PR #79](https://github.com/Aymenchaghoub/etaye/pull/79) (`typescript` 7.0.2) | `0a93ce4` · [45794733206](https://github.com/Aymenchaghoub/etaye/actions/runs/45794733206) | pass / fail / skip / skip | **5/10.** Counted failure: web build failed inside `@hey-api/openapi-ts@0.99.0` (`TypeError: Cannot read properties of undefined (reading 'AnyKeyword')` with `typescript@7.0.2`). `Web verify` also failed. Closed and `7.x` major ignored until toolchain support. |
| [PR #80](https://github.com/Aymenchaghoub/etaye/pull/80) (`@types/node` 26.6.6) | `404bcbc` · [45813437617](https://github.com/Aymenchaghoub/etaye/actions/runs/45813437617) | pass / pass / pass / pass | **6/10.** Passed all four heavy steps on final rebased head (`@types/node` aligns with ADR-002 / Node 26). Merged as `ee037bb`. |
| [PR #81](https://github.com/Aymenchaghoub/etaye/pull/81) (`docs/ci-observation`) | `6bd5669` · [45808930268](https://github.com/Aymenchaghoub/etaye/actions/runs/45808930268) | skip / skip / skip / skip | **Excluded.** Path-only skip; heavy steps did not execute. Merged as `52e2337`. |
| [PR #82](https://github.com/Aymenchaghoub/etaye/pull/82) (SonarQube Cloud) | `f02d16e` · [45805181905](https://github.com/Aymenchaghoub/etaye/actions/runs/45805181905) | pass / pass / pass / pass | **7/10.** Passed all four heavy steps (role verifier, four-image build, 11 real-stack browser tests, four Trivy scans). Merged as `52d672e`. |
| [PR #84](https://github.com/Aymenchaghoub/etaye/pull/84) (coverage report integrity) | `193cf90` · [45892171237](https://github.com/Aymenchaghoub/etaye/actions/runs/45892171237) | pass / pass / pass / pass | **8/10.** All four steps executed. |
| [PR #85](https://github.com/Aymenchaghoub/etaye/pull/85) (API hardening) | `9cf30a0` · [45894517250](https://github.com/Aymenchaghoub/etaye/actions/runs/45894517250) | pass / pass / pass / pass | **9/10.** All four steps executed. |
| [PR #86](https://github.com/Aymenchaghoub/etaye/pull/86) (proxy headers) | `0c11cdf` · [45891616410](https://github.com/Aymenchaghoub/etaye/actions/runs/45891616410) | pass / pass / pass / pass | **10/10.** All four steps executed. |
| [PR #87](https://github.com/Aymenchaghoub/etaye/pull/87) (tooling and CI) | `9d6c38b` · [45890705704](https://github.com/Aymenchaghoub/etaye/actions/runs/45890705704) | pass / pass / pass / pass | **11/10.** All four steps executed. |
| [PR #88](https://github.com/Aymenchaghoub/etaye/pull/88) (Web tests and coverage) | `8d93ae5` · [45893643404](https://github.com/Aymenchaghoub/etaye/actions/runs/45893643404) | pass / pass / pass / pass | **12/10.** All four steps executed. |
| [PR #89](https://github.com/Aymenchaghoub/etaye/pull/89) (operations documentation) | `861b6f2` · [45895113332](https://github.com/Aymenchaghoub/etaye/actions/runs/45895113332) | skip / skip / skip / skip | **Excluded.** The successful wrapper classified a documentation-only change. |
| [PR #90](https://github.com/Aymenchaghoub/etaye/pull/90) (managed PostgreSQL compatibility) | `8889360` · [45969202995](https://github.com/Aymenchaghoub/etaye/actions/runs/45969202995) | pass / pass / pass / pass | **13/10.** Passed all four heavy steps (`fix/managed-postgres-compatibility`). Merged as `f402352`. |
| [PR #92](https://github.com/Aymenchaghoub/etaye/pull/92) (GHCR release readiness) | `5cd9d87` · [45969893665](https://github.com/Aymenchaghoub/etaye/actions/runs/45969893665) | pass / pass / pass / pass | **14/10.** Passed all four heavy steps (`ci/ghcr-release-readiness`). Merged as `1640268`. |
<!-- /observation-rows -->

## SonarQube Cloud

SonarQube Cloud (Free plan, [`aymenchaghoub`](https://sonarcloud.io/organizations/aymenchaghoub/projects)) landed via [PR #82](https://github.com/Aymenchaghoub/etaye/pull/82) (`52d672e`) and stays optional across at least ten eligible authenticated PRs (excluding Dependabot/fork PRs skipped by `preflight` and path-only skips). [Operations guide](sonarqube-cloud.md)

**Current eligible PR count** 8/10. Count one authenticated pull request if any in-scope scanner executes. A path-skipped project adds no project execution, and an entirely skipped PR adds no observation.

| PR | Assessed head and run | API / Web / Tooling scan steps | Decision |
| --- | --- | --- | --- |
| #82 | `f02d16e` · [45805182021](https://github.com/Aymenchaghoub/etaye/actions/runs/45805182021) | pass / pass / pass | Counted. |
| #81 | `6bd5669` · [45808930297](https://github.com/Aymenchaghoub/etaye/actions/runs/45808930297) | skip / skip / pass | Counted. |
| #84 | `193cf90` · [45892171265](https://github.com/Aymenchaghoub/etaye/actions/runs/45892171265) | pass / pass / pass | Counted. |
| #85 | `9cf30a0` · [45894517170](https://github.com/Aymenchaghoub/etaye/actions/runs/45894517170) | pass / skip / skip | Counted. |
| #86 | `0c11cdf` · [45891616358](https://github.com/Aymenchaghoub/etaye/actions/runs/45891616358) | skip / skip / skip | Excluded on path scope. |
| #87 | `9d6c38b` · [45890705667](https://github.com/Aymenchaghoub/etaye/actions/runs/45890705667) | pass / pass / pass | Counted. |
| #88 | `8d93ae5` · [45893643329](https://github.com/Aymenchaghoub/etaye/actions/runs/45893643329) | pass / pass / pass | Counted. |
| #89 | `861b6f2` · [45895113442](https://github.com/Aymenchaghoub/etaye/actions/runs/45895113442) | skip / skip / skip | Excluded on path scope. |
| #90 | `8889360` · [45969203270](https://github.com/Aymenchaghoub/etaye/actions/runs/45969203270) | pass / skip / pass | Counted (merged as `f402352`). |
| #92 | `5cd9d87` · [45969893621](https://github.com/Aymenchaghoub/etaye/actions/runs/45969893621) | pass / pass / pass | Counted (merged as `1640268`). |

Project execution counts within the eight observations are API 7, Web 5 and Tooling 7. All executed scan steps passed. The [exact integrated main analysis](https://github.com/Aymenchaghoub/etaye/actions/runs/45895446497) passed all three scanners and contributes no PR observation. [Integrated metrics](quality-baseline.md#verified-integrated-main-at-b4e8ff9)

## Upstream image vulnerabilities

Use the four Trivy JSON reports in the run's `package-evidence` artifact. For a suspected regression, compare base and head with the same Trivy and database snapshot. Record the image digest, scanner/database dates, severity and any published fixed version. Identify each observation by image service, advisory ID and package path so duplicate occurrences stay visible. Preserve material findings and the run link here or in the audit before the artifact's 14-day retention expires.

| Watch item | Baseline and next check |
| --- | --- |
| Demo PostgreSQL 18.7 on Debian trixie | 48 distinct HIGH/CRITICAL IDs (`97` HIGH / `2` CRITICAL findings). Track Debian libxml2 CVE-2026-6653 and the other unfixed distro findings, plus the `gosu` Go toolchain notices including CVE-2025-68121. Re-scan when an upstream digest or corrected `gosu` build ships. Run role provisioning and the real stack after updating. |
| Flyway `13.13.0` (upgraded from `12.11.0`) | `16` HIGH / `0` CRITICAL (`10` distinct IDs; down from `50` HIGH / `2` CRITICAL / `22` distinct on `12.11.0`, eliminating both CRITICALs including Netty CVE-2026-75595), verified on `ee037bb` ([45814104447](https://github.com/Aymenchaghoub/etaye/actions/runs/45814104447)). Remaining HIGHs track upstream `jackson`, `guava`, `lz4-java`, `mssql-jdbc` and `httpcore5` updates in future Flyway digests. |
| API and Web | Zero HIGH/CRITICAL findings on `nginx:1.30.8-alpine-slim` and API image (`ee037bb`). Rejected [PR #77](https://github.com/Aymenchaghoub/etaye/pull/77) (`1.31.0-alpine-slim`) regressed to `11` HIGH / `0` CRITICAL fixable findings across `libcrypto3`, `libssl3`, `musl`, `pcre2` and `zlib`. |

Review weekly and on affected Dependabot PRs. Record the upstream source, current availability, exposure, owner and next trigger for a remaining finding. Trivy stays informational during observation. After enough comparable runs, propose a blocking policy for newly introduced fixable HIGH/CRITICAL findings and documented, expiring exceptions. [Baseline image details](ci-cd-quality-audit.md#143-container-comparison-and-remediation)

## Dependency review and delivery scope

`Dependency review` is active as the eighth required check in [main ruleset 26390376](https://github.com/Aymenchaghoub/etaye/rules/26390376) (`integration_id: 15368`, strict updates, no bypass actors). The policy tool now checks eight workflow-owned job names (the seven active ruleset contexts plus `Package verify` staged for ruleset promotion). The GitHub code-scanning integration independently emits the `CodeQL` context (`integration_id: 57789`).

**Proposed owner decision:** require `Package verify` as the ninth check, bound to GitHub Actions (`integration_id: 15368`). First prepare a repository change that adds that job to the policy's required-name checks and their tests, then verify a relevant PR and a documentation-only PR before updating the ruleset. Keep the other eight contexts, strict status policy, and bypass settings. The Package job starts for every pull request, including forks and Dependabot, with read-only permissions and disposable credentials. An irrelevant path permits a documented heavy-step skip while retaining the job context. Keep Trivy findings report-only under the existing policy while treating scanner execution failure as failed verification. Monitor registry, Maven transport and runner/browser reliability after promotion. The observed failure in PR #79 caught a TypeScript/toolchain incompatibility and justified rejecting that update.

Keep all three SonarQube jobs optional. The shared observation threshold remains unmet and checks depend on an external service and trusted-only credentials. Fork and Dependabot runs skip scanners deliberately. A missing token on a trusted, in-scope run fails the relevant scanner job. Continue secret-free native gates and trusted-main scanner verification, with no elevated PR trigger. Revisit promotion only after the ten eligible PRs and an explicit policy for absent analysis. This proposal leaves ruleset 26390376 unchanged.
