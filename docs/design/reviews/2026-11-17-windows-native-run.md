# Native Windows Figma Desktop run — 2026-11-17 and 2026-11-18


All 456 desktop states in 94 sections passed native structural revalidation on 2026-11-18. The final file contains 28,001 editable text nodes. The authorized twelve old trial roots and their 3,492 nodes were removed after revalidation. A post-cleanup observation confirms exactly 94 replacement sections and no old targets. Foundations `2:74`, components `2:75` and the original eight S-06 root IDs are present. The named final version is `2423132943961499284`, “Etaye desktop import — 456 states verified — 2026-11-18”.

The [actual run evidence](2026-11-17-windows-native-run.json) records the final node IDs, fonts, source digests, deleted IDs, version and opened visual comparisons. The import is complete. Targeted visual findings remain for design review.

Figma Desktop opened the original Étaye file on page `15:2`. The named recovery version `Before Windows native import — 2026-11-17` is visible in the file’s version history. The official Windows archive and all 1,694 listed payloads matched their SHA-256 digests. The Figma-generated development plugin ID was retained. The import selected the 94 original release packets and used the exact native fonts discovered on this PC.

The [native run record](2026-11-17-windows-native-run.json) preserves exact section and root IDs from the historical partial checkpoint. That checkpoint verified 187 corrected desktop states across 35 packets. That includes eight reused S-06 roots in section `26:2` and 179 newly created roots. Figma rolled back the 864 nodes of the active packet when it refused an underline-only style setter on strikethrough text. The queue resumed after that correction and reached S-26. The source of `S-26/conflit` uses 11.6667 px text. Comparison now uses the four decimal places represented by the prepared source, with exact text and font family/style comparisons. Figma rolled back the 554 nodes in that active packet before the fix.

## Desktop compatibility

Figma Desktop relays engine messages from the editor above two sandbox frames. The supplied UI discarded those replies until its sender check was corrected. The native read-back of S-06 matched all 2,035 ordered records except eight vector bounding-box coordinates. The observed rounding difference is bounded to 0.001 px per vector coordinate. Node IDs, types, ordering, non-vector geometry, paints and text properties remain checked against the captured baseline. Native checksums for sections created here retain their original strictness.

The renderer uses editable Figma strikethrough for line-through source runs. Figma supplies the default thickness for the twelve source runs with an explicit 1.5 px thickness. The source runs all use a solid line in the same color as their text. The archive, frozen packet identities and HTML/PDF sources retain their release bytes.

## Reconciliation of S-09

The resumed queue stopped on the fingerprint of section `30:8643`. A fresh native reconstruction on a temporary page matched all 1,107 current records after mapping their IDs to the previously recorded order. The observed and rebuilt fingerprints are identical. The old stored fingerprint differs. The temporary page `30:45758` was removed. The importer recognizes this exact section, packet and digest pair from the documented proof. It leaves the S-09 nodes untouched and retains the fingerprint guard for every other section and subsequent change.

## Visual comparison

Nine native screen states were exported and compared against the frozen HTML at 1,440 px. See the exact dimensions, hashes, node IDs and opened image filenames in the [native run record](2026-11-17-windows-native-run.json).

- [S-06/nora](2026-11-17-windows-native/S-06-nora.png) retains the dashboard layout and statuses. The reference-only prototype footer is outside the editable layer capture.
- [S-02/patient-invalide](2026-11-17-windows-native/S-02-patient-invalide.png) retains the error summary and field-level error. The list marker in the HTML error summary is absent in Figma.
- [S-09/long](2026-11-17-windows-native/S-09-long.png) and [S-09/louis](2026-11-17-windows-native/S-09-louis.png) retain the long and sparse timelines. There are text-spacing and annotation differences.
- [S-18/valide](2026-11-17-windows-native/S-18-valide.png) retains the professional-only warning and cited source table. The HTML is 1,663 px tall and the native frame is 1,683 px tall. Some citations and lines wrap or shift differently.
- [S-22/lecture-seule](2026-11-17-windows-native/S-22-lecture-seule.png) and [S-22/longue](2026-11-17-windows-native/S-22-longue.png) retain the version hierarchy and text decorations. Inline French addition and deletion badges are absent in the native comparison rows. Their legend remains visible.
- [S-23/echec-publication](2026-11-17-windows-native/S-23-echec-publication.png) retains the publishing error and retry control. The corrected paragraph wraps differently around citations.
- [S-25/lien-retire](2026-11-17-windows-native/S-25-lien-retire.png) retains the access-revoked message and portal layout.

Rendered all nine PDF references at 105 dpi and opened all 22 pages in contact sheets. The ten unique prepared PNGs used for PDF content previews also match the bundled reference previews byte-for-byte. On 2026-11-18, all nine native PDF preview states and all 19 email states were exported at 1,440 px and compared with their frozen HTML references. The source PDF pages were opened as recorded above. These are design previews and source PDFs. A future application PDF generator and real email clients remain untested.

## Verification

The 25 importer fixture tests and four legacy importer preservation tests passed, including a regression for missing captured image hashes and refusal of a changed image. JavaScript syntax and Git whitespace checks passed. The local documentary validator reported eight original SVG differences caused by CRLF checkout conversion and a shared-skill symlink represented as text on Windows. The committed SVG bytes match their original hashes and the committed skills link has Git mode `120000`. The Windows checkout and Git configuration were preserved.

The project remains in design review with the application stories in backlog. The desktop import and guarded cleanup are complete. Full responsive, component/token and prototype reconciliation, the visual findings below and Aymen’s design approval remain open.

## Extended comparison and cleanup — 2026-11-18

The opened comparisons now cover 42 states, comprising 14 screen/interaction states, all 19 email variants and all nine PDF variants. The nine screen renders opened on 2026-11-17 have identical HTML and native PNG SHA-256 values on revalidation. The run record links every opened comparison and each email contact sheet.

The PDF and email layouts retain their content, status labels, section structure and intended light/dark/plain-text versions. Text metrics and wrapping differ in places. The extra screen comparisons include the expired-session overlay, portal PDF preview and short/long PDF-export preview. The I-13 input focus outline differs from HTML, and S-39 PDF page-preview borders are absent. The earlier S-02 marker, S-22 badges, citations and S-18 height findings remain open.

The first cleanup preflight refused the old trial and ten image placements. Inspection found 12 frame-box rounding differences of 0.001px in `15:3`, one such vector-box difference in `19:2`, and missing image hashes in the REST inventory of the ten placements. Their actual hashes match the independent explicit candidate allowlist. The captured-inventory comparator reconciles only these rounding values and known image identities. It still refuses new children, missing nodes, changed content/paints/hashes, or larger geometry differences. The current-section fingerprints retain their checks.

The original guarded cleanup revalidated all 456 replacements and all twelve candidates before the first deletion. It removed exactly the authorized roots and their 3,492 observed nodes. The final read-back confirms 456 states, 28,001 texts, 94 sections, no surviving cleanup targets and the protected page IDs. The native evidence records the actual deleted IDs and final saved version.

## Delivery state

Historical checkpoint below. The [2026-11-18 closure report](2026-11-18-figma-native-closure.md) supersedes its access/delivery limitations. Git/gh access now works outside the sandbox; integration preserves later remote planning and background studies. The visual findings above remain open until native corrections and before/after evidence are recorded.

All code, tracking, reports and opened evidence are saved in the local checkout. PR16 is now merged, and its original remote branch is absent. Current remote `main` was observed at `eb58c81b3be3b91819dd2ca31a4f2412b509b5e3`, which includes later planning and background-study changes. The full current remote continuation was saved in `winfig/remote-main-continuation.md` for integration. The frozen release design was used for this import. The later background study was not applied to these frames.

This runtime cannot write the checkout’s Git metadata, and GitHub connector writes returned `403 Resource not accessible by integration`. The native-run changes are uncommitted and unpushed. Integrate them with current `main` on a new documentation branch using the user’s authenticated terminal, preserving the later continuation content. The local Windows documentary validator retains the checkout limitations recorded above. No new CI run is claimed.
