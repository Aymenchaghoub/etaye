---
title: "Étaye — Experience specification"
status: draft
design_status: ready-for-design
user_approval: not-approved
updated: 2026-11-02
---

# Foundation

Responsive web application; physician primary, patient portal complementary. The UI system is the existing Étaye Figma foundation composed into accessible React primitives later. [DESIGN.md](DESIGN.md) owns visual identity; [business rules](../business-rules.md) own domain transitions. No application or complete prototype exists in this planning package.

# Information Architecture

Practitioner: Today → Patients → selected record (chronology/history/measurements/labs/documents) → preparation/brief → encounter → report/review/history → publication. Schedule remains a distinct domain. Patient: Home → appointments/preparation → published reports/explanations → authorized sources/downloads. Administration: Team → permissions/invitations → operational settings → allowed audit. Profile belongs to the authenticated account. Practice context and patient identity stay visible during work. An administrator without clinical grant never sees a clinical shell populated with hidden fields.

Every surface has a numbered [catalog entry](../../design/screen-catalog.md). Overlay/inline actions have distinct [interaction entries](../../design/interaction-catalog.md). PDFs/emails are first-class surfaces, not screenshots. Coverage is not inferred from happy-path frames.

# Voice and Tone

French product copy is preserved. Use concrete labels: “Enregistrer les notes”, “Valider cette version”, “Publier pour Claire Martin”, “Source non disponible”, “Information non renseignée”. Explain the audience and effect before publication/withdrawal. Unknown is not “Aucun”. AI text is “Brouillon à relire”, never certified or certain. State dates, units and authors instead of invented confidence percentages. Existing clinical Figma copy is illustrative; use the shared synthetic dataset for new journeys.

# Component Patterns

Use `{colors.accent-accent}` reference markers to open the exact dated source, initially focusing its heading/locator; return focus to the originating marker. Long reading or editing uses a page/full-width region; small bounded decisions use one dialog; contextual evidence uses a drawer. Do not nest dialogs. Primary/destructive actions identify their effect and target. The exact I-ID specifies fields, guards, keyboard, recovery and mobile transformation.

Filters are URL state only where non-sensitive; do not put patient text in public URLs. Results expose pagination and active filter reset. A file preview has the same permission as download. Patient-visible citations resolve to approved excerpts/attachments only, never a raw internal source panel. A reviewer can compare generated baseline, human changes and exact current version without silently applying a new generation.

# State Patterns

Cold load, incremental load, empty, filtered no-results, partial error, failed, unavailable, read-only and restricted states are distinct. Show a failure within the affected section while retaining independently loaded data. Dirty/saving/saved/failed states reflect confirmed persistence. AI task state, document review state, freshness and publication use separate labels. An old published version can remain available with a dated stale notice until explicit replacement/withdrawal; a replaced/withdrawn publication exposes no old body under the initial patient policy.

# Interaction Primitives

Dialogs: initial focus at heading or first appropriate field; trap focus; Escape closes safe non-submitting work; restore focus to trigger. Destructive confirmation does not focus the destructive action by default. Pending mutations disable duplicate submission but preserve operation identity and can be recovered by checking outcome after an ambiguous network failure. Closing dirty work invokes one integrated discard/stay guard, never a second stacked modal. I-20/I-21/I-22 specify unsaved/version/session recovery. In-memory recovery is scoped to account/practice and cleared on logout/reset; clinical text is not persisted in shared browser storage.

# Accessibility Floor

Proposed WCAG 2.2 AA target, not measured conformance. Keyboard-complete journeys; focus visible and unobscured; semantic heading/landmark/form relationships; errors linked to fields and summary; text alternatives; no color-only meaning; status announcements that do not steal focus. Charts have tables. Touch target design goal 44×44px. Respect reduced motion; no necessary interaction relies on hover or animation. Actual contrast uses `{colors.texte-encre}` and appropriate surfaces rather than assuming every palette combination works. Test 200% zoom and 320px reflow.

# Key Flows

- **J-02/J-04/J-05 — Dr Nora Lefebvre:** opens Claire Martin's upcoming visit, sees a conflicting patient-reported allergy history and missing treatment information, inspects dated sources, conducts the encounter and corrects an unsupported AI sentence. The climax is validating an exact version while it is still internal, then deliberately selecting the separate patient explanation and publishing it. A patient tab opened before publication must not contain that draft.
- **J-06 — Claire Martin:** prepares her visit on a phone, books or changes an eligible slot, later reads the explicitly published explanation and opens an approved source excerpt. A failed request preserves safe input; an internal report link remains inaccessible.
- **J-07 — Mila Dubois:** invites a colleague and reviews membership history. A clinical deep link is denied. Attempting to deactivate the last administrator stops with an actionable explanation.
- **J-01/J-08 — Dr Sami Rahmani:** switches between two legitimate memberships while record caches clear. A record ID from the previous practice cannot leak into the new context. A concurrent booking conflict and AI timeout have navigable recovery branches.

# Responsive & Platform

Desktop review: 1440×1024. Tablet: 1024×768. Mobile: 390×844; minimum reflow 320 CSS px. Review keyboard, long French text and partial failures at each appropriate size. Calendar becomes agenda/day selection on mobile; editing becomes a page rather than a tiny drawer. Evidence context remains reachable and focus order follows visual order. A4 PDF previews are separately designed and printed in grayscale; emails require narrow reading widths and plain-text alternatives.

# Internationalization

Store locale-independent dates/instants/decimal strings and unit codes; format labels with message keys and locale-aware rendering. Clinical narrative/source language is metadata, not silently translated evidence. Keep French product design in this phase. An English-facing demo is A-09/D-10, requiring a product decision, targeted copy/content review and no automatic duplication of all frames.
