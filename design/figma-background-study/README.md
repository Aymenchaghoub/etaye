# Background study frames — Figma development plugin

Status: **prepared, never run in Figma.** This is design tooling for the [background direction study](../../docs/design/background-study/README.md) (variant B, paper with a blueprint touch, adopted tone). It creates editable Figma frames of the study; it does not approve the direction, change DESIGN or the token reference, or touch any application code. Nothing in this folder was executed against Figma: the Figma MCP and REST limits of the Starter plan make that slow, and a development plugin needs no MCP call, token or network.

It follows the pattern of the [Windows queue importer](../figma-windows-import/README.md) and the [local importer](../figma-local-import/README.md): a local development plugin run in Figma Desktop, a prepared JSON batch, an offline test with a fake Plugin API, and downloadable evidence.

## What it builds

One new **section** with **8 frames** on a dedicated page: the study's stand-in content at 1440×1024, 768×1024, 1024×910 and 390×1150 (the page height of the HTML study at those widths), each in light and dark (light left, dark right, one row per width). Each frame is, from bottom to top:

| Layer | What it is | Source of the numbers |
|---|---|---|
| Frame fill | `surface/papier` (light) or `surface/fond` (dark), **bound to the file's variable** | adopted tone, README section 3.1 |
| `Fond · grain de papier` | full-frame rectangle, image fill `TILE` at scale 1, paint opacity **0.12** | one 256×256 PNG per theme, the alpha of `paper-grain.svg` in the theme's ink (`#1b1b18` or `#ece9e1`) |
| `Fond · plaque calme` | `surface/papier` at **0.8**, soft edge by a layer blur | plate rectangle, inset and feather of the HTML page |
| `Fond · touche blueprint` | two 192 px L-rulers (top-left, bottom-right turned by 180°), two registration marks | `background.css` and the study README section 3.2 |
| `Contenu d'exemple` | real text, frames and borders of the study; the original logo as an **instance** of `Logo/Clair` (4:13) or `Logo/Sombre` (4:33); a uniform CSS border (the secondary button and the preview panel, 8 px radius) is a native **inside stroke** bound to `filet/*`, as in the verified native import | the existing exporter `design/figma-local-import/capture.js` run on `study.html` |

The fills of the layers and of the content are bound to the existing local color variables (`Couleurs · Clair` and `Couleurs · Sombre`, names such as `surface/papier`, `texte/encre`) wherever the color equals a token exactly; a color that matches no token or several tokens is left as it is and **counted in the evidence**. No new color is created. The batch holds about 420 content layers per run and the whole run about 1,720 layers (1,719 in the offline test). The tile reproduces the CSS grain: composited at 12 % over the adopted base it predicts the measured open-area mean tone of the HTML render within 0.32 levels in light and 0.08 in dark (checked by the exporter).

## Prerequisites

1. **Figma Desktop on Windows or macOS.** A development plugin cannot be run from the Linux session; the Starter plan's MCP quota is not involved.
2. **The original Étaye file** (key `vfh4BSnbRAbaDE1q0CyssL`). Save a named version first.
3. **A dedicated, empty page named exactly `Étaye — Fond papier (étude)`**, created by hand (the plugin never creates pages). The Figma screenshot of 2026-11-18 shows four pages (Couverture, Étaye — Import HTML, Fondations, Composants); whether the Starter plan allows a fifth is **to verify**, so create the page first and stop and report if Figma refuses. It must not be `2:74` (foundations), `2:75` (components) or `15:2` (imported states).
4. **The variable collections and logo components already in the file:** `Couleurs · Clair`, `Couleurs · Sombre`, `Logo/Clair` (`4:13`), `Logo/Sombre` (`4:33`).
5. **Exact fonts available to Figma:** Atkinson Hyperlegible Next Regular and Medium, Atkinson Hyperlegible Mono SemiBold, Source Serif 4 Regular (the OFL files under `design/received/2026-11-03`). A similar family or style does not satisfy the preflight.
6. **The native import is closed.** [PR 19](https://github.com/Aymenchaghoub/etaye/pull/19) was merged on 2026-11-18 (final saved version `2423224260331333575`, read back through the Figma REST `/versions` route). Save a named version of the current state before running the plugin; it writes only to its own new section and does not touch the corrected roots.

## Run

1. In Figma Desktop, open the original file, then the dedicated page.
2. **Plugins → Development → New plugin**, Design plugin with a custom UI. Keep Figma's generated plugin ID. Copy `code.js` and `ui.html` into the generated plugin folder and apply every field of `manifest.template.json` to its `manifest.json`, **retaining the generated `id`**. `enablePrivatePluginApi: true` makes `figma.fileKey` available (the plugin fails closed without it); it asks for no network access and no paid feature.
3. Run the plugin, choose `batches/background-study-v1.json`, click **Créer l'étude**.
4. Preflight, before any canvas write: file key, page name and id, batch not already present, exact fonts, collections and variables, logo components. A failure lists exactly what is missing and creates nothing.
5. The run creates one section. It checks the target again at the end. **A failure, a stop (Arrêter) or a changed target removes the whole new section**; nothing outside it is ever modified or deleted.
6. Download the evidence JSON from the plugin window (also after a failure) and keep it with the batch hash.

Re-running the same batch on the same page is refused. To rebuild, delete the study section by hand first.

## What must be inspected on the first real run

The offline tests prove the plugin's guards and structure, not how Figma Desktop draws them. Open the section at 100 % and compare each frame with the HTML render of the same viewport and theme (the adopted-tone PNGs recorded in the [cross-browser report](../../docs/design/reviews/2026-11-18-background-cross-browser-tone.md)), then record the result separately from any approval:

1. **Ruler fade.** Each ruler must be solid for its first 96 px and fade to nothing at 192 px, away from its corner. The vertical gradient matrix (`[[0,1,0],[-1,0,1]]`) and the mask order were written from the Plugin API documentation and are the likeliest thing to be wrong.
2. **Bottom-right ruler.** Same symbol turned by 180°, band along the right and bottom edges, fading towards the page centre.
3. **Calm plate.** The soft edge is a layer blur of radius 0.8 × feather on a rectangle grown by inset + feather/2: an approximation of the CSS two-axis alpha ramp. Check that it reads as a calm area with no visible step; adjust the radius if it does not.
4. **Grain.** Visible at 12 %, seamless at 256 px, not rescaled, and in the dark frames made of the light ink (`#ece9e1`).
5. **Bindings.** The frame fill and every layer fill show a variable; read `colorBinding` in the evidence for raw or ambiguous colors.
6. **Content.** Same line breaks as the HTML, the 1 px border of the secondary button and of the preview panel following the 8 px corner radius (no square corner sticking out), original logo instances in the right theme, panel on an opaque `surface/surface`, nothing under the plate except the hero block.
7. **Untouched.** The foundations, components and imported pages are unchanged.

Figma's native `NOISE` effect cannot bind a color variable and has no CSS equivalent; the PNG tile keeps the exact grain but its color is baked per theme and does not follow a variable. That is a known limit, not a defect.

## Rebuild the batch

```
python3 design/figma-background-study/export_batch.py          # rewrite the batch and print the checks
python3 design/figma-background-study/export_batch.py --check  # fresh export must equal the committed file
node --check design/figma-background-study/code.js
node --test  design/figma-background-study/test-plugin.cjs
```

The exporter needs Chromium, Pillow and websocket-client; it renders `study.html` over a local server, exports native text and frame geometry with `capture.js`, removes the study's own background layer, swaps the logo images for instances, and records the SHA-256 of every source file in `sourceFiles`. The batch is deterministic: a fresh export is byte-identical to the committed file (checked on 2026-11-18).

## Offline verification (2026-11-18)

- `node --test design/figma-background-study/test-plugin.cjs`: 8 tests pass with nonzero assertions (one of them checks that rounded boxes carry an inside stroke, and the previous per-edge rectangles fail it). They cover the batch contract, layer order, geometry, variable bindings and logo reuse, refusal of a wrong file, page, font, collection, variable or logo, a duplicate batch, rollback after a failure, a stop and a changed target, malformed or unsafe batches, and a source check (no network call). Four deliberate breakages of the plugin (wrong dark base, no rollback, wrong grain opacity, no file-key check) were each caught.
- The 8 frames and the tile mean tone were checked against the measured render by `export_batch.py`.
- `ruff check --select E9,F`, the repository's Python tests (15) and `python3 tools/validate_docs.py` pass; the secret scan finds nothing after the scoped exceptions added to `.gitleaks.toml` for the public Figma file identifier and the provenance digests of the generated batch.

## What is not verified

- **No run in Figma Desktop**, so the points of the inspection list above are open. No Figma node was read or written by this work.
- The page limit of the Starter plan, the exact variable and component names in the file (the plugin checks them and stops), and font availability on the Windows machine.
- Safari, Android WebView and the other items of the study's "Not verified" list.

Phase 2 of the study (applying the background to S-25, S-02 and S-01) is **not** part of this kit. It needs the final state of the imported screens (PR 19 is merged; responsive frames and Aymen's approval are still missing) and responsive frames that do not exist yet; it would clone those screens onto new frames and leave the 456 imported roots and the protected pages untouched.
