# Étaye design system: offline reference

Status: **Proposal, written 2026-11-18 as the offline working reference for agents. Not a design approval and not a development authorization.** It restates existing verified values and the received HTML design reference so that work can continue, with the same rendering, when Figma cannot be read. It was written from files in this repository only; no Figma call was made. It creates no token, no color and no component: everything below points to a source. Evidence labels follow [AGENTS.md](../../AGENTS.md): **User decision**, **Verified observation**, **Proposal**, **[ASSUMPTION]**, **To verify**.

## 1. Purpose and precedence

**Purpose.** Figma is the visual source of truth, but access is limited (Starter plan, View seat): the MCP quota reads "Out of calls", REST tier-1 reads return 429 until about 2026-11-22, and development plugins need Figma Desktop. **User statement (2026-11-18):** Aymen expects to work without Figma because of the quota, so this file is the working reference, not only a fallback. An agent builds and reviews from it and the sources it links, and records the limit instead of guessing. The result must be the same with or without Figma: clear, homogeneous, built from the same tokens and recipes.

**Precedence when sources disagree** (highest first). Stop and surface a conflict; never pick silently.

| Rank | Source | Owns |
|---|---|---|
| 1 | Aymen's decisions | Scope, approvals, development authorization. |
| 2 | Verified Figma properties (without Figma access this is a frozen record: the 2026-11-02 resolved values and the 2026-11-18 corrected state, never re-measured by guessing): [resolved-tokens.json](figma/resolved-tokens.json), [file-snapshot.json](figma/file-snapshot.json), [manifest](figma/manifest.json) | Existing colors, typography, spacing, radii, logos, and the button, status and marker components. |
| 3 | [DESIGN.md](../planning-artifacts/ux/DESIGN.md) and [EXPERIENCE.md](../planning-artifacts/ux/EXPERIENCE.md) (drafts, not approved) | Brand spine, behavior, accessibility floor, responsive and content rules. |
| 4 | Received HTML design reference: [tokens.css](../../design/received/2026-11-03/export/etaye-design-system/tokens/tokens.css) and the component CSS in [ds/](../../design/received/2026-11-03/ds/) (not approved) | The CSS form of everything the Figma import was generated from. |
| 5 | This file | Nothing of its own: it restates ranks 2 to 4. |

**What "the same rendering" covers.** The 76 design families (S, I, P and M identifiers in the [coverage tracker](coverage.csv)) exist as received HTML and as native Figma frames (456 states in 94 sections, 52 of them corrected; see the [closure report](reviews/2026-11-18-figma-native-closure.md)). Using the tokens and recipes below reproduces those screens. It does **not** cover anything that was never designed (see section 12), and it does not turn the received components into approved ones.

## 2. Procedure when Figma is unavailable

1. **Record the limit** in the task evidence and in [continuation.md](../continuation.md): what was unavailable, when, and the exact error. Never claim Figma verification.
2. **Take values only from section 4.** Use the CSS custom properties of `tokens.css` (`--encre`, `--espace-4`, `--rayon-1`, ...). No new hex value, no estimated size, no generic component theme.
3. **Build from the recipes in section 6** and the layout rules in section 5. For a missing component, use the closest existing one and flag a design gap per the [coverage guide](coverage-guide.md); do not invent a token.
4. **Reuse the original logo files** (section 4.6). Never retype, recolor, stretch or redraw them.
5. **Render and compare** at matching viewports: 1440×1024, 1024×768, 768×1024, 390×844 and a 320 px reflow check. The reference screens can be rebuilt offline with `python tools/rebuild_design_receipt.py --output <folder outside the repository> --pdf` (see the [received README](../../design/received/2026-11-03/README.md)).
6. **Measure contrast on rendered pairs**, in both themes, for text, control borders and focus. A palette entry does not prove a combination accessible.
7. **Label the result** "built without Figma access, to reconcile against Figma when available".

### Consistency checklist

1. Only `var(--…)` custom properties of `tokens.css`; no literal color or size, except in e-mail and print, where the reference uses literal values.
2. One shell per audience: practitioner and administration (`.etaye-app`), sign-in (`.etaye-auth`), patient (`.etaye-papp`), A4 documents (`doc-a4`), e-mail (`.mail`).
3. Text only from the 14 roles of section 4.3; spacing only from the 10 steps and radii only from the 4 values of section 4.4.
4. Every state in both themes, with the focus ring, and never meaning by color alone.
5. French interface copy, synthetic data, original logo files.
6. Compare at 1440×1024, 1024×768, 768×1024, 390×844 and a 320 px reflow check, both themes.
7. Anything outside these rules is a recorded design gap, not an improvisation.

## 3. Sources of truth

| Source | What it gives | Status |
|---|---|---|
| [resolved-tokens.json](figma/resolved-tokens.json) | 17 color roles in two themes, 14 text styles, spacing, radii and the button, status and marker properties, with node IDs | **Verified observation** (Figma version `2417265104370398313`, 2026-11-02); not a complete variable export |
| [figma-inventory.md](figma-inventory.md), [token-reference.md](token-reference.md) | Existing nodes, assets and token notes | Reviewed evidence |
| `tokens.css` and [tokens.json](../../design/received/2026-11-03/design-system/tokens.json) | The tokens as CSS custom properties | Colors, spacing, radii and text styles equal the Figma values; the generator below re-checks it. Strokes, targets, motion and the shadow are **only** in this received export (not read from Figma) |
| [bundle-base.css](../../design/received/2026-11-03/ds/bundle-base.css), [bundle-app.css](../../design/received/2026-11-03/ds/bundle-app.css), [ext-consult.css](../../design/received/2026-11-03/ds/ext-consult.css), [ext-print.css](../../design/received/2026-11-03/ds/ext-print.css), [icons.css](../../design/received/2026-11-03/icons.css) | Component, layout, print and icon CSS | Received, not approved; same origin as the Figma import |
| [screen-catalog.md](screen-catalog.md), [interaction-catalog.md](interaction-catalog.md), [pdf-email-specifications.md](pdf-email-specifications.md) | What each surface contains | Reviewed specifications |
| [background-study](background-study/README.md) | Paper and blueprint background layer | **Proposal** (section 10) |

Figma file: see [figma-inventory.md](figma-inventory.md) for its identifier and the nodes. Pages: Couverture, Étaye — Import HTML (`15:2`, the 456 imported states), Fondations (`2:74`), Composants (`2:75`). The two last pages and the imported roots are protected: never modify them to "fix" an offline mismatch. Saved versions: desktop import `2423132943961499284`; final corrected state `2423224260331333575`.

**Generated tables.** The token tables in section 4 are produced by [tools/design_system_reference.py](../../tools/design_system_reference.py) from `tokens.css` and cross-checked against `resolved-tokens.json` (34 color values, 14 text styles, 10 spacing steps, 4 radii). `python3 tools/design_system_reference.py --check` fails if the file is stale or if the two sources disagree; `--write` regenerates the blocks. A repository test runs the check.

**Reference snapshot identity.** The corrected HTML reference (398 delivered states plus the reviewed overlay, 523 files) is identified by SHA-256 `d2f0800e275c4b156fedbcf23656ffe2bf435f9b6a72faf0f86fc293e2a82ad6` in [html-reception.csv](html-reception.csv) (the same value for all 76 families); its generator and CSS are in `design/received/2026-11-03`. It was matched again on 2026-11-18 by the [closure report](reviews/2026-11-18-figma-native-closure.md); this session did not rebuild it.

## 4. Tokens

### 4.1 Themes

Set `data-theme="clair"` (default) or `data-theme="sombre"` on `<html>`; every color below is a CSS custom property redefined per theme, so components never hard-code a color. Both themes are first-class: check each state in both. Logos swap by theme with the pair `img.etaye-logo-c` (light) and `img.etaye-logo-s` (dark) without script.

### 4.2 Colors

<!-- generated:colors:start -->
| Role (Figma name) | CSS variable | Light (`clair`) | Dark (`sombre`) | Use |
|---|---|---|---|---|
| `surface/fond` | `--fond` | `#efeeea` | `#0f1012` | Quiet framing: application background, top bar, navigation. |
| `surface/surface` | `--surface` | `#ffffff` | `#1a1c1f` | Work areas: main region, panels, fields, dialogs, drawers, secondary button. |
| `surface/surface-2` | `--surface-2` | `#f7f6f3` | `#222529` | Secondary surface: hover, disabled and read-only fills, skeletons. |
| `surface/papier` | `--papier` | `#f6f4ee` | `#17181a` | Reading and document areas, sign-in and patient shells, excerpts. |
| `texte/encre` | `--encre` | `#1b1b18` | `#ece9e1` | Primary text, primary button fill, selected tab underline, error border. |
| `texte/encre-2` | `--encre-2` | `#57534a` | `#aba69b` | Secondary text, labels, disabled text. |
| `texte/encre-3` | `--encre-3` | `#6b655a` | `#969189` | Meta text: dates, origins, source references. |
| `texte/encre-inverse` | `--encre-inverse` | `#f6f4ee` | `#1b1b18` | Text on the ink fill (primary button, toast, selected slot). |
| `filet/filet` | `--filet` | `#dedad0` | `#2e3136` | Hairlines and decorative borders: panels, dividers, table rows. |
| `filet/filet-controle` | `--filet-controle` | `#8c877c` | `#767b82` | Control borders (fields, secondary button), rails, 3:1 boundary. |
| `accent/accent` | `--accent` | `#2b4c7e` | `#8faadb` | Source marker, links, focus ring, validation fill, selected states. |
| `accent/accent-fond` | `--accent-fond` | `#e8edf5` | `#1d2738` | Accent wash: validated status, highlighted excerpt, information banner. |
| `accent/sur-accent` | `--sur-accent` | `#ffffff` | `#0f1012` | Text on accent fills. |
| `clinique/critique` | `--critique` | `#b42318` | `#ff8f85` | Critical status and withdrawal; never a computed risk category. |
| `clinique/critique-fond` | `--critique-fond` | `#fcebe9` | `#3b1613` | Critical wash (allergy block, critical banner). |
| `clinique/attention` | `--attention` | `#a6470a` | `#f5a524` | Warning, declared or discordant information, draft, to confirm. |
| `clinique/attention-fond` | `--attention-fond` | `#fdf1e3` | `#36260e` | Attention wash (banner, confirmation block). |
<!-- generated:colors:end -->

**Rules.** Paper (`papier`) is for reading and documents, surface (`surface`) for forms and primary work areas, background (`fond`) for framing. Accent is restrained and meaningful: reference markers, selected controls, links and validation. Warning and critical colors always come with text or an icon and never imply a computed risk category; missing or contradictory data gets explicit language and is never rendered as green reassurance. Text on the ink fill uses `encre-inverse`; text on an accent fill uses `sur-accent`. Existing French role names are preserved when binding Figma variables; an implementation may map them to English identifiers with a reversible mapping.

**Measured pairs.** With the background layer of section 10 applied, the study measured on rendered pixels: light `encre-3` small text 4.98:1 (minimum 4.5), light secondary-button border 3.08:1 (minimum 3), dark `encre-3` 5.51:1 and dark control border 4.04:1. These are measurements of that study, not a conformance claim for the product.

### 4.3 Typography

<!-- generated:typography:start -->
| Role | CSS class | Family | Size / line height | Weight | Letter spacing |
|---|---|---|---|---|---|
| `ui-titre` | `.ui-titre` | Atkinson Hyperlegible Next | 22 / 28 px | 500 | 0 |
| `ui-section` | `.ui-section` | Atkinson Hyperlegible Next | 16 / 24 px | 500 | 0 |
| `ui-corps` | `.ui-corps` | Atkinson Hyperlegible Next | 15 / 22 px | 400 | 0 |
| `ui-dense` | `.ui-dense` | Atkinson Hyperlegible Next | 14 / 20 px | 400 | 0 |
| `ui-libelle` | `.ui-libelle` | Atkinson Hyperlegible Next | 13 / 20 px | 500 | 0.13 px (0.01 em) |
| `meta` | `.meta` | Atkinson Hyperlegible Next | 12 / 16 px | 400 | 0 |
| `donnee` | `.donnee` | Atkinson Hyperlegible Mono | 14 / 20 px | 400 | 0 |
| `donnee-forte` | `.donnee-forte` | Atkinson Hyperlegible Mono | 14 / 20 px | 600 | 0 |
| `doc-titre` | `.doc-titre` | Source Serif 4 | 26 / 32 px | 500 | 0 |
| `doc-intertitre` | `.doc-intertitre` | Source Serif 4 | 18 / 28 px | 500 | 0 |
| `doc-corps` | `.doc-corps` | Source Serif 4 | 16 / 28 px | 400 | 0 |
| `patient-titre` | `.patient-titre` | Atkinson Hyperlegible Next | 28 / 36 px | 500 | 0 |
| `patient-corps` | `.patient-corps` | Atkinson Hyperlegible Next | 18 / 28 px | 400 | 0 |
| `patient-action` | `.patient-action` | Atkinson Hyperlegible Next | 18 / 24 px | 500 | 0 |
<!-- generated:typography:end -->

**Where each role is used.** `ui-titre` page title (h1); `ui-section` panel and section headings; `ui-corps` default body and navigation rows; `ui-dense` tables and dense blocks; `ui-libelle` field labels (0.01 em); `meta` dates, origins and fine print; `donnee` and `donnee-forte` measurements, identifiers and source numbers (with `font-variant-numeric: tabular-nums`, class `.etaye-num`); `doc-*` reading and report text in Source Serif 4; `patient-*` the patient portal, larger by design.

**Fonts.** Atkinson Hyperlegible Next (Regular 400, Medium 500, SemiBold 600), Atkinson Hyperlegible Mono (Regular 400, SemiBold 600), Source Serif 4 (Regular 400, Medium 500), as WOFF2 files in the [exported design system](../../design/received/2026-11-03/export/etaye-design-system/fonts/) with `font-display: swap`. Licenses (SIL OFL 1.1 for the three families, ISC for Lucide) are in [LICENCES.md](../../design/received/2026-11-03/export/etaye-design-system/LICENCES.md). Never substitute a similar family or style: a fallback stack is for loading only.

**Numbers and dates.** French locale rendering, stored values and units kept separate, comparable numeric columns aligned right in the mono face, observation time distinguished from entry time. Never truncate a value, comparator, unit or interval to fit.

### 4.4 Spacing, shape, strokes and targets

<!-- generated:scale:start -->
| Group | Tokens |
|---|---|
| Spacing (4 px base) | `--espace-1` 4px, `--espace-2` 8px, `--espace-3` 12px, `--espace-4` 16px, `--espace-5` 20px, `--espace-6` 24px, `--espace-8` 32px, `--espace-10` 40px, `--espace-12` 48px, `--espace-16` 64px |
| Radii | `--rayon-0` 0px, `--rayon-1` 4px, `--rayon-2` 8px, `--rayon-3` 12px |
| Strokes | `--trait-filet` 1px (Hairline), `--trait-focus` 2px (Focus ring), `--trait-erreur` 2px (Error border) |
| Targets | `--cible-soignant` 32px (Clinician touch target), `--cible-patient` 48px (Patient touch target) |
| Font stacks | `--font-texte` "Atkinson Hyperlegible Next", system-ui, sans-serif, `--font-donnee` "Atkinson Hyperlegible Mono", ui-monospace, monospace, `--font-document` "Source Serif 4", Georgia, serif |
<!-- generated:scale:end -->

Group spacing is larger than spacing inside a group. Radii are small: `rayon-0` (square) for panels, `rayon-1` (4 px) for fields, buttons, status labels and banners, `rayon-2` (8 px) for patient buttons, dialogs, the sign-in card and toasts, `rayon-3` (12 px) for the patient card. Do not turn every item into a pill.

### 4.5 Motion and the floating shadow

<!-- generated:motion-effects:start -->
| Token | Value |
|---|---|
| `--courbe` | `cubic-bezier(0.22, 1, 0.36, 1)` |
| `--duree-retour` / `--duree-flottant` / `--duree-entree` | 140ms / 180ms / 320ms |
| `--ombre-flottant`, light | `0 8px 24px rgba(27,27,24,0.08), 0 0 0 1px rgba(27,27,24,0.10)` |
| `--ombre-flottant`, dark | `0 8px 24px rgba(0,0,0,0.40), 0 0 0 1px rgba(236,233,225,0.10)` |
<!-- generated:motion-effects:end -->

Only the color transitions of links and buttons (`--duree-retour`) and a 0.98 press scale on buttons exist in the reference. The shadow is for floating surfaces only (preview card, dialog, drawer, toast). No decorative glow, gradient or gratuitous animation. `prefers-reduced-motion: reduce` removes every transition. **To verify:** the Figma effect style `2:73` itself was never read; the shadow values above come from the received export.

### 4.6 Logo and icons

**Logo.** Original SVGs, never recolored or stretched: [docs/design/figma/assets](figma/assets/) holds the Figma exports (`logo-light`, `logo-dark`, `logo-monochrome`, `logo-patient`, `mark-light`, `mark-monochrome`, `app-icon-light`, `app-icon-dark`); the exported design system adds the dark patient variant. Figma components: `Logo/Clair` `4:13`, `Logo/Sombre` `4:33`, `Logo/Monochrome` `4:19`, `Logo/Espace patient` `4:20`. Use light on light backgrounds, dark on dark, monochrome for print and document headers, the patient variant for the portal header. The monochrome component's original description mentions prescriptions, which are outside the product scope: do not infer a prescription feature. Alternative text is "Étaye" or "Étaye — Espace patient"; a decorative duplicate has empty alternative text. [ASSUMPTION] Keep at least one sign-height of clear space until an exact rule is recorded in Figma.

**Icons.** One family: Lucide (ISC), 1.5 stroke, drawn as a CSS mask in `currentColor` (`.etaye-icone`, sizes 16, 18, 24 and 32 px via `--18`, `--24`, `--32`). Every icon has an accessible name or is decorative, and consequential actions also carry text.

<!-- generated:icons:start -->
54 icons: `valide`, `brouillon`, `alerte`, `doute`, `patients`, `agenda`, `document`, `courrier`, `reglages`, `recherche`, `message`, `biologie`, `preparation`, `suite`, `fermer`, `ouvrir`, `imprimer`, `modifier`, `aujourdhui`, `historique`, `menu`, `deplier`, `precedent`, `retour`, `ajouter`, `deposer`, `telecharger`, `verrou`, `voir`, `relancer`, `sortie`, `compte`, `cabinet`, `bouclier`, `horloge`, `filtre`, `info`, `lien`, `interdit`, `horsligne`, `envoyer`, `comparer`, `liste`, `mesures`, `equipe`, `audit`, `ia`, `publier`, `retirer`, `remplacer`, `copier`, `supprimer`, `demo`, `cle`.
<!-- generated:icons:end -->

## 5. Layout

**Viewports to review.** 1440×1024, 1024×768, 768×1024, 390×844, plus a 320 px reflow check. At 200 % zoom nothing is lost.

**Practitioner and administration shell** (`.etaye-app`): a two-column grid, 248 px navigation on `fond` with a right hairline and a fluid main region on `surface`; the top bar is 56 px, on `fond`, with the logo (22 px high), the practice context button (40 px) and the account. `.etaye-page` pads 24 px with a 24 px gap (16 px padding and 20 px gap below 760 px, 12 px padding below 360 px); `.etaye-page--large` caps at 1440 px. The page header (`.etaye-entete`) holds a breadcrumb (13/20), the title (22/28) and the actions; tabs (`.etaye-onglets`) are 40 px high with a 2 px `encre` underline on the current one.

**Columns:** `.etaye-cols--2` is fluid plus 360 px, `--2g` is 360 px plus fluid, `--3` is 300 px, fluid and 340 px, `--egal` is two equal columns. Below 1180 px the three-column layout becomes 280 px plus fluid with the third column full width; below 1100 px the navigation narrows to 216 px and every column stacks; below 760 px the layout is a single column, the navigation is hidden behind a menu button, tables restack into labelled rows, dialogs and drawers become full pages and the scrim disappears.

**Sign-in shell** (`.etaye-auth`): `papier` background, a card (`surface`, 1 px `filet-controle`, 8 px radius, 24 px padding) at most 440 px wide (640 px for the wide variant), title 26/32.

**Patient shell** (`.etaye-papp`, mobile first): `papier`, body 18/28, content at most 720 px wide, tabs with 48 px targets, a 24 px logo; larger type and touch targets than the practitioner side.

**Overlays:** one at a time. Dialog `min(560px, 100% − 48px)` wide, 96 px from the top, `surface`, 8 px radius, floating shadow; drawer `min(480px, 100%)` from the right; scrim `rgba(15, 16, 18, 0.48)`. Long reading or editing uses a page, not a dialog. Focus moves to the heading or first field, is trapped, and returns to the trigger.

## 6. Components

### 6.1 Existing in Figma (verified)

| Component (Figma) | Verified properties | CSS class and states in the reference |
|---|---|---|
| `Bouton` set `5:22`: Principal, Valider, Secondaire, Discret, Désactivé | Horizontal auto layout, height 32, gap 8, padding 12 (Discret 4), radius 4 | `.etaye-bouton`, 14/20 weight 500, 1 px border. **Principal** `encre` fill, `encre-inverse` text. **Valider** `accent` fill, `sur-accent` text. **Secondaire** `surface` fill, `filet-controle` border, hover `surface-2`. **Discret** transparent, `accent` underlined text. **Désactivé** `surface-2` fill, `encre-2` text, `filet` border. Press scales to 0.98. Patient size: 48 px high, 24 px padding, 18/24, radius 8. Withdrawal variant `--retrait`: `surface`, `critique` text and border |
| `Statut` set `5:46`: Critique, Attention, Validé, Neutre | Height 24, gap 4, padding 8, radius 4 | `.etaye-statut`, 13/20 weight 500. Critique `critique-fond` and `critique`; Attention `attention-fond` and `attention`; Validé `accent-fond` and `accent`; Neutre transparent, `encre-2`, inset 1 px `filet`. Reference-only: Publié (`surface`, `accent`, inset 1 px `accent`). Always a word, never color alone |
| `Repère` `5:47` | Height 16, gap 2, padding 2, radius 2 | `.etaye-repere`: the original insertion sign (a 7×6 mask) plus the source number, 11/16 mono SemiBold in `accent`, raised by 0.45 em; hover or expanded gives an `accent-fond` wash. Opens the exact dated source and returns focus to the marker |

Existing sample status labels in Figma all read "À valider": complete the real lifecycle labels in design, and never derive a medical classification from a variant name.

### 6.2 Received in the HTML reference (not Figma components, not approved)

Class names are in the [component CSS](../../design/received/2026-11-03/ds/); the values come from the tokens, so no new color appears.

| Component | Key values |
|---|---|
| Field (`.etaye-champ`) | Label 13/20 weight 500; input min-height 32 (patient 48), padding 0 12, `surface`, 1 px `filet-controle`, radius 4; help 13/20 `encre-2`. **Error**: 2 px `encre` border and `encre` help text, with a linked summary (`.etaye-resume-erreurs`, 2 px `encre` border). **Read-only**: `surface-2` fill, dashed border. **Disabled**: `surface-2`, `encre-2`, `filet`. Select has a drawn chevron; choices and checkboxes are 20 px (patient 24 px) |
| Banner (`.etaye-bandeau`) and toast | Banner: 12/16 padding, 1 px border, radius 4, icon plus title plus actions; variants attention, critique, info, neutre use the matching wash and border. Toast: `encre` fill, `encre-inverse` text, radius 8, floating shadow |
| Table (`.etaye-tableau`) | 14/20; header 13/20 `encre-2` with a 1 px `filet-controle` rule; cells 8/12 padding, 36 px rows, 1 px `filet` rules, row hover `surface-2`, selected row `accent-fond`; numeric columns right-aligned in mono; abnormal values bold `attention` or `critique`; below 760 px the table restacks (`--empile`) |
| Chronology (`.etaye-chrono`) | Columns 84 px date, 16 px rail, fluid body; 1 px `filet-controle` rail with an 8 px square node (`accent` for actions); type 13/20 `encre-2`, title 15/22 weight 500, detail 14/20 |
| Navigation (`.etaye-nav`) | 248 px, `fond`; 36 px rows, 15/22, `encre-2`; hover `surface-2`; current page `surface` with an inset 1 px `filet`; section labels 12/16 `encre-3`; search field 32 px with a keyboard hint |
| Identity banner (`.etaye-banniere`) | `surface`, bottom hairline, name 18/24, traits 14/20, mono identifiers; allergy block on `critique-fond` (or a neutral outlined "none" variant) |
| Panel (`.etaye-panneau`) | `surface`, 1 px `filet`, radius 0; head 12/16 padding with a hairline and a 16/24 title; body 16 padding, 12 gap |
| Annotation (`.etaye-annote`) | Reading text in Source Serif 4 16/28 and a 248 px dated note in the margin; a dashed bracket marks generated text, solid `accent` validated text, none for human text; below 760 px the bracket becomes a left border |
| Assertion and provenance (`.etaye-assertion`) | Statement 15/22 and a provenance line (12/16 `encre-3`) with source markers; struck-through text for removed content |
| Source drawer and viewer | Excerpt on `papier` with a highlighted `mark` (`accent-fond`, 2 px `accent` underline); viewer page in A4 ratio on `surface-2` |
| Information states (`.etaye-etat`) | Distinct glyph plus word for manquant (dashed square), négatif (crossed square), déclaré (circle, `attention`), discordant (triangle, `attention`), observé (filled), corrigé (outlined `accent`). Missing is never rendered as negative |
| Text origin labels (`.etaye-etiquette`) | A word in a bordered label (11/18): humain `accent`, alerte `attention`, manque dashed; generated text is underlined dashed, human text solid `accent`, alert wavy `attention` |
| Version diff, review checklist, audience, two-step validation and publication | Deleted text struck through on `surface-2`; inserted text underlined `accent` on `accent-fond`; checklist rows with 24 px state icons; audience block with a 1 px `accent` border naming the exact recipient; validation and publication are two separate steps, never one vague "Confirmer" |
| Upload and quarantine, empty, error and session states, skeletons | Dashed 1.5 px `filet-controle` drop zone on `surface-2`; empty block with a dashed border and a 16/24 title; error and session pages at most 560 px wide with a mono code line; skeleton bars on `surface-2` |
| Agenda, slots and days | 64 px time column, 48 px rows, appointments with a 3 px `accent` left edge on `accent-fond` (cancelled on `surface-2`); patient slots 48 px, selected slot `encre` fill; below 760 px the grid becomes a list |
| Trend chart (`.etaye-courbe`) | Series 2 px `accent`, second series 2 px dashed `encre-2`, hairline grid, missing periods drawn as gaps, always paired with a table; no score gauge |
| Consultation workspace (`ext-consult.css`) | Structured notes with an edit outline of 1 px `accent`, report editor on `papier`, version lineage with an inset 3 px `accent` current marker, patient-visible preview with a dashed border |
| Patient portal (`.etaye-patient`, `.etaye-carte-patient`, `.etaye-a-faire`) | `papier` page, `surface` card with 1 px `filet-controle` and radius 12, large date line 28/36, 24 px checkboxes, 48 px actions |
| Demo notice (`.etaye-demo`) | 12/16 `encre-3` with a 6 px square; shown on demonstration data (BR-018) |

Status vocabularies stay separate: processing, review, freshness and publication are distinct axes (`.etaye-axes`); never merge them into one badge. Button meanings include saving notes, validating a version and publishing to a named patient.

## 7. States and accessibility

- **Focus:** every interactive element shows a 2 px `accent` outline with a 2 px offset (`:focus-visible`), unobscured; scroll regions are focusable.
- **Meaning is never color alone:** a word, an icon or a line style accompanies every status (dashed for missing or generated, solid for validated, wavy for alert); the print and e-mail variants are grayscale-safe.
- **Targets:** 32 px for clinicians, 40 px for tabs, close and context controls, 48 px for patients; the design goal is 44×44 where space allows ([EXPERIENCE.md](../planning-artifacts/ux/EXPERIENCE.md)).
- **Hover** is an enhancement only; selection uses `aria-current`, `aria-pressed`, `aria-selected` or `aria-checked`.
- **Motion:** `prefers-reduced-motion: reduce` removes transitions; nothing necessary relies on animation.
- **Target level:** proposed WCAG 2.2 AA, **not measured conformance**; test 200 % zoom and 320 px reflow; charts have tables; announcements do not steal focus.

## 8. Content and tone

French interface copy is preserved. Demo data is synthetic only ([synthetic data](synthetic-data.md)). Button labels name the real action. No emoji navigation, no AI imagery or excess AI badges, no unsupported medical or product claim, no decorative illustration on clinical screens. Visual richness belongs in chronology, evidence relationships and legible data. Reuse realistic shared fictional content, dated references and corrections.

## 9. Documents, PDF and e-mail

A4 documents use the `doc-a4` rules in [ext-print.css](../../design/received/2026-11-03/ds/ext-print.css): page margins 22 mm top, 18 mm sides and 20 mm bottom, body 10.5 pt Source Serif 4 at 1.5 line height, headings in Atkinson, hairlines in points, statuses as a word plus a line style (dashed by default, solid validated, heavier solid on white published, dotted failure). Print sizes are specified separately rather than copying CSS pixels. E-mails are 600 px wide, 16 px body, one action, with a plain-text twin; their colors are literal values (the action is the `accent` value) because mail clients do not use custom properties. Details per document are in the [PDF and e-mail specifications](pdf-email-specifications.md).

## 10. Background layer (proposal, not approved)

**Proposal, not applied to any screen.** Paper grain with a restrained blueprint touch (variant B, confirmed as a variant by Aymen on 2026-11-18), the adopted dark tone `neutral-dark` (the dark grain starts from `--fond`), a 12 % grain and calm plates behind text and controls. It introduces no new color: it uses `papier`, `encre`, `filet`, `accent-fond` and `fond`, and clinical roles never sit on it. Full specification, allow and deny lists and measurements: [background-study](background-study/README.md); CSS: `docs/design/background-study/background.css`. A Figma Desktop development plugin that builds the study frames lives in `design/figma-background-study` once its pull request is merged. Do not add this layer to a screen without a design decision.

## 11. Reproducing and checking the rendering

```sh
python3 tools/design_system_reference.py --check        # this file matches the CSS tokens and the Figma-resolved values
python tools/rebuild_design_receipt.py --output <folder outside the repository> --pdf   # rebuild the reference screens
```

Compare a rebuilt screen and your implementation at the same viewport and theme, by opening both images. Measure contrast on the rendered pixels for each text and control pair. Record which images were opened and what remains unverified. Never report a documentary check as an application test.

## 12. Gaps and open items

- **Not designed as approved Figma components:** everything in section 6.2 exists as received HTML and flat imported frames, not as a Figma component library; practitioner navigation and selectors, fields, calendar and date controls, paginated tables, source drawers, version comparison, dialogs, drawers and print blocks still need design completion with interactive, focus, disabled, read-only, loading, error, restricted and long-content variants.
- **Not verified:** Figma effect style `2:73` values, the full variable collections (REST variables need Enterprise), Safari and Android WebView rendering, real devices, measured WCAG conformance, forced-colors and print rendering of the background layer.
- **Open decisions:** Aymen's design approval of the received screens, responsive frames for the corrected states, the clear-space rule for the logo, and any application of the background layer.
- **No development is authorized.** All 45 stories stay in backlog; this file does not change that.

## 13. Maintenance

Change the sources first (Figma, then `tokens.css`, `resolved-tokens.json` or the component CSS), then run `python3 tools/design_system_reference.py --write` and review the diff; the token tables are never edited by hand. Hand-written sections change only with Aymen's validation. Without Figma, a deliberate change of an existing value needs Aymen's decision; it is made in `tokens.css` or the component CSS, recorded with its date and reason, and listed in [continuation.md](../continuation.md) as "Figma to reconcile". The check fails on purpose until a recorded deviation is supported by the tool, so an unrecorded change cannot slip in. Update the status line and [continuation.md](../continuation.md) when a limit in section 12 is lifted.

## 14. Implementing the mockups without Figma

**User decision (2026-11-18):** "ok donc en general on doit etre capable de faire les maquettes qu'on a travaillé sur en dev meme sans figma, fait tout le necessaire". The mockups worked on must be implementable from this offline reference. This section is the exception record that the [coverage guide](coverage-guide.md) asks for ("Aymen's dated acceptance, affected row IDs, impact, fallback and explicit resumption trigger"), written for Aymen's confirmation. **It does not authorize development and does not replace design approval.**

| Element | Record |
|---|---|
| Dated acceptance | 2026-11-18, Aymen's message above; he then accepted the recorded wording the same day, and it is written in the `exceptions` column of the 76 [tracker](coverage.csv) rows (existing text kept) |
| Affected rows | All 76 families, for the requirement of exact Figma nodes only |
| What replaces the Figma node | The reference snapshot of the surface (HTML states, identity above), the tokens and recipes of this file, and the surface's entries in the [screen](screen-catalog.md), [interaction](interaction-catalog.md) and [PDF and e-mail](pdf-email-specifications.md) catalogs |
| Impact | The visual contract is the HTML reference, not Figma. The Figma file is left as it is and differences are reconciled later |
| Fallback | When Figma is readable again, compare the implemented surfaces with the corrected Figma frames and record node IDs in the tracker |
| Resumption trigger | Figma read access returns (MCP quota or REST window), or Aymen asks for the reconciliation |

**What still blocks development (verified 2026-11-18):** the explicit development authorization (not given); design approval (`coverage.csv`: 76 of 76 "Not approved"; `html-reception.csv`: 76 of 76 "No approval recorded"); and the design reviews of the coverage guide (desktop, tablet, mobile, A4 and functional). Figma access is not one of the blockers.

**In BMAD.** Team overrides in `_bmad/custom/` load this file, a short rule summary and the development gate into the UX, build, review, test, story and sprint-planning workflows and into the dev and UX agents (list and verification in [agent-setup.md](../agent-setup.md)). The gate is `[etaye] development_authorized` in `_bmad/custom/config.toml`, currently `false`: while it is false the build workflows are told to refuse implementation.

**Per-surface procedure, once development is authorized:**

1. Read the surface's tracker row, its catalog entries, stories and the other required sources of [AGENTS.md](../../AGENTS.md).
2. Open the reference state at the matching viewport and theme (rebuild with `tools/rebuild_design_receipt.py` if the external copy is missing).
3. Implement with the `.etaye-*` classes and the custom properties as the style contract: reuse `tokens.css`, the component CSS and `icons.css`; do not inherit a generic component theme or re-theme.
4. Capture the running UI at 1440×1024, 1024×768, 390×844 and the 320 px reflow check, in both themes, including empty, error, long, restricted and focus states.
5. Open the reference and the implementation at the same width and compare them as matching pairs (the method of the closure report); record every difference.
6. Measure contrast on rendered pairs; test keyboard, focus and 200 % zoom.
7. Record the evidence in the tracker (`visual_evidence`, `exceptions`); never mark a row approved without Aymen.

**Ready:** generated and checked tokens, component recipes, shells and breakpoints, the snapshot identity, this procedure. **Missing before a first screen can be built:** design approval, the development authorization, the frontend scaffolding (not authorized), a decision on how the tokens reach the React code (the proposal is to reuse `tokens.css` unchanged) and a harness that compares running-UI screenshots with the reference (the measurement tools of the [background study](background-study/README.md) show a workable method). **Not verified:** that the full reference rebuilds in this environment today.
