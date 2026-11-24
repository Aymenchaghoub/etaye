# Story 3.1 design review — S-07, S-08 and I-04 adapted states

Status: **Aymen approved the 26 specimens as presented on 2026-11-23** (“J’approuve ces états tels que présentés. Cette réponse est une approbation de design seulement ; mon autorisation de développement reste une décision écrite distincte.”). [UD-64](../../planning-artifacts/decisions-assumptions.md) records the scope. This is a design approval of specimens only: at the time of UD-64 it was not a development authorization (`3.1` was not yet in `[etaye] authorized_stories`; Aymen granted that separately the same day, [UD-65](../../planning-artifacts/decisions-assumptions.md)), and it is not a running-UI approval or a merge authorization. [Local review page](2026-11-23-story-3-1-specimens/index.html) · [Context packet](../../implementation-artifacts/story-3-1-context-packet.md) · [Arbitration page](2026-11-23-story-3-1-phase0-arbitration.html)

## What was shown

The page shows 26 static specimens built with the pinned offline snapshot `d2f0800e275c4b156fedbcf23656ffe2bf435f9b6a72faf0f86fc293e2a82ad6` (its tokens, `etaye.css`, `review-corrections.css`, logos and fonts), each in the light and dark themes at 1440×1024, 1024×768, 390×844 and 320×844. They apply the arbitration of UD-63 to the references of the three families. Each specimen lists its starting reference and the change.

| Family | Specimens (file in `2026-11-23-story-3-1-specimens/specimens/`) |
|---|---|
| **S-07 · SAMPLED** | `s07-liste`, `s07-chargement`, `s07-vide`, `s07-aucun-resultat`, `s07-suite`, `s07-suite-echec`, `s07-erreur`, `s07-a-actualiser`, `s07-longs`, `s07-cree`, `s07-planificateur`, `s07-planificateur-court`, and `menu-mobile` for the menu below 760 px (D-46). |
| **S-08 · I-04 · SAMPLED** | `s08-nouveau`, `s08-erreur`, `s08-doublon`, `s08-doublon-change`, `s08-enregistrement`, `s08-verification`, `s08-echec`, `s08-sale` (the I-20 guard, UD-54), `s08-modifier`, `s08-conflit`, `s08-restreint`, `s08-planificateur`, `s08-planificateur-refus`. |

**Approved deviations from the references (UD-63, shown in the specimens).** The `.etaye-app` shell carries a navigation limited to “Patients”, with the account name and a sign-out button replacing the undelivered profile link. The list has no “Espace patient” column or filter and no “dossier R-A-001” identifier, rows act through “Modifier l’identité” because S-09 does not exist, search is submitted with “Rechercher”, and the list has an explicit “Afficher plus”. The form has five fields (no “Nom d’usage”, no “Origine de ces informations”) and a segmented Jour / Mois / Année birth-date field without calendar. The duplicate panel lists typed same-practice candidates and “Créer quand même” confirms a distinct person; a new candidate re-displays the list. Creation returns to S-07 with the new row marked “Dossier créé”. The edit banner is identity-only. The scheduler states drop the stale “Proposition PD-001, non validée” wording (DF-3). The “Ouvrir le dossier existant” link leads to the patient's identity page until S-09 exists (3.2).

**Shell and background pinned (UD-60).** Top bar and navigation on `--fond`, work area on `--surface`, page canvas `--fond`, in both themes; the same shell holds the success, loading, empty, error, restricted, stale and recovery states. The scheduler specimens use the same shell for review only: their navigation is decided with I-10 in story 4.2.

## Changes after the specimens were first generated

Before the approval was recorded, the generator was re-run to fix one defect and one detail; Aymen opened the live local page while the generator was still being re-run, so they are disclosed here instead of assumed approved. (1) States that move focus to their title on load (`s07-cree`, `s08-restreint`, `s08-planificateur-refus`) showed a focus ring on the heading, which Aymen had asked to avoid in story 1.1; the ring is removed and only keyboard-focusable controls show it. (2) The viewer scales the frame to the page width. No copy, state, layout or field changed. The approval covers the final files committed with this record; revert either change on request.

## Change after the approval

On 2026-11-23, looking at the running list, Aymen remarked that the "Rechercher" button was misaligned with the search field. The specimens had the same offset (the button sat on the bottom of the field block, beside the help text). The button now shares the field's own row in the application and in the S-07 specimens, which were regenerated; the full specimen verification below was run again on the corrected files with the same result (208 views, 52 axe-core runs, no failure).

## Evidence and limits

Chromium 153.0.8010.12 and axe-core 4.14.1 (via `@axe-core/playwright` 4.14.0), run on the committed files:

- **208 views** (26 specimens × 2 themes × 4 viewports): no horizontal overflow, a visible `h1`, the page and top bar on the `--fond` value and the work area on the `--surface` value of the theme, the theme attribute applied, no focus ring on a focused heading, no page error and no failed request. **No failures.**
- **52 axe-core runs** at 390×844 (26 specimens × 2 themes): zero violations and zero incomplete results. The review index page: six runs (three widths, two themes), zero violations.
- **Keyboard.** At 390×844 the menu button opens the navigation (`aria-expanded` true, focus moves into the navigation) and Escape closes it and returns focus to the button. `s08-sale` starts with focus on “Rester sur le formulaire” and `s08-erreur` on the error summary. In `s08-doublon` every Tab stop through the banner, the five fields and the submit button shows a 2 px outline.
- **Opened captures.** The review index at 1440×1024 light; S-07 liste at 1440×1024 light; S-08 conflit at 1024×768 dark; S-08 doublon, S-07 cree and the open menu at 390×844 (dark, dark and light); S-07 longs at 320×844 light. The other specimens were rendered and checked automatically but not all opened; no claim of a full visual comparison is made.
- **Not verified.** Screen-reader behavior, 200 % zoom, browsers other than Chromium, the live search, create and correction flows, the idempotent retry, and any comparison of the running application with these specimens. Contrast was not measured on rendered pixels in this review. The specimens are static HTML: the generator was kept outside the repository and the committed files are the artifact. Figma was not read; the 18 November exception applies.

## Effect

`coverage.csv` and `html-reception.csv` record the design approval of S-07, S-08 and I-04 for the 3.1 specimens, with functional review `Pending`. Development authorization of story 3.1 alone followed in writing the same day ([UD-65](../../planning-artifacts/decisions-assumptions.md)). The decisions that remain are Aymen's approval of the matched running-UI comparisons (six sampled screens at 1440×1024 and 390×844, both themes, UD-63 I1; see the [running review](2026-11-23-story-3-1-runtime.md)) and the merge.
