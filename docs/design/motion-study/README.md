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
| Rest | "Enregistrer" (the existing label of each action keeps its own wording, for example "Enregistrer la mesure"). "Annuler les modifications" restores the initial note |
| Pending | "Enregistrement…" with a 14 px ring. Save and cancel remain in place with `aria-disabled="true"` and ignore activation. The save width is set by the longest label. A polite status says "Enregistrement en cours." |
| Confirmed | The acknowledgement must match the revision sent and the current note. The prototype checks a revision counter and returns to rest silently for a superseded response. A matching success shows "Enregistré" with a check drawn in `--duree-flottant` and an `accent` border. It returns to rest after 2.4 s **[ASSUMPTION]**. The polite status says "Modifications enregistrées." Editing the note clears confirmation |
| Failed | Return to rest and preserve the note. The banner uses `critique-fond`, a 2 px `critique` border, a glyph, "L’enregistrement a échoué. Vos modifications sont conservées." and "Réessayer". When the focused retry button disappears, focus returns to "Enregistrer" |

Static equivalent under reduced motion uses the same labels and failure banner, a fixed dotted ring during pending and a fixed check after confirmation. Color feedback uses `--duree-retour`. The existing 0.98 press scale, `--rayon-1` and 32 px `--cible-soignant` also apply.

### B. File drop target (I-08, S-13)

| State | Behavior and proposed copy |
|---|---|
| Rest | `surface-2`, 1 px `filet-controle`. "Glissez un document ici", "PDF, PNG ou JPEG · 20 Mo au plus" (types and size are the proposal of I-08), button "Choisir un fichier" always present |
| Drag over | An `accent` border and 1 px inner ring, `accent-fond` fill and a 4 px icon lift. "Relâchez pour ajouter le document" shares the space reserved for the rest instruction. The target keeps its height and "Choisir un fichier" stays visible and keyboard accessible |
| Refused | `critique` border; per file "Type non accepté" or "Trop volumineux"; the other files and the metadata are kept |
| Accepted | The proposed label is "En attente d’inspection". The file stays unavailable until inspection finishes (existing I-08 rule) |

Drag feedback applies on web. Phones use the picker and the camera. The border and inner ring give a combined 2 px effect, on the same scale as `--trait-focus`, with `--accent`, `--accent-fond` and `--duree-flottant`. Under reduced motion, the border, inner ring, fill and instruction change immediately. The icon stays fixed.

### C. Sign trace while the demo is prepared (S-01, I-26)

| State | Behavior and proposed copy |
|---|---|
| Fast (under 400 ms) | The result appears as soon as it is ready. The trace is skipped |
| Preparing | "Préparation du scénario…" appears when preparation starts. After 400 ms, the original sign is traced once with `--duree-trace`, starting with the bar and then the insertion mark after `--duree-flottant`. Its outline uses `encre` and `accent`. If preparation outlasts the trace, the outline stays fixed |
| Ready | "Scénario prêt." appears immediately on completion, including when the trace is still running. The trace ends and the sign fills in over `--duree-entree` |
| Unavailable | Fixed outline in `encre-3`, with "La démo est momentanément indisponible." and "Réessayer" |

The text and retry action live in a stable `role="status"` region outside the sign's `aria-busy` container. Under reduced motion, the sign uses a fixed outline during loading and a filled sign immediately on completion. The original geometry and colors are preserved. The proposed `--duree-trace` stays at 1200 ms (see section 5).

### D. Overflow fade on bounded lists (source, document and history lists)

| Scroll position | Visible band and proposed copy |
|---|---|
| Start | Bottom band with "D’autres documents en dessous" when more rows follow |
| Middle | Top band with "D’autres documents au-dessus" and bottom band with "D’autres documents en dessous" |
| End | The bottom band disappears. The top band remains while earlier rows are available |
| All rows fit | Both bands are hidden |

The bands occupy reserved space above and below the scroll region on `--surface`. The indicators fade with `--duree-flottant` and `--courbe`, and their position keeps them clear of the rows and inner focus rings. Their text uses `--encre-2` and is available to assistive technology when visible. The named scroll region and its rows can receive keyboard focus. Under reduced motion, band text appears and disappears immediately.

## 4. Not adopted from the inspiration

Hold-to-confirm (a longer gesture does not explain the consequence and excludes the keyboard), the decorative experiments (cassette player, playing cards, ticket, stamp, receipt, foil) and the motion on consequential confirmations. Confirmations keep their explicit wording with the named target and audience.

## 5. Token proposal

| Token | Value | Use | Status |
|---|---|---|---|
| `--duree-trace` | 1200 ms | One-shot trace of the sign, S-01 only | **Proposal**, validated by Aymen. Not in `tokens.css`, `tokens.json` or Figma |

The received export is frozen and the token tables of the design system are generated and checked, so this token is not added by hand. Promotion path: create the Figma variable, re-export, regenerate (design-system section 13). Until then the study is its only source.

## 6. Evidence

**Verified observation (2026-11-20).** The local prototype ran in headless Chromium 153.0.8010.12 through Playwright 1.66.0-alpha-2026-11-18, at 1440 × 1024, 390 × 844 and 320 × 844. Each width ran in light and dark with and without the system reduced-motion preference, for 12 configurations. The checks found no page-level horizontal overflow or console errors. Representative state captures were opened at each width.

| Pattern | Executed check and result |
|---|---|
| A | During a 3 s save, attempted cancellation by keyboard and pointer. The note and pending state were preserved. Simulated failure kept the note, and keyboard retry moved focus to save before confirmation. Cancellation outside the pending state restored the initial note. Editing during a slow save suppressed the older acknowledgement. The save button measured 165 px in rest, pending and confirmed states. Reduced motion used the dotted ring and static check |
| B | Dispatched browser drag events with synthetic files, including a mixed batch and a long filename. The drop zone measured 200 px at 1440, 216 px at 390 and 238 px at 320, with the same height before, during and after hover. The picker stayed visible and focusable during hover and opened from the keyboard. Accepted files stayed listed as "En attente d’inspection" when a file was refused. The reduced-motion highlight had zero transition duration and the icon stayed in place |
| C | Ran 150 ms, 1.5 s, 6 s and failure preparations. The shortest skipped tracing. The 1.5 s case showed "Scénario prêt." before the trace could complete. The 6 s case held the finished outline while preparing. Reduced motion showed a fixed outline and immediate fill, and failure showed the outline and enabled retry. The live region stayed mounted outside `aria-busy` |
| D | Focused the named region and traversed 14 rows with Tab and Shift+Tab. Wheel scrolling exercised the top, middle and bottom. Band boxes stayed outside the scroll region and visible labels appeared in Chromium’s accessibility tree. The bottom label was absent at the end. Reduced-motion transitions measured 0 s |

**To verify.** Figma variants and token bindings, actual screen-reader announcements, real devices and touch, Firefox, WebKit, contrast of every state in both themes, 200 % zoom, forced colors and the 400 ms and 2.4 s timings in real use. The browser run used synthetic data and simulated asynchronous responses.

## 7. Figma variants to draw (not drawn)

Drawn first as variants of the existing families, light and dark, at 1440×1024, 1024×768 and 390×844, then reviewed per the [coverage guide](../coverage-guide.md); the tracker rows inherit them through "Required states per catalog".

| Pattern | Family | States |
|---|---|---|
| A | I-05, I-06 as references, then I-04, I-07, I-15, I-24 | Rest, pending with save and cancel disabled, confirmed, failed with banner and kept input, focus on save after retry, reduced-motion equivalents |
| B | I-08 in S-13 | Rest and drag over with picker visible at the same height, refused type, refused size, "En attente d’inspection", long filename, keyboard focus on the picker, static drag feedback, mobile file picker and camera |
| C | S-01 | Preparing with trace in progress, preparing with trace complete, ready before trace completion, unavailable with retry, reduced-motion fixed outline and fill |
| D | Bounded lists of S-13 and the source drawer | Neither band, top only, bottom only, both bands, named region with focus, focused row at an edge, static indicators |

## 8. Inspiration and licences

The idea came from the "Kitchen" collection of dqnamo (https://www.dqnamo.com/kitchen), consulted on 2026-11-20: Dynamic Button, Magnetic Drop Zone, Logo Trace Loader and Scroll Fade List. **Verified observation:** the page presents 15 experiments and states no licence and no way to consume them. A Codex session of the same day reported MIT for Dynamic Button and Scroll Fade List on their 21st.dev pages and an unclear licence for Magnetic Drop Zone (**To verify**, not checked by the author of this study). **No code, image or text was copied:** only interaction ideas, reimplemented here from Étaye's tokens. If code from those pages is ever reused, check its licence first and keep its notice in the repository (AGENTS.md).

## 9. Open points

- The 400 ms threshold and the 2.4 s confirmed dwell are **[ASSUMPTION]** values to confirm with real use.
- The inspection label and directional band copy are proposals to review in the Figma variants.
- Native equivalents (ADR-010) are designed with the native screens; the technique is decided then.
- If Figma access remains limited, the offline exception of design-system section 14 applies to these states.
