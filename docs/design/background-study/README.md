# Background direction study — paper texture with a blueprint touch

Status: **Proposal. On 2026-11-17 Aymen accepted the recommended working direction (variant B with calm zones); this is not a design approval.** Nothing here changes [DESIGN](../../planning-artifacts/ux/DESIGN.md), the [token reference](../token-reference.md), the [coverage tracker](../coverage.csv) or any Figma node. No application code exists or is authorized. All people and data in the examples are the fictional ones of the [synthetic dataset](../synthetic-data.md).

Evidence labels: **User decision**, **Verified observation**, **Proposal**, **[ASSUMPTION]**, **To verify**.

## 1. Scope and how this was produced

**User decision:** explore ONE background direction, Paper texture (main) with a restrained Blueprint touch (secondary), as a visual proposal, so that Étaye stops looking generic. The anchor stays the real logo and one readable source relationship (AC-S01-V).

**Verified observation:** the Figma exploration page could not be built on 2026-11-17. The MCP server refused the second file call ("tool call limit on the Starter plan"; `whoami`: seat View, tier Starter, team admin) and REST node reads returned HTTP 429 with `Retry-After: 385343` (about 4.5 days, rate-limit type `low`). Aymen then chose to write the specification without Figma and show rendered examples. The examples are therefore **real HTML/CSS rendered in Chromium 156**, the technique a developer would ship, not Figma mock-ups. Details and the verification log: [first review report](../reviews/2026-11-17-background-direction-study.md) and [calm-zones report](../reviews/2026-11-17-background-calm-zones.md).

**User decision (2026-11-17, second round):** Aymen reviewed the examples and loved a much stronger grain (12 %, slider at its maximum) in light and dark. Measured at 12 %, small `encre-3` text and the light secondary-button border fail contrast, so the grain is now strong in open areas and calmer behind text and controls ("calm plates", section 3.3). Aymen then took the recommendations on every open point (section 7).

## 2. Latest visual identity (consolidated snapshot)

Existing values only; sources are named so nothing is estimated. **Verified observation (2026-11-17):** every color below in [tokens.css](../../../design/received/2026-11-03/export/etaye-design-system/tokens/tokens.css) equals the value read from the Figma variable collections `Couleurs · Clair` and `Couleurs · Sombre` (34 values compared: 17 roles in two themes, 0 mismatches).

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
- **Proposed addition:** the background layer below (paper grain, blueprint touch, calm plates). It is the only new element and introduces **no new color**: it uses `papier`, `encre`, `filet`, `accent-fond` and `fond`.

## 3. The proposal

Layers behind public and entry surfaces. Everything is plain CSS and SVG, static, no network request, no animation, no raster photo, no plugin.

### 3.1 Paper (main)

Fill `--papier`, plus one tiled grain: very fine paper tooth with short, faint fibers in two directions. No gradient, no hue change, no animation.

| Parameter | Value |
|---|---|
| Base fill | `var(--papier)` |
| Grain | `::after` filled with `var(--encre)`, masked by `assets/paper-grain.svg` |
| Tile | **256 × 256 CSS px**, seamless (`stitchTiles`), `mask-size: 256px 256px`, `repeat` |
| Tile content | `feTurbulence fractalNoise` baseFrequency 0.9, 2 octaves, seed 4 → alpha = 4·R − 2; fibers: baseFrequency `0.055 0.8` seed 9 and `0.8 0.055` seed 21 → alpha = 6·R − 3.4, each added at weight 0.35 |
| Opacity (grain, open areas) | **0.12, light and dark** (`--bg-grain-opacity`) |
| Mean lightness shift, open areas | **−2.53 L\* light, +3.32 L\* dark** (measured); hue unchanged (neutral ink) |

**Flag against the original brief ("no color shift"):** at 12 % the average open-area paper is visibly off the token (about 2.5 to 3.3 L\*). The hue stays neutral and the reading areas stay within 0.6 L\* of the token (section 3.3), but the brief is stretched here. **Proposal for exact mean neutrality (not built):** a second pseudo-element with a light speck (`--surface`) balancing the dark one.

Why a mask: only the tile's alpha matters, so the visible color is always the theme token and follows light/dark with no second asset. **Fallback (flat grain, no plates):** `--bg-grain-opacity` 0.03 in light and up to 0.09 in dark pass every check without plates (measured in the first report).

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
| Clear space | content at the usual 24 px page padding keeps ≥ 8 px from every drawn mark (8 / 16 / 24 px on phone / tablet / desktop) |

The mask gradients carry alpha only; their `#fff` stops are not a design color.

### 3.3 Calm plates (reading areas)

A block that carries small meta text (`--encre-3`) or an outline control takes the class `etaye-calm`. It lays a `--papier` plate over the grain, which cuts the grain behind the block to (1 − plate opacity) of its strength: 12 % × (1 − 0.8) = **2.4 %**, inside the limits of section 5.

| Parameter | Value |
|---|---|
| Plate | `::before` of the block, `--papier`, `opacity: var(--bg-calm-opacity)` = **0.8** |
| Solid margin around the block | `--bg-calm-inset` = **8 px** (4 px on phones) |
| Fade | `--bg-calm-feather` = **32 px** (≥ 1100 px wide), **24 px** (640 to 1099 px), **4 px** (< 640 px), by a two-axis alpha mask (`mask-composite: intersect`), not a shadow, so the edge has no step |
| Placement rule | plate reach (inset + feather) ≤ page padding − 16 px band: 40 / 32 / 8 px for paddings of 64 / 48 / 24 px |
| Minimum opacity that passes | about 0.7 in light (border 3.00:1), about 0.5 in dark |
| Effect on mean tone | reading area −0.61 L\* light, +0.48 L\* dark |

Design note: on phones the plate fade is short, so the reading area reads as a flat sheet on a textured page. Open areas outside plates keep the full grain.

### 3.4 Markup, switches and files

The layer is one decorative element at the bottom of the page host; the symbols are declared once in the application shell (copy them from `study.html`).

```html
<div class="etaye-bg-host">                       <!-- position: relative; min-height: 100vh; data-calm="off" disables plates -->
  <div class="etaye-bg" aria-hidden="true">        <!-- add data-paper="off" and/or data-blueprint="off" -->
    <div class="etaye-bg__paper"></div>
    <div class="etaye-bg__blueprint">
      <svg class="etaye-bp-ruler" focusable="false"><use href="#etaye-bp-ruler-sym"/></svg>
      <svg class="etaye-bp-ruler etaye-bp-ruler--end" focusable="false"><use href="#etaye-bp-ruler-sym"/></svg>
      <svg class="etaye-bp-mark etaye-bp-mark--tr" focusable="false"><use href="#etaye-bp-mark-sym"/></svg>
      <svg class="etaye-bp-mark etaye-bp-mark--bl" focusable="false"><use href="#etaye-bp-mark-sym"/></svg>
    </div>
  </div>
  <section class="etaye-calm"><!-- text blocks and controls on the layer --></section>
  <!-- forms, tables, clinical data: always on an opaque --surface panel -->
</div>
```

- **Variant A** = paper only (`data-blueprint="off"`); **variant B** = paper + blueprint (the working direction). The layers and the plates never depend on each other; `data-paper="off"` restores today's plain `--fond`.
- Page wrappers must not keep an opaque background of their own, or they hide the layer.
- Files: [background.css](background.css) (layer and plates), [assets/paper-grain.svg](assets/paper-grain.svg) (the tile), [study.html](study.html) (runnable example and the blueprint symbols), [frame.html](frame.html) and [measure.py](measure.py) (measurement), [results/contrast.json](results/contrast.json) and [results/plate-sweep.json](results/plate-sweep.json) (outputs).

### 3.5 Offline, performance, safety

- **Offline:** the tile is a same-origin static file of about 1.6 KB; precache it with the application shell (web, PWA, Capacitor). Nothing is fetched at runtime. The blueprint is inline SVG and the plates are CSS: no extra file.
- **CSP:** serving the tile as a file keeps `img-src 'self'`. Inlining it as a `data:` URI works but needs `img-src data:`, which is not recommended for this project.
- **Performance:** one repeated 256 px mask, no animation, no filter applied to the live page (the filter is inside the tile image), `contain: strict` on the layer. **To verify:** per-engine rasterization cost of the tile, cost of the plate masks, and behavior on low-end phones; nothing was timed.
- **Accessibility:** the layer is `aria-hidden`, has no events, and the grain, blueprint and plates are all switched off under `forced-colors: active`, `prefers-contrast: more`, `prefers-reduced-transparency: reduce` and `print`. Never in PDF templates or e-mails.
- **Portability:** rendered and measured in Chromium 156 at device scale factor 1 only. **To verify:** Safari/WebKit and Android WebView (the Capacitor targets), including `mask-composite: intersect` (prefixed `-webkit-mask-composite: source-in` is also declared) and feTurbulence-as-mask; a pre-rasterized 256 px alpha PNG tile is the planned fallback if feTurbulence differs. Opening via `file://` was not tested; serve over HTTP, as the application will.
- **Figma:** nothing was built in Figma, so there is no Figma-only effect to flag. If designers reproduce it later: Figma's native `NOISE` effect cannot bind a color variable and has no exact CSS equivalent (flag it then); a PATTERN/vector tick run is the CSS-equivalent route; the plate is a rectangle with a layer mask.

## 4. Where it may and may not appear

**Accepted recommendation (Aymen, 2026-11-17):**

- Allowed: entry and public surfaces (S-01 showcase, S-02 login backdrop, S-03 and S-04 backdrop) and the patient home header zone (S-25), patient screens first (UD-13). Forms, panels and clinical-looking content sit on an opaque `--surface` panel that fully covers the layer.
- Never: tables, forms, clinical data, source panels, document viewers, PDF templates, e-mails, and the whole practitioner workspace (S-06 to S-24 and administration), which stays on plain surfaces.

## 5. Measured contrast (real pixels)

**Method (Verified observation):** every configuration, variant A and B × light and dark × desktop 1440×1024, tablet 768×1024, tablet landscape 1024×(page height) and mobile 390×(page height), was rendered twice (16 configurations). The second render hides every glyph, border and the logo but keeps all fills, so it shows exactly what sits behind each element (grain, plate, blueprint margin, opaque panel, button fill). The **worst single pixel** under each element's box is compared with its real computed color, using WCAG 2.x relative luminance. Thresholds: 4.5:1 text, 3:1 for text ≥ 24 px (or ≥ 18.66 px bold) and for control boundaries. The worst pixel is a full-coverage grain pixel, so the minimum is also a lower bound independent of resolution. The logo is a logotype and exempt. Panel text is measured on its opaque surface.

| Element (token, size) | Required | Light, worst | Dark, worst |
|---|---|---|---|
| Notice and note (`encre-3`, 12 / 14 px) | 4.5 | **4.98** | **5.39** |
| Heading (`encre`, 28 px) | 3 (4.5 also met) | 14.88 | 13.92 |
| Lead (`encre-2`, 18 px) | 4.5 | 6.60 | 6.96 |
| Token samples `encre` / `encre-2` / `encre-3` / `accent` (14 px) | 4.5 | 14.88 / 6.60 / 4.98 / 7.42 | 13.92 / 6.96 / 5.39 / 7.19 |
| Primary button label (`encre-inverse` on `encre`, 18 px) | 4.5 | 15.69 | 14.23 |
| Primary button edge vs layer | 3 | 14.88 | 13.92 |
| Secondary button label (`encre` on `surface`) | 4.5 | 17.26 | 14.07 |
| **Secondary button border vs layer** (`filet-controle`) | 3 | **3.08** | **3.96** |
| Panel statement, source line, quote, status (on `surface`) | 4.5 | ≥ 5.78 | ≥ 5.45 |
| Source marker (`accent`, 11 px mono, on `surface`) | 4.5 | 8.61 | 7.27 |

Result: **all 16 configurations pass**; no blueprint element or plate fade overlaps any content (the plate fade ends exactly at the blueprint band on tablet and phone widths). Static token pairs that never touch the layer: `encre-inverse` on `encre` 15.69 / 14.23, `sur-accent` on `accent` 8.61 / 8.10, `critique` on its fond 5.70 / 7.27, `attention` on its fond 5.35 / 7.14 (light / dark).

**Why plates (to report, not hide).** At the 12 % grain Aymen liked, with no plate: light `encre-3` text 4.06:1 and secondary border 2.51:1, dark `encre-3` text 4.11:1, all below the thresholds. The earlier flat values (3 % light, 4.5 % dark) passed but looked much quieter. Plate-opacity sensitivity (desktop, variant B, grain 12 %, worst pixel):

| Plate opacity | Light `encre-3` 12 px | Light border (≥ 3) | Dark `encre-3` 12 px | Dark border (≥ 3) |
|---|---|---|---|---|
| none | 4.06 ✗ | 2.51 ✗ | 4.11 ✗ | 3.02 |
| 0.5 | 4.63 | 2.87 ✗ | 4.97 | 3.65 |
| 0.6 | 4.72 | 2.92 ✗ | 5.09 | 3.74 |
| 0.7 | 4.85 | 3.00 | 5.27 | 3.87 |
| 0.75 | 4.94 | 3.05 | 5.33 | 3.91 |
| **0.8** | 4.98 | **3.08** | 5.39 | 3.96 |
| 0.9 | 5.12 | 3.17 | 5.56 | 4.08 |

0.8 is chosen for a margin on the light border (0.08:1). This is a documentary design measurement: no audit, no assistive-technology test, no other browser, no 200 % zoom.

Run it again with `python3 docs/design/background-study/measure.py --out <dir> --json docs/design/background-study/results/contrast.json` (Chromium, Pillow and NumPy required; the script starts its own local server; `--sweep` runs the plate-opacity table). Full-size PNG renders and their background-only twins are kept outside Git because the grain makes them large; their SHA-256 hashes are in the calm-zones report.

## 6. Look at it

Serve the repository root and open the page with the review controls (paper, blueprint, calm plates, dark theme, grain slider):

```
python3 -m http.server 8765 --bind 127.0.0.1      # from the repository root
http://127.0.0.1:8765/docs/design/background-study/study.html?ui=1&v=b
```

Query options: `v=a|b`, `t=clair|sombre`, `grain=0.12`, `tick=0.32`, `calm=off`, `calmop=0.8`, `paper=off`, `bp=off`, `ui=1`.

## 7. Decisions

**User decision (2026-11-17):** "je prends tes recommandations sur les points ouverts". Only the first item had been recommended explicitly before; the others were formulated at that moment and are applied provisionally, each reversible.

1. **Grain:** option 2, strong grain in open areas and calm plates behind text and controls (this document). The flat fallback stays documented.
2. **Variant:** **B** (paper + blueprint touch), the variant Aymen's screenshot showed; A remains one attribute away (`data-blueprint="off"`).
3. **Scope:** the allow and deny lists of section 4.
4. **Tablet:** both 768×1024 (as requested) and 1024×768 (screen catalog) are measured here; Figma frames, when built, should use 768 as requested and 1024×768 for the catalog review.

Still open: building the Figma page and Phase 2 (blocked by the access limits below), revising DESIGN and the token reference after validation, the neutral-mean dual-tone option, and the explicit development authorization, which has **not** been given.

## 8. Not done

- No Figma page, frame, variable binding or node was created or changed; Phase 2 (applying the winner to S-01, S-02 and S-25) was not run. S-01 `30:2`, S-02 `30:464` / `30:941` and S-25 `30:44609` exist in Figma, desktop 1440 only as last observed; no tablet or mobile frames of those screens were seen (node reads were blocked).
- No change to authoritative design artifacts, to any approval column, or to application code. No development is authorized by this study.
