# ADR-012 — Functional motion for state feedback

Status: Accepted by Aymen on 2026-11-20 (UD-42): "je valide", after he played the interactive proposal. Date: 2026-11-20. Related: UD-16, UD-27 (this is the mini-ADR it requires), UD-28, ADR-010, [design system](../design/design-system.md) sections 4.5 and 7, [motion study](../design/motion-study/README.md), S-01, S-13, I-04 to I-08, I-15 and I-24.

The design system allows only the color transitions of links and buttons and a 0.98 press scale, and bans "gratuitous animation". **User statement (2026-11-19):** the project must show screens that feel fluid and modern while keeping one visual identity across web and mobile. A few state changes are currently invisible or abrupt: saving, a file hovering over a drop target, the wait on the demo entry, a list that continues out of view.

**Choice:**

1. **Functional motion is allowed, nothing else.** Motion is permitted only when it carries a state the user needs: pending, confirmed, failed, active drop target, more content available. Four patterns are accepted: the states of a save button (SP-SAVE), the file drop target of I-08, the one-shot trace of the sign while the demo is prepared (S-01), and an overflow fade on bounded lists. Details, copy and timings are in the [motion study](../design/motion-study/README.md).
2. **Five rules for every functional motion.** It reuses `--courbe` and the existing durations; it has a static equivalent carrying the same information under `prefers-reduced-motion: reduce`; it never replaces text, so the state is also written; it never runs longer than the work it reports and nothing plays automatically for more than 5 seconds; it never shows success before the server commit.
3. **One new token, proposed.** `--duree-trace` = 1200 ms, for the sign trace of S-01 only. It exists in the study, not in `tokens.css` or in Figma: the received export is frozen and the token tables of the design system are generated. It is promoted through Figma variables and the next token export.
4. **The sign may be animated in one place.** The original paths of the sign are unchanged and are not redrawn; the trace and the fill are the only change, on S-01, and only when the preparation lasts more than 400 ms.
5. **Excluded.** Hold-to-confirm gestures, skeuomorphic scenes, decorative glow or gradient, and any motion on publish, withdraw, validate or discard. Confirmations keep their explicit wording with the named target and audience.
6. **Order of work.** The states are drawn as Figma variants and reviewed first (the coverage tracker rows inherit them through "Required states per catalog"). No application code is written for them under this decision; development stays gated by UD-28.
7. **Native.** The same rules apply when the native screens are designed (ADR-010). The technique (for example Reanimated) is decided then, not here.

**Alternatives:**

- Keep the no-animation rule: rejected; the stated goal is fluid, modern screens, and the four patterns remove real ambiguity (is it saving, did it fail, can I drop here).
- Adopt the visual language or components of the Kitchen collection that inspired the idea: rejected; most of it is decorative, its licence is not stated on the site, and the design system forbids inheriting a generic component theme. Only interaction ideas were taken, reimplemented from Étaye's tokens; no code or image was copied.
- Add an animation library: rejected; CSS covers the four patterns, and a library adds weight and a second source of motion.

**Consequences:**

- Written with this ADR: the motion study and its playable prototype, a pointer in the design system, the states added to I-08, S-01 and the shared save pattern, UD-42 in the register.
- **To verify:** rendering in Firefox and WebKit and on real devices, contrast of every state in both themes, a screen-reader pass of the announcements, the 400 ms and 2.4 s values with real use. Measured on 2026-11-20 only in Chromium (study section 6).
- Native equivalents and the Figma variants are design work still to do. If Figma access stays limited, the offline exception of design-system section 14 applies.
