# ADR-013 — Web component behaviour layer: React Aria Components

Status: Accepted by Aymen on 2026-11-20 (UD-43), after he compared two interactive cases (calendar or not) and answered "Calendrier : oui". Date: 2026-11-20. Amends the "UI primitives" row of [ADR-002](ADR-002-stack-and-tooling.md). Related: UD-16, UD-27 (this is the mini-ADR it requires), UD-28, ADR-010, ADR-014, [design system](../design/design-system.md) section 6, [review record](../design/reviews/2026-11-20-design-system-review.md), D-42 to D-49.

[ADR-002](ADR-002-stack-and-tooling.md) proposed "Radix Primitives plus CSS tokens and components authored from Figma", and left the rest open. The design needs behaviours that are the usual source of accessibility defects when written by hand: dialogs and drawers with a focus trap and focus return (23 mentions of dialogs and 14 of drawers in the interaction catalog; 9 dialogs and 6 drawers among its 28 interactions), a patient lookup combobox (I-10, S-07), a source preview popover on every citation marker, and date handling for the agenda navigation (S-14) and the date of birth (S-08).

**Verified observations (2026-11-20, registry and repositories):**

- Radix (`radix-ui` 1.8.1, MIT, 55 sub-packages) has no combobox, no calendar or date field and no drawer. Its combobox request (issue 1342) was closed on 2025-01-25 and the last comment points to a shadcn recipe built on its popover.
- Base UI (`@base-ui/react` 1.9.0, MIT) has combobox, autocomplete and drawer, and no calendar or date picker.
- React Aria Components (`react-aria-components` 1.22.1, Apache-2.0, peer React up to 19) has combobox, calendar, date picker and date field, and a `Sheet` (drawer) added in 1.22.0 on 2026-10-08. `@internationalized/date` 3.12.4 depends only on `@swc/helpers`, with no React.

**Choice:**

1. **React Aria Components behaviour, Étaye style.** Dialog, sheet (drawer), popover, menu, combobox, calendar, date field and date picker come from React Aria Components with `@internationalized/date`. The Étaye tokens and the `.etaye-*` CSS are the only styling layer; no theme or default style of the library is used.
2. **Native HTML stays** for buttons, fields, checkboxes, radios and selects (the received CSS already styles them), tables, banners, chronology, source viewer and diff. Panel tabs and tooltips are not retained until a screen needs them; page navigation stays links with `aria-current`.
3. **A calendar is in the product** (Aymen's choice): a month calendar with closed days, French labels and full keyboard navigation for the agenda (S-14) and the date of birth (S-08). Its interaction specification is deferred (D-48); the patient app has no calendar in its first version.
4. **Base UI is the documented fallback**, with the calendar written by hand. **Radix is not retained.**
5. **A spike precedes the commitment**: at the first authorised UI story, check the `Sheet` (released six weeks before this decision), French labels and date formats, keyboard and screen-reader behaviour with the Étaye CSS, and bundle weight. A failure moves to the fallback (D-49).

**Alternatives:**

- Keep Radix as ADR-002 proposed: rejected; two critical gaps (combobox, calendar), no drawer, and its ecosystem default moved to Base UI (the shadcn changelog lists "Base UI as the Default", June 2026).
- Base UI as the primary choice: kept as the fallback; without a calendar the riskiest control would be written by hand. The two are close when no calendar is needed: an external review scored React Aria 86.5 and Base UI 72.5 on the project weights, and the project assistant's sensitivity check (equal accessibility evidence, no native date need, no calendar) left 78.5 against 76.0. The calendar decision separates them ([review record](../design/reviews/2026-11-20-design-system-review.md)).
- A styled kit (Mantine, MUI, Chakra) or shadcn: rejected; they bring a theme or a Tailwind class layer that contradicts "do not inherit a generic component theme".
- No library: rejected; all the accessibility risk of dialogs, drawers, combobox and calendar falls on the project, against a WCAG 2.2 AA target that has not been measured.

**Consequences:**

- Nothing is installed or written in code by this decision; development stays gated by UD-28. ADR-002 gains the row below; its React line is aligned with the verified `apps/web` version.
- The Apache-2.0 licence needs its notice when the package ships. `@internationalized/date` has no React dependency, so it could be shared with the Expo app under ADR-010 if a native date need appears (none in the first version).
- **To verify:** everything listed in the spike, plus the quality of the French strings, which is not established.
