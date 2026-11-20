# CORE families review — 2026-11-20

Status: **Aymen's approval of the 19 CORE families of increment 1 (UD-46).** It approves a design reference, not code, and authorizes no development. Evidence labels follow [AGENTS.md](../../../AGENTS.md).

## 1. What was shown

The families of [roadmap section 9](../../planning-artifacts/roadmap.md) that UD-16 requires to be designed, reviewed and approved before the increment 1 build: 15 screens (S-01, S-02, S-05, S-06, S-09, S-10, S-18, S-19, S-21, S-22, S-23, S-25, S-28, S-29, S-36) and 4 interactions (I-09, I-15, I-16, I-17). For each family a local visual page showed its primary state at 1440×1024, 1024×768 and 390×844, up to three critical states (failure, loading, read-only, long, empty, retired, stale) at 1440×1024, and links to every state of the family in the committed offline reference. The captures were taken by Claude Code with headless Chromium from the committed [offline reference](../../../design/offline-reference/README.md) (snapshot digest `d2f0800e275c4b156fedbcf23656ffe2bf435f9b6a72faf0f86fc293e2a82ad6`), as JPEG images (93 files, 5.5 MB). **The page and the captures are not committed.**

## 2. What Aymen answered

He answered "APPROUVÉE" to each of the 19 families, with no remark: "Total : 19 APPROUVÉE". That answer is what the page's batch button produces (it marks every family not yet judged as approved). [ASSUMPTION] he looked at the sample as the page asked before using it; nothing in the answer says which families he opened.

## 3. What the approval covers, and what it does not

- **Covers:** the visual and functional reference of the 19 families, which is the corrected HTML reference, as shown. It satisfies the CORE design gate of UD-16 for the increment 1 build of those families.
- **Does not authorize development.** UD-28 still applies: only the stories listed in `authorized_stories` (UD-41, UD-45) may be implemented.
- **Does not approve the other 57 families**, which stay "Not approved".
- **Not judged on these captures:** keyboard focus, long French content, 200 % zoom, the 320 px reflow, print, the dark theme (only reachable through a link), and every state that was not shown (the reference has 456). The functional review column of the tracker stays Pending.
- **Not part of it:** the interaction specifications that are deferred (D-42 to D-48), and the component CSS corrections of the overlay (D-53), which do not appear on the captures. Figma was not read; the tracker rows keep the Figma-less exception of the design system section 14.

## 4. Families and states shown

| Family | Name (tracker) | Primary state | Critical states shown |
|---|---|---|---|
| S-01 | Showcase and demo entry | `accueil` | `indisponible` |
| S-02 | Login | `connexion` | `champ-vide`, `limite`, `suspendu` |
| S-05 | Practice and access-context selection | `choix` | `retire` |
| S-06 | Practitioner home and daily preparation | `nora` | `chargement`, `echec-partiel`, `jour-vide` |
| S-09 | Patient record and longitudinal timeline | `louis` | `erreur-partielle`, `lecture-seule`, `long` |
| S-10 | History, allergies and reported treatments | `louis` | `evaluation-erreur`, `lecture-seule` |
| S-18 | Sourced pre-visit brief | `louis` | `echec`, `perime` |
| S-19 | Consultation workspace and structured notes | `louis-terminee` | `echec`, `lecture-seule`, `longue` |
| S-21 | AI draft review and processing result | `revue` | none named |
| S-22 | Version history and comparison | `comparaison` | `lecture-seule`, `longue` |
| S-23 | Human validation and patient publication review | `validation` | `echec-publication`, `perime` |
| S-25 | Patient home | `accueil` | `indisponible`, `lien-retire`, `retiree` |
| S-28 | Published patient reports and explanations | `liste` | `erreur`, `retiree`, `vide` |
| S-29 | Patient published content detail | `a-jour` | `retiree`, `source-indisponible` |
| S-36 | Forbidden and not-found states | `patient` | `interdit` |
| I-09 | Open supporting source | `ouvert` | `indisponible` |
| I-15 | Edit generated content | `ouvert` | `echec` |
| I-16 | Validate human-reviewed version | `ouvert` | `erreur` |
| I-17 | Publish to named patient | `ouvert` | `erreur`, `perime` |
