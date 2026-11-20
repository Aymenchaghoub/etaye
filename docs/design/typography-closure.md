# Type roles after the design-system closure

Status: **decided by Aymen on 2026-11-20** (UD-43, [ADR-014](../decisions/ADR-014-design-system-closure.md)). Not applied to any file yet: `tokens.css`, the token tables and the component CSS are unchanged by this decision. Evidence labels follow [AGENTS.md](../../AGENTS.md).

## 1. Finding (Verified observation)

Rule 3 of the consistency checklist in the [design system](design-system.md#consistency-checklist) says that text comes only from the 14 roles of section 4.3. Measured on `main` at `b48d1c2` over every `font:` shorthand with a `var(--font-*)` family in `bundle-base.css`, `bundle-app.css` and `ext-consult.css`: **130 usages in 39 distinct combinations** of family, size / line height and weight, of which **26 combinations (63 usages, 48 %) match none of the 14 roles**. Weight counts: `13/20` at weight 400 (15 usages) is not `ui-libelle` (13/20 at 500), and the button text (`14/20` at 500) is not `ui-dense` (400).

Not counted: declarations written as separate `font-size` and `line-height` properties, `ext-print.css` (documents have their own scale) and e-mail. `tools/design_system_reference.py --check` compares the generated tables with `tokens.css` and the Figma-resolved values, not the component CSS, so this drift was invisible to it. An independent review had estimated about 20 off-scale combinations; counting the weight gives 26.

## 2. Decision

The roles of section 4.3 describe fewer styles than the received mockups use. The mockups are what Aymen reviewed, so the roles follow them and not the other way round:

- A combination used by **two or more selectors**, or by the Figma-verified Repère component, **becomes a role** (13 roles).
- Two isolated mono values of **patient touch targets** are kept as roles (`donnee-action`, `donnee-jour`): reducing them to 14 px would shrink the slot and day controls of the 48 px patient scale. Total **15 roles added, 29 in all**.
- An **isolated value moves to the nearest role** (same family; then size, line height, weight) when the shift is at most 1 px of size and 4 px of line height (9 values, below).
- The two **A4 preview** values keep their own document scale (2 exceptions).
- No value of a component verified in Figma (Bouton, Statut, Repère) moves.

## 3. Roles added (15)

| Role | Class | Family | Size / line height | Weight | Usages | Where |
|---|---|---|---|---|---|---|
| `doc-citation` | `.doc-citation` | Source Serif 4 | 15 / 26 px | 400 | 2 | quotations and excerpts in notes and source layers: `.etaye-notes__corps blockquote`, `.etaye-couches blockquote` |
| `donnee-secondaire` | `.donnee-secondaire` | Atkinson Hyperlegible Mono | 13 / 20 px | 400 | 4 | identifiers, numeric table column, pagination, error-page code: `.etaye-identifiant`, `.etaye-tableau .etaye-col-3` |
| `donnee-meta` | `.donnee-meta` | Atkinson Hyperlegible Mono | 12 / 16 px | 400 | 3 | keyboard hint, navigation counter, agenda hours: `.etaye-touche`, `.etaye-nav__compte` |
| `repere` | `.repere` | Atkinson Hyperlegible Mono | 11 / 16 px | 600 | 1 | the Repère component (Figma `5:47`, verified): `.etaye-repere` |
| `ui-aide` | `.ui-aide` | Atkinson Hyperlegible Next | 13 / 20 px | 400 | 15 | field help, margin notes (the most used off-role value, 15 usages): `.etaye-champ__aide`, `.etaye-annote__marge` |
| `ui-corps-fort` | `.ui-corps-fort` | Atkinson Hyperlegible Next | 15 / 22 px | 500 | 7 | allergy list, chronology titles and other emphasised body text: `.etaye-allergies__liste`, `.etaye-chrono__titre` |
| `ui-lecture` | `.ui-lecture` | Atkinson Hyperlegible Next | 16 / 24 px | 400 | 5 | patient tabs and list secondary text: `.etaye-ptabs a`, `.etaye-pliste__txt small` |
| `ui-action` | `.ui-action` | Atkinson Hyperlegible Next | 14 / 20 px | 500 | 3 | buttons, source preview title: `.etaye-bouton`, `.etaye-apercu__titre` |
| `meta-aere` | `.meta-aere` | Atkinson Hyperlegible Next | 12 / 18 px | 400 | 2 | note origin, editor bar (candidate to merge with `meta`): `.etaye-notes__origine`, `.etaye-redaction__barre` |
| `meta-fort` | `.meta-fort` | Atkinson Hyperlegible Next | 12 / 16 px | 500 | 2 | step numbers, patient-visibility title: `.etaye-deuxtemps__num`, `.etaye-vu-patient__titre` |
| `ui-dense-aere` | `.ui-dense-aere` | Atkinson Hyperlegible Next | 14 / 22 px | 400 | 2 | layer and option lists (candidate to merge with `ui-dense`): `.etaye-couches > li`, `.etaye-option ul` |
| `ui-sous-titre` | `.ui-sous-titre` | Atkinson Hyperlegible Next | 20 / 28 px | 500 | 2 | dialog and patient-page secondary headings (h2): `.etaye-pmain h2`, `.etaye-dialogue__tete h2` |
| `ui-titre-page` | `.ui-titre-page` | Atkinson Hyperlegible Next | 26 / 32 px | 500 | 2 | sign-in and error-page headings (h1): `.etaye-auth h1`, `.etaye-etat-page h1` |
| `donnee-action` | `.donnee-action` | Atkinson Hyperlegible Mono | 16 / 24 px | 500 | 1 | slot buttons (patient, 48 px target): `.etaye-creneau` |
| `donnee-jour` | `.donnee-jour` | Atkinson Hyperlegible Mono | 18 / 24 px | 500 | 1 | day number of the day strip: `.etaye-jour b` |

## 4. Values normalised (9)

| Current value | Where | Normalised to | Change |
|---|---|---|---|
| Source Serif 4 14 / 22 px, 400 | `.etaye-sources-liste blockquote` | `doc-citation` (15 / 26 px, 400) | size +1 px, line height +4 px |
| Source Serif 4 15 / 24 px, 400 | `.etaye-apercu__extrait` | `doc-citation` (15 / 26 px, 400) | size +0 px, line height +2 px |
| Source Serif 4 16 / 30 px, 400 | `.etaye-redaction p` | `doc-corps` (16 / 28 px, 400) | size +0 px, line height -2 px |
| Source Serif 4 17 / 26 px, 500 | `.etaye-redaction h3` | `doc-intertitre` (18 / 28 px, 500) | size +1 px, line height +2 px |
| Atkinson Hyperlegible Mono 12 / 20 px, 400 | `.etaye-chrono__date` | `donnee-secondaire` (13 / 20 px, 400) | size +1 px, line height +0 px |
| Atkinson Hyperlegible Next 11 / 18 px, 500 | `.etaye-etiquette` | `meta-fort` (12 / 16 px, 500) | size +1 px, line height -2 px |
| Atkinson Hyperlegible Next 13 / 18 px, 500 | `.etaye-rdv strong` | `ui-libelle` (13 / 20 px, 500) | size +0 px, line height +2 px |
| Atkinson Hyperlegible Next 14 / 18 px, 500 | `.etaye-contexte__cabinet` | `ui-action` (14 / 20 px, 500) | size +0 px, line height +2 px |
| Atkinson Hyperlegible Next 15 / 24 px, 400 | `.etaye-notes__corps` | `ui-corps` (15 / 22 px, 400) | size +0 px, line height -2 px |

## 5. A4 scale exception (2)

Simulated A4 content follows the document scale (section 9 of the design system) and is not a user-interface role:

- `.etaye-visionneuse__page`: Source Serif 4 12 / 18 px, 400.
- `.etaye-a4`: Source Serif 4 14 / 21 px, 400.

## 6. Consolidation candidates

`meta-aere` (12/18) and `ui-dense-aere` (14/22) differ from `meta` (12/16) and `ui-dense` (14/20) by 2 px of line height only. They are kept as validated because each is used by two selectors. Merging them is a simple consolidation once the generator and a rendered before/after comparison exist (D-50).

## 7. How and when this is applied

Never by hand: the generated tables stay at the 14 Figma-resolved roles until the token generator of story 10.6 writes the new ones from `tokens.json` (D-50). The 15 added roles have no Figma text style: they are recorded as "Figma to reconcile" ([design system section 13](design-system.md#13-maintenance)). The component CSS keeps its current values until the same step. Until then this file is the authority for the 15 added roles.

## 8. Limits

One script run on one commit; the selector lists are truncated to the first usages. A rendered specimen of the 29 roles, the 9 normalisations (before and after) and the 2 kept values was reviewed by Aymen on 2026-11-20 and accepted (a local page, not committed); the effect on a running interface is not measured.
