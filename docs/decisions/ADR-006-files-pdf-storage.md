# ADR-006 — Guarded persistent files and PDF exports

Status: Proposed. Date: 2026-11-02. Related: BR-010/016, AD-9.

**Choice:** private object storage behind a `FileStore` port. Metadata, practice ownership, record linkage, scan state, checksum, content type and grants stay in PostgreSQL. Local private directory adapter for isolated development; persistent object-store adapter for demo. Prefer authenticated proxy streaming for small demo files; no public object URL.

Alternative: store all binary payloads in PostgreSQL simplifies atomicity but consumes a small free database quickly. Ephemeral host storage is unacceptable for user uploads. A paid persistent disk creates instance coupling and backup duties. Neon S3-compatible storage and Cloudflare R2 are candidates, not activated integrations. Provider region, access controls, price and SDK compatibility require a deployment-time check.

Uploads: proposed 10 MiB/file, PDF/PNG/JPEG allowlist, filename normalization and independent MIME/signature checks. Quarantine until scan completes; scan failures stay inaccessible with retry status. Never execute HTML, SVG, macros or instructions from imported content. Preview/download use explicit server authorization, private caching and attachment headers. Filename alone is not a type check. Ordinary upload does not extract laboratory results.

PDFBox is a proposed renderer to limit headless-browser memory. A layout service consumes a versioned export view model, embeds licensed Unicode fonts, keeps text selectable, numbers pages and repeats table headers. Tagged reading order/accessibility must be tested; server generation by itself proves neither security nor accessibility. Save template version and checksum. Short/long/incomplete sample layouts and A4 review are owned by [PDF specifications](../design/pdf-email-specifications.md) (resolve final catalog name via design index).
