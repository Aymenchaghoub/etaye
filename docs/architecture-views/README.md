# Planned architecture views

[Explore the five interactive diagrams](https://aymenchaghoub.github.io/etaye/). From a local checkout, open [site/index.html](site/index.html) in a browser. Select **Live** in a diagram to animate its connections.

The diagrams reflect the planning baseline at commit `eb58c81b3be3b91819dd2ca31a4f2412b509b5e3`. They show the proposed application and identify deferred capabilities. The [architecture spine](../planning-artifacts/architecture/ARCHITECTURE-SPINE.md) and [ADRs](../decisions/README.md) remain authoritative.

## Updating the diagrams

The editable Archify 3.2.0 candidates are in [sources](sources/). They retain the original delivery paths and evidence references. When the plan changes, refresh the affected candidates against the new source revision, run Archify's complete finalize checks, and copy the passed standalone HTML into [site](site/). When the system view changes, refresh [overview.svg](overview.svg) using **Export → SVG · Auto**. The root README uses this export for its light/dark preview.

The [publication workflow](../../.github/workflows/architecture-pages.yml) uploads only `site/` to GitHub Pages. It publishes from `main` after changes to the site or workflow, and supports a manual rerun. The site includes Archify’s [license and attribution](site/licenses/).
