# Story 1.3 running UI review

2026-11-21 · Story 1.3 · AT-003-C · Application candidate `309891ee3607e0f9f9aa94699fb196412ce88c66`.

The left panels use the offline references approved under UD-54. The right panels show Chromium running the application against a disposable Spring Boot and PostgreSQL stack with synthetic identities and practice grants. I-20 uses the test-only form approved under UD-52. The [design review](2026-11-21-story-1-3-variants.md) records the Figma exception and approved reference.

| Evidence | Opened comparison | Observed details |
| --- | --- | --- |
| [S-05 chooser, portal and revoked access](2026-11-21-story-1-3-runtime/S-05.webp) | 1440 px desktop, 390 px mobile, 320 CSS px reflow | The chooser retains the existing account name and sign-out action. The application lists grants in server order. The revoked state uses the existing “Accès modifié” heading with the approved warning and remaining choices. |
| [S-05 error and verification](2026-11-21-story-1-3-runtime/S-05-recovery.webp) | 1024 px tablet, 390 px mobile | The error and retry fit within the chooser. The running failure state omits the normal explanatory paragraph. The mobile verification state uses the approved short waiting message. |
| [I-20 confirmation, failed save and focus](2026-11-21-story-1-3-runtime/I-20.webp) | 1440 px desktop, 390 px mobile | The error stays next to the source and destination text. Mobile buttons wrap with “Enregistrer et continuer” above the two exit actions at 390 px. The reference stacks all three. The fixture uses a minimal background. The close button gets its blue outline after Tab, with focus starting on the title. |

The local full-size capture set at `/tmp/etaye-13-final-evidence.KZI7Qp` contains 60 S-05 comparison pairs across six states, five dimensions (1440×1024, 1024×768, 768×1024, 390×844 and 320×844) and light and dark themes. The sample also includes one verification capture while a test held a genuine session response. I-20 has 16 comparison pairs across four widths and two themes, plus the focused mobile close button. The committed sheets preserve the opened comparisons. Browser assertions covered long French content reflow, keyboard operation, pending dismissal and failed-save recovery independently.

**Review pending.** Aymen approved the designs under UD-54. Review of the running application and the differences above remains a merge gate. The [implementation record](../../implementation-artifacts/story-1-3-implementation-record.md) gives the local verification command and runtime entry points.
