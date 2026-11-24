# Story 3.2 running UI review

2026-11-24 · Story 3.2 · AT-010-C (CORE for S-09 and S-10, SAMPLED for I-27, UD-16, UD-46) · branch `feat/3.2-explore-chronology-and-explicit-assertions`. [Browsable comparison page](2026-11-24-story-3-2-runtime/index.html) · [Design review](2026-11-24-story-3-2-design-review.md) · [Implementation record](../../implementation-artifacts/story-3-2-implementation-record.md)

**Status: prepared for Aymen's decision; not approved.** The running-UI approval is a separate written decision (UD-69 did not give it). Nothing here claims it. This page states what was captured, what was opened, what differs from the approved specimens and what was not checked.

The left panel of every sheet is the approved specimen (UD-68); the right panel is Chromium 153 (Playwright 1.63.0) running the application at the code revision of the implementation record. States the real stack can produce come from the local demonstration package (Compose project `etaye-demo-32`: the React build served by nginx, the Spring Boot API, PostgreSQL 18.7, migrations to V10, the account of the fictional Dr Nora Lefebvre or, for the restricted state, Mila Dubois). States that need a fault, a delay or a long fixture (loading, partial and full failure, restricted source, unknown event date, long French content) come from the stubbed API of the smoke suite, because they cannot be induced on the real stack without fault injection. Each capture was taken with the system color scheme emulated, at the stated viewport, and measured on the rendered page: no horizontal overflow, no clipped date, author or source box, top bar and page on `--fond`, work area on `--surface` (94 of 94, no failure).

## Matrix (packet section 3)

- **Every state of S-09, S-10 and I-27 and the S-07 list at 1440×1024, light and dark:** 25 states, 50 sheets (15 for S-09, 7 for S-10, 2 for I-27, 1 for S-07).
- **Six risk states at 1024×768, 390×844 and 320 CSS px (reflow), light and dark:** rich record, thin record, past-instant view, full failure, loading, long French content; 36 sheets.
- **Restricted state** (administration-only account, compared with the approved S-36 reference) at the four widths, both themes: 8 sheets.
- **Keyboard focus** (running UI only, no specimen): the past-instant control, an opened source and a source marker, at 1440 and 390, both themes: 12 captures.
- The `s10-vocabulaire` specimen is a reference sheet, not an application screen, and has no running counterpart.

## What was opened

Opened by me, side by side, from the successive generations of sheets: S-09 rich 1440 light, S-09 rich 390 dark, S-09 thin 1440 light (final generation), S-09 thin 390 light, S-09 past-instant 1440 light, S-09 loading 390 dark, S-09 long 320 light, S-10 conflict 1440 light, I-27 opened sources 1440 dark and light, S-36 restricted 390 light and 1440 dark, and the keyboard-focus capture of an opened source at 1440 light (final generation).

The sheets and captures were regenerated after the Codex review fixes, on a stack rebuilt on a fresh volume; of that last generation, I-27 with opened sources (1440 dark) was opened again. Three rounds of fixes followed from these openings (below). **The other sheets were produced by the same scripts and checked by their page assertions only; no claim of a full visual comparison is made.**

Fixed after opening: the allergy block wrapped on two lines (a 420 px cap I had added), the “non confirmé” chip labelled a negative consultation note, the conflict line of the sections panel quoted the whole patient sentence, unresolved sections used the alert icon (only a conflict does), the restricted state used a banner instead of the approved S-36 layout, the loading state showed the filters and an alert icon in the allergy block, the I-27 review lacked cards and used a blue banner for its note, the thin-record notice was amber instead of neutral and a count line appeared under an empty record.

## Differences from the specimens that remain

See section 4 of the [implementation record](../../implementation-artifacts/story-3-2-implementation-record.md): the weight correction wording, the origin chip scope, the omitted “Champ manquant” row of I-27, the meta line of an empty record, the toggle appearing once the record is loaded, the restricted sentence and shell navigation, Paris time as a constant, and the wrapped present-state header line. The role label reads “Praticien · Nora Lefebvre” (story 1.3), not “Praticienne · Dr Nora Lefebvre”. Hover is not shown in a static capture; the in-place source buttons and markers keep the ring of every control on keyboard focus (2 px, asserted by the smoke suite on the first 14 tab stops).

## How Aymen can look at it

From the repository root, with the branch checked out: `COMPOSE_PROJECT_NAME=etaye-demo-32 ETAYE_DEMO_WEB_PORT=4182 docker compose -f infra/demo/compose.yml up --build -d`, then open http://localhost:4182, sign in as `nora.lefebvre@example.test` (the demo password is in `infra/demo/README.md`), choose the practice, **Patients**, **MARTIN Claire** or **BERNARD Louis**. Things to judge: the date column on a phone width, the in-place sources, the past-instant view at 20/10/2026 10:14, the conflict review, the sentences of the sections (never “aucune allergie”), the filter bar, and Mila's refusal.

## Not verified

Screen-reader behavior; 200 % zoom; browsers other than Chromium; hover and the pointer states; rendered-pixel contrast beyond axe-core; a comparison against Figma (not readable in this session, the 18 November exception applies); the tablet width for the states other than the six risk states; the real stack under a database outage.
