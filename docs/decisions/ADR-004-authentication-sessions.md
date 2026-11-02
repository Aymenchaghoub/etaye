# ADR-004 — Browser authentication with server-side sessions

Status: Proposed. Date: 2026-11-02. Related: FR-001/002/016, AD-4.

**Choice:** one account authority, Spring Security credential verification and Spring Session JDBC opaque sessions. Distinct clinician and patient entry copy can route to the same authenticated account. A capability is resolved through membership or portal link, never inferred from entry route.

JWT bearer tokens were evaluated. They suit independent API consumers, but add refresh-token rotation/revocation and browser storage tradeoffs here. Same-origin cookie sessions provide straightforward revocation and membership changes without a browser token store. A managed OIDC provider remains an option if future scope needs federation; no external identity subscription is required now.

Secure host-only cookie, CSRF token endpoint/header, ID rotation after login, password hashing via Spring's supported password encoder (parameters benchmarked at setup), generic recovery messages and hashed single-use invitation/reset tokens. Suggested demo settings: 30-minute inactivity timeout, 8-hour absolute timeout, 15-minute recovery token, 72-hour invitation. These are proposed targets, not clinical policy. Store no password/token values in audit or logs.

Logout, password reset, suspension and privilege withdrawal invalidate relevant access. On practice switching, reauthorize and clear client caches/forms only after unsaved-change handling. Concurrent tabs must show current practice explicitly; each API route remains practice-qualified so one tab cannot silently change another tab's destination.

Deployment consequence: serve the application frontend and `/api` under one origin. Default `pages.dev` and `onrender.com` are separate sites; they do not satisfy this choice. Prefer Spring-serving the validated static artifact initially; Cloudflare Pages can host a separate showcase. A custom-domain same-origin reverse proxy is an alternative to evaluate later, with additional routing/operational checks. Do not weaken cookies or disable CSRF to make free domains work.
