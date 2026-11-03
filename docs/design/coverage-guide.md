# Design coverage tracking

coverage.csv owns Figma design progress for 40 screens, 28 interactions, 3 PDFs and 5 email templates (76 surface families). It does not track implementation. The original repository baseline has every row Ready for design; this is not evidence that no newer external work exists. Inspect and merge that work before updating progress.

The 2026-11-03 HTML delivery is now preserved and independently inspected in the [reception report](reviews/2026-11-03-reception.md). [html-reception.csv](html-reception.csv) records delivery, automatic checks, targeted visual scope, Aymen approval and Figma import separately for all76 IDs. It does not replace the producer's historical tracker or upgrade any Figma row. A self-review “Reviewed” label is evidence attributed to Claude, not independent acceptance. Open findings and uninspected variants remain visible; merge newer evidence by ID without resetting this history.

The separate [Codex correction report](reviews/2026-11-03-corrections.md) owns dispositions and corrected snapshot evidence. Appended corrected columns in html-reception.csv do not overwrite the original reception columns. Full automatic state coverage does not imply every variant was visually inspected. Bind evidence to the complete HTML/CSS/JS/fonts/logos/PDF/preview snapshot; invalidate affected evidence when any dependency changes. Preserve previous failed/partial runs rather than relabeling them successful.

Aymen has selected editable Figma Design layers as the intended synchronization output. Keep Pending until actual import, layer inspection and review; the original raster-frame plugin is not the synchronization strategy. Reauthentication is an access step, not approval to replace foundations, add paid services, or approve the result.

Move a row through Draft → Ready for design → Designed → Reviewed → Approved by Aymen only with corresponding evidence. Record exact Figma file/node/version, required variants, viewports, opened export paths, reviewer/date, findings and their resolution. “Reviewed” is an actual visual/functional design review, not a document completeness check. Only Aymen can supply approval. Leave N/A only where a viewport/medium is genuinely inapplicable and explain why.

## HTML work while Figma is unavailable

Offline HTML/CSS design and limited prototype navigation are authorized design artifacts. Keep an external `html-design-progress.csv` (or equivalent explicit table) keyed by the same S/I/P/M IDs: actual source/artifact version, file/entry route, variants, viewport/theme/font conditions, opened capture paths, functional and visual findings, reviewer/date and synchronization status. Label synchronization Pending until the existing Figma file is actually updated and checked. Record any Aymen feedback with its real reference; never infer approval from silence or a successful render.

Do not place HTML paths or invented placeholders in figma_node, nor advance Figma Designed/Reviewed status solely because HTML is ready. After actual import/reconciliation, record the real node/version, review the synchronized result and transfer only the evidence that applies; an HTML screenshot alone does not prove the imported frame matches. Keep outstanding import/access limits visible. Development still requires Aymen's explicit development authorization after complete design/review (or his specifically accepted design exception).

Attachment06 and regenerated kits are snapshots, not reset commands. Back up current tracking, merge by stable ID, preserve newer node references/reviews/findings and resolve conflicting revisions explicitly. An export never overwrites or silently downgrades the authoritative newer tracker. Recover partial work under the [session recovery note](session-recovery.md).

Desktop 1440×1024, tablet 1024×768, mobile 390×844 plus 320px/200% zoom checks; every P-ID has full A4 short/long/incomplete and grayscale review. For emails review narrow and desktop widths. Every S/I family includes relevant empty/error/loading/read-only/permission/concurrency/AI states. Minor variants belong to the family, with node refs in variants, not falsely counted as an independent completed page.

All scope rows must be designed and reviewed before development. A specific exception requires Aymen's dated acceptance, affected row IDs, impact, fallback and explicit resumption trigger. A blank exception is not permission. Future BMAD sprint-status is separate; do not add design values to its schema.
