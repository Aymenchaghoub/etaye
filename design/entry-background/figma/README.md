# One-run Figma Desktop handoff · Étaye entry background

This development plugin creates a new review section in the existing Étaye file from the 20 desktop light-theme source states. It checks the captured root fingerprints and the protected Foundations and Components pages, clones the native content, adds a background-only image from the reviewed HTML and exports all native frames. The text, vectors and controls remain editable. HTML holds the responsive and dark-theme comparisons.

## Native run

1. In Figma Desktop for Windows or macOS, open the existing Étaye Design file using an account with edit access. Confirm the frozen source version `2423224260331333575` or the matching current state.
2. In **Plugins → Development → New plugin**, register a local Design plugin with a custom UI. Copy the bundled `code.js` and `ui.html` into its folder. Transfer the fields in `manifest.template.json` to its generated manifest, retaining the generated plugin ID.
3. Run the plugin, select the bundled `entry-background.json` and click **Créer les 20 variantes et exporter** once. The plugin stops before writing if the source fingerprint differs or the review section is already present.
4. Download `etaye-entry-background-native-evidence.json` from the plugin. It contains the created section and frame IDs, saved version ID and all 20 PNG exports. Supply it for comparison with the HTML reference before any native visual approval.

The manifest disables network access. The desktop flow uses the Figma plugin API and consumes no Figma MCP calls. The source file and required fonts must be accessible in Figma Desktop. The plugin never edits a baseline frame or protected page.

## Package from the repository

Use the same locked Python design environment and Chromium as the entry background review.

```sh
python design/entry-background/figma/build.py --output /tmp/etaye-entry-background-figma
node --test design/entry-background/figma/test-runtime.cjs
```

The build packs cropped, UI-free decorative layers and the verified native source identities. The local tests exercise preflight, creation, evidence collection and rollback. A native run and opened native exports are required to establish native fidelity.
