# Design coverage tracking

coverage.csv owns design progress for 40 screens, 28 interactions, 3 PDFs and 5 email templates (76 surface families). It does not track implementation. Every row starts Ready for design with no new Figma node, review or approval invented.

Move a row through Draft → Ready for design → Designed → Reviewed → Approved by Aymen only with corresponding evidence. Record exact Figma file/node/version, required variants, viewports, opened export paths, reviewer/date, findings and their resolution. “Reviewed” is an actual visual/functional design review, not a document completeness check. Only Aymen can supply approval. Leave N/A only where a viewport/medium is genuinely inapplicable and explain why.

Desktop 1440×1024, tablet 1024×768, mobile 390×844 plus 320px/200% zoom checks; every P-ID has full A4 short/long/incomplete and grayscale review. For emails review narrow and desktop widths. Every S/I family includes relevant empty/error/loading/read-only/permission/concurrency/AI states. Minor variants belong to the family, with node refs in variants, not falsely counted as an independent completed page.

All scope rows must be designed and reviewed before development. A specific exception requires Aymen's dated acceptance, affected row IDs, impact, fallback and explicit resumption trigger. A blank exception is not permission. Future BMAD sprint-status is separate; do not add design values to its schema.
