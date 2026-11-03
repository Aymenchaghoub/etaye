# Received HTML design reference, 3 November 2026

Status: Delivered; independently reviewed with open findings. **Not approved by Aymen. Not imported into Figma. No application implementation.**

This directory preserves a compact, byte-identical selection of Claude's received generator, CSS, tokens, fonts, logos and evidence inputs. [provenance.json](provenance.json) maps every selected original file to the received archive and its SHA-256. Supplemental license notices are identified separately in licenses/sources.json. The original instructions are inert text in receipt/AGENTS.original.txt; the repository's root AGENTS.md remains authoritative. The original report is testimony, not the independent review.

The [reception report](../../../docs/design/reviews/2026-11-03-reception.md) owns findings and limitations. Current repository specifications govern product behavior; gen/spec/06-coverage.csv is only the historical scope input required by the received tracker. No bundled specification prose is promoted to authority. gen/synthetic-data.json is an integrity-preserved fixture input; the canonical dataset remains docs/design/synthetic-data.json.

The full received ZIP, generated site, nine PDFs, contact sheets, Design System gallery and inactive Figma plugin remain in the external receipt described in the report. They are deliberately excluded from Git. Keep them for exact visual comparison. Do not run the plugin during this phase. Its frame imports are raster references, not editable component or prototype completion.

## Reconstruct

Use Python 3.12+; the archive's Python 3.11 claim does not match its f-string syntax. The tested review environment used Python 3.14.8, Playwright 1.58.0 / Chromium 145.0.7632.6, Pillow 12.1.0, and Poppler 26.05.0. Additional font-metadata inspection used fonttools 4.61.1 and brotli 1.2.0; it is not required for rendering. No paid AI or service credentials are required.

From the repository root, with Playwright/Pillow and its Chromium installed in an isolated environment:

```sh
python tools/rebuild_design_receipt.py --output /path/outside/the/repository --pdf
```

The wrapper verifies selected originals, creates a unique external child folder, prepares the exported Design System directory and runs build → PDF → build → journeys → lint → tracker. Omit --pdf for HTML only; PDF previews will be absent. It never writes generated outputs into this Git reference or overwrites an earlier build. Inspect rebuild.log. The received checkers print findings without failing; their zero exit code is not a clean design verdict. The inherited tracker still contains Claude self-review, not new independent approval.

The complete-tree reconstruction performed for reception produced the same 398 state HTML files, identical dataset, nine PDFs and page counts. The index ordering differed; PDF layout/extraction and metadata/bytes vary. The received P-03-incomplete PDF also contains older wording than its supplied HTML/source (DHR-024). The second build is necessary for PDF-derived previews when starting without generated files. No pixel-identical reconstruction or complete visual approval is claimed.

## Credits and custody

Preserve the original Étaye logo and verified Figma foundations. The selected receipt is not a replacement for canonical tokens or approved application components. Received font versions: Atkinson Hyperlegible Next/Mono 2.001; Source Serif 4.004. Font copyright metadata is retained; complete OFL notices and the Lucide/Feather notice were supplemented from official pinned sources under licenses/. Preserve Damir Sokolovsky's Unslop credit in export/etaye-design-system/LICENCES.md and the repository's existing third-party notices.
