# Security and privacy design

Status: Proposed design, not a security certification or implemented control. 2026-11-02. Public portfolio data is exclusively synthetic. Real patient use and regulatory compliance are outside this project phase. [Access matrix](access-matrix.md) and ADR-003/004 own permission/session choices.

## Trust boundaries and threat cases

Browser input, uploaded files/record text, provider output and request practice IDs are untrusted. Boundaries: public browser→authenticated origin; API→database nonowner tenant-scoped transaction; worker→specific task context; API→private byte store; adapter→AI/email provider; CI contribution→trusted release environment. Assume guessed IDs, stale membership, crafted upload, injection in source text, duplicate booking/task, malicious PR and leaked public URL attempts.

| Threat | Planned prevention | Required evidence |
|---|---|---|
| Cross-practice read/write/export/task exposure | Current membership+capability+resource check, composite ownership FKs, same-transaction RLS under nonowner role | Two-practice API/PostgreSQL tests for every resource group, absent context and pooled-connection reuse |
| Admin sees clinical record | Independent clinical capability; minimal admin/audit projections | Admin-only negative deep-link/search/download tests |
| Patient sees internal draft/source | Exact current publication projection and explicit source/attachment grant | Validated-unpublished, replaced, withdrawn and guessed source tests |
| Session theft/CSRF | Secure host-only HttpOnly opaque session, CSRF header, TLS, ID rotation, revocation and safe redirects | Cookie/CSRF/logout/reset/suspension tests; no bearer token localStorage |
| SQL/content injection | Parameterized queries, allowlisted filtering, safe rich-text rendering/plain text baseline, output escaping | Malicious input payloads; no raw query concatenation; CSP verification |
| File execution/path abuse | Type/signature/size checks, quarantine/scan, opaque storage key, no public bucket, safe disposition | Polyglot/oversize/filename/range/preview/scan-failure tests |
| AI instruction injection/data exfiltration | Minimized synthetic payload, data delimiters, no arbitrary tool/URL execution, schema/source support validation | Malicious source corpus, invented citation and boundary logs inspection |
| Lost edits/double effects | Expected version + DB scheduling constraints + scoped idempotency/outbox/lease fence | Concurrent booking/edit/publication/job tests |
| Secret theft through untrusted CI | Read-only PR token, no deployment secrets on forks, pinned actions, trusted promotion | Permissions review and fork negative simulation |

## Authentication and authorization

One account authority with independently scoped memberships/portal links; separate entry copy does not determine privileges. Spring Security and opaque JDBC sessions proposed. Recheck current grants on every request/task/download. Force reauthentication/assurance for sensitive security changes. Proposed inactivity/absolute/recovery/invitation limits are in ADR-004. Rate limit login/recovery/invitation/generation and avoid account enumeration. Password encoder parameters require benchmark and current supported guidance at implementation; never store plaintext or reusable reset tokens.

Use 401 missing/expired session; 404 for inaccessible resource existence across practice/record boundary; 403 for forbidden action on a visible resource; avoid timing/list/count disclosure. Server ignores client claims of role or ownership. Global account suspension affects all sessions; one membership suspension affects that practice only. No unrestricted clinical operator role by default.

## Files, transport and browser

TLS all network paths; DB TLS verifies certificates for remote providers. Private/no-store protected responses and downloads; no clinical bodies in URLs/referrers/service-worker cache. Restrictive CSP, safe content disposition and media sniffing protections. Verify authorization for thumbnails/range requests/export snapshots, not only initial page. Patient grants are checked at delivery; even short-lived signed URLs require explicit revocation design and are not the default small-demo path. No clinical drafts in persistent shared-browser storage. Logs and screenshots contain only synthetic content and safe identifiers.

## Data inventory, retention and minimization

Account/contact/session/security data, synthetic clinical records and source documents, AI drafts/citations, publication/export bytes, task/outbox metadata and audit events have separate ownership and retention purposes. No advertising analytics. Proposed demo retention: reset to fixture baseline at a disclosed cadence and manual bounded operator reset; expire operational tasks/export scratch bytes after 7 days, idempotency receipts after 24h, raw invitation/reset tokens never stored. Audit retention target 30 days for synthetic demo, reviewed against storage needs; snapshots/exports retained only while necessary for version demonstration. These are portfolio operational assumptions, not legal healthcare retention rules. Before any real-data use, a separate retention/legal/privacy assessment must replace them.

Audits are append-only to the app role with restricted operational access and safe metadata. Mutation success audit is atomic; failed access/job outcomes are distinct. Cover read/download/denial/correction/review/publication/retry and settings/access changes explicitly. An aspect/helper cannot prove coverage. Tamper-resistant operational backup/log controls remain to be implemented and tested.

## Configuration and incidents

Document variable names only: DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD, APP_ORIGIN, SESSION_COOKIE_SECURE, FILE_STORE_ENDPOINT, FILE_STORE_BUCKET, FILE_STORE_ACCESS_KEY, FILE_STORE_SECRET_KEY, AI_MODE, AI_PROVIDER_API_KEY, AI_DAILY_BUDGET, EMAIL_MODE, EMAIL_API_KEY. No deployment/paid AI credentials needed now. Connector login preferred; local ignored files or service secret stores hold values. Never log/commit tokens or put them in handoffs/screenshots.

Incident plan: stop affected capability, revoke sessions/links/tokens as relevant, preserve safe scoped evidence, identify affected artifact/version boundary, correct/withdraw publication where appropriate, restore verified synthetic baseline or compatible release, document what was actually tested. A downloaded file cannot be recalled. No compliance claim follows from this plan.
