# Background cross-browser check and paper tone — review report

Date: 2026-11-18. Author: Claude (Claude Code session). Status: **proposal evidence, not a design approval.** It continues the [calm-zones report](2026-11-17-background-calm-zones.md); the specification is the [study README](../background-study/README.md). Nothing here changes DESIGN, the token reference, the coverage tracker, any Figma node or application code, and no development is authorized.

## 1. Trigger and decisions

**User decision (2026-11-18):** Aymen asked what could be done next in the session. I proposed four items; he answered "si j'ai validé ta proposition, vasy pour 1, 2 puis on discutera 3". Done here: (1) render and measure the layer in other engines, at display scale 2 and at 200 % zoom; (2) build the neutral-mean tone option announced as "not built" in the previous report. Item 3 (preparing the Figma work for when access returns) is to be discussed and was not started. Item 4 (readiness inventory) was not requested.

**User decision (2026-11-18, after the measurements):** "je prends neutral-dark, commite et ouvre la PR". Recorded as: the dark theme starts its grain from `--fond`, the light theme keeps `--papier`; commit and open the PR (not merge).

**User decision (2026-11-18, PR 20 open):** Aymen asked to see variant B in captures "juste pour la valider définitivement", I opened the desktop light and dark captures and the all-viewport contact sheet of the adopted tone, and he answered "oui je confirme": **variant B (paper + blueprint touch) is confirmed definitively.** This confirms the variant only; it is not a design approval.

**Verified observation:** before this round the only evidence was Chromium 156 at display scale 1, and the 12 % grain left the open areas 2.5 to 3.3 L\* away from the `papier` token, which stretched the original "no color shift" brief.

## 2. What changed

- `engines.py` (new): three engine drivers behind one interface (CSS viewport, display scale, zoom). Chromium by command line (unchanged flags), Firefox over WebDriver BiDi with a throw-away profile, WebKitGTK through `webkit_capture.py` (new, one process per capture, X11 backend, `GDK_SCALE` for display scale 2, real page zoom for 200 %).
- `measure.py`: takes an engine, a display scale and a zoom; every pixel index is CSS px × device pixels per CSS px; a `narrow-320` viewport exists for explicit use; tone statistics include Δa\*, Δb\*, ΔE and the tone step between the open area and the calm plate.
- `crossbrowser.py` (new): runs the matrix (rows 1x, 2x, zoom 200 %) per engine and prints a summary.
- `background.css`: **the adopted tone is now the layer's default** (dark base `--fond`, light base `--papier`) through one custom property, `--bg-paper-base`; `data-tone="papier"` restores the 2026-11-17 rendering and `data-tone="neutral"` (light `--surface`) is kept as the rejected comparison; all fall back to `--papier` in the high-contrast, reduced-transparency and print rules.
- `study.html`: `tone=papier|neutral` query option and a Tone selector in the review controls (adopted, papier, neutral).
- `results/contrast.json` and `results/plate-sweep.json` regenerated with the adopted tone (light values unchanged, dark moved); eleven result files in total.
- README updated (status, sections 1, 3.1, 3.3, 3.4, 3.5, 5, 5.1, 5.2, 6, 7, 8).

## 3. Verification

| Check | Result |
|---|---|
| Engines | Chromium 156.0.8078.47; Firefox 159.0 (headless, BiDi); WebKitGTK 2.54.2 (Linux GTK port, **not Safari**) |
| Matrix per engine and tone | variant B, light and dark; row 1x = 5 viewports (desktop 1440×1024, tablet 768×1024, tablet-landscape 1024×768, mobile 390×844, narrow 320×568), row 2x = 4 viewports, row zoom 200 % = windows of 1440, 1024, 768 px (page sees 720, 512, 384 CSS px) → 24 configurations per engine, 72 per tone, 216 over the three tones (adopted, `papier`, `neutral`) |
| Contrast, worst pixel | **0 failures in 216 configurations.** Adopted tone: `encre-3` text 4.98 / 5.51, secondary border 3.08 / 4.04 (light / dark; WebKitGTK reads 3.11 in light at scale 2 and zoom); tightest margin anywhere: light secondary border 3.08:1 against 3:1 |
| Blueprint and plate clearance | 0 overlaps in every configuration; the plate fade ends exactly on the blueprint band on tablet and narrow widths (0 px gap, by the placement rule) |
| Default Chromium run, 16 configurations | with `tone=papier` the tool reproduces the merged 2026-11-17 `contrast.json` values and the 32 PNG SHA-256 hashes; with the adopted tone the 16 light renders are byte-identical to them and the 16 dark ones differ, as intended |
| Adopted equals its parts | on all 72 matrix configurations the adopted renders are byte-identical to the `papier` renders in light and to the `neutral` renders in dark (hash) |
| Determinism | the `papier` and `neutral` matrices were run twice, and the `neutral` one a third time after the CSS default changed: all 216 PNG files are byte-identical to the first run |
| Documentary checks | `python3 tools/validate_docs.py`, `git diff --check`: see section 8 |

**Images opened.** `papier` and `neutral` matrices: 18 contact sheets (one per engine and row, light above dark, each frame scaled to 330 px high), **all opened**. Adopted matrix: nine contact sheets assembled, the **three 1x sheets opened**; the other six were not opened, but every frame of them is byte-identical (hash) to a frame of the `papier` light or `neutral` dark sheets, which were opened. Detail was checked at device resolution on a 1:1 crop of the 2x light render in the three engines (opened), and the tone comparison (`papier`, `neutral`, flat token; open area and plate edge; light and dark) was opened. The 216 individual full-size renders were **not** opened one by one. Background-only twins were deleted after measuring (pixel analysis only).

Result files (SHA-256): `contrast.json` `a5540e833a0be594c4fc30bf3576663326dab81fe6e63cbcf99d3ba7121d1a60`; `plate-sweep.json` `f78879e5a9231f1ba6b60c575b6acfa0c7f9ad4c123cc78ed9bdb56f1c28c697`; adopted `cross-browser-chromium.json` `ac9a876d0ec235f84903de5c56c6e9b37b989d3d2c7c2f25c2757a2b409a2d2f`, `…-firefox.json` `488d2d1f79a945f987ad88252d3d5e1b12f94951134784f75306805a14515cfe`, `…-webkit.json` `c54ca217cf7274b49ba827164e7a2c50811dd0b19b0397f7e98c59ce99c10424`; `tone-papier-chromium.json` `6e846678e66c15e24c061e39c522dbe0ce3a1183c7ea7fef41d29f6a15fee23d`, `…-firefox.json` `a62f93b524aae9fec80c7cab089f12c64080fa89e1c8cadd564b3bed86574ee0`, `…-webkit.json` `336759bc1d06b53d8a57446120873710018a4248cebfbb9899dc9c4cac9fbf2b`; `tone-neutral-chromium.json` `6d4e3d0d1856d850818c370ceadf5bf00172a7df9fb791e76b11b72487558143`, `…-firefox.json` `1d8ac242684feff12762d54c2841fe655c876c46892200d6bd183a309cd93dc5`, `…-webkit.json` `6f1b5cf115bd0eb71eea0146270c17e88a39e0101773862a83d5a40fed93e170`.

Evidence outside Git (large, grain-heavy PNG files, each folder with a `SHA256SUMS.txt`):

- `~/Projets/etaye-design-receipts/2026-11-18-background-cross-browser/`: the **`papier`-tone** matrix (it was the default when rendered), 72 renders plus 10 review images; manifest SHA-256 `b6097081a403a66c6a9649ec7dff17024e3d624f66393a78c01e4aeb124ab831`.
- `~/Projets/etaye-design-receipts/2026-11-18-background-neutral-tone/`: the `neutral` (rejected) matrix, 72 renders plus nine contact sheets and `tone-compare.png`; manifest SHA-256 `9f7df9bc1d2ac537c4d411a8b57e5f21bfb525e732058d0b4c1a3992d240a90c`.
- `~/Projets/etaye-design-receipts/2026-11-18-background-adopted-tone/`: the **adopted** matrix, 72 renders plus nine contact sheets; manifest SHA-256 `554ab2d1e79141aac06dc780d3c503b681ee002c18230fc5fa495d06d0973464`.

### 3.1 Cross-browser, display scale and zoom

The three engines give the same layout and all checks pass at display scale 1 and 2 and at 200 % zoom (table in README section 5.1). The worst contrast values are identical to two decimals across engines at scale 1 (WebKitGTK is 0.03 higher on the light secondary border at scale 2 and zoom), which is consistent with feTurbulence being a specified deterministic algorithm; the open-area means differ by about 0.1 L\* between engines, so the renders are independent. Observed at device resolution: the grain is fine and the text is crisp in all three engines at scale 2.

### 3.2 Tone

| Option | Light open ΔL\* / ΔE | Dark open ΔL\* / ΔE | Plate-to-open ΔE, light / dark |
|---|---|---|---|
| `papier` (previous) | −2.54…−2.44 / 2.44…2.54 | +3.17…+3.34 / 3.18…3.36 | 1.82…1.93 / 2.78…2.87 |
| **adopted** (dark base `--fond`) | −2.54…−2.44 / 2.44…2.54 | **−0.72…−0.54 / 0.61…0.77** | 1.82…1.93 / **0.31…0.42** |
| `neutral` (rejected, light `--surface`) | +1.17…+1.28 / 3.31…3.35 | −0.72…−0.54 / 0.61…0.77 | 2.74…2.78 / 0.31…0.42 |

**Finding.** Starting the dark grain from `--fond` brings the open area to the `papier` tone (ΔE 3.3 → 0.6 to 0.8) and nearly erases the tone step around the plates (2.85 → 0.3 to 0.4). It also lifts the dark contrast (`encre-3` text 5.39 → 5.51, border 3.96 → 4.04); with no plate the dark `encre-3` text is now 4.54:1, a pass by 0.04:1, so the dark plate has become a margin rather than a necessity (the light plate is still required). In the light theme no existing token does the same: `--surface` is a cool white while `papier` is a warm cream, so lightness is corrected (+1.2 L\*) but the hue moves (Δb\* −3.1), ΔE stays 3.3 and the cream plate shows as a card on a whiter page (step 1.9 → 2.7). A second light-speck layer was considered and dropped: balancing the ink would need about three quarters of the surface covered at full alpha. The light theme therefore keeps `--papier`, with the 2.5 L\* shift Aymen accepted on 2026-11-17.

## 4. Defects and findings in this round

- **Parallel edits in the main worktree (not mine).** At the start of the round `docs/README.md`, `docs/continuation.md` and `docs/design/continuation-prompt.md` had uncommitted changes in `~/Projets/etaye` that I did not make (another agent working in parallel). I left them untouched and did all work in a separate worktree (`etaye-wt-bg-xb`) on branch `docs/background-cross-browser-tone`. They are not identical to the versions in [PR 19](https://github.com/Aymenchaghoub/etaye/pull/19) (section 6), which touches the same files, so their origin is unconfirmed. PR 19 and this PR both change `docs/continuation.md` and `docs/test-artifacts/document-validation.json`.
- Firefox BiDi rejected the websocket handshake (Origin header check); the driver now connects without an Origin header.
- WebKitGTK could not create a GL context under the Wayland backend (core dump); the helper now forces the X11 backend and software compositing. An unrelated callback signature bug in the helper was fixed.
- `--tag -neutral` was read by argparse as an option; the matrix is invoked with `--tag=-neutral`.
- No design defect of the layer was found in the new engines.

## 5. Limits and flags

- **WebKit is not Safari.** WebKitGTK shares the engine family but not the build, the graphics stack or the platform text rendering. Safari on macOS and iOS and Android WebView (the Capacitor targets) remain **To verify**.
- Firefox ran in a throw-away profile with telemetry and experiment sync switched off; background traffic of the browser itself was not monitored. The page requested only `127.0.0.1`.
- Variant A was not re-measured (no blueprint layer, a subset of B). 200 % zoom skips phone width (195 CSS px); 320 CSS px is covered at scale 1 only.
- Not tested: real devices and touch, rendering cost (nothing was timed), forced-colors, high-contrast, reduced-transparency and print rendering (the rules exist, they were not rendered), `file://`.
- The light theme keeps its 2.5 L\* open-area shift and the 1.9 plate step; only the dark theme was neutralized.
- A static HTML study measured on rendered pixels, not the application. No clinical, regulatory or accessibility compliance is claimed.

## 6. Open choices, access and Figma context

**Context read from [PR 19](https://github.com/Aymenchaghoub/etaye/pull/19) at Aymen's request (description, closure report, continuation changes; nothing was checked in Figma).** The PR is an open **draft**, CI green on its head, from branch `docs/figma-native-closure`. It reports the native desktop import completed locally in Figma Desktop: 456 desktop states in 94 sections, 28,001 editable texts, the eight S-06 roots keeping their original `26:*` IDs, protected pages `2:74` and `2:75` intact, and the twelve authorized old trial roots (`15:3`, `19:2`, `22:2`–`22:11`) removed after the replacements were verified, with a saved named version. Visual findings remain open there (S-22 badges, S-02 error marker, I-13 focus ring, S-39 PDF borders, S-18 height, text wrapping and citation placement); Figma MCP still reported the Starter call limit; the background studies were preserved as studies and not applied to the frozen import; responsive, component, token and prototype reconciliation and Aymen's approval are open.

Effects on this study: the guarded cleanup listed as pending in earlier notes is reported done there (not verified by me); any Phase 2 work on S-01, S-02 and S-25 must leave the 456 imported roots and the protected pages intact and go on new exploration frames; and item 3 should build on that state.

- **Item 3, to discuss:** preparing offline the Figma scripts and call plan for when MCP access returns and the REST limit ends (about 2026-11-22). Nothing was run against Figma in this round.
- Still open from before: whether the other provisional choices (the allow and deny scope list, both tablets) stand. Variant B is confirmed.
- Access unchanged: no plan change was made. The explicit development authorization has **not** been given and all planning stories stay in backlog.

## 7. Next actions

1. Aymen reviews the PR (`study.html?ui=1&v=b`, Tone selector, both themes) and merges it or asks for changes. PR 19 and this PR both change `docs/continuation.md` and `docs/test-artifacts/document-validation.json`: the second to merge should be updated from `main`, and `python3 tools/validate_docs.py` re-run.
2. Aymen decides whether to start item 3.
3. When MCP access returns: Figma exploration frames, then Phase 2 on S-25 first (UD-13); only after validation, DESIGN and token-reference updates.

## 8. Records and checks

Branch `docs/background-cross-browser-tone`, from `origin/main` at `eb58c81`. Committed and pushed on Aymen's explicit instruction ("commite et ouvre la PR"); merged only on a further explicit instruction.

Documentary checks (not application tests): `python3 tools/validate_docs.py` reports 0 errors and `git diff --check` is clean, run on the final tree of this round.
