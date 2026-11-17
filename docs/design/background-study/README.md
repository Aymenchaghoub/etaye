# Background direction study — paper texture with a blueprint touch

Status: **Proposal, not approved.** Created 2026-11-17 for Aymen's review. Nothing here changes [DESIGN](../../planning-artifacts/ux/DESIGN.md), the [token reference](../token-reference.md), the [coverage tracker](../coverage.csv) or any Figma node. No application code exists or is authorized. All people and data in the examples are the fictional ones of the [synthetic dataset](../synthetic-data.md).

Evidence labels: **User decision**, **Verified observation**, **Proposal**, **[ASSUMPTION]**, **To verify**.

## 1. Scope and how this was produced

**User decision:** explore ONE background direction, Paper texture (main) with a restrained Blueprint touch (secondary), as a visual proposal, so that Étaye stops looking generic. The anchor stays the real logo and one readable source relationship (AC-S01-V).

**Verified observation:** the Figma exploration page could not be built on 2026-11-17. The MCP server refused the second file call ("tool call limit on the Starter plan"; `whoami`: seat View, tier Starter, team admin) and REST node reads returned HTTP 429 with `Retry-After: 385343` (about 4.5 days, rate-limit type `low`). Aymen then chose option 3: write the specification without Figma and show rendered examples. The examples are therefore **real HTML/CSS rendered in Chromium 156**, the technique a developer would actually ship, not Figma mock-ups. Details and the verification log: [review report](../reviews/2026-11-17-background-direction-study.md).

## 2. Latest visual identity (consolidated snapshot)

Existing values only; sources are named so nothing is estimated. **Verified observation (2026-11-17):** every color below in [tokens.css](../../../design/received/2026-11-03/export/etaye-design-system/tokens/tokens.css) equals the value read from the Figma variable collections `Couleurs · Clair` and `Couleurs · Sombre` (26 colors compared, plus the 8 clinical roles).

| Role (Figma name) | Clair | Sombre | Use |
|---|---|---|---|
| `surface/fond` | `#efeeea` | `#0f1012` | quiet framing, today's page background |
| `surface/surface` | `#ffffff` | `#1a1c1f` | work areas, forms, panels (opaque) |
| `surface/surface-2` | `#f7f6f3` | `#222529` | secondary surface |
| `surface/papier` | `#f6f4ee` | `#17181a` | reading and document areas |
| `texte/encre` | `#1b1b18` | `#ece9e1` | primary text, primary button fill |
| `texte/encre-2` | `#57534a` | `#aba69b` | secondary text |
| `texte/encre-3` | `#6b655a` | `#969189` | meta text |
| `texte/encre-inverse` | `#f6f4ee` | `#1b1b18` | text on the ink fill |
| `filet/filet` | `#dedad0` | `#2e3136` | hairlines, decorative borders |
| `filet/filet-controle` | `#8c877c` | `#767b82` | control borders |
| `accent/accent` | `#2b4c7e` | `#8faadb` | source marker, links, validation |
| `accent/accent-fond` | `#e8edf5` | `#1d2738` | accent wash |
| `accent/sur-accent` | `#ffffff` | `#0f1012` | text on accent fills |

Clinical roles (`critique`, `attention` and their `-fond`) are unchanged and never sit on the background layer.

- **Typography (existing):** Atkinson Hyperlegible Next (interface), Atkinson Hyperlegible Mono (data and source numbers), Source Serif 4 (reading and documents); 14 roles from `ui-titre` 22/28 to `patient-action` 18/24, exact values in the token reference. Fonts are the OFL files under `design/received/2026-11-03`.
- **Spacing and shape (existing):** `espace-1..16` = 4, 8, 12, 16, 20, 24, 32, 40, 48, 64 px; `rayon-0..3` = 0, 4, 8, 12 px; `trait/filet` 1 px; targets 32 px (clinician) and 48 px (patient).
- **Logo (existing):** Figma components `Logo/Clair` `4:13`, `Logo/Sombre` `4:33`, `Logo/Monochrome` `4:19`, `Logo/Espace patient` `4:20`; original SVGs in `docs/design/figma/assets`. Reused as-is, never recolored, redrawn or stretched; the studies use `logo-light.svg` / `logo-dark.svg` unchanged.
- **Components (existing, reused unchanged from the received design system):** primary button = `encre` fill with `encre-inverse` label; secondary = `surface` fill with a `filet-controle` border; source marker `Repère` = 11 px mono, `accent`.
- **Signature (existing):** the dated note, the insertion mark paired with a source number. Visual richness belongs in chronology and evidence, not in decoration.
- **Proposed addition:** the background layer below. It is the only new element and introduces **no new color**: it uses `papier`, `encre`, `filet`, `accent-fond` and `fond`.

## 3. The proposal

Two independent layers behind public and entry surfaces. Everything is plain CSS and SVG, static, no network request, no animation, no raster photo, no plugin.

### 3.1 Paper (main)

Fill `--papier`, plus one tiled grain: very fine paper tooth with short, faint fibers in two directions. No gradient, no hue change, no animation.

| Parameter | Value |
|---|---|
| Base fill | `var(--papier)` |
| Grain | `::after` filled with `var(--encre)`, masked by `assets/paper-grain.svg` |
| Tile | **256 × 256 CSS px**, seamless (`stitchTiles`), `mask-size: 256px 256px`, `repeat` |
| Tile content | `feTurbulence fractalNoise` baseFrequency 0.9, 2 octaves, seed 4 → alpha = 4·R − 2; fibers: baseFrequency `0.055 0.8` seed 9 and `0.8 0.055` seed 21 → alpha = 6·R − 3.4, each added at weight 0.35 |
| Opacity (grain) | **0.03 light, 0.045 dark** (`--bg-grain-opacity`) |
| Mean lightness shift | −0.68 L\* light, +1.15 L\* dark (measured); hue unchanged (neutral ink) |

Why a mask: only the tile's alpha matters, so the visible color is always the theme token and follows light/dark with no second asset. The grain only darkens (light theme) or lightens (dark theme); the average paper is therefore slightly off the token, by the amounts above. **Proposal for exact mean neutrality (not built):** a second pseudo-element with a light speck (`--surface`) balancing the dark one.

### 3.2 Blueprint touch (secondary)

Survey-sheet vocabulary, restrained: two L-shaped rulers (top-left, bottom-right) and two registration marks (top-right, bottom-left). Never a grid, never full-surface, never cyan.

| Parameter | Value |
|---|---|
| Unit | `espace-2` = 8 px and `espace-6` = 24 px; all positions multiples of 8 |
| Ruler module | 192 × 192 px (8 × `espace-6`) anchored in the corner; the bottom-right one is the same symbol with `rotate(180deg)` |
| Band | 16 px (`espace-4`) tinted `var(--accent-fond)` along both edges of the corner |
| Hairline | 1 px at 8 px (`espace-2`) from the edge, `var(--filet)` |
| Ticks | 1 px thick, `var(--encre)` at `--bg-tick-opacity` (**0.32 light, 0.38 dark**); minor every 8 px and 4 px long, major every 24 px and 8 px long, pointing inward |
| Fade | linear alpha mask: opaque for the first 96 px, then 0 at 192 px (CSS equivalent: `mask-image: linear-gradient`) |
| Registration marks | 16 × 16 px box at 8 px from the top-right and bottom-left corners: circle r = 4 plus a 16 px crosshair, 1 px `var(--encre)` at tick opacity |
| Clear space | content at the usual 24 px page padding keeps ≥ 8 px from every drawn mark (measured 8 / 16 / 24 px on mobile / tablet / desktop) |

The mask gradients carry alpha only; their `#fff` stops are not a design color.

### 3.3 Markup, switches and files

The layer is one decorative element at the bottom of the page host; the symbols are declared once in the application shell (copy them from `study.html`).

```html
<div class="etaye-bg-host">                       <!-- position: relative; min-height: 100vh -->
  <div class="etaye-bg" aria-hidden="true">        <!-- add data-paper="off" and/or data-blueprint="off" -->
    <div class="etaye-bg__paper"></div>
    <div class="etaye-bg__blueprint">
      <svg class="etaye-bp-ruler" focusable="false"><use href="#etaye-bp-ruler-sym"/></svg>
      <svg class="etaye-bp-ruler etaye-bp-ruler--end" focusable="false"><use href="#etaye-bp-ruler-sym"/></svg>
      <svg class="etaye-bp-mark etaye-bp-mark--tr" focusable="false"><use href="#etaye-bp-mark-sym"/></svg>
      <svg class="etaye-bp-mark etaye-bp-mark--bl" focusable="false"><use href="#etaye-bp-mark-sym"/></svg>
    </div>
  </div>
  <!-- page content, on opaque surfaces wherever it holds forms, tables or clinical data -->
</div>
```

- **Variant A** = paper only (`data-blueprint="off"`); **variant B** = paper + blueprint. The two layers never depend on each other; `data-paper="off"` restores today's plain `--fond`.
- Page wrappers must not keep an opaque background of their own, or they hide the layer.
- Files: [background.css](background.css) (the layer), [assets/paper-grain.svg](assets/paper-grain.svg) (the tile), [study.html](study.html) (runnable example and the blueprint symbols), [frame.html](frame.html) and [measure.py](measure.py) (measurement), [results/contrast.json](results/contrast.json) (output).

### 3.4 Offline, performance, safety

- **Offline:** the tile is a same-origin static file of about 1.6 KB; precache it with the application shell (web, PWA, Capacitor). Nothing is fetched at runtime. The blueprint is inline SVG: no file.
- **CSP:** serving the tile as a file keeps `img-src 'self'`. Inlining it as a `data:` URI works but needs `img-src data:`, which is not recommended for this project.
- **Performance:** one repeated 256 px mask, no animation, no filter applied to the live page (the filter is inside the tile image), `contain: strict` on the layer. **To verify:** per-engine rasterization cost of the tile and behavior on low-end phones; nothing was timed.
- **Accessibility:** the layer is `aria-hidden`, has no events, and is switched off under `forced-colors: active`, `prefers-contrast: more`, `prefers-reduced-transparency: reduce` and `print`. Never in PDF templates or e-mails.
- **Portability:** rendered and measured in Chromium 156 at device scale factor 1 only. **To verify:** Safari/WebKit and Android WebView (the Capacitor targets), where a pre-rasterized 256 px alpha PNG tile is the planned fallback if feTurbulence-as-mask differs. Opening via `file://` was not tested; serve over HTTP, as the application will.
- **Figma:** nothing was built in Figma, so there is no Figma-only effect to flag. If designers reproduce it later: Figma's native `NOISE` effect cannot bind a color variable and has no exact CSS equivalent (flag it then); a PATTERN/vector tick run is the CSS-equivalent route.

## 4. Where it may and may not appear

**Proposal (needs Aymen's decision):**

- Allowed candidates: entry and public surfaces (S-01 showcase, S-02 login backdrop, S-03/S-04 backdrop) and the patient home header zone (S-25), patient screens first (UD-13). Forms, panels and clinical-looking content sit on an opaque `--surface` panel that fully covers the layer.
- Never: tables, forms, clinical data, source panels, document viewers, PDF templates, e-mails, and the whole practitioner workspace (S-06 to S-24 and administration), which stays on plain surfaces.

## 5. Measured contrast (real pixels)

**Method (Verified observation):** every configuration, variant A and B × light and dark × desktop 1440×1024, tablet 768×1024 and mobile 390×(page height), was rendered twice. The second render hides every glyph, border and the logo but keeps all fills, so it shows exactly what sits behind each element (grain, blueprint margin, opaque panel, button fill). The **worst single pixel** under each element's box is compared with its real computed color, using WCAG 2.x relative luminance. Thresholds: 4.5:1 text, 3:1 for text ≥ 24 px (or ≥ 18.66 px bold) and for control boundaries. The worst pixel is a full-coverage grain pixel, so the minimum is also an analytic lower bound independent of resolution. The logo is a logotype and exempt. Panel text is measured on its opaque surface.

| Element (token, size) | Required | Light, worst | Dark, worst |
|---|---|---|---|
| Notice and note (`encre-3`, 12 / 14 px) | 4.5 | **4.89** | **5.13** |
| Heading (`encre`, 28 px) | 3 (4.5 also met) | 14.61 | 13.25 |
| Lead (`encre-2`, 18 px) | 4.5 | 6.48 | 6.63 |
| Token samples `encre` / `encre-2` / `encre-3` / `accent` (14 px) | 4.5 | 14.61 / 6.48 / 4.89 / 7.29 | 13.25 / 6.63 / 5.13 / 6.84 |
| Primary button label (`encre-inverse` on `encre`, 18 px) | 4.5 | 15.69 | 14.23 |
| Primary button edge vs layer | 3 | 14.61 | 13.25 |
| Secondary button label (`encre` on `surface`) | 4.5 | 17.26 | 14.07 |
| **Secondary button border vs layer** (`filet-controle`) | 3 | **3.03** | **3.77** |
| Panel statement, source line, quote, status (on `surface`) | 4.5 | ≥ 5.78 | ≥ 5.45 |
| Source marker (`accent`, 11 px mono, on `surface`) | 4.5 | 8.61 | 7.27 |

Result: **all 12 configurations pass**; no blueprint element overlaps any content (minimum clearance 8 / 16 / 24 px). Static token pairs that never touch the layer: `encre-inverse` on `encre` 15.69 / 14.23, `sur-accent` on `accent` 8.61 / 8.10, `critique` on its fond 5.70 / 7.27, `attention` on its fond 5.35 / 7.14 (light / dark).

**Tuning and one failure (to report, not hide).** The first value tried, 5 % in light, **failed**: the secondary button border dropped to 2.92:1 (flat token pair 3.25:1). Sensitivity, worst pixel:

| Grain opacity | ΔL\* light | `encre-3` 12 px | Secondary border (≥ 3) | Dark: border | Dark: `encre-3` |
|---|---|---|---|---|---|
| 0 (flat) | 0 | 5.25 | 3.25 | 4.17 | 5.68 |
| 0.02 | −0.44 | 4.98 | 3.08 | 3.99 | 5.44 |
| **0.03** | −0.68 | 4.89 | **3.03** | 3.87 | 5.27 |
| 0.035 | −0.77 | 4.85 | 3.00 | 3.83 | 5.21 |
| 0.04 | −0.84 | 4.80 | 2.97 ✗ | 3.78 | 5.15 |
| 0.05 | −1.09 | 4.72 | 2.92 ✗ | 3.69 | 5.03 |
| 0.08 | −1.64 | 4.46 ✗ | 2.76 ✗ | 3.42 | 4.66 |

Light is therefore limited by the secondary border (≤ 0.035) and dark by `encre-3` text (≤ about 0.09). The chosen 0.03 / 0.045 keep a margin of 0.03:1 on the light border and 0.63:1 on dark `encre-3` text. This is a documentary design measurement: no audit, no assistive-technology test, no other browser, no 200 % zoom.

Run it again with `python3 docs/design/background-study/measure.py --out <dir> --json docs/design/background-study/results/contrast.json` (Chromium, Pillow and NumPy required; the script starts its own local server; `--sweep` runs the opacity table). The 12 full-size PNG renders and their background-only twins are kept outside Git because the grain makes them large: `/home/aymen.chaghoub/Projets/etaye-design-receipts/2026-11-17-background-study/`, SHA-256 in the review report.

## 6. Look at it

Serve the repository root and open the page with the review controls (paper, blueprint, dark theme, grain slider):

```
python3 -m http.server 8765 --bind 127.0.0.1      # from the repository root
http://127.0.0.1:8765/docs/design/background-study/study.html?ui=1&v=b
```

Query options: `v=a|b`, `t=clair|sombre`, `grain=0.03`, `tick=0.32`, `paper=off`, `bp=off`, `ui=1`.

## 7. Decisions for Aymen

1. Keep the direction? Choose **A (paper only)**, **B (paper + blueprint touch)**, or neither.
2. Accept the tuned opacities (0.03 light, 0.045 dark), or ask for a stricter or looser rule (worst pixel versus median)?
3. Accept the candidate screen list in section 4?
4. Tablet frame: the studies use 768 × 1024 as requested; the screen catalog's tablet review viewport is 1024 × 768. Which one is authoritative?
5. After validation, the Figma frames and, if wanted, the revision of DESIGN and the token reference follow; they are blocked today only by the access limits above.

## 8. Not done

- No Figma page, frame, variable binding or node was created or changed; Phase 2 (applying the winner to S-01, S-02 and S-25) was not run. S-01 `30:2`, S-02 `30:464` / `30:941` and S-25 `30:44609` now exist in Figma, desktop 1440 only; no tablet or mobile frames of those screens were observed (node reads were blocked).
- No change to authoritative design artifacts, to any approval column, or to application code. No development is authorized by this study.
