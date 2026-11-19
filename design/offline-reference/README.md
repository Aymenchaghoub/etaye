# Étaye mockups for offline review

From a local clone, open [the mockup index](site/index.html) in a browser. It lists all 456 states across 76 families. [Guided journeys](site/parcours.html) demonstrates the main flows with fictional data. The bundle includes the light and dark styles, original fonts and logos, and nine PDF samples.

## Which version to use

| Reference | Use |
| --- | --- |
| [Corrected HTML snapshot](snapshot.json), `d2f0800e275c…` | Runnable visual baseline for all families. The complete 523-file site is preserved byte for byte from the [17 November 2026 handoff release](https://github.com/Aymenchaghoub/etaye/releases/tag/figma-windows-20261117), the source used for the desktop import and closure comparison. |
| [18 November 2026 native closure](../../docs/design/reviews/2026-11-18-figma-native-closure.md), saved Figma version `2423224260331333575` | Latest native version evidenced in the repository. Use its 52 native exports and matching HTML comparisons for the corrected desktop states. The report and [per-state evidence](../../docs/design/reviews/2026-11-18-native-closure-final.json) identify the relevant images. |
| [Design system](../../docs/design/design-system.md) and [coverage tracker](../../docs/design/coverage.csv) | Exact token values, component recipes, breakpoint behavior and review status. |
| [Design alignments](../../docs/planning-artifacts/roadmap.md#12-follow-ups-outside-this-change-by-owner) | Check the roadmap's design alignment section and the [business rules](../../docs/planning-artifacts/business-rules.md) before relying on copy or interactions from the snapshot. |
| [Background study](../../docs/design/background-study/README.md) | Variant B and the dark paper tone are recorded for the study. Application to product screens still needs design evidence. |

The offline workflow uses the corrected HTML and the recorded native corrections. If a later Figma revision exists, import its exports and review the affected states before treating those changes as part of the reference. Tablet and mobile review, alignment with later product decisions, and approval of the core families remain open. Application development requires Aymen's explicit authorization.

The [entry background preview](../entry-background/README.md) applies the paper and blueprint direction to S-01, S-02 and S-25 for review.

## Provenance and updates

The documentary validator verifies the [snapshot manifest](snapshot.json) against every file under `site/`. The [rebuild instructions](../corrections/README.md) regenerate the corrected references with `--corrected --pdf`. A fresh rebuild on 19 November 2026 matched all 456 state HTML files, CSS, fonts and logos. The nine regenerated PDFs, 22 PDF preview images and the generated index had different bytes. Use the committed bundle for exact comparison.

Keep later revisions traceable to their source and review evidence. Preserve the [font and icon licenses](../received/2026-11-03/licenses/) and the [original logo exports](../../docs/design/figma/manifest.json).
