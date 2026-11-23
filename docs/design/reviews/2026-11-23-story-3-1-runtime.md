# Story 3.1 running UI review

2026-11-23 · Story 3.1 · AT-009-C (SAMPLED, UD-37) · branch `feat/3.1-find-register-local-patient`. [Browsable comparison page](2026-11-23-story-3-1-runtime/index.html) · [Design review](2026-11-23-story-3-1-design-review.md) · [Implementation record](../../implementation-artifacts/story-3-1-implementation-record.md)

**Status: running-UI approval is awaited from Aymen.** The design of the specimens is approved (UD-64); this record shows the running application beside them and decides nothing.

The left panel of every sheet is the approved specimen; the right panel is Chromium 153.0.8010.12 running the real stack: the React build served by nginx, the Spring Boot API with the V8 migration and PostgreSQL 18, from the local demonstration package (Compose project `etaye-demo-31`, `etaye.demo.patient-input-enabled` on). The account is the fictional Dr Nora Lefebvre. The pairs were produced in one scripted session per theme at 1440×1024 and 390×844, with the system color scheme emulated; the page canvas, shell and `data-theme` were asserted on every capture (24 of 24 on the expected theme, no horizontal overflow).

## Sampled screens (UD-63 I1: six screens, two widths, both themes)

| Sheet | Opened | What the running application shows beside the specimen |
|---|---|---|
| S-07 list (`liste-*`) | 1440 light | Same shell, navigation limited to "Patients", search field with "Rechercher", loaded count and one "Modifier l'identité" per row. The list has four rows instead of three: the check created one synthetic long-named patient. |
| S-07 no result (`aucun-resultat-*`) | 1440 dark | Same dashed block, the same copy and the two actions; "Effacer la recherche" appears in the search row as in the specimen. |
| S-07 long names (`longs-*`) | 390 dark | The long composed name wraps onto three lines, the birth date stays whole and the action stays below. The check filtered on "Dupont", so the sheet shows "Effacer la recherche" and "1 patient affiché" where the specimen shows the unfiltered template. |
| S-08 input errors (`erreur-*`) | 1440 light | Error summary with two linked errors, field messages, the segmented date field with the "31 février" message and the entered values kept. |
| S-08 likely duplicate (`doublon-*`) | 390 dark, 1440 light | Warning banner with the typed candidate and the confirmation sentence, "Ouvrir le dossier existant" and "Créer quand même", the form filled below. |
| S-08 unsaved changes (`sale-*`) | 390 light | The guard replaces the form: heading, explanation, "Rester sur le formulaire" and "Abandonner les modifications". |

All 24 sheets are in [the folder](2026-11-23-story-3-1-runtime/); seven were opened side by side for this record (the ones marked above) and the other seventeen were produced by the same script and checked only through the page assertions.

## Observed differences from the specimens

1. **Role label.** The running header reads "Praticien · Nora Lefebvre"; the specimen reads "Praticienne · Dr Nora Lefebvre". The application keeps the existing story 1.3 label from the session projection.
2. **Focus ring on arrival.** The specimens show a ring on the error summary and on "Rester sur le formulaire" because they use the `autofocus` attribute. After a pointer action the running application moves focus there without a ring; the ring appears for keyboard focus (UD-62 rule). The stubbed smoke suite asserts the focus target and the 2 px keyboard ring.
3. **Context button.** About 4 px taller than in the specimens (the 1.3 style); its accessible name includes the account name.
4. **Conflict panel.** Without a sheet in this sample: it shows "version N · hh:mm" and no author, because the API exposes none (single deviation from the approved specimen; it is stated in the implementation record).
5. **After a save.** The form shows "Enregistré" for 600 ms and the list then shows an "Identité enregistrée" notice, which the specimens do not draw.
6. **Several candidates.** With more than one candidate the panel has an "Ouvrir ce dossier" link per candidate instead of one button.

## Functional evidence for this revision

- API: `./mvnw -B -ntp verify`, 198 tests, 0 failures (PostgreSQL 18.7 Testcontainers). Web: `tsc -b`, `build` with the SDK drift check, Vitest 38 tests, stubbed Chromium smoke 110 tests (29 new, each state scanned with axe-core, plus keyboard, focus, shell background, overflow and idempotency-key assertions).
- Real stack: five Playwright specs on the rebuilt package pass in one clean run (registration with the duplicate warning and the named confirmation, practice scoping, a read-only member refused with the restricted state, a stale correction keeping the input, the unsaved-changes guard). Earlier runs failed for test reasons only (a list read before it rendered, the package treating a hard navigation as an expired session, the per-address sign-in limit) and the specs were corrected; no application defect was found by them.

## Not verified

Tablet 1024×768 and the 320 CSS px reflow on the running application (the smoke suite asserts reflow and overflow with stubs, not these captures), screen readers, 200 % zoom, browsers other than Chromium, contrast measured on rendered pixels, behavior under real latency, the real outage spec, and the remaining seventeen sheets opened by eye. Figma was not read; the 2026-11-18 exception applies.

## How to look at it

The package is running on this machine: open http://localhost:4179, choose the practitioner scenario, sign in as `nora.lefebvre@example.test` with the demo password from `infra/demo/README.md`, then "Patients". Try the search ("Martin", "18/04/1978", "Petit"), create "Claire Martin" born 18/04/1978 to see the duplicate panel, correct a patient, and leave the form with typed text. To start it elsewhere run `./infra/demo/start.sh`. Reset restores the fictional fixture.
