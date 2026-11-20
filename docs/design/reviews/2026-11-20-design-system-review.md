# Design-system review before closing the topic — 2026-11-20

Status: **review record** supporting [ADR-013](../../decisions/ADR-013-web-component-behaviour-layer.md) and [ADR-014](../../decisions/ADR-014-design-system-closure.md). It approves no design and authorizes no development. Evidence labels follow [AGENTS.md](../../../AGENTS.md).

## 1. How the review was made

Aymen asked for one last design-system review and a look at component libraries before the next story. The project assistant (Claude Code) built a package of the design system for an external chat assistant: eight text bundles of 22 numbered pieces from `main` at `b48d1c2`, a French evaluation prompt, verified library facts and a script-computed contrast table. Aymen ran the review himself in an external chat (he calls the model "GLM 5.3"; the model version is not verified here). Nothing was uploaded by the assistant and no service or account was created. The package contained no secret, e-mail address or real data (checked by pattern search); it did contain Aymen's first name as the decision owner.

**The external answer is untrusted input.** Each claim below was checked against the repository or a primary source before use. The reviewer skipped the requested library scoring in its first consolidated answer and delivered it afterwards.

## 2. Claims checked (Verified observation unless stated)

| Claim of the review | Result |
|---|---|
| The "14 roles" rule is contradicted by the component CSS | Confirmed and larger: 26 of 39 combinations, 63 of 130 usages, once the weight is counted ([type roles](../typography-closure.md)); the review estimated about 20 |
| `.etaye-touche` has `opacity: .8` and fails 4.5:1 | Confirmed: it inherits `encre-3` in `.etaye-nav__recherche` over `surface`; 3.72:1 light, 3.99:1 dark at 12 px; 5.78 and 5.45 without the opacity |
| No code in the repository generates `tokens.css` | Confirmed: four files mention it, none writes it |
| No `forced-colors`, no `color-scheme`, no `prefers-color-scheme`, no `z-index`; a 1200 px breakpoint in `ext-consult.css`; `.etaye-a4` has no `max-width`; scrim `rgba(15,16,18,.48)` hard-coded; `.etaye-fond-flou { filter: none }` | All confirmed |
| `tokens.json` has no layout group; `.etaye-repere` is 16 px high | Confirmed |
| ADR-010 DF-19 says "approved 390×844 designs" while no family is approved | Confirmed; reworded in the ADR-010 amendment |
| "23 dialogs" in the interaction catalog | **Wrong**: 23 was a keyword count of mentions; the inventory has 28 interactions: 9 dialogs, 6 drawers, 7 pages, 6 inline |
| Radix combobox request "open since 2022" | **Inexact**: issue 1342 was created 2022-04-25 and closed on 2025-01-25; the package list still has no combobox |
| Base UI has no combobox | **Wrong**: Base UI has combobox, autocomplete and drawer; it has no calendar |
| `--duree-trace` absent from the token files is a critical drift | **Inexact**: ADR-012 says it is proposed, lives in the motion study and is promoted through the next token export |
| "40 pairs computed pass" | Inexact: the table has 56 rows (40 text, 16 non-text), all passing |
| WOFF2 cannot be used with `expo-font` | **Not established**: the Expo documentation shows only `.ttf` and `.otf`; it does not say WOFF2 is refused (spike, D-51) |
| Panel tabs are needed on S-19 | Not supported: S-19 mentions neither tabs nor "onglet" |

## 3. Contrast addendum (computed by script; not an audit)

Same method as the earlier table: WCAG 2.x relative luminance on the token values of `tokens.css`, both themes. Eleven further pairs used by the component CSS all pass: `encre` on `accent-fond` 14.68 / 12.37, `encre` on `attention-fond` 15.51 / 12.02, `encre` on `critique-fond` 14.96 / 13.21, `accent` on `papier` 7.83 / 7.56, `attention` on `papier` 5.41 / 8.71, `critique` on `papier` 5.98 / 8.05, `encre-2` on `papier` 6.96 / 7.33, focus `accent` on `attention-fond` 7.74 / 6.21 and on `critique-fond` 7.47 / 6.82, and `filet-controle` on `papier` 3.25 / 4.17 (the tightest). Print and e-mail pairs pass (9.02 to 15.41, and 8.22 for the dark e-mail). The one failure found is `.etaye-touche` (section 2). **Not measured:** rendered pixels, opacity stacks, the background grain, states, forced-colors.

## 4. Library facts used (registry and repositories, 2026-11-20)

| Package | Version, licence | Fact |
|---|---|---|
| `radix-ui` | 1.8.1, MIT | 55 sub-packages; no combobox, calendar or drawer; repository pushed 2026-10-14, 302 open issues |
| `@base-ui/react` | 1.9.0, MIT | combobox, autocomplete, drawer; no calendar; repository pushed 2026-11-20; the former `@base-ui-components/react` stopped at 1.0.0-rc.0 |
| `react-aria-components` | 1.22.1, Apache-2.0 | combobox, calendar, date picker; `Sheet` first shipped in 1.22.0 (2026-10-08, absent from 1.21.0); 1.16.0 dated 2026-03-04 |
| `@internationalized/date` | 3.12.4, Apache-2.0 | only dependency `@swc/helpers`; no React |
| shadcn changelog | n/a | lists "Base UI as the Default" (June 2026), "React Aria" (July 2026) and "Unified Radix UI Package" (January 2026) |
| Expo SDK 58 (`expo` 58.0.26) | MIT | bundles react-native 0.88.1, react 19.3.0, reanimated 4.7.1, worklets 0.13.0, react-native-svg 15.16.0; `expo-document-picker`, `expo-sharing`, `expo-file-system`, `expo-print`, `@react-native-picker/picker` are bundled; `react-native-pdf` and `@react-navigation/native` are not |

Compatibility of the native styling libraries with Expo SDK 58, bundle weights and real screen-reader behaviour were **not established**.

## 5. Weighted comparison

On the project weights (accessibility 25, style freedom 20, project health 15, agent predictability 10, web-native parity 10, lock-in 10, complexity 5, portfolio value 5), the external review scored React Aria 86.5, Base UI 72.5, Radix 70.5, shadcn 70.0, Ark UI 65.0, Mantine 57.0 (the arithmetic and three weight scenarios were reproduced exactly). The lead rests on three subjective points: accessibility 5 against 4 supported by vendor statements, a web-native parity credit for sharing `@internationalized/date` although no native date need exists in the first version, and a calendar the review assumed to be central. With equal accessibility evidence, no native parity and no calendar, React Aria 78.5 and Base UI 76.0; with Base UI credited for being the shadcn default, 78.5 and 77.0. Radix stays at about 70 in every variant. Aymen then chose to have a calendar, which makes React Aria the clear choice ([ADR-013](../../decisions/ADR-013-web-component-behaviour-layer.md)).

## 6. Visuals reviewed by Aymen (local pages, not committed)

A page comparing the two cases (native date field against an Étaye calendar) and a preview of the seven decisions, both built with the real tokens and component CSS; and a specimen of the 29 type roles with the 9 normalisations before and after. Aymen accepted all of it on 2026-11-20 (the two touch-target mono values stay roles). The pages embed the fonts and are kept outside the repository.

## 7. Not done

No application code, no package installed, no Figma access, no screen-reader test, no measurement on a running interface, no real device.
