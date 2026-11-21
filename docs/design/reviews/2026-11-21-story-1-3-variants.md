# Story 1.3 design review — S-05 and I-20 additions

**User decision (UD-54).** Aymen approved the 17 variants after receiving the interactive page and both contact sheets (“oui je valide”). UD-46 covers the original S-05 reference. UD-51 covers the shown I-20 specimen and its focus correction. UD-52 sets the scope of these additions and UD-53 authorizes the story. The recorded approval covers this offline design preview. Functional and running-UI verification remain delivery gates. [Decision register](../../planning-artifacts/decisions-assumptions.md) · [Context packet](../../implementation-artifacts/story-1-3-context-packet.md)

Open the [local review page](2026-11-21-story-1-3-variants/index.html) and choose a state in either theme. The page uses the frozen HTML snapshot's exact fonts, logos and CSS, with the verified ADR-014 scrim value. Its controls navigate among synthetic examples. Actual persistence, request outcomes and authorization will be exercised during implementation. [Offline reference](../../../design/offline-reference/README.md) · [Design system](../design-system.md)

| Family | What to review |
|---|---|
| **S-05 · CORE** | Mixed practitioner, administrator and patient grants. One eligible context opens directly after verification. An empty list, loading and temporary failure each have distinct copy. A revoked target is unnamed in recovery, and the selector shows currently eligible options only. The destination header shows the active practice and role and keeps the switch control accessible at phone width. Open the long French variant to check that labels wrap and the action remains reachable. |
| **I-20 · SAMPLED** | The dialog names the selected destination. Stay and Close safely return to the current draft. Discard is separate and explicit. Pending save blocks duplicate actions and Escape. A failed or invalid save leaves the draft available for correction. A confirmed save followed by a denied destination reports both outcomes. Loss of the source grant displays neutral recovery with the protected editor removed. Opening focuses the title and Tab shows focus on ×. |

## Preview identity and test boundary

The design builds on the main baseline `d58dde93e4016769fda5ce854711da4f12020d73` and frozen HTML snapshot `d2f0800e275c4b156fedbcf23656ffe2bf435f9b6a72faf0f86fc293e2a82ad6`. The live Figma connector remained unavailable during phase 0. UD-46 and UD-51 govern the existing examples. UD-53 reserves the three-lens review of the eventual completed implementation for Zcode. [Delivery rules](../../operations/delivery-rules.md)

**Verified in Chromium 153.0.8010.12.** The final batch opened 170 state/viewport/theme combinations at 1440×1024, 1024×768, 768×1024, 390×844 and 320×844. It checked images, horizontal reflow, control bounds and the opening focus outline. The 34 full axe-core 4.14.1 scans at 390×844 reported zero violations. In both themes at all five widths, pressing Escape closes a safe dialog and restores focus to the switch control. During save, Escape keeps the dialog open. Tab and Shift+Tab stay within the modal, and staying preserves the demonstration input. Keyboard focus on × retains its outline.

A diagnosis on mobile showed that the received overlay kept the closed dialog visible with `display: grid !important`. The preview now hides the native dialog in its closed state. Pressing Escape in the pending state uses explicit keyboard handling and native dismissal protection. The switch control's accessible name begins with its visible practice and role. The title retains programmatic focus with its ordinary appearance.

The final run produced 100 full-size screenshots under `/tmp/etaye-1-3-variants.1RHAuy`. The two opened contact sheets preserve 12 representative views. [S-05 contact sheet](2026-11-21-story-1-3-variants/S-05.webp) · [I-20 contact sheet](2026-11-21-story-1-3-variants/I-20.webp)

The evidence is for design fixtures in Chromium. Screen reader announcements, 200% zoom, application persistence and current server grants remain part of subsequent evidence.
