# Existing Figma inventory

Status: Reviewed source inventory, **not design approval**. Consulted 2026-11-02 through read-only REST. File [vfh4BSnbRAbaDE1q0CyssL](https://www.figma.com/design/vfh4BSnbRAbaDE1q0CyssL/Etaye?node-id=0-1), version `2417265104370398313`, last modified `2026-11-02T13:47:25Z`. No file mutations occurred.

| Node | Actual name / type | Assessment and use |
|---|---|---|
| 0:1 | Couverture / page | Existing cover; preserve French text and identity. |
| 2:74 | Fondations / page | Existing light/dark palette, typography, spacing, radii and logos. |
| 2:75 | Composants / page | Existing basic buttons, status variants, source marker and annotation example; no complete application journey. |
| 3:2 | Couleurs · Clair / frame | 17 actual light swatch fills verified against labels. |
| 3:87 | Couleurs · Sombre / frame | 17 dark fills verified. |
| 3:172 | Typographie / frame | 14 named text samples with actual applied style properties exported. |
| 3:216 | Espacements et rayons / frame | Additional foundation absent from initial prompt list; 4px-based spacing and 0/4/8/12 radii observed. |
| 4:13 | Logo/Clair / component | Original SVG exported. |
| 4:19 | Logo/Monochrome / component | Original SVG exported; original description mentions prescriptions, which are outside product scope. Do not infer a prescription feature. |
| 4:20 | Logo/Espace patient / component | Original SVG exported, including patient-space label. |
| 4:33 | Logo/Sombre / component | Original SVG exported. |
| 4:37, 4:41 | Signe/Clair, Signe/Monochrome | Original standalone signs exported. |
| 4:42, 4:47 | Icône app/Clair, Icône app/Sombre | Original app icons exported. |
| 5:22 | Bouton / component set | Principal, Valider, Secondaire, Discret, Désactivé. Interaction states and responsive variants still required. |
| 5:46 | Statut / component set | Critique, Attention, Validé, Neutre. All visible sample labels currently say “À valider”; complete correct lifecycle labels in design phase. No automatic medical classification follows from variant names. |
| 5:47 | Repère / component | Reference/annotation signature: insertion sign plus number. Must link a statement to a dated supporting source. |
| 6:2 | Exemple · Annotation / frame | Useful source/validation composition reference; illustrative clinical content is not a clinical rule or the shared dataset. |

## Evidence and visual inspection

[Manifest](figma/manifest.json) identifies every exported node/file/hash. [Screenshots](figma/screenshots/) were actually opened on 2026-11-02: light colors, dark colors, typography, logos, buttons, statuses and annotated example. Color/typographic hierarchy and original logo geometry are preserved. Transparent isolated component screenshots require a suitable backing surface to evaluate contrast; black transparency in a viewer is not an actual component fill.

All eight exported SVG variants were rasterized into a contact sheet that was opened to verify their appearance. These exports are source assets, not approximations.

## Access and revision limitations

MCP authentication succeeded but its read quota was exhausted on the Starter/View seat. Aymen supplied a local token; file, comments, versions and SVG/PNG export reads succeeded. The comments endpoint returned an empty array. The versions endpoint returned one current version with pagination metadata; complete historical revision coverage is not claimed. Full variable-collection metadata is unavailable through the Starter REST capability; observed alias IDs and resolved values are retained in [resolved-tokens.json](figma/resolved-tokens.json). Claude can inspect collections in the Figma editor or use a future authorized connector with available quota. No paid upgrade is required merely to use these node-property exports.

## Coverage rule

Existing foundations/components are source evidence only. The [coverage tracker](coverage.csv) owns design progress for all required S/I/P/M identifiers. A new screen requires a real node link, states, responsive frames, opened visual exports and recorded review. Never mark a whole journey complete merely because the foundation page exists.
