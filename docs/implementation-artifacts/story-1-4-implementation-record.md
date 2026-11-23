# Story 1.4 implementation record — Demonstrate server-side resource isolation

Status: In implementation on `feat/1.4-demonstrate-server-side-resource-isolation`, started from `origin/main` at `f01288e94c0f23b2e8581fba48b82de69dc081bd` on 2026-11-23. Authorization: UD-61 (story 1.4 only, global flag false). Scope, design approvals and arbitration are in the [context packet](story-1-4-context-packet.md); this record carries the implementation decisions and the executed evidence. Nothing here claims clinical validation, certification or readiness for real patients.

## Contract review (first commit, before application code)

**Verified observation.** At `f01288e` the OpenAPI document is `0.5.0-planning` with 99 operations. `getPatientRecord` already declares `If-None-Match`, `200`, `304` (with `ETag`, no body), `400`, `401`, `403`, `404`, `406`, `429` and `503`, and the generated SDK exposes it (`apps/web/src/api/generated/sdk.gen.ts`). The observed server behavior of the carrier read matches the AT-004-B cases the packet proposes:

| Condition | Observed answer | Source |
|---|---|---|
| No, expired or revoked session | `401` | `IdentityResolutionFilter`, `SecurityConfig` |
| Membership in the route practice without `CLINICAL_READ`, or suspended membership | `403 FORBIDDEN` | `PracticeSecurityService.requireCapability` |
| No membership in the route practice (including unknown practice and a portal-only account), absent record, record of another practice | `404 NOT_FOUND`, identical body modulo `correlationId` | `PracticeSecurityService`, `PatientService.getPatientRecord`, `GlobalExceptionHandler` |
| Non-UUID identifier | `400 INVALID_PARAMETER` | `GlobalExceptionHandler` |

**Decision.** No transport change is needed for the record-only proof, so `openapi.yaml`, `operation-index.json` and the generated SDK stay untouched and no SDK drift check is affected. The contract guide gains a short "Carrier read refusals" paragraph that states the existence-hiding policy AT-004-B refers to (documentation follows code, ADR-011). The packet's `[ASSUMPTION]` is confirmed: the guided demo entry stays in the existing public showcase and needs no API payload, scenario value or public projection. The synthetic record and practice identifiers the guided read uses are the deterministic fixture identifiers (`md5('etaye-design-v1/' || alias)::uuid`); the browser guards them with the `demoDatasetVersion` it already reads and with the current session contexts, and never renders, stores or puts them in a URL.

**Still verified at the source.** The three guarded resource groups the epic defers (sources, tasks, exports) have no HTTP route yet (`openapi.yaml` lists them as planned operations), so the matrix reports them as pending under D-55 and a router 404 is not evidence.
