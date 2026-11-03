# Local editable-layer import into Figma

Aymen requested this free fallback after the official MCP connector's Starter/View monthly quota was actually exhausted. The local token worked for read-only REST access; no token belongs in this plugin. The original raster-frame plugin is not used.

## What this delivers and what remains unverified

The exporter reads the frozen corrected HTML in Chromium and produces native-layer scene batches. The plugin creates new **TEXT**, vector SVG, shape and nested frame nodes. Discrete PDF-page content previews remain images, as in the interface; entire UI screenshots are never used as screen fills. Logos/icons retain source SVG geometry. Browser geometry and text-line boundaries are retained for inspection. This is **not automatic reconstruction of reusable component variants, token bindings, auto-layout, responsive behavior or prototype wiring**; these require Figma reconciliation/review. PDF source bodies can be edited as layers; the nine actual paginated PDFs remain the print reference.

Actual local export, scene checks and isolated plugin tests are recorded in the delivery report. **Native Figma execution, font availability, imported rendering, nodes and design approval remain unverified until Aymen runs a first batch.** Do not update coverage.csv from an exported JSON. A successful local export does not bypass Figma file editing permissions or guarantee that a plan permits a particular action.

## Platform requirement

Figma's official development-plugin flow requires **Figma Desktop on Windows or macOS**. This session is Linux; no compatible local Figma runtime was found or used. Aymen confirmed that he has Windows available at home and will run the kit later. This is a pending native verification, not a reason to install an unsupported Linux client or purchase a plan.

## Install in Figma Desktop without publishing or a paid service

1. Open the existing Étaye Design file in Figma Desktop. Preserve a named version/backup first. Choose a work page; the plugin appends a **new section** to its right and does not replace foundations. This also avoids assuming that Starter allows more pages.
2. In Plugins → Development, choose **New plugin**, Design, then the option with a custom UI. Keep Figma's generated plugin ID. Copy code.js and ui.html from this folder into the generated plugin directory. Apply manifest.template.json's fields to the generated manifest.json, **retaining its real id**. The template deliberately contains no invented plugin ID. No build, token, network permission or publication is needed.
3. Run that development plugin. Select batches/batch-001.json and import. Missing fonts stop before any canvas write, listing exact family/weight/style. Make the official Atkinson Hyperlegible Next/Mono and Source Serif 4 fonts available to Figma; do not silently substitute them. The Desktop kit includes unmodified installable TrueType fonts in windows-fonts/ with complete notices. [Pinned font sources](font-sources.json) record URLs, hashes, actual versions and named styles. Install fonts on Windows and restart Figma; its exact font discovery still needs the native first-batch check.
4. Inspect the actual first imported screen: editable text, vector logo/icons, wrapping, alignment, fields, layers and dimensions against HTML at 1440px. If anything is wrong, stop and report/export that evidence before importing the rest. Sections are explicitly marked “à revoir”; no approval is inferred.
5. Continue in small batches only after this check. An identical batch on the current page is refused; existing work is not overwritten. Import errors roll back only the newly created section. Keep prior import/evidence files when retrying a corrected version. A changed snapshot is a new revision, not permission to delete the old one.
6. Download the plugin's actual node/evidence JSON after each successful batch. Reconcile real IDs into the design coverage tracker only after actual import and review. Complete responsive/theme frames, component reuse/token binding, native navigable prototypes and A4/email review before considering design completion.

The exporter currently supplies **all456 states at desktop1440**, in57 batches of at most8. It labels any --limit export Partial. Other reviewed viewports remain HTML/capture evidence, not imported Figma variants. Do not count these desktop frames as complete responsive Figma coverage.

Official instructions consulted 2026-11-03: [create a development plugin](https://help.figma.com/hc/en-us/articles/360042786733-Create-a-classic-plugin-for-development), [manifest and assigned IDs](https://developers.figma.com/docs/plugins/manifest/), [MCP access limits](https://developers.figma.com/docs/figma-mcp-server/rate-limits-access/). Runtime behavior still needs the actual Figma check.

## Regenerate outside the repository

Use the optional hash-locked design environment from ../corrections/README.md. Supply a new external output directory:

```sh
/path/to/design-env/bin/python tools/export_figma_layers.py --root /exact/corrected/etaye-design --output /new/external/figma-import
node --test design/figma-local-import/test-plugin.cjs
```

Output contains plugin sources, batches and MANIFEST.json with date, full-site snapshot digest and per-file hashes. Generated payloads remain external. Preserve the manifest and final corrected-design ZIP. No application development is authorized.
