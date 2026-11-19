# Entry background on S-25, S-02 and S-01

Date 2026-11-19. Status **HTML design review completed for the background overlay; Figma import pending.**

**User decision.** Apply variant B with the adopted dark tone to the three planned screens and validate the result. Apply to Figma in a single write when connector access permits.

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

## Native access

Figma MCP opened the existing S-25 section `30:44609`. The next library read returned “You've reached the Figma MCP tool call limit on the Starter plan.” No native write was attempted. When the quota returns, preflight all three source sections, create review variants in one write, preserve the imported roots and protected pages, and inspect the native result. The existing background-study plugin builds the study frames only. This design work does not authorize application development.
