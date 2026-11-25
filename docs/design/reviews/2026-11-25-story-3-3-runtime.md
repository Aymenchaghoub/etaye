# Story 3.3 running UI review

2026-11-25 · Story 3.3 · AT-011-C (SAMPLED for S-11, UD-16, UD-37) · branch `feat/3.3-measurements-trends`. [Browsable comparison page](2026-11-25-story-3-3-runtime/index.html) · [Design review](2026-11-25-story-3-3-design-review.md) · [Implementation record](../../implementation-artifacts/story-3-3-implementation-record.md)

**Status: prepared for Aymen's decision; the running UI is not approved.** The approval of the running interface (UD-72 approved the specimens only) and the merge authorization are separate, later decisions. This page states what was captured, what was opened, what differs from the approved specimens and what was not checked.

The left panel of every sheet is the approved specimen (UD-72); the right panel is Chromium 153 (Playwright 1.63.0) running the application at the code revision of the implementation record. States the real stack can produce come from the local demonstration package (Compose project `etaye-demo-33`: the React build served by nginx, the Spring Boot API, PostgreSQL 18, migrations to V12, the account of the fictional Dr Nora Lefebvre): the default state, the lineage opened, the record without measurement, the three past instants and the filter without result. States that need a fault, a delay or a long fixture (loading, partial failure, mixed units, incomplete reading, long content, truncated series, failure of the whole record) come from the stubbed API of the smoke suite, because they cannot be in the real fixture without fault injection; each sheet says which source it uses.

## Matrix (packet section 3, SAMPLED)

- **Thirteen S-11 states and the shared failure of the whole record, at 1440×1024 and 390×844, light theme:** 28 sheets.
- **The default, the lineage opened and the mixed-unit states also in the dark theme:** 6 sheets.
- **320 CSS px reflow for every state:** 14 sheets.
- **Keyboard focus** (application only): the period filter, a source marker, the lineage button and the past-instant button at 1440 light: 4 captures.
- The tablet width (1024×768) was not captured: the SAMPLED level asks for desktop and mobile (UD-37). In all, 48 sheets and 4 focus captures.

## What was opened

Opened by me, side by side. **After the last change** (the tab colour fix below): the default state at 390 light (real stack), the mixed units at 1440 light, the incomplete reading at 1440 light, the truncated series at 390 light, the partial failure at 1440 light and the record without measurement at 390 light. **Before that change**, so that the tab colour difference is visible on them: the default at 1440 light, the lineage at 390 dark, the past instant before the correction at 1440 light, the filter without result at 1440 light and the long content at 320 light. In all 11 sheets were opened; the earlier generation of five was regenerated afterwards but not opened again. **The other sheets were produced by the same scripts and checked by their page assertions only (no overflow, nothing clipped, the shell and background of the theme asserted on every capture); no claim of a full visual comparison is made.**

Fixed after opening: the inactive tabs were blue in the application and grey in the specimens, because the authentication stylesheet colours every class-less link with the accent (specificity 0,2,1) and outranked the tab rule; this has existed since story 3.2 for S-09 and S-10 and is fixed for the three tabs, with a computed-colour assertion that fails without the fix. Earlier openings of the stubbed captures by the implementation pass found and fixed the panel order (blood pressure first, as in the specimen) and the origin chip of a corrected row.

## Differences from the specimens that remain

1. **Table order.** The application orders the table by observed time, newest first, as the contract says (the 20/10/2026 10:12 weight above the 10:10 blood-pressure reading); the approved specimen listed the two readings the other way.
2. **Origin cell.** The author is shown on a corrected row too; "Observé" or "Observée" agrees with the measure only for blood pressure, other concepts default to the masculine.
3. **Count line and truncation.** A line "N mesures affichées" shows in every state, not only the filtered one, and the truncation notice carries its button inside the panel.
4. **Specimen-only wording.** The 429 sentence of the failure banner, the template banners and the sentence "Axe propre à cette unité : aucune conversion" are not built; the mixed-unit note is kept in a shorter form.
5. **Axes** are computed from the data, so ticks differ from the hand-set ones of the specimens (for example 9 to 15 instead of 0 to 20).
6. **The record without measurement** names the patient as "BERNARD Louis", the banner label, instead of "Louis Bernard".
7. **The as-of banner icon** is the information icon delivered in 3.2, not the history icon of the specimen. **The role label** reads "Praticien · Nora Lefebvre" (story 1.3), not "Praticienne · Dr Nora Lefebvre".
8. **The truncated series** lists the 25 loaded rows, where the specimen shows four.
9. **The incomplete reading** of the stubbed capture is at 07:40 (Paris), the specimen at 08:40: stub data, not the application.

## After the review fixes (UD-74)

The Zcode review of the branch (see the [implementation record](../../implementation-artifacts/story-3-3-implementation-record.md#6-review)) led to fixes after the sheets above were produced. They change three things a user can see, none of which has a specimen or a comparison sheet:

- **A section "Mesures corrigées"** shows the previous value, the reason and the history of a corrected measurement that no drawn chart carries (a series left in the table, an incomplete reading). It reuses the section title, the correction chip and the note of the series panel.
- **A notice "Fuseau du cabinet non disponible"** (attention banner) appears above the record when the session carries no readable zone and the screens fall back to Paris time.
- **Two short sentences:** "Historique raccourci : …" when a lineage exceeds 100 revisions, and "valeur non chargée" in the correction note when the history read failed.

They are asserted by the smoke suite (axe-core on the first two) and were never opened as a picture, because the demonstration data cannot produce them and the specimens do not cover them: **they are the part of the running UI I ask Aymen to look at with a stubbed or edited dataset if he wants a visual decision.** The states the specimens do cover were recaptured on the rebuilt package (25 real-stack captures, no horizontal overflow, shell and background asserted in both themes); the desktop light default and the 390 px dark state with the history open were opened and match the sheets. The singular wording of the sentence about the series left in the table was corrected ("1 autre série n'est pas tracée").

## How Aymen can look at it

From the repository root, with the branch checked out: `COMPOSE_PROJECT_NAME=etaye-demo-33 ETAYE_DEMO_WEB_PORT=4183 ./infra/demo/start.sh`, then open http://localhost:4183, sign in as `nora.lefebvre@example.test` (the demo password is in `infra/demo/README.md`), choose the practice, **Patients**, **MARTIN Claire**, then the **Mesures** tab. Things to judge: the table order, the corrected weight and **Voir l'historique**, the past instant at 20/10/2026 10:14 and 10:16, the chart and its sentence, the three tabs at phone width, and Louis's empty state.

## Not verified

Screen-reader behavior; 200 % zoom; browsers other than Chromium; hover and the pointer states; rendered-pixel contrast beyond axe-core; a comparison against Figma (not readable in this session, the 18 November exception applies); the tablet width; the states that need a fault or a long fixture on the real stack (they use the stubbed API); the real stack under a database outage.
