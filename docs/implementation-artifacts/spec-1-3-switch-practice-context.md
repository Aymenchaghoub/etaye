---
title: 'Story 1.3 — Select and switch practice context'
type: 'feature'
created: '2026-11-21'
status: 'ready-for-dev'
baseline_commit: 'ab7ebcc9093014f1eed57ae1257719b6fc2b4b6e'
context:
  - '{project-root}/AGENTS.md'
  - '{project-root}/docs/implementation-artifacts/story-1-3-context-packet.md'
  - '{project-root}/docs/design/reviews/2026-11-21-story-1-3-variants.md'
---

## Intent

Deliver one explicitly selected practice context per browser tab so an authenticated staff or patient account only sees the current context. Resolve active memberships and verified self links on the server. Show the practice and role in the approved minimal destination.

## Readiness and scope

The installed BMAD readiness criteria pass for the authorized story. FR-002 and FR-016 trace through Epic 1 AT-003 and the scoped context packet. Story 1.2 is merged. UD-52 settles contract and state ownership, UD-53 scopes authorization, and UD-54 approves the additional design with the offline exception. The 1.3 slice is independently completable through the existing account and record carrier. Later portal content, administration, editing screens and the general isolation demonstration retain their existing owners.

## Tasks

- `docs/contracts/openapi.yaml` and `docs/contracts/README.md` — define the optional `AccessContext[]` status projection before updating the generated client or code.
- `apps/api/src/main/resources/db/migration/V4__access_context_projection.sql` and `IdentityLookupRepository` — select safe metadata in the existing restricted account scope and derive current IDs from the projected grants in a single snapshot.
- `AccountAccessController` — include the projection on `GET /session`, preserve sign-in, activation, expiry, no-store and current server authority.
- `apps/web/src/auth` — show the chooser and minimal destination from current grants, verify on entry and change, clear old state before rendering a destination, preserve scoped deep-link and session recovery behavior.
- `apps/web/src/auth` and Web tests — verify a reusable I-20 guard on a test-only dirty fixture. Show pending, failed and explicit discard outcomes without attaching a production editor to 1.3.
- API and Web suites — validate projection allowlisting, revocation, concurrency and real-stack selection/outage. Open the running UI at the evidence widths against the approved offline preview.
- Delivery — one read-only Zcode review with silent failures, edge cases and AC compliance, then evidence-based triage, reruns and the required GitHub review path.

## Acceptance

- Given an active signed-in account with varied grants, when it chooses or switches contexts, then only eligible choices are shown and no previous-context request or state can enter the new view (AT-003-A).
- Given a stale, hidden or revoked route, when the browser requests or re-enters it, then the gate checks fresh authority and returns a generic recovery without protected data. Given a failed save, when the user retries or stays, then the input remains scoped to the authorized source (AT-003-B).
- Given the required states in French, when reviewed at the approved widths and both themes, then labels and actions reflow, focus visibly reaches keyboard controls, Escape is safe, and loading and failure states match the reviewed designs (AT-003-C).

[Context packet and source versions](story-1-3-context-packet.md) · [Design approval and evidence](../design/reviews/2026-11-21-story-1-3-variants.md)
