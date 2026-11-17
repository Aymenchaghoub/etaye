# Background direction study — review report

Date: 2026-11-17. Author: Claude (Claude Code session). Status: **proposal evidence, not a design approval.** Update: the opacity values below (3 % light, 4.5 % dark) were superseded later the same day by the 12 % grain with calm plates, see the [calm-zones report](2026-11-17-background-calm-zones.md). The specification is in [the study README](../background-study/README.md); this report records what was verified, what failed and what was not done.

## 1. Mission and outcome

**User decision (2026-11-17):** explore one background direction, Paper texture with a restrained Blueprint touch, in the existing Figma file as a visual proposal; Phase 1 standalone frames, Phase 2 on S-01, S-02 and S-25 if they exist. After the access limits below, Aymen chose **option 3: write the specification without Figma and show examples to validate**, then provide the latest visual identity, after which development is intended to start.

**Outcome:** the specification, the consolidated identity snapshot, runnable CSS/SVG examples and a pixel-level contrast measurement are delivered. **No Figma node was created, duplicated or modified; Phase 1 and Phase 2 in Figma were not executed.** Development is not started and not authorized (see section 7).

## 2. Verified state at start and end

| Item | Observation |
|---|---|
| Git | worked from a new branch `docs/background-direction-study` created from fresh `origin/main` (`cfc86de`); the worktree was clean before switching; `docs/figma-windows-handoff` and `docs/settle-open-decisions` untouched; upstream tracking removed from the new branch to prevent an accidental push to `main`; when the study was written, PRs #16, #15 and #5 were open; **nothing was committed, pushed or merged during the study itself** (the merges that followed are recorded in the continuation log) |
| Figma file | `vfh4BSnbRAbaDE1q0CyssL`. Version **before** this session's work: `2422837510414927925` (version entry 2026-11-17T14:19:45Z, last touched 14:34:55Z). Version **after**: `2422848884517885113` (version entry 14:51:04Z, last touched 14:56:13Z). The change is not from this session (no write was made); Aymen reported finishing his own native import in the meantime |
| Figma nodes created or duplicated | **none**; removal instructions therefore reduce to deleting the study folder |
| Protected nodes (`2:74`, `2:75`, `26:2`, import output, scheduled deletions `15:3`, `19:2`, `22:2`–`22:11`) | not used, not modified |
| MCP | sign-in had expired and was redone by Aymen; `create_session` and one read-only `use_figma` inventory succeeded; the next `use_figma` was refused: "reached the Figma MCP tool call limit on the Starter plan". `whoami`: seat **View**, tier **Starter**, role admin. Classification: **plan/seat limit**, not credential or permission. No retry loop, no plan change |
| REST | token read at run time from the local file only, never printed or copied. Three tier-1 reads succeeded, then HTTP 429 with `Retry-After: 385343` s and `x-figma-rate-limit-type: low`; `/meta` and `/versions` still answered. Node-level state is therefore unreadable until about 2026-11-22. The token file has mode 644 (world-readable); not changed |
| Skills loaded | `figma-use`, `figma-generate-design` (before the only use_figma calls) |

**Read-only MCP inventory (verified):** pages `0:1` Couverture, `15:2` Étaye — Import HTML, `2:74` Fondations, `2:75` Composants; variable collections `Couleurs · Clair` (`VariableCollectionId:2:3`, one mode, 17 colors), `Couleurs · Sombre` (`2:21`, one mode, 17 colors) and `Dimensions` (`2:39`, 19 floats); 14 text styles; one effect style `ombre-flottant`. Scopes allow fill/stroke binding for the five requested roles.

## 3. Findings

1. **Light and dark are two separate single-mode collections**, not two modes of one collection. A dark Figma frame must bind the `Sombre` variables directly and existing component instances would need per-instance rebinding. This should be examined in the design-system review.
2. **S-01, S-02 and S-25 now exist in Figma**: sections `30:2` (S-01, 4 states), `30:464` and `30:941` (S-02, 9 states), `30:44609` (S-25, 7 states), all `desktop1440 · à revoir`. [coverage.csv](../coverage.csv) still has an empty `figma_node` for them and [EXISTING-FIGMA-STATE.json](../windows-handoff/EXISTING-FIGMA-STATE.json) (added by PR #16) predates them: both are stale on this point (**not changed here**). No tablet or mobile frame of these screens was seen in the page structure read at about 14:34Z, which predates the end of Aymen's import; current content is unread (REST and MCP limits).
3. **Logos and components are real components**: `Logo/Clair` `4:13`, `Logo/Monochrome` `4:19`, `Logo/Espace patient` `4:20`, `Logo/Sombre` `4:33`, `Bouton` set `5:22`, `Statut` set `5:46`, `Repère` `5:47`: instances are possible without redrawing.
4. **Figma native support (verified in the Plugin API typings):** `NOISE` effects (monotone, duotone, multitone) and `TEXTURE` effects exist but their colors cannot be bound to variables and they have no exact CSS equivalent; `PATTERN` paints exist. This is why the delivered technique is CSS/SVG.
5. **Existing component rules** in `design/received/2026-11-03/ds/bundle-base.css` are identical to the corrected prototype (checked for the primary, secondary and patient button rules). The primary button is an `encre` fill, not an accent fill.
6. **Token equality:** 34 color values (17 roles × 2 themes) read from Figma variables equal `tokens.css`; 0 mismatches.
7. **Contrast:** all 12 configurations pass after tuning; the first value tried (5 % in light) failed on the secondary button border (2.92:1 against 3:1), so light grain was lowered to 3 %. Table and sensitivity in the README.

## 4. Defects found in my own tooling (corrected, listed so the numbers can be trusted)

- A `--` sequence inside an XML comment of the tile made the SVG invalid, so the grain silently disappeared; a standalone render looked non-empty only because it showed the browser's parse-error text.
- `--dump-dom` reported a viewport 87 px shorter than the screenshot, shifting measured boxes by about 43 px; fixed by measuring inside an iframe of exact size, plus an alignment self-check on the primary button.
- A 140 ms color transition in the prototype CSS could be read mid-flight in dark theme; transitions are disabled in the study.
- Headless Chromium refuses windows narrower than 500 px; the 390 px mobile viewport is obtained from the iframe.
- Overlap logic counted touching rectangles and a hidden zero-size logo as overlaps; it now tests strict intersection and measures a wrapper.
- First visual passes showed a woven, linen-like texture (fibers too long and regular) and too dense a grain (mean tone drift); both were retuned before measuring.

## 5. Evidence

Tool: [measure.py](../background-study/measure.py), output [contrast.json](../background-study/results/contrast.json) (SHA-256 `2f58fdfb5da3bd0fa548048e19eb432235726642f5af9a2266124951e877a93d`). Browser: Chromium 156.0.8078.47, device scale factor 1. All 12 reviewed PNG renders and three zoom crops were **opened** (ten of the twelve before a last one-paragraph change that only affected how the source sentence wraps on tablet; the two tablet renders affected were reopened afterwards). The 12 background-only twins were **not viewed**: they exist only for pixel analysis. Everything lives outside Git in `/home/aymen.chaghoub/Projets/etaye-design-receipts/2026-11-17-background-study/` (manifest `SHA256SUMS.txt`, digest `dd4e8f8b570734a5834bd8975ccef2dd2a4251ac6ab956e8173645dbd97f7a09`).

| Variant | Viewport | Theme | SHA-256 of the reviewed PNG |
|---|---|---|---|
| A | desktop | light | `50f2a9720946509beee18e7493d5d1eec281813411d553d901cdad4adb371240` |
| A | desktop | dark | `1c23de0f5c6df9b2512c5eccfb9a72cbb9ef782b348247b4e4a421e152ace54d` |
| A | tablet | light | `ce6737e6187af441d8ee6b57ad2377d45c9e73e66d86a0301d1929113512094e` |
| A | tablet | dark | `769230147191b628ce8d20a9282db886209a1ac5fb8fafe7c0ef6d5567880d23` |
| A | mobile | light | `b33927f4014599091543aa5c566c1e4251240c63e213f070695ddec0a8798aed` |
| A | mobile | dark | `cfa4fa6a60b4d27f4848f06e8d6b8f7970392f2db9ff86e2622aa8b63017e59c` |
| B | desktop | light | `59110c7b58423816349725b9b997e2953d5465eb4260f2b873ebb599a832316c` |
| B | desktop | dark | `61ecf06e5bbec0de1ac35e92915bd045d0cf9211bd087600fb8b6855e20c90c6` |
| B | tablet | light | `d40f1d62d8cf402c46722a720f0e65ede061f057a82785dda63633225a42d8de` |
| B | tablet | dark | `45b747504ee488e30b3c842ff2887eb2d212ce653bdfd7935eea84a1be5504c5` |
| B | mobile | light | `44123f2596d04cab4ff669cd3e7ec34cb01857461a496b9520fef90a3c1382d8` |
| B | mobile | dark | `a75bfb3b737f3af0b2f4ef56727877ea124872c3e75fa4a919a1e654d0002e8a` |

Limits: Chromium only; no Safari/WebKit, no Android WebView, no device scale factor above 1, no 200 % zoom, no assistive technology, no low-end device timing; static HTML study, not the application; the worst-pixel criterion is stricter than the usual flat-pair reading. No clinical, regulatory or accessibility compliance is claimed.

## 6. Open choices and access

- Aymen's first reaction to the examples (screenshot of the review controls, grain slider at 12 %, he said he loves the result in light and dark): he prefers a visibly textured paper. Measured at 12 %, it fails: light `encre-3` text 4.06:1 and secondary-button border 2.51:1, dark `encre-3` text 4.11:1; dark at 9 % passes everything. Options awaiting his choice: (1) light 3 % and dark 9 %; (2) 12 % in margins and open areas with a calmer grain behind text and controls, not built; (3) 12 % uniform with small meta text moved off `encre-3` and an opaque halo for the secondary button.
- Aymen's choice among variant A, variant B or neither; the tuned opacities; the candidate screen scope; the tablet reference viewport (768×1024 used as requested, catalog says 1024×768). The single-letter message "a" received mid-session was not interpreted as a choice.
- Access: Figma MCP quota (plan/seat limit) and REST tier-1 rate limit (until about 2026-11-22). Requesting more is a plan decision for Aymen; none was made.

## 7. Development gate

Aymen said development will start after he validates the examples and the visual identity. That is recorded as an intention, **not** as the explicit development authorization required by AGENTS.md: the design review is incomplete (456 states, none approved; coverage rows not approved; responsive, component and token reconciliation pending). All planning stories remain in planning/backlog.

## 8. Next actions

1. Aymen reviews the examples and answers the choices in section 6.
2. When MCP access returns (and the tablet and mobile frames can be read), build the exploration page in Figma with variant-specific frames, then Phase 2 on S-25 first (UD-13), S-02, S-01.
3. Only after validation: propose changes to DESIGN and the token reference, update the coverage tracker rows with real node IDs, and record Aymen's explicit development authorization if and when he gives it.
