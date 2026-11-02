---
name: Étaye
description: Calm evidence-centered clinical workspace and accessible patient portal.
status: draft
design_status: ready-for-design
user_approval: not-approved
sources:
  - ../../design/figma/file-snapshot.json
  - ../../design/token-reference.md
colors:
  surface-fond: '#efeeea'
  surface-surface: '#ffffff'
  surface-surface-2: '#f7f6f3'
  surface-papier: '#f6f4ee'
  texte-encre: '#1b1b18'
  texte-encre-2: '#57534a'
  texte-encre-3: '#6b655a'
  texte-encre-inverse: '#f6f4ee'
  filet-filet: '#dedad0'
  filet-filet-controle: '#8c877c'
  accent-accent: '#2b4c7e'
  accent-accent-fond: '#e8edf5'
  accent-sur-accent: '#ffffff'
  clinique-critique: '#b42318'
  clinique-critique-fond: '#fcebe9'
  clinique-attention: '#a6470a'
  clinique-attention-fond: '#fdf1e3'
  surface-fond-dark: '#0f1012'
  surface-surface-dark: '#1a1c1f'
  surface-surface-2-dark: '#222529'
  surface-papier-dark: '#17181a'
  texte-encre-dark: '#ece9e1'
  texte-encre-2-dark: '#aba69b'
  texte-encre-3-dark: '#969189'
  texte-encre-inverse-dark: '#1b1b18'
  filet-filet-dark: '#2e3136'
  filet-filet-controle-dark: '#767b82'
  accent-accent-dark: '#8faadb'
  accent-accent-fond-dark: '#1d2738'
  accent-sur-accent-dark: '#0f1012'
  clinique-critique-dark: '#ff8f85'
  clinique-critique-fond-dark: '#3b1613'
  clinique-attention-dark: '#f5a524'
  clinique-attention-fond-dark: '#36260e'
typography:
  ui-titre:
    fontFamily: "Atkinson Hyperlegible Next"
    fontSize: 22.0
    fontWeight: 500
    letterSpacing: 0.0
    lineHeight: 28.0
  ui-section:
    fontFamily: "Atkinson Hyperlegible Next"
    fontSize: 16.0
    fontWeight: 500
    letterSpacing: 0.0
    lineHeight: 24.0
  ui-corps:
    fontFamily: "Atkinson Hyperlegible Next"
    fontSize: 15.0
    fontWeight: 400
    letterSpacing: 0.0
    lineHeight: 22.0
  ui-dense:
    fontFamily: "Atkinson Hyperlegible Next"
    fontSize: 14.0
    fontWeight: 400
    letterSpacing: 0.0
    lineHeight: 20.0
  ui-libelle:
    fontFamily: "Atkinson Hyperlegible Next"
    fontSize: 13.0
    fontWeight: 500
    letterSpacing: 0.13
    lineHeight: 20.0
  meta:
    fontFamily: "Atkinson Hyperlegible Next"
    fontSize: 12.0
    fontWeight: 400
    letterSpacing: 0.0
    lineHeight: 16.0
  donnee:
    fontFamily: "Atkinson Hyperlegible Mono"
    fontSize: 14.0
    fontWeight: 400
    letterSpacing: 0.0
    lineHeight: 20.0
  donnee-forte:
    fontFamily: "Atkinson Hyperlegible Mono"
    fontSize: 14.0
    fontWeight: 600
    letterSpacing: 0.0
    lineHeight: 20.0
  doc-titre:
    fontFamily: "Source Serif 4"
    fontSize: 26.0
    fontWeight: 500
    letterSpacing: 0.0
    lineHeight: 32.0
  doc-intertitre:
    fontFamily: "Source Serif 4"
    fontSize: 18.0
    fontWeight: 500
    letterSpacing: 0.0
    lineHeight: 28.0
  doc-corps:
    fontFamily: "Source Serif 4"
    fontSize: 16.0
    fontWeight: 400
    letterSpacing: 0.0
    lineHeight: 28.0
  patient-titre:
    fontFamily: "Atkinson Hyperlegible Next"
    fontSize: 28.0
    fontWeight: 500
    letterSpacing: 0.0
    lineHeight: 36.0
  patient-corps:
    fontFamily: "Atkinson Hyperlegible Next"
    fontSize: 18.0
    fontWeight: 400
    letterSpacing: 0.0
    lineHeight: 28.0
  patient-action:
    fontFamily: "Atkinson Hyperlegible Next"
    fontSize: 18.0
    fontWeight: 500
    letterSpacing: 0.0
    lineHeight: 24.0
rounded:
  none: 0px
  sm: 4px
  md: 8px
  lg: 12px
spacing:
  '1': 4px
  '2': 8px
  '3': 12px
  '4': 16px
  '5': 20px
  '6': 24px
  '8': 32px
  '10': 40px
  '12': 48px
  '16': 64px
components:
  source-marker:
    foreground: "{colors.accent-accent}"
    font: "{typography.donnee}"
  document:
    surface: "{colors.surface-papier}"
    body: "{typography.doc-corps}"
---

# Brand & Style

Étaye is calm, attentive and rigorous. Its material is the dated note: chronology, annotation and identifiable evidence. The signature is the original insertion/reference sign paired with a source number. Meaningful content carries detail; navigation and framing stay quiet. Existing Figma foundations and original logo are retained. This spine captures the user-supplied direction; it is not approval of new screens. Where a future mock conflicts, reconcile its behavior against EXPERIENCE.md and its existing token values against the verified source, recording any intentional change.

# Colors

The frontmatter maps existing French Figma role names to flat machine-readable keys; [token reference](../../design/token-reference.md) owns node evidence. Use `{colors.surface-fond}` for quiet framing, `{colors.surface-surface}` for work areas and `{colors.surface-papier}` for reading. Accent is restrained and meaningful. Clinical warning/critical colors cannot imply a computed risk category. Missing and contradictory data need explicit language. Contrast must be measured on actual rendered pairs, including dark mode, focus and disabled/read-only distinctions.

# Typography

Use Atkinson Hyperlegible Next for the workspace and patient portal, Mono for comparable measurements and source numbers, Source Serif 4 for reading/report hierarchy. Preserve exact role values in the frontmatter. Use French locale formatting, nonbreaking spacing around units where appropriate, tabular alignment for comparable figures and complete dates in source panels. Dense professional labels must remain legible; patient body/action roles are larger. Print sizes are specified separately rather than copying CSS pixels to A4 blindly.

# Layout & Spacing

Use the verified 4px-based scale. [ASSUMPTION] Professional desktop uses a persistent practice/identity header, compact navigation, chronology/content region and an optional source pane. Tablet rearranges the pane sequentially; mobile is a focused single-column task view, not a shrunken three-column desktop. The patient portal starts from a simple home, appointments, preparation and published information. At 1440×1024, 1024×768, 390×844 and 320 CSS px reflow, preserve identity, date, source and action hierarchy. Avoid repetitive cards when a chronology, list or table expresses relationships more clearly.

# Elevation & Depth

Prefer surfaces and meaningful dividers. Existing effect style `2:73` names floating menus/source previews; full effect values are not established by style metadata alone. Claude must inspect it before applying exact shadows. No decorative glows or gradients. Use overlays only for bounded work; source context remains readable without obscuring the originating statement.

# Shapes

Existing radii are 0/4/8/12px. Preserve small control/document corners and reserve larger rounding for surfaces where it serves grouping. Do not turn every item into a pill. Logos preserve original geometry and accent. [ASSUMPTION] Clear-space proposal in the token reference requires design review.

# Components

Existing: logos/signs/icons, button set 5:22, status set 5:46, marker 5:47. Required completion: navigation/practice selector; field/textarea/select/checkbox; date/time/calendar; paginated table and search filters; chronology event; assertion/provenance row; measurement/lab table and accessible trend; source drawer/viewer; task status; version comparison; review checklist; publication audience preview; upload/quarantine panel; toast/banner; dialog/drawer; empty/error/forbidden/session states; print/email blocks.

Each requires interactive, focus, disabled, read-only, loading, error, restricted and long-content variants as relevant. Distinct status components represent processing, review, freshness and publication. Existing sample status labels must be completed deliberately. Button meanings include saving notes, validating a version and publishing to a named patient; never collapse these to a vague “Confirm”. Use one consistent icon family [ASSUMPTION: Lucide candidate], accessible names and text for consequential actions. Exact future component dimensions are design work, not invented verified tokens.

Charts retain axis/unit/date context, missing periods and original measurements. No unsupported score gauge. Timelines group events by meaningful date without losing source type or correction lineage. Source reference numbers work in text and A4; keyboard focus and selected statement/source relationship must remain visible.

# Do's and Don'ts

Reuse the original Étaye logo and exact values. Show realistic shared fictional content, dated references and corrections. Keep French interface copy. Avoid generic AI imagery, emoji navigation, gratuitous animation, excessive AI badges, decorative illustrations on clinical screens and unsupported medical/product claims. A scientific-plate composition may enrich showcase/editorial materials only if clinical workflow usability remains intact. Print/email share type, sign and restrained color with purpose-built layouts.
