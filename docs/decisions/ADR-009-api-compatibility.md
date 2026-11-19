# ADR-009 — API compatibility and client versioning

Status: Accepted by Aymen on 2026-11-17 (UD-29). Date: 2026-11-17. Related: AD-10, ADR-007, ADR-008, NFR-15, story 10.2.

Browsers always load the current front end from the application origin, but an installed web app can keep an old cached shell and a native app is updated by hand or through a store, sometimes weeks after the server. The API must therefore tolerate older clients for a defined window.

**Choice:**

- `/api/v1` changes are additive within a major version: new optional request fields, new response fields and new operations. A new required request field, a removed or renamed field, a changed meaning or a tightened constraint is breaking and requires `/api/v2`. Clients must ignore unknown response fields and tolerate unknown enum values; the generated client is configured accordingly.
- Clients send `X-Client-Version` (semantic version and client kind). `GET /api/v1/meta` (planned operation `getApiMeta`) returns the API version, the minimum supported client version and the latest client version. Below the minimum, protected operations answer `426` with the problem code `CLIENT_TOO_OLD`, and the client shows an update screen instead of a generic failure.
- Compatibility window [ASSUMPTION]: the two previous released client versions, and at least 90 days after a replacement is published. Aymen confirms or changes these numbers.
- Deprecations are announced with `Deprecation` and `Sunset` response headers and recorded in the contract README before removal.
- The OpenAPI document stays the single source. CI regenerates the typed client and fails on drift between contract and client; the generated client is the only way the web app calls the API.
- Database changes keep the expand and contract rule of ADR-007.

Alternatives: URL-per-release versioning (more routes to maintain), header-only content negotiation (harder to cache and debug), and no policy (breaks installed clients silently).

Consequences: one planned operation, one new error code in the catalog, a client-version log field, a contract-drift check in story 10.2 and an update screen to design (DF-9). The window numbers are assumptions until measured against real release cadence.
