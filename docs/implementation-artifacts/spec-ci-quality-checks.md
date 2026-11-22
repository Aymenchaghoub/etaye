---
title: 'Report test coverage and check dependency changes'
type: 'chore'
created: '2026-11-22'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent">

## Intent

The application checks run meaningful suites but report test counts instead of measured code coverage. Dependabot can report existing advisories while a pull request adds a vulnerable dependency.

Publish real JaCoCo XML and TypeScript LCOV reports through the existing checks. Add a pull-request check for new high and critical dependency advisories, with a native audit when the pnpm graph changes. Establish the coverage baseline before turning on a percentage threshold.

</frozen-after-approval>

## Implementation Notes

Authorization was given by Aymen on 2026-11-22. Base `7d466546061f82982e97a3308ca04e0c63f54ffc`. The [delivery strategy](../operations/cicd-deployment-strategy.md) and [test strategy](../test-artifacts/test-strategy.md) identify JaCoCo, TypeScript LCOV and separate Sonar analyses. The Web report measures the Vitest suite with untested handwritten sources included. The Chromium smoke suite keeps its existing independent job steps. Automated Sonar quality gates require linked projects and scanner credentials.

The first local report captured 1,293 of 1,507 backend lines in the 160-test Maven suite and 29 of 622 Web lines in the five-test Vitest suite. The 45-test Chromium smoke run and the SDK drift check passed. The baseline pnpm audit found three high `js-yaml` advisories. Auditing the new lockfile returned no known vulnerabilities.

## Review Triage Log

- **Medium.** Move Web unit reports outside Playwright's output directory. A browser smoke run preserved the report in its new location.
- **Medium.** Parse JaCoCo XML and complete LCOV records and reject zero measured coverage. Tests cover truncated and empty evidence.
- **Medium.** Verify Vitest's JSON result and expected suite in addition to LCOV. Tests reject skipped cases and missing required files.
- **Medium.** Run Web unit coverage after a successful dependency installation even if a browser step fails.
- **Medium.** Run the native audit independently of the GitHub dependency graph result and keep either failure visible in the job conclusion.
- **Medium.** Trigger dependency review when an edit retargets a PR to `main`.
- **Low.** Require the named dependency workflow consistently in the application scope validator and its fixtures.
- **Medium.** Start JaCoCo with fresh execution data in the existing single Surefire fork.
- **Low.** Include `.test.tsx` in test discovery and exclude it from application coverage alongside `.test.ts`.
