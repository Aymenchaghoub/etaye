# React Aria Components spike — 2026-11-20

Status: **Result of the spike that ADR-013 made a condition of its choice (D-49).** Aymen read the results on a visual page and confirmed the choice (UD-47) with the reservations of section 5. The spike code is a throwaway outside the repository and is not committed; this record keeps what it established and what it did not. Evidence labels follow [AGENTS.md](../../../AGENTS.md).

## 1. Method and who verified what

- **Attempt 1.** A local agent (zcode) built the spike and reached its 40-minute limit without writing a report. Its raw results came from an earlier version of the code (the drawer failed, the combobox showed two violations). They are not used.
- **Attempt 2 (the evidence).** The orchestrator rebuilt the checks and reran every one on the final code. Environment: React 19.3.1, react-aria-components 1.22.1, `@internationalized/date` 3.12.4, Vite 8.3.8, axe-core 4.14.1, Chromium 153 driven by Playwright, one throwaway project outside the repository. The four demos (dialog, drawer built with the library's `Sheet` and `SheetOverlay`, patient combobox, date calendar) were dressed only with the received Étaye CSS and tokens, plus the few classes listed in section 4.
- **Overlay exclusivity, rechecked.** The first test of "one overlay at a time" read the wrong DOM attribute for the drawer-then-dialog direction and was inconclusive. It was redone: with the drawer open the dialog button sits inside an inert subtree under the veil and a click does not open the dialog; with the dialog open a click under the veil does not open the drawer.

## 2. Results (Verified observation, Chromium only)

| Criterion | Dialog | Drawer | Combobox | Calendar (fr-FR) |
|---|---|---|---|---|
| Keyboard: opens, focus enters, Tab stays inside, Escape closes and returns focus to the trigger | pass | pass in the initial desktop check (see section 3) | pass (arrows, Escape, Enter; value chosen) | pass (arrows, Home, End; unavailable days focusable but not selectable) |
| axe-core, light and dark themes | 0 violations | 0 violations | 3 violations (section 3) | 0 violations |
| French strings | pass | pass | pass | pass ("novembre 2026", L M M J V S D, "Date sélectionnée : vendredi 27 novembre 2026") |
| Mobile 390 px | full page | full width, veil hidden. Escape closure failed in the standalone check (section 3) | not measured | not measured |
| Closed days (Saturday 21 and Thursday 26 November in the test) | not applicable | not applicable | not applicable | struck through by Étaye CSS, not selectable |
| Gzip weight of the component's own chunk, shared chunks excluded | about 6.5 kB | about 7.2 kB | about 31 kB | about 49 kB |

The common core (React, react-dom and the React Aria core, one file) is about 101 kB gzip; it was not separated from React. The chunks share the modal, input and filter code, so the figures do not add up.

## 3. Findings

1. **Combobox, three axe violations, identical in both themes.** `page-has-heading-one` is an artefact of the test page. `region` flags the suggestion list, mounted in a container outside the main landmark: probably reproducible in a real page, to be confirmed. `aria-hidden-focus` (impact serious): while the list is open the rest of the page is hidden from assistive technology although the header stays reachable by keyboard. The dialog and the drawer do not show it because their background is made inert.
2. **French strings.** All labels are French. The live announcement "5 options disponible(s)" is awkward wording from the library (the English-term detector flagged it as a false positive).
3. **Source drawer geometry and closure.** The approved I-09 pattern is anchored to the right at full height on desktop. The standalone spike used `Sheet position="right"`. Its parent laid it out with `align-items: center`, and `Sheet` had inline `position: relative`, so the received absolute-position inset rules did not stretch it. Reproduced at 1440 × 900 in Chromium: the drawer was 480 × 399 px at x=960, y=250.5. A temporary `align-self: stretch` override made it 480 × 900 px at x=960, y=0 and Escape returned focus to the trigger in that run. At 390 × 844 the drawer filled the viewport and stayed mounted after Escape both with and without the override. The original follow-up also observed stalled closure via the close button at full width. These findings apply to the prototype's `Sheet` wiring. Desktop geometry and full-width closure need verification in the first implementation that consumes the drawer.

## 4. CSS the spike had to add

The spike added layout CSS for the combobox list and popover and an inner scroll region for the drawer. The library supplies behavior without a theme. The first UI story that consumes each component owns the resulting integration through the overlay mechanism of D-53.

## 5. Not tested, and consequences

- Screen readers (NVDA, VoiceOver, TalkBack), Safari, Firefox, real touch, and the full date picker (field plus popover) in a real page. Only Chromium was used.
- **Reservations attached to the confirmation:** a screen-reader pass on the dialog, drawer, combobox and calendar before the first screen that uses them is released; the `aria-hidden-focus` finding resolved in the first UI story that uses the combobox.
- This spike claims no accessibility conformance and no certification.

## 6. Decision

Aymen confirmed React Aria Components for ADR-013 (UD-47, option "confirm"). The standalone drawer findings were reproduced after that decision and leave the primitive integration open. D-49 tracks the drawer checks, the combobox finding and the screen-reader pass. Base UI remains the documented fallback.
