# Review of the previously downloaded design-system bundle

Source: local etaye-design-system.zip, SHA256 9ffca590026ae65a3321a101d56c61f63e3e548e930fd363b29348db7a866333. Read-only archive inspection on 2026-11-02. This may precede the work currently running in Claude's chat; do not overwrite that work or assume this is its latest revision.

Observed archive: 82 files, 16 HTML files, 30 PNG captures, 10 SVG logos, 7 WOFF2 fonts. The chat reported 102 files; record the downloaded artifact hash/version rather than treating those counts as proof of coverage. Its README names only three mockups: Today, consultation preparation and mobile patient portal. This does not establish the required 76 surface-family coverage or Figma import/review.

## Concrete correction required

The README's voice/content section says an empty field may read “Non renseigné” or “Aucune allergie connue”. The latter is not an acceptable fallback for empty allergy data. Use explicit unknown/not-asked language; “Aucune allergie connue” needs a dated explicit negative declaration with provenance. Preserve a newer contradictory patient report and both sources. See BR-008, the synthetic dataset and the screen/interaction catalogs. Correct this rule in docs, tokens/examples/components and all affected mockups, not just one visible label.

## Continuation constraints

Complete the required catalogs and navigable prototypes; verify every actual frame/HTML counterpart, responsive state and A4/email layout. HTML mockups are design references, not implemented React/Spring application screens and not proof that Figma was modified. Keep the current collision correction in Claude's working bundle; this review does not diagnose an unseen current screenshot. Open new captures at the actual viewport after fixing overflow.

Before importing or copying font/logo assets into the repository, verify their source/version, exact geometry/token mapping and required license notices. Existing Figma remains the visual source until changes are explicitly reconciled. Update coverage only with real nodes, states and opened visual review evidence; no invented user approval.
