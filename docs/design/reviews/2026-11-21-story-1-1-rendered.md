# Story 1.1 running UI comparison

On 2026-11-21 the isolated Spring, React and PostgreSQL stack rendered the six approved S-03 states and the four approved I-03 states. Chromium captured both the application and its [approved HTML reference](../../../design/offline-reference/README.md) at 1440×1024, 1024×768, 768×1024, 390×844 and 320×844 in both themes. The test checked horizontal reflow at every width. The reference snapshot is `d2f0800e275c4b156fedbcf23656ffe2bf435f9b6a72faf0f86fc293e2a82ad6`. The implementation candidate is `5ec9e4a483785301f9742c2c4026e35e9e7d43a9`.

| State | Desktop or tablet | Mobile |
|---|---|---|
| S-03 team invitation | [1440 light](2026-11-21-story-1-1-rendered/S-03-equipe-1440-clair.webp) | [390 light](2026-11-21-story-1-1-rendered/S-03-equipe-390-clair.webp) |
| S-03 password confirmation | [1440 light](2026-11-21-story-1-1-rendered/S-03-erreur-1440-clair.webp) | [390 dark](2026-11-21-story-1-1-rendered/S-03-erreur-390-sombre.webp) |
| S-03 revoked | [1440 light](2026-11-21-story-1-1-rendered/S-03-revoquee-1440-clair.webp) | [390 dark](2026-11-21-story-1-1-rendered/S-03-revoquee-390-sombre.webp) |
| I-03 patient invitation | [1440 light](2026-11-21-story-1-1-rendered/I-03-ouvert-1440-clair.webp) | [390 dark](2026-11-21-story-1-1-rendered/I-03-ouvert-390-sombre.webp) |
| I-03 verification refusal | [1440 dark](2026-11-21-story-1-1-rendered/I-03-erreur-1440-sombre.webp) | [390 light](2026-11-21-story-1-1-rendered/I-03-erreur-390-clair.webp) |
| I-03 expired | [1024 dark](2026-11-21-story-1-1-rendered/I-03-expiree-1024-sombre.webp) | |

The pairs show the HTML reference on the left and the running application on the right. Desktop and tablet images retain the main column at native scale. The first comparison led to matching the invitation detail grid spacing, date typography and placement of the confirmation summary. The running screen uses live dates, a masked recipient address, empty sensitive fields and heading focus when a new screen opens. Dynamic copy and the reference-only sample labels account for different line wrapping and footer positions. Patient contact explains how to reach the practice through its existing coordinates or invitation message.

The automated axe and keyboard checks are reported separately in the [implementation record](../../implementation-artifacts/story-1-1-implementation-record.md). Manual screen-reader, forced-colors and browser-zoom review remain open. The Figma connection returned `UNAUTHORIZED / TRIGGER_REAUTHENTICATION` for this session. The approved HTML exception covers the comparison and native reconciliation is pending. [Design approval](2026-11-21-story-1-1-review.md)
