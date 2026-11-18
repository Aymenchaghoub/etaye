# Windows native Figma queue importer

This local development plugin continues the corrected desktop import in the original Étaye file. It is design tooling only. It uses prepared native JSON, the exact local fonts and embedded PNG bytes; it needs no token, account-wide MCP call, network access, Node installation or Linux path on the destination PC. Native Windows execution, all 456 desktop states and authorized cleanup were verified on 2026-11-18. The [run report](../../docs/design/reviews/2026-11-17-windows-native-run.md) records the final version and remaining visual findings.

The [subsequent closure](../../docs/design/reviews/2026-11-18-figma-native-closure.md) corrects the targeted findings in 52 existing states and records their final native evidence. Those intentional later edits retain the original immutable import fingerprints. Do not rerun the queue over the delivered final file or reset its fingerprints to bypass drift refusal; inspect the current closure observation before further design work.

The plugin is separate from the older [local importer](../figma-local-import/README.md). Its renderer follows the corrected native scene renderer exercised on 2026-11-17, including text decoration and color, SVG paths, measured coordinates, borders, shadows and zero-blur outline correction. Asymmetric dashed/dotted edges use editable rectangular segments with explicit gaps and bounded edge geometry. Text remains native editable text; SVG content becomes native vectors. Only discrete synthetic PDF preview content uses image fills. It does not create reusable components, token bindings, responsive designs or prototype navigation; source link targets are recorded in evidence.

## Run on Windows

1. Extract the complete versioned Windows handoff archive to an ordinary local folder. Keep its prepared JSON packets and fonts together with the supplied plugin files. The archive manifest supplies provenance and file hashes.
2. Install the bundled exact font files through Windows, then restart Figma Desktop so its font inventory refreshes. A similar family or font style does not satisfy the preflight.
3. Open the original [Étaye Figma file](https://www.figma.com/design/vfh4BSnbRAbaDE1q0CyssL/Etaye), then page **Étaye — Import HTML**, ID `15:2`. A duplicated file, another page or an unavailable file key is refused.
4. In **Plugins → Development → New plugin**, create a Design plugin with a custom UI. Keep Figma's generated plugin ID. Copy this kit's `code.js` and `ui.html` into the generated plugin folder, then apply every field from `manifest.template.json` to its generated `manifest.json`, **retaining that real `id`**. The template deliberately has no invented plugin ID. No build or publication is needed. Run this registered local development plugin from **Plugins → Development**.
5. Run the plugin. In its JSON picker, open the prepared native packets folder and select all **94 JSON files** with Ctrl+A. Click **Vérifier et importer**. Every packet, exact font, target and already imported section is checked before the first new section. The 456 states cover 76 families.
6. Let the queue complete. **Arrêter** is processed between states and discards only the new section being built. Earlier complete sections and original content survive. Leave Figma open while it reports an active operation.
7. Download the JSON evidence, including after a failed or cancelled run. Fix the reported cause before retrying with the same full selection. Complete sections are re-read and reused; an incomplete, renamed or edited section is refused for manual inspection. The plugin never silently overwrites it.
8. Open native exports and compare them with the bundled HTML/PDF references at matching dimensions. Record the actual native/visual evidence separately from design approval.
9. Only after all 456 states reverify, the optional cleanup button becomes available. It revalidates the complete replacement set and all old targets before deleting the authorized old trials. Download the updated cleanup evidence afterward.

The Windows run identified four compatibility fixes in this checkout. The UI accepts replies relayed by the desktop editor through its sandbox frames. The recorded REST inventory and Desktop Plugin API may differ by one thousandth of a pixel on a vector bounding-box coordinate. The captured-inventory comparison uses that tolerance for bounding boxes. Captured image hashes omitted by REST are reconciled only against the exact per-candidate image allowlist. The renderer uses the native solid strikethrough for line-through runs. Twelve source runs specify 1.5px thickness and use Figma's native default thickness. Fractional font sizes are compared at the four decimal places represented in the source packets. Keep the 94 packet files and their hashes from the release. Copy the current `code.js` and `ui.html` when registering from the dated archive.

The manifest intentionally sets `enablePrivatePluginApi: true`: Figma exposes `figma.fileKey` for local development plugins with that flag. The plugin fails closed if the key is unavailable. This flag does not request network access or a paid subscription. See Figma's [manifest documentation](https://developers.figma.com/docs/plugins/manifest/#enableprivatepluginapi-boolean) and [fileKey documentation](https://developers.figma.com/docs/plugins/api/figma/#filekey-string--undefined-readonly), checked 2026-11-17. Keep `networkAccess.allowedDomains` set to `["none"]`.

## Prepared packet contract

Each JSON file is one uncompressed `etaye-windows-native-v2` packet. The queue must contain indexes `0` through `93` exactly once and the exact ordered state assignment in [canonical-queue.json](canonical-queue.json), covering 456 states in 76 families. The plugin embeds this canonical list and its raw packet hashes, pins the enhanced capture digest, and compares common existing/cleanup identities. File picker ordering is irrelevant; `packetIndex` controls execution and source `x`/`y` controls section placement.

| Field | Required value or meaning |
| --- | --- |
| `format` | `etaye-windows-native-v2` |
| `packetIndex`, `totalPackets`, `totalStates` | Zero-based index, `94`, `456` |
| `fileKey`, `pageId` | `vfh4BSnbRAbaDE1q0CyssL`, `15:2` |
| `snapshot` | `d2f0800e275c4b156fedbcf23656ffe2bf435f9b6a72faf0f86fc293e2a82ad6` |
| `capture` | `a6c9010bc215d86d32e430c0e50abac5b75bca16c1ff6102f46892e62b17974b`, the enhanced capture source |
| `rawPacketSha256` | Original native packet byte digest, matching that index's `rawSha256` in the embedded canonical list |
| `name`, `x`, `y` | Recorded deterministic section identity and position |
| `fonts`, `colors`, `svgs`, `nodes`, `frames`, `images` | Original native packet tables from the corrected renderer |
| `imageData` | Object mapping used asset SHA-256 keys to base64 **raw PNG bytes**, without a data-URL prefix; empty object when no image is used |
| `expectedExisting` | Common `completedSections` identities from the dated observation: section `26:2` with all eight exact S-06 root IDs/names/dimensions; only packet 0 additionally carries `nativeInventory` |
| `cleanupCandidates` | Common twelve-item array of `{id,type,name,imageHashes}`; only packet 0 additionally carries `nativeInventory` for every item |

`imageData` contains only the images referenced by that packet's type-3 nodes. `images` may retain the historical Figma image-hash table, but the plugin ignores it and calls `figma.createImage` on embedded bytes. It checks the PNG signature and decodes every unique local image before creating canvas nodes; the archive builder owns SHA-256 byte/provenance verification. Historical `replaceId` is ignored everywhere. It must never cause `26:2` to be replaced.

The canonical literal is generated from `canonical-queue.json` into `code.js` when this tooling is prepared; runtime never reads a sidecar file. The builder verifies raw packet bytes and the delivered archive hashes. Runtime validates the packet's declared raw digest and exact state assignment against the embedded canonical values. No source or archive hash proves native execution.

Native frames use `{id,width,height,background,node,texts}`. `texts` is the expanded native text-node count, not the earlier source scene's text-run count. Node arrays preserve the corrected renderer's schema:

| Kind | Array fields after kind |
| --- | --- |
| `0` frame | `x,y,width,height,name,fillColorIndex,radius,clipsContent,opacity,edges,outline,childIndexes,linkTarget,effects` |
| `1` text | `x,y,width,height,characters,fontIndex,fontSize,lineHeight,colorIndex,letterSpacing,decoration,multiline` |
| `2` SVG | `x,y,width,height,name,svgIndex,opacity,linkTarget,childIndexes` |
| `3` image | `x,y,width,height,name,assetSHA256,opacity,linkTarget` |

The prepared first packet must match recorded section `26:2`. Reuse requires the real section parent/name, exact root IDs/names, root dimensions, text counts, text content, font sizes and exact family/style names. It also compares every descendant against the observed `nativeInventory`, preserving preorder, IDs, parents, names, absolute bounding boxes, opacity, visibility, frame clipping, fills/strokes and text properties. Numeric values round to three decimals; omitted opacity/visibility normalize to 1/true. Paint tuples are `[type,visible,opacity,r,g,b,imageHash,scaleMode]`, with null color or image fields when inapplicable. Inventory records follow `{id,parentId,type,name,box,opacity,visible,clipsContent,fills,strokes,text}`; text is `{characters,family,style,size}` or null.

The preexisting baseline covers these common observable properties. It does not establish equality of unrecorded vector path geometry, effects or border widths in the older sections. New sections save a SHA-256 fingerprint in plugin data after creation and must match it on every reuse and cleanup verification. It covers their complete current descendant inventory plus native paints, vector paths/network, transforms, effects, stroke geometry settings, radii and text formatting. A moved/hidden/cleared text node, added descendant or changed vector path therefore stops reuse. Later sections also need this plugin's snapshot/index/state marker. A homonymous section without that marker is refused. Actual matching root names outside their verified section also prevent duplication, including when a section was renamed and its plugin data was stripped. The plugin does not add markers to or edit the recorded S-06 section.

Each new packet owns one section. Three roots fit per row at deterministic offsets, preserving the corrected renderer's geometry. Evidence includes `captureSha256`, the ordered 94 `rawPacketSha256s`, actual created node IDs, root dimensions, text/font inventory, node-type counts and source link targets. Reused sections reconstruct source links with `state`, `target`, `sourceNodeIndex`, `tuplePath` and `childPath`; `nodeId:null` and `nativeNodeResolution:source-only-on-reuse` explicitly mark that no native link-node ID was reestablished. New links include their actual created node IDs. At the end it reads every section again before declaring `completeExpectedStateSet: true`. All reports retain `visuallyReviewed: false`, `approved: false` and `applicationImplemented: false`.

## Guarded cleanup

The optional action is bound to a successfully verified full queue in the current plugin session. Reopening the plugin requires running that queue again; valid sections are reused. The action then re-reads all 456 roots and resolves all twelve old targets before its first deletion.

Only the following dated identities are eligible: old user section `15:3`, pilot section `19:2` and ten temporary image placement frames `22:2` through `22:11`. Their IDs, types, exact names, parent page, actual descendant image hashes and entire observed native inventories must match the prepared metadata and the plugin's fixed allowlist. Added children, moved content or cleared text paints block the whole cleanup set. Already absent targets are treated as previously cleaned. Any other identity mismatch prevents the entire cleanup action. Foundation/component pages `2:74` / `2:75`, recorded complete section `26:2` and new queue sections cannot be cleanup targets. No cleanup runs automatically after import or on failure/cancel.

Cleanup reports actual removed root and descendant IDs. If a native Figma deletion itself throws after earlier removals, the error and already removed IDs remain downloadable; deletion is not claimed transactional. No deletion has been executed by preparing or testing this plugin.

## Local verification and limits

Run `node design/figma-windows-import/test-plugin.cjs` from the repository root for the detailed test report and `node --check design/figma-windows-import/code.js` for syntax. The isolated native-API fixtures use canonical state IDs/assignments and native leaf nodes without `ChildrenMixin`. They exercise a full 94-packet/456-state queue, wrong target/font/data/capture/provenance refusal, baseline and fingerprint drift, link preservation on reuse, native text/decoration/dashed-border creation, duplicate prevention, failed/current-section rollback, cancellation, local image preflight, incomplete-set cleanup refusal, cleanup identity/inventory guards and the downloadable evidence link. SHA-256 is checked against standard UTF-8 vectors.

These fixtures are design-tooling checks. They do not run Figma Desktop, Windows font installation, native rendering, account access, application behavior or a visual acceptance review. Actual Windows execution and the 42-state comparison, including all nine PDF preview variants and 19 email states, are recorded in the run report. Full long-document/font-metric reconciliation and review by Aymen remain pending. A prepared packet count, passing fixture or successful node import does not approve a design or authorize application development.
