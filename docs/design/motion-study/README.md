# Functional motion study — four state-carrying patterns

Status: **Proposal validated by Aymen on 2026-11-20 as written (UD-42, [ADR-012](../../decisions/ADR-012-functional-motion.md)); not a design approval of any screen.** Nothing here changes [DESIGN](../../planning-artifacts/ux/DESIGN.md), the [token reference](../token-reference.md), `tokens.css`, the [coverage tracker](../coverage.csv) or any Figma node. No application code exists for these patterns and none is authorized (UD-28).

Evidence labels: **User decision**, **Verified observation**, **Proposal**, **[ASSUMPTION]**, **To verify**.

## 1. Scope and decision

**User decision (2026-11-20):** after asking whether Étaye could take ideas from the Kitchen collection (dqnamo), Aymen asked to update the design system from it and to see the result before approving it. He then played the [interactive prototype](prototype.html) and answered "je valide". **[ASSUMPTION]** read as acceptance of the whole proposal as shown: the four patterns A to D, the token `--duree-trace` of 1200 ms, and animating the sign on S-01. He can still amend any point; the register entry UD-42 carries the same wording.

**Verified observation:** the prototype uses only existing Étaye values: the colors, spacing, radii, strokes, targets and motion tokens of `tokens.css` (received export of 2026-11-03), the three Atkinson Hyperlegible fonts and the original sign paths of `etaye-signe.svg`. Motion values are not read from Figma (the design system already says so). French copy below is proposed new copy; existing Figma copy stays untouched. All data is synthetic.

## 2. Rules

1. Motion exists only when it carries a state the user needs: pending, confirmed, failed, active drop target, more content available.
2. It reuses `--courbe` (`cubic-bezier(0.22, 1, 0.36, 1)`) and `--duree-retour` 140 ms, `--duree-flottant` 180 ms, `--duree-entree` 320 ms, except the sign trace.
3. Every motion has a static equivalent that carries the same information under `prefers-reduced-motion: reduce`.
4. The state is always written as well; motion never replaces text and never carries meaning alone.
5. A motion never runs longer than the work it reports, and nothing plays automatically for more than 5 seconds. The sign trace plays once.
6. Success is shown only after the server commit (shared behavior contract of the [interaction catalog](../interaction-catalog.md)).
7. Excluded: hold-to-confirm gestures, skeuomorphic scenes, decorative glow or gradient, and any motion on publish, withdraw, validate or discard.

## 3. The four patterns

### A. Save button states (SP-SAVE; I-04 to I-07, I-15, I-24)

| State | Behavior and proposed copy |
|---|---|
| Rest | "Enregistrer" (the existing label of each action keeps its own wording, for example "Enregistrer la mesure") |
| Pending | Label becomes "Enregistrement…" with a 14 px ring (static dotted ring under reduced motion). The button stays in place and ignores further activation; the width never changes (the longest label sets it). Announced politely: "Enregistrement en cours." |
| Confirmed | Only after the server acknowledgement: "Enregistré" with a check drawn in `--duree-flottant`; border `accent`. Returns to rest after 2.4 s **[ASSUMPTION]**. Announced: "Modifications enregistrées." Focus is not moved |
| Failed | Back to rest, plus a banner (`critique-fond`, 2 px `critique` border, glyph and words): "L’enregistrement a échoué. Vos modifications sont conservées." and "Réessayer". Entered data is kept |

Tokens: `--duree-retour` for color, `--duree-flottant` for the check, the existing 0.98 press scale, `--rayon-1`, `--cible-soignant` 32 px.

### B. File drop target (I-08, S-13)

| State | Behavior and proposed copy |
|---|---|
| Rest | `surface-2`, 1 px `filet-controle`. "Glissez un document ici", "PDF, PNG ou JPEG · 20 Mo au plus" (types and size are the proposal of I-08), button "Choisir un fichier" always present |
| Drag over | Border and a 1 px inner ring in `accent` (an inner shadow, so the page never shifts), `accent-fond` fill, icon 4 px higher, text "Relâchez pour ajouter le document". The target stays still: no magnetic pull |
| Refused | `critique` border; per file "Type non accepté" or "Trop volumineux"; the other files and the metadata are kept |
| Accepted | File listed as pending; it stays unavailable until inspection finishes (existing I-08 rule) |

Web only. On a phone there is no drag and drop: the picker and the camera apply. Tokens: `--trait-focus` 2 px effect, `--accent`, `--accent-fond`, `--duree-flottant`. Static equivalent: border and fill change, the icon does not move.

### C. Sign trace while the demo is prepared (S-01, I-26)

| State | Behavior and proposed copy |
|---|---|
| Fast (under 400 ms) | No loader. The content appears directly |
| Preparing | After 400 ms: the two parts of the original sign are traced once over `--duree-trace` (the bar first, the insertion mark 180 ms later), outline in `encre` and `accent`. "Préparation du scénario…". `aria-busy` is set. If the work outlasts the trace, the outline simply stays |
| Ready | The sign fills in over `--duree-entree`; "Scénario prêt." appears with a 6 px rise |
| Unavailable | Static outline in `encre-3`, no loop: "La démo est momentanément indisponible." and "Réessayer" |

There is never an artificial delay: the loader follows the real preparation. Static equivalent: the outlined sign without tracing, then filled. The sign's geometry and colors are those of the original files; only the stroke and fill are animated. New token **[Proposal]**: `--duree-trace` 1200 ms (see section 5).

### D. Overflow fade on bounded lists (source, document and history lists)

A 28 px fade in `surface` appears at the top or bottom edge only when content continues in that direction, with an opacity transition of `--duree-flottant`. Rows keep an inner focus ring and the container reserves `scroll-padding` so a focused row is always fully visible and never sits under a fade. Static equivalent: the fade without transition. Known limit: a fade can hide a focus ring; hence the inner ring.

## 4. Not adopted from the inspiration

Hold-to-confirm (a longer gesture does not explain the consequence and excludes the keyboard), the decorative experiments (cassette player, playing cards, ticket, stamp, receipt, foil) and the motion on consequential confirmations. Confirmations keep their explicit wording with the named target and audience.

## 5. Token proposal

| Token | Value | Use | Status |
|---|---|---|---|
| `--duree-trace` | 1200 ms | One-shot trace of the sign, S-01 only | **Proposal**, validated by Aymen. Not in `tokens.css`, `tokens.json` or Figma |

The received export is frozen and the token tables of the design system are generated and checked, so this token is not added by hand. Promotion path: create the Figma variable, re-export, regenerate (design-system section 13). Until then the study is its only source.

## 6. Evidence

**Verified observation (2026-11-20, Chromium 153.0.8010.12 through Playwright 1.63.0, viewport 1100 and 400 px, light and dark):** the page loads the three Atkinson faces; no horizontal overflow at 400 px; no console error. Behaviors exercised: the save button goes rest, pending, confirmed, rest and keeps its width; after a simulated failure the banner shows and the typed note is kept; the drop target takes the over state and refuses `script.exe` while keeping `bilan-exemple.pdf`; the sign trace starts after 400 ms and is skipped for a 150 ms preparation; the list fade follows the scroll position. The prototype is a design artifact opened from this folder; it uploads nothing.

**To verify:** Firefox and WebKit, real devices and touch, contrast of every state in both themes (measure rendered pairs as the design system requires), the 200 % zoom and 320 px reflow, a screen-reader pass of the announcements, the 400 ms and 2.4 s values with real use, forced-colors rendering.

## 7. Figma variants to draw (not drawn)

Drawn first as variants of the existing families, light and dark, at 1440×1024, 1024×768 and 390×844, then reviewed per the [coverage guide](../coverage-guide.md); the tracker rows inherit them through "Required states per catalog".

| Pattern | Family | States |
|---|---|---|
| A | I-05, I-06 as references, then I-04, I-07, I-15, I-24 | Rest, pending, confirmed, failed with banner and kept input |
| B | I-08 in S-13 | Rest, drag over, refused type, refused size, accepted pending, long filename, keyboard focus on the picker; mobile without drop target |
| C | S-01 | Preparing with trace in progress, preparing with trace complete, ready, unavailable, reduced-motion static |
| D | Bounded lists of S-13 and the source drawer | No fade, top fade, bottom fade, both, focused row at an edge |

## 8. Inspiration and licences

The idea came from the "Kitchen" collection of dqnamo (https://www.dqnamo.com/kitchen), consulted on 2026-11-20: Dynamic Button, Magnetic Drop Zone, Logo Trace Loader and Scroll Fade List. **Verified observation:** the page presents 15 experiments and states no licence and no way to consume them. A Codex session of the same day reported MIT for Dynamic Button and Scroll Fade List on their 21st.dev pages and an unclear licence for Magnetic Drop Zone (**To verify**, not checked by the author of this study). **No code, image or text was copied:** only interaction ideas, reimplemented here from Étaye's tokens. If code from those pages is ever reused, check its licence first and keep its notice in the repository (AGENTS.md).

## 9. Open points

- The 400 ms threshold, the 2.4 s confirmed dwell and the 28 px fade are **[ASSUMPTION]** values to confirm with real use.
- Native equivalents (ADR-010) are designed with the native screens; the technique is decided then.
- If Figma access remains limited, the offline exception of design-system section 14 applies to these states.
