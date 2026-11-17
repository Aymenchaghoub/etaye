# Background calm zones — review report

Date: 2026-11-17. Author: Claude (Claude Code session). Status: **proposal evidence, not a design approval.** It continues the [first review report](2026-11-17-background-direction-study.md), whose opacity values (3 % light, 4.5 % dark) are superseded here; the specification is the [study README](../background-study/README.md).

## 1. Trigger and decisions

**Verified observation:** Aymen answered the first examples with a screenshot of the review controls (paper and blueprint on, grain slider at its maximum, 12 %) and said he loved the result in light and dark. **Measured at 12 % with no plate** (worst pixel): light `encre-3` text 4.06:1 and secondary-button border 2.51:1, dark `encre-3` text 4.11:1, all below the 4.5:1 and 3:1 thresholds, so the rendering he liked fails the contrast rule he set.

**Proposal made:** keep the 12 % grain in open areas and lay a calmer plate behind text and controls ("option 2"); alternatives were a flat 3 % / 9 % grain (option 1) and a uniform 12 % with a token-usage change (option 3).

**User decision (2026-11-17):** "je prends tes recommandations sur les points ouverts". Only the grain recommendation had been stated explicitly beforehand. The others were formulated at the time of acceptance and applied provisionally, each reversible: variant **B** (it is what the screenshot showed), the allow and deny scope list unchanged, and both tablet viewports measured (768×1024 as requested, 1024×768 per the screen catalog) rather than choosing one.

## 2. What changed

- `background.css`: grain 0.12 in both themes; new `.etaye-calm` plate (opacity 0.8, solid margin 8 px, two-axis alpha-mask fade of 32 / 24 / 4 px by width, placement rule reach ≤ page padding − 16 px); plates switched off in the same contexts as the layers; `data-calm="off"` switch.
- `study.html`: the hero is a calm block; `calm`, `calmop` query switches and a review control; plate geometry exported for measurement.
- `measure.py`: fourth viewport (1024×768), plate reach added to the blueprint clearance test, statistics of the open area and of the reading area, plate-opacity sweep.
- README rewritten with the decisions, parameters and results; `results/contrast.json` regenerated (16 configurations) and `results/plate-sweep.json` added. No authoritative design artifact, tracker row or Figma node touched.

## 3. Verification

| Check | Result |
|---|---|
| Configurations | 16 (variant A and B, light and dark, desktop 1440×1024, tablet 768×1024, tablet landscape 1024×(page height), mobile 390×(page height)) |
| Contrast, worst pixel | **0 failures**; tightest: secondary border 3.08 / 3.96, `encre-3` text 4.98 / 5.39 (light / dark); full table in the README |
| Blueprint and plate clearance | 0 overlaps; the plate fade ends exactly at the blueprint band on tablet and phone widths |
| Mean tone vs `papier` | open areas −2.53 L\* light and +3.32 L\* dark; reading areas −0.61 and +0.48 |
| Documentary checks | `python3 tools/validate_docs.py` 0 errors; `git diff --check` clean (documentary, not application tests) |

All 16 reviewed PNG renders and the three comparison crops of this round were **opened**; the 16 background-only twins were **not viewed** (pixel analysis only). Tool: [measure.py](../background-study/measure.py), output [contrast.json](../background-study/results/contrast.json) (SHA-256 `28c2eca91e862cba0b3709249d511379e9fa60a6b2b1880b05063582d076378a`), Chromium 156.0.8078.47, device scale factor 1. Full-size renders live outside Git in `/home/aymen.chaghoub/Projets/etaye-design-receipts/2026-11-17-background-study-calm/` (manifest `SHA256SUMS.txt`, digest `57c4be178799f579d2f6e9d894ebf4da993786add1862dbcb8901f8040f0097b`).

| Variant | Viewport | Theme | SHA-256 of the reviewed PNG |
|---|---|---|---|
| A | desktop | light | `f46d613e385bed9c4c030bf53834918177613dbddb54765401d1f1e1aa5a04a7` |
| A | desktop | dark | `e7f726784539d8f4ebb09b7462ee514aa8031f518a3f0d2bdbd01f97e3013750` |
| A | tablet | light | `c8ebac0f3f46ebf0882ecd8e669441a48317c73534d2fd08ad1a4264ddec6198` |
| A | tablet | dark | `8a02f1423307b7d3ff109f96d5950169c98de74352c7435176692a57f9c9d4a8` |
| A | tablet-landscape | light | `9dc3126bc590963b6bd4118af0cbb2b47a444c592ab7e06e99c6089f19909f17` |
| A | tablet-landscape | dark | `684a9ed5f89d1e673c0d3b3f34f7015e587e5332530fade45a38511d54f6ebe7` |
| A | mobile | light | `ae196bf003268965fe998288cac2e4b521a2f87b2576b037691dbe0cbaf56f21` |
| A | mobile | dark | `c6ed8abeeeb08852ea6c9e7db2ed6f12b1ca5fbe23a9b2e04246e67f5d6402f8` |
| B | desktop | light | `667b6dd040d666c19de459e2244c9549f84f53a414c270027e1194c6dccd4a73` |
| B | desktop | dark | `e24d786e733e987c315a6583cecb91ed243754a8e54121ad9a9085a95981eac6` |
| B | tablet | light | `57022173f8631ba12b140d926984a8fc7b81ab93f4de9521a0b36d802d37ad5a` |
| B | tablet | dark | `e90d39764abf36b51a184587794f7eda0a05b0f29dbb0ac9405afaa188ec818c` |
| B | tablet-landscape | light | `08d87e0c5e18b22377f338acda8a9a1aef26feefbecb5458a6345a46f77b2981` |
| B | tablet-landscape | dark | `a1a63b60b20aebd0c74c16e3e75f77ffd59a87e28ab1fe42d7c8b6fa651825ad` |
| B | mobile | light | `71527794650b29e5640c74b91ee96f9151da2007f307ed5f31f6e619b9275c51` |
| B | mobile | dark | `1140b0566d17ca90ccd06322f6b4fdec88ccdc1be000cef5d6299741ab32c5aa` |

## 4. Defects found in this round (corrected)

- The first plate faded by a blurred shadow, which left a visible step at its edge, most visible in dark theme; replaced by a two-axis alpha mask.
- The 32 px fade overlapped the blueprint band on tablet widths (page padding 48 px); the fade is now tiered and the placement rule (reach ≤ page padding − 16 px) is documented and tested.
- The per-theme grain override left over from the flat setting was removed so the 12 % applies to both themes.

## 5. Limits and flags

- **Against the original brief ("no color shift"):** at 12 % the open-area average is 2.5 to 3.3 L\* off the token; hue is neutral and the reading areas stay within 0.6 L\*. An exact-mean dual-tone variant is proposed, not built.
- **Narrow screens:** the 4 px fade makes the reading area read as a flat sheet on a textured page; to be judged by Aymen.
- Chromium only at device scale factor 1; no Safari/WebKit, Android WebView, low-end timing, 200 % zoom or assistive technology; `mask-composite` support outside Chromium is **To verify**. A static HTML study, not the application. No clinical, regulatory or accessibility compliance is claimed.

## 6. Open choices and access

- Whether the provisional recommendations stand (variant B, scope, both tablets), whether the 12 % look is accepted with its tone shift, and whether to build the dual-tone option.
- Access: Figma MCP quota (plan/seat limit) and REST tier-1 rate limit (until about 2026-11-22); no plan change was made. The explicit development authorization has **not** been given and all planning stories stay in backlog.

## 7. Next actions

1. Aymen reviews the examples again (`study.html?ui=1&v=b`) and confirms or changes the provisional choices.
2. When MCP access returns, build the exploration page in Figma (768 portrait and 1024×768 tablet frames), then Phase 2 on S-25 first (UD-13), S-02, S-01.
3. After validation only: propose DESIGN and token-reference updates and update the coverage rows with real node IDs.
