# Native Figma MCP import test

2026-11-17. **One editable desktop state imported and inspected; full synchronization and design acceptance remain pending.** Aymen requested a fresh MCP attempt because Windows is not yet available, then supplied the existing local REST-token location to preserve connector usage. No token value was displayed, copied into the repository or included in this evidence.

## Verified result

The existing file is accessible through the official Figma connector. The [isolated test frame](https://www.figma.com/design/vfh4BSnbRAbaDE1q0CyssL?node-id=19-3) is `19:3`, inside section `19:2` on page `15:2`, “Étaye — Import HTML”. It contains only `S-06/nora`, at 1440×1113. The scene comes from `figma-editable-mail-reviewed/batches/batch-025.json`, verified against its export manifest and corrected snapshot `d2f0800e275c4b156fedbcf23656ffe2bf435f9b6a72faf0f86fc293e2a82ad6`.

Structural read-back through REST confirms 144 frames, 41 rectangles, 105 vectors and 64 editable text nodes, with no image-filled node. The two used families are Atkinson Hyperlegible Next (Regular/Medium) and Atkinson Hyperlegible Mono (Regular). Source Serif 4 is available in the connector's font inventory but is not exercised by this screen. A screenshot alone is not the editability evidence; actual native node types and text/font properties were read back.

Before the test, the file already contained eight imported states under section `15:3`. Aymen identified this as his earlier attempt with substantial layout shifts and asked that it not count as accepted work. It remains intact and unapproved. Selected properties of pages “Fondations” and “Composants” match the previously recorded canonical snapshot; the prior trial also matches the pre-test REST snapshot after this operation. Comments returned empty.

## Text correction and opened renders

The first import used fixed-width, height-growing native text frames for browser text runs that were already split into visual lines. Small native font-metric differences caused extra wrapping: “Aujourd'hui”, “Agenda”, the date heading and other lines broke and overlapped adjacent content. That render was opened and rejected for fidelity.

A targeted second write changed only the 64 new text nodes to native `WIDTH_AND_HEIGHT` auto-resize, preserving their fonts, weights, text and x/y positions. The next REST export was opened at full size and compared with the corrected HTML reference. The duplicate wrapping and large resulting overlaps are corrected. This is a single-state conversion check, not completion of the application's responsive layout or component system.

- [Corrected HTML reference](2026-11-17-figma-mcp/S-06-nora-html-reference.png).
- [Initial native render: rejected wrapping](2026-11-17-figma-mcp/S-06-nora-before.png).
- [Native render after the targeted correction](2026-11-17-figma-mcp/S-06-nora-after.png).
- [Machine evidence, native IDs and limits](2026-11-17-figma-mcp-import.json).

The final REST observation records file version `2422776646896194476`, last modified `2026-11-17T10:08:59Z`. The initial and corrected renders share the same returned version ID, so their distinct timestamps and PNG hashes remain necessary evidence. The opened final PNG hashes to `4c2c840d67409b268de2c5df9748ef2e6193be08919c7860cd936f302a4f4e49`.

## Access and operational limits

Identity reported Starter/View; file REST access reported owner. These observations do not establish unlimited connector usage or a changed subscription. Initial discovery used MCP reads; subsequent file/structural checks and PNG exports used the existing token through read-only REST. Two successful MCP writes created the isolated frame and corrected its text behavior. The initial uncompressed script was rejected by the connector's 50,000-character parameter limit before execution; lossless packing reduced the successful script to 32,405 characters. No extra canvas inspection was required for that pre-execution rejection.

## Remaining reconciliation

Native link underlines and some radio/status indicators still differ from HTML; native text widths are not pixel-identical. Reusable component instances, token/style bindings, responsive/theme variants and native prototype wiring remain unreconciled. This test uses editable measured geometry, the previously selected import approach, rather than substituting a generic component theme. The local Windows/macOS plugin itself was not executed or newly certified.

The test establishes that this single-state MCP route currently works without the Windows fallback. It does not justify importing all remaining states unchanged. Correct the observed conversion gaps first, then proceed in controlled batches with targeted native review and exact node/snapshot tracking. `S-06` remains “Ready for design” because only one of its eight corrected states was imported; the prior user trial is excluded from accepted progress. No Aymen approval, development authorization, application test or paid-service activation is inferred.
