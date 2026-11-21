# Story 1.3 running UI review

2026-11-21 · Story 1.3 · AT-003-C · Application candidate `ffc88f44d0c5fef4e8ee628c4ab534cd1978843c`.

The left panels use the offline references approved under UD-54. The right panels show Chromium running the application against a disposable Spring Boot and PostgreSQL stack with synthetic identities and practice grants. I-20 uses the test-only form approved under UD-52. The [design review](2026-11-21-story-1-3-variants.md) records the Figma exception and approved reference.

| Evidence | Opened comparison | Observed details |
| --- | --- | --- |
| [S-05 chooser, portal and revoked access](2026-11-21-story-1-3-runtime/S-05.webp) | 1440 px desktop, 390 px mobile, 320 CSS px reflow | The chooser retains the existing account name and sign-out action. The application lists grants in server order. The revoked state uses the existing “Accès modifié” heading with the approved warning and remaining choices. |
| [S-05 error](2026-11-21-story-1-3-runtime/S-05-recovery.webp) and [verification](2026-11-21-story-1-3-runtime/S-05-verification.webp) | 1024 px tablet, 390 px mobile | The chooser keeps its explanatory paragraph while verifying access and after a failure. The warning, retry and live status fit within the respective viewports. |
| [S-05 long French label and keyboard focus](2026-11-21-story-1-3-runtime/S-05-long.webp) | 320 CSS px in dark mode | The full practice label and focused action fit inside the viewport. The application displays the short role from the contract. The design stress test also uses a long descriptive role sentence. |
| [I-20 confirmation, failed save and focus](2026-11-21-story-1-3-runtime/I-20.webp) | 1440 px desktop, 390 px mobile | The running error stays immediately below the destination. The design reference distributes its text vertically. The actions stack at 390 px and the fixture uses a minimal background. The close button gets its blue outline after Tab, with focus starting on the title. |

The local full-size capture set at `/tmp/etaye-13-reviewed-evidence.2fL1pX` contains 70 S-05 comparison pairs across seven states including long labels, five dimensions (1440×1024, 1024×768, 768×1024, 390×844 and 320×844) and light and dark themes. It also contains a verification pair taken while the browser test held a genuine session response. I-20 has 16 comparison pairs across four widths and two themes, plus the focused mobile close button. The committed sheets preserve the opened comparisons. Browser assertions covered long French content reflow, keyboard operation, pending dismissal and failed-save recovery independently.

**User decision (UD-55).** Aymen approved the running application comparison and the I-20 focus behavior for PR #50 on 2026-11-21. The [decision register](../../planning-artifacts/decisions-assumptions.md#user-decisions-recorded-on-2026-11-21) records the approval. The [implementation record](../../implementation-artifacts/story-1-3-implementation-record.md) gives the local verification command and runtime entry points.
