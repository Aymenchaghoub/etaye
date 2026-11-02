# Token and asset reference

Status: Reviewed evidence for existing values; new semantic mappings remain proposed. Source: Figma version `2417265104370398313`, 2026-11-02. Authoritative machine-readable resolved property evidence: [resolved-tokens.json](figma/resolved-tokens.json). Full node evidence: [file-snapshot.json](figma/file-snapshot.json). The export is not a claim that all variable collections were retrieved.

## Colors

The [exact color table](figma/color-table.md) records 17 roles in each mode with source node IDs. The actual values of `surface/fond`, `texte/encre` and `accent/accent` are respectively `#efeeea`, `#1b1b18` and `#2b4c7e` in light mode; labels were not used as the sole evidence. Preserve the original French token names when binding existing Figma variables. An implementation mapping may use English identifiers with an explicit reversible mapping.

Use paper for reading/document areas, surface for forms and primary work areas, background for framing. Accent identifies reference markers, selected controls and validation actions. Warning/critical roles require text and icons; never turn missing data into green reassurance. New success/focus/destructive semantics must be checked against actual foundations and annotated as additions, not passed off as existing tokens. Every foreground/background pair needs contrast verification at its real size; palette existence does not prove every combination accessible.

## Typography and numerical formatting

| Role | Family | Size / line height px | Weight | Sample node |
|---|---|---|---|---|
| ui-titre | Atkinson Hyperlegible Next | 22 / 28 | 500 | 3:176 |
| ui-section | Atkinson Hyperlegible Next | 16 / 24 | 500 | 3:179 |
| ui-corps | Atkinson Hyperlegible Next | 15 / 22 | 400 | 3:182 |
| ui-dense | Atkinson Hyperlegible Next | 14 / 20 | 400 | 3:185 |
| ui-libelle | Atkinson Hyperlegible Next | 13 / 20 | 500 | 3:188 |
| meta | Atkinson Hyperlegible Next | 12 / 16 | 400 | 3:191 |
| donnee | Atkinson Hyperlegible Mono | 14 / 20 | 400 | 3:194 |
| donnee-forte | Atkinson Hyperlegible Mono | 14 / 20 | 600 | 3:197 |
| doc-titre | Source Serif 4 | 26 / 32 | 500 | 3:200 |
| doc-intertitre | Source Serif 4 | 18 / 28 | 500 | 3:203 |
| doc-corps | Source Serif 4 | 16 / 28 | 400 | 3:206 |
| patient-titre | Atkinson Hyperlegible Next | 28 / 36 | 500 | 3:209 |
| patient-corps | Atkinson Hyperlegible Next | 18 / 28 | 400 | 3:212 |
| patient-action | Atkinson Hyperlegible Next | 18 / 24 | 500 | 3:215 |

ui-libelle has 0.13px letter spacing; the other exported samples have zero. Use localized decimal/date rendering with separate stored values and units. Align comparable numeric columns; distinguish observation time from entry time. Never truncate a value, comparator, unit or interval to fit. Font binaries and licenses must be obtained from official distributions before embedding in implementation/PDFs; the Figma style declaration is not a bundled font license.

## Spacing, shape and density

Observed spacing sample widths: espace-1=4, 2=8, 3=12, 4=16, 5=20, 6=24, 8=32, 10=40, 12=48, 16=64px. Observed radii: rayon-0=0, 1=4, 2=8, 3=12px. Node IDs/properties are in the JSON. These sample widths establish the displayed scale, not a complete variable definition list. Use the grid consistently and maintain larger separation between groups than within them. Professional density must still expose units, dates, source links and focus; patient layouts favor larger type/touch targets.

## Logo assets

Use original [SVG assets](figma/assets/) according to their names: logo-light, logo-dark, logo-monochrome, logo-patient; mark-light/monochrome; app-icon-light/dark. Do not replace the accented wordmark with typed approximate text, recolor arbitrary paths, stretch it or substitute a generic medical/AI symbol. Use accessible alternative text “Étaye” or “Étaye — Espace patient”; decorative duplicates have empty alternative text. [ASSUMPTION] Preserve at least one sign-height of clear space until Claude records an exact logo-usage rule in Figma. The intended mark is the source-reference detail, not a decorative badge.

## Design-system completion

Complete fields, errors, selects, calendar/date/time controls, tables, chronology entries, source panels, version diff, upload/preview, task states, dialog/drawer shells, navigation, banners and print/email blocks before composing journeys. Add hover, focus, active, disabled, loading, error, read-only, long text and restricted variants. Chart series must retain labels and units, missing periods remain gaps, and a table alternative is required. Accessibility and responsive rules are in [DESIGN](../planning-artifacts/ux/DESIGN.md) and [EXPERIENCE](../planning-artifacts/ux/EXPERIENCE.md).

Unslop provides craft guidance, not substitute tokens. Avoid glows, decorative gradients, repetitive dashboard cards, emoji navigation, unsupported claims and gratuitous animation. Put visual richness in chronology, evidence relationships and legible data. Print and email share the identity while using their own functional layout.
