# Story 1.3 implementation record

Date: 2026-11-21. Branch: `feat/1.3-switch-practice-context`. Approved scope: UD-52, UD-53 and UD-54. Baseline: `d58dde93e4016769fda5ce854711da4f12020d73` (PR #49). [Context packet and source versions](story-1-3-context-packet.md) · [Spec](spec-1-3-switch-practice-context.md)

## Delivery

Preparation commit `ab7ebcc` records the design review and written authorization. The installed BMAD readiness check passed for 1.3. Contract commit `0b24129` defines the optional `AccessContext[]` projection on the existing `getSession` operation before implementation. The implementation candidate is `309891ee3607e0f9f9aa94699fb196412ce88c66`. Global development authorization remains false.

V4 projects current roles and labels from active staff memberships and verified portal links, in the existing account lookup scope. Record endpoints retain their independent capability and practice ownership checks. The web app makes a fresh session read on selection, switch and re-entry. It drops the previous view and ignores responses from superseded checks. The browser tab owns the selected URL and its UI state. I-20 uses a reusable guard and the approved test-only form.

## Verification

| Scope | Actual result |
| --- | --- |
| Contract and documentary package | OpenAPI validation passed. The documentary validator passed separately. The authorization validator's scope suite passed 30 tests. |
| API | Java 25 Maven verify and Testcontainers PostgreSQL 18.7 passed 160 tests with zero failures, errors or skips. The targeted account suite passed seven. The isolated V3-to-V4 migration preserved an existing synthetic membership. |
| Web | Node 26.13.1 and pnpm 12.21.0 completed a frozen install, TypeScript check, generated SDK verification and production build. Chromium smoke passed 45 cases including 12 S-05 and I-20 cases and axe assertions. The CI evidence gates require 160 API and 45 smoke tests, including the new practice context smoke file. |
| AT-003-A | The real account switched among clinical, administration and patient contexts. A second tab kept its independent selection. The prior header disappeared and the delayed response smoke case could not restore a superseded view. |
| AT-003-B | The real browser saw 200 for its clinical record, 403 for a disallowed capability and 404 for a record under the wrong practice without that record's payload. It refused a suspended practice on re-entry and offered a surviving choice. The smoke suite also checked guessed context, independently revoked grants and failed change recovery. |
| AT-003-C | The [opened running UI comparison](../design/reviews/2026-11-21-story-1-3-runtime.md) records actual states, viewports and visual differences. The smoke suite covered keyboard focus and reflow. I-20 starts focus on the title, keeps the close control reachable by Tab and retains input after a failed save. Aymen's running UI review remains open. |

Five Playwright cases passed on the real Spring Boot, React and PostgreSQL stack. They covered 1.3, the existing invitation slice and recovery after stopping or pausing PostgreSQL. The final run used a fixed loopback port for the disposable database and seeded each practice's records under its RLS context. The separate 1.2 inbox browser case was outside this run because its fixed local inbox port was already occupied. Account recovery still ran in the API and smoke suites.

With a disposable stack running, set the database container and web port to match that stack. The recorded command ran from `apps/web`:

```sh
ETAYE_E2E_DB_CONTAINER=etaye-13-fixed-postgres \
ETAYE_E2E_INVITE_DB_CONTAINER=etaye-13-fixed-postgres \
ETAYE_E2E_BASE_URL=http://127.0.0.1:15174 \
corepack pnpm test:e2e-real practice-context.spec.ts invitation-access.spec.ts outage.spec.ts
```

The sign-in route is `/connexion`. With the local web dev server, I-20's test form is at `/tests/fixtures/practice-switch.html?outcome=failed`. The form is outside the production build.
