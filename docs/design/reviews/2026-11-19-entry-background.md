# Entry background on S-25, S-02 and S-01

Date 2026-11-19. Status **Native desktop review batch created and compared. Visual differences and Aymen’s review remain open.**

**User decision.** Apply variant B with the adopted dark tone to the three planned screens and validate the result. Create the 20 desktop light review states in one Figma Desktop batch.

| Screen | Scope | Reviewed comparison |
| --- | --- | --- |
| S-25, patient home, 7 states | Paper and blueprint only behind the “Votre espace” title. Appointment, publications, navigation, and access messages use their existing plain surfaces. | [S-25 contact sheet](2026-11-19-entry-background/S-25.jpg) |
| S-02, login, 9 states | Textured backdrop with calm areas around the heading, small print and footer. The sign-in card and fields remain opaque. | [S-02 contact sheet](2026-11-19-entry-background/S-02.jpg) |
| S-01, showcase, 4 states | Textured backdrop around the showcase. Reading content has a calm plate and the sample clinical excerpt uses the opaque surface token. | [S-01 contact sheet](2026-11-19-entry-background/S-01.jpg) |

The [preview builder](../../../design/entry-background/README.md) copies the exact offline baseline (`d2f0800e275c`) to an external directory, reuses the study CSS, grain tile and blueprint symbols, and changes only the 20 targeted HTML state files. The other 503 baseline files matched by SHA-256. The preview has its own state and theme index. The [native source evidence](2026-11-18-figma-native-closure.md) is Figma version `2423224260331333575`.

## Render evidence

Chromium Headless Shell 159.0.8154.6 rendered all 20 states in both themes at 1440×1024, 1024×768, 768×1024, 390×844 and 320×844. All 200 runs passed the horizontal reflow, exact theme, mask, decorative semantics and layer-scope checks. Forced-color and print tests verified that the decorative grain and blueprint are disabled. The three opened contact sheets compare the default desktop pages with their baseline and show tablet, mobile and one alternate mobile state per family.

Rendered pixel measurements at desktop, landscape tablet, mobile and 320 px found these minimum text contrast ratios:

| Target | Light | Dark |
| --- | ---: | ---: |
| S-01 secondary notice | 4.98:1 | 5.51:1 |
| S-02 secondary sign-in note | 4.91:1 | 5.47:1 |
| S-25 heading | 14.88:1 | 14.21:1 |

These checks cover the scoped HTML background change. Full accessibility, Figma composition, device/browser matrix, and the core-family design approval remain separate.

## Native Figma Desktop result

One creation run added review section `54:34` on the existing import page `15:2`. Figma Desktop version-history links identified source version `2423224260331333575` and new named version `2423528717189287238`. The local Design plugin used the verified release, the ID assigned by Figma, and the bundled offline manifest. The release ZIP and all five declared payload hashes matched. [Review section](https://www.figma.com/design/vfh4BSnbRAbaDE1q0CyssL/Etaye?node-id=54-34) · [Saved version](https://www.figma.com/design/vfh4BSnbRAbaDE1q0CyssL/Etaye?node-id=54-34&version-id=2423528717189287238)

The original plugin window stayed on “Vérification, création et export en cours…” after the named version appeared. A read-only recovery with the same development plugin identity exported the existing section in small messages. The recovery observed messages from the outer Figma editor, which the bundled UI’s immediate-parent check excludes. The registered files were restored to the verified bundle after download. The recovery created no nodes or versions.

The [scoped evidence index](2026-11-19-entry-background/native/evidence.json) maps all 20 unique Figma review frames and their locked decoration layers to the 20 original source IDs. The read-only check matched every scoped original fingerprint against the release, including the seven S-25, nine S-02 and four S-01 frames. It also matched the two protected page fingerprints for Fondations `2:74` and Composants `2:75`. It verified 441 editable text nodes across the review frames, with the original text content and fonts present. The check covers the 20 scoped originals and the two protected pages.

All 20 native PNGs decoded at 1440 × 1113 and have unique SHA-256 values. The 11,883,021-byte raw download, `etaye-entry-background-native-evidence.json`, remains in local evidence storage. Its SHA-256 is `2f8f5252678a2115013fe187ff857f4c1d278b2b12b2eabff12416093abf005e`. The evidence index carries identifiers, dimensions and hashes. The PNG payloads stay in the local archive.

## Opened native comparisons

Opened all 20 matched product-area pairs and nine detail crops at 1:1. Frames were cropped to the 1440 × 1024 native product shell, excluding the extra 89 px of mockup chrome. The HTML copy was rendered at a 1440 × 1024 viewport and device scale factor 1 in Chromium Headless Shell 159.0.8154.6. Eighteen product areas share the same height. The two longer HTML products below were compared through their full footers in the detailed review.

| Screen | Observations and remaining differences | Opened detail evidence |
| --- | --- | --- |
| S-25 · 7 states | Grain and blueprint remain inside the 680 × 84 title band around “Votre espace”. Navigation, appointment, publication, and access surfaces remain plain. The unavailable alert body spans three native lines and two HTML lines. `remplacee` and `retiree` keep a 1024 px native product height. Their HTML shells extend to 1060 px and 1032 px, placing the HTML footers 36 px and 8 px lower, respectively. | [Title band and unavailable alert](2026-11-19-entry-background/native/S-25-details.jpg) |
| S-02 · 9 states | Grain remains around the opaque cards, fields and error summaries. Both professional and patient email focus rings are visible. Empty-field and invalid-credentials states retain their summary bullets and outlined fields. Minor differences appear in text antialiasing and in the error summary’s top stroke. The comparison covers static visual states. | [Focus and errors](2026-11-19-entry-background/native/S-02-details.jpg) |
| S-01 · 4 states | The outer paper and calm reading area surround the opaque sample clinical panel. The synthetic quotation wraps before “jeunesse.” in Figma. HTML keeps that word on the first line. The inline citation spacing and highlight extent also differ. | [Opaque sample panel](2026-11-19-entry-background/native/S-01-details.jpg) |

The HTML overlay's 200 configurations and the four offline plugin fixtures are recorded independently of native visual inspection. The native artifacts cover desktop light at 1440 px. Responsive and dark evidence is in the HTML review above. Next action is Aymen’s review of the saved Figma section and the recorded differences. All 45 stories remain in backlog and development authorization is pending.
