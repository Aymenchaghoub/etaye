# Story 1.2 design review

On 2026-11-20, Aymen approved the story 1.2 gallery after requesting a colored error summary in the password reset validation state. He accepted the revised amber preview with “parfait”. [Story 1.2](../../planning-artifacts/epics/epic-1.md#story-12-sign-in-recover-and-expire-safely)

| Surface | Decision |
|---|---|
| S-02 sign-in | Approved with the CORE families under UD-46. |
| S-04 access recovery | Approved with the revised amber `nouveau-erreur` error summary. |
| S-38 expired session and return | Approved with a neutral background. Remove the protected content from the rendered page when the session expires. |
| I-22 session expiration | Approved as shown. |
| M-03 recovery email | Shown for journey context. Its delivery belongs to story 8.3. |

## Revised password reset error summary

Use the same attention colors as the rate-limit banner in S-04. Set the summary fill to `--attention-fond` and its 2 px border to `--attention`. Keep its existing text, link to the invalid field and alert semantics. The change applies to the password-reset validation summary. [Rate-limit reference](../../../design/offline-reference/site/ecrans/S-04/limite.html) · [Error summary and banner recipes](../design-system.md#62-received-in-the-html-reference-not-figma-components-not-approved)

The gallery used the [corrected HTML reference](../../../design/offline-reference/README.md), snapshot `d2f0800e275c4b156fedbcf23656ffe2bf435f9b6a72faf0f86fc293e2a82ad6`. The neutral S-38 background and the revised S-04 summary were rendered from local variants of that reference. The source snapshot remains reproducible, and Figma reconciliation remains pending.

Chromium rendered the revised S-04 state in both themes at 1440, 1024, 768, 390 and 320 CSS px. The summary and its link measured 15.51:1 and 7.74:1 in the light theme, and 12.02:1 and 6.21:1 in the dark theme. The viewport checks found no horizontal overflow. The captures and gallery are local review artifacts. Runtime and functional acceptance still require implementation evidence.
