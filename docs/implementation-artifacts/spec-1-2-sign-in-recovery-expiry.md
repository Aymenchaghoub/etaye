---
title: 'Story 1.2 — Sign in, recover and expire safely'
type: 'feature'
created: '2026-11-20'
status: 'in-review'
baseline_commit: '9f82546e06bee11841cb2a903bd2551d3529862b'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/AGENTS.md'
  - '{project-root}/docs/implementation-artifacts/epic-1-context.md'
  - '{project-root}/docs/design/reviews/2026-11-20-story-1-2-review.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

Give existing account holders web sign-in, access recovery and safe return after expiry. Use both approved entry variants with one credential authority. Provide the current session and grants for later practice selection.

## Boundaries & Constraints

Use the generated SDK, opaque JDBC session cookie, header CSRF and approved French designs S-02, S-04, S-38 and I-22 (UD-46, UD-48). Invitations, practice selection, clinical pages, native sessions and branded mail delivery retain their assigned stories. Keep secrets outside API responses, logs, browser storage and reusable database fields.

Aymen approved a manual test inbox for the local demo. Make it opt-in, bounded and reachable through a separate loopback listener with a fixed local origin. Only synthetic accounts use it. Deliver the one-use link after commit, hold it in memory until expiry and keep it out of traces and the application API. The token stays in the reset page fragment until consumed. M-03, the general mail worker and external delivery belong to 8.3.

## I/O & Edge-Case Matrix

| Scenario | Expected behavior |
|---|---|
| Valid sign-in | Rotate session and CSRF, return current real grants and bound expiry, accept only an allowlisted authorized return. |
| Bad password, missing or suspended account | Generic 401, bounded attempts and 429 with Retry-After. |
| Recovery request | Same accepted response for eligible and ineligible identities. Deduplicate retry, create an expiring digest, and deliver only after commit through the selected boundary. |
| Reset | Enforce 12 characters, redeem one token once within 15 minutes, change the password and revoke old sessions. Reject expired, used and concurrent replay without claiming success. |
| Expiry or lost rights | Remove protected content from the DOM. Restore eligible memory only after the same account and context are authorized. Clear it on exit or mismatch. Never replay a write automatically. |

</frozen-after-approval>

## Code Map

- `docs/contracts/openapi.yaml`, `operation-index.json` and `README.md` already define the five account operations. Align observed outcomes before regenerating.
- `apps/api/src/main/java/dev/aymen/etaye/infrastructure/security/SecurityConfig.java` and `IdentityResolutionFilter.java` own session/CSRF and live grant resolution. `infrastructure/web/CsrfController.java` is the anonymous issuance boundary.
- `apps/api/src/main/java/dev/aymen/etaye/identity/` owns credentials and recovery. V1 keeps password hashes unreadable to the runtime and lookup roles. Add narrow database operations with fixed privileges in V2.
- `apps/web/src/api/browser.ts` owns the same-origin transport. Add auth screens under `apps/web/src/`; preserve the existing readiness entry and tests.

## Tasks & Acceptance

- [x] `docs/contracts/openapi.yaml` and indexes — confirm request validation, expiry, generic problems, idempotency and cache policy.
- [x] `apps/api/src/main/resources/db/migration/V2__identity_access.sql` and `identity/` — add restricted credential lookup, bounded digest/receipt storage, the isolated local capture adapter, and atomic reset, session revocation and safe audit. Prove fresh and upgraded databases.
- [x] `infrastructure/security/` and `CsrfController.java` — enforce absolute and idle expiry, rotate credentials, bound anonymous issuance (D-35) by direct client address within a global budget and retain exact-origin/header-only CSRF behavior.
- [x] `apps/web/src/` — add approved sign-in, recovery and expiry behaviors using generated tokens and original assets. Apply amber to the approved password error summary.
- [x] `apps/api/src/test/` and `apps/web/tests/`, `apps/web/e2e/` — exercise the matrix, wrong/missing CSRF, disallowed return, changed grants, outage, keyboard and axe.

Given a synthetic account on the isolated stack, when the actor signs in, follows a captured recovery link or returns after expiry, then real HTTP and PostgreSQL enforce AT-002-A/B. Given the running screens at the evidence widths, when compared with the approved references in both themes, then required copy, focus and responsive behavior satisfy AT-002-C.

## Implementation Notes

Java 25 verification passed 150 tests, Chromium smoke passed 19 tests, and the isolated real-stack recovery and outage tests both passed. The disposable V1-to-V2 migration preserved an existing synthetic account and membership. The live visual comparison covered both themes and widths from 1440 to 320 CSS px.

## Spec Change Log

## Review Triage Log

## Design Notes

Baseline inspected on 2026-11-20 at `da7ff5ae3f0f39840e279d94d633b78ce8dedd30`. FR-001, BR-003/005/015–018, J-01, ADR-003/004/008, the API contract and AT-002 govern the slice. Use ADR-004's 30-minute idle and 8-hour absolute values as initial demo defaults and UD-26's 15-minute recovery limit. Verify the visual source through the [approved HTML review](../design/reviews/2026-11-20-story-1-2-review.md). Figma reconciliation remains open.

## Verification

Run Maven verify with PostgreSQL, generated SDK drift/build, browser smoke and the isolated authentication journey. Open matching captures at 1440, 1024, 768, 390 and 320 CSS px. Run documentary validation. Follow the [delivery rules](../operations/delivery-rules.md) for the three-lens local review and manual CodeRabbit request.
