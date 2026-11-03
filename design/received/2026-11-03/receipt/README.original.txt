# Étaye — design system, HTML mockups, A4 documents and e-mails (design export)

This folder holds design artifacts only. It contains no application code, no API, no database migration and no user story. Every person, practice, date and value comes from the fictional dataset `etaye-design-v1` (`gen/synthetic-data.json`). Nothing here is medical advice, and Aymen has not approved any of it.

- **Export date:** 2026-11-03.
- **Status:** all 76 scope IDs are designed in HTML, with 398 states: 40 screens (S-01…S-40), 28 interactions (I-01…I-28), 3 A4 documents (9 real PDFs) and 5 e-mails.
- **Review:** Claude reviewed its own work, with every capture opened. This is not an independent review.
- **Figma:** nothing has been imported yet. A local plugin is included (see "Figma").

Read `review/REPORT.md` for coverage, findings and the decisions that are Aymen's to take.

## Open it

- `site/index.html`: every state, grouped by area. Add `?theme=sombre` to any page for the dark theme. Pages work with scripts disabled; the only script switches the theme.
- `site/parcours.html`: 10 navigable journeys (85 steps). They cover practitioner, patient and administration flows, AI correction → validation → publication, replacement and withdrawal, and failure and recovery.
- `site/docs/*.pdf`: P-01…P-03 in short, long and incomplete versions. These are real A4 PDFs: tagged, with embedded fonts and "Page x / y".
- `design-system/`: the Design System sources as published to the claude.ai artifact "Étaye": brand book, `tokens.json`, `maquettes.md`, component and mockup cards, `components/bundle.css` and fonts.
- `figma-plugin/`: a local Figma development plugin (variables, text styles and one frame per state). See its README.
- `review/`: progress log, per-batch notes, the HTML tracker, the PDF and lint reports, and the contact sheets that were actually opened.

## Folder map

| Path | What |
|---|---|
| `site/` | Generated mockups: `ecrans/<ID>/<state>.html`, `docs/` (PDFs + page previews), `css/etaye.css`, `tokens/`, `fonts/`, `logos/`, `manifest/*.json` |
| `gen/` | Generator: `lib.py` (shells, components, French formatting, status axes, information states), `screens_*.py` (one module per family group), `build.py`, `render.py`, `pdf.py`, `parcours.py`, `lint.py`, `overflow_check.py`, `figma_export.py`, `ds_*.py`, `tracker.py`, `spec/`, `synthetic-data.json` |
| `ds/` | CSS sources: `bundle-base.css`, `bundle-app.css`, `ext-consult.css` (versions, validation, publication), `ext-print.css` (A4 and e-mail); `icons.css` at the root |
| `design-system/` | Copy of `ds/project/` (the published Design System) |
| `figma-plugin/` | `manifest.json`, `code.js`, `ui.html`, `data.json`, `frames/*.jpg` |
| `logos/`, `export/etaye-design-system/` | Original Étaye SVG logos; token CSS, fonts and licences used by the build |
| `review/` | `REPORT.md`, `PROGRESS.md`, `notes-*.md`, `html-design-progress.csv`, `evidence.json`, `pdf-report.json`, `lint-report.json`, opened sheets |

## Rebuild and check

Requires Python 3.11+, Pillow, Playwright with Chromium and poppler-utils (`pdftoppm`, `pdfinfo`, `pdffonts`, `pdftotext`).

```
cd gen
python3 build.py                      # all modules → site/ (+ index, bundle.css)
python3 pdf.py                        # A4 PDFs + page images (after build)
python3 parcours.py                   # site/parcours.html (fails if a step is missing)
python3 render.py S-09 --variantes    # captures: desktop/tablet/mobile/320 (+ dark, fallback fonts) → review/S-09/
python3 overflow_check.py screens_dossier   # horizontal overflow at 320/390/1024 px
python3 lint.py                       # rule lint over all states → review/lint-report.json
python3 tracker.py                    # review/html-design-progress.csv
python3 ds_cards.py && python3 ds_maquettes.py && python3 ds_section.py   # Design System cards and section
python3 figma_export.py               # figma-plugin/data.json + frames/
```

Set `ETAYE_ROOT` if the folder is moved; scripts otherwise resolve paths from `gen/`.

## Rules carried by every screen

- Interface copy is in French; annotations, notes and these docs are in English.
- Status axes stay separate: content (Brouillon / En relecture / Validé), task, freshness, publication, appointment, invitation, membership. Validated never means published. A successful task creates a draft only.
- AI processing is called *tâche* (not *traitement*), so it cannot be confused with a patient's treatment.
- Information states stay distinct: missing ≠ explicit negative ≠ patient-reported. An empty allergy section reads "Inconnues · non renseignées". "Aucune allergie connue" appears only as the quoted, dated source SRC-A-001.
- A source marker appears only where a dated source supports that exact statement.
- The design uses no computed risk, no confidence percentage, no "IA" badge, no gradient, no emoji and no invented clinical value. States without a dataset example are labelled "template" or "variant".
- Practice and role stay visible. Administration never sees clinical data. Scheduler variants follow PD-001, which is a proposal and has not been approved.

## Figma

Nothing has been imported. Aymen's account is on the Starter plan with a View seat, and the MCP quota was used on 2026-11-02. `figma_sync` reads "Pending" in the tracker. `figma-plugin/` imports variables, text styles and image frames with the Plugin API: it needs no token and uses no MCP quota, but it does need a file Aymen can edit. A REST access token cannot create frames or components.

## Licences

- Atkinson Hyperlegible Next and Mono (Braille Institute) and Source Serif 4 (Adobe): SIL Open Font License 1.1.
- Lucide icons: ISC.
- Clean-up principles adapted from unslop by Damir Sokolovsky (@heydamir), CC BY 4.0.
- The Étaye logo, sign, tokens and components belong to the project.
