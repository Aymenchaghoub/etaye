# Architecture views

[Explore the five interactive diagrams](https://aymenchaghoub.github.io/etaye/). From a local checkout, open [site/index.html](site/index.html) in a browser. Select **Live** in a diagram to animate its connections.

The diagrams combine the planned application with what is built on `main`. A node whose subtitle starts with **Built** or **Partly** has code at commit `e48f0f8dcdfb3bb6f79fa9156dd15120624e199b` (25 November 2026); every other node is planned. Each status cites its evidence: the Flyway migrations V1 to V10, the operations the [API contract guide](../contracts/README.md) lists as served, the ArchUnit test, the GitHub workflows and the local Compose package. The planned content keeps the baseline of commit `f05b30b527d910872da79bd62f55f68447921961` (React Native native app, ADR-010). The [architecture spine](../planning-artifacts/architecture/ARCHITECTURE-SPINE.md) has not changed since that baseline; the later ADR-009 amendment (version discovery) and ADR-013 (React Aria Components replaces Radix) change only details recorded in the access and modules views. The spine and the [ADRs](../decisions/README.md) remain authoritative, and the diagrams identify deferred capabilities.

## Updating the diagrams

The editable Archify 3.2.0 candidates are in [sources](sources/). When a story merges or the plan changes, refresh the affected candidates against the new revision:

1. Move the **Built** or **Partly** status and its cited evidence for each node the change touches. The schema allows three sources per node and about 3.6 px per tag character, so keep one planning reference and the strongest code evidence. A statement the code now contradicts leaves the tag and the cards.
2. Run Archify's `finalize` for each changed candidate with `--repo-root` and `--quality showcase`. A rerun that reaches the browser check needs its own `--out-dir`.
3. Open the captures from `visual-check`, then copy the passed standalone HTML into [site](site/).
4. When the system view changes, refresh [overview.svg](overview.svg) with the viewer's **Export → SVG · Auto**. The root README uses this export for its light/dark preview.

The [publication workflow](../../.github/workflows/architecture-pages.yml) uploads only `site/` to GitHub Pages. It publishes from `main` after changes to the site or workflow, and supports a manual rerun. The site includes Archify’s [license and attribution](site/licenses/).
