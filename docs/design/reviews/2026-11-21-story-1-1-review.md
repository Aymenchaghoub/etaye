# Story 1.1 design review

On 2026-11-21, after the phase-0 arbitration (UD-50), Aymen was given the direct paths of the offline reference states and answered "approuvé, tu peux y aller". [Story 1.1](../../planning-artifacts/epics/epic-1.md#story-11-activate-an-intended-invitation)

| Surface | Decision |
|---|---|
| S-03 invitation and account activation | Approved as shown: the 6 delivered reference states (`equipe`, `expiree`, `deja-utilisee`, `revoquee`, `invalide`, `erreur`). |
| I-03 activate patient portal | Approved as shown: the 4 delivered reference states (`ouvert`, `deja-active`, `expiree`, `erreur`). |
| I-01 invite team member | Not reviewed: its issuance dialog is deferred to increment 2 (D-23). |

## What was judged and what was not

Aymen opened the [corrected HTML reference](../../../design/offline-reference/README.md) states himself in his browser (entry point `site/index.html`; dark theme available with `?theme=sombre`) and approved the two families as shown. His exact browser width and theme choice were not recorded, and this review does not claim them: keyboard order, 200 % zoom, 320 px reflow, forced-colors, the dark theme and the functional states were not separately judged here. The SAMPLED evidence of AT-001-C — functional assertions and automated axe-core checks for every state, plus opened desktop and mobile captures at two widths for the risk-sampled subset — is delivered by the story implementation and tracked separately from this design approval.

The snapshot identity stays `d2f0800e275c4b156fedbcf23656ffe2bf435f9b6a72faf0f86fc293e2a82ad6`; the 2026-11-18 named exception stands and Figma reconciliation remains pending. This approval covers the delivered reference states as the design contract of the story; it authorizes the story only through UD-50 and approves no other family.
