# Native import closure — 2026-11-18

Status: desktop import and the documented targeted corrections are complete and evidenced. This is design/proof/tooling work. Aymen's design approval and explicit application-development authorization remain pending.

Delivery: [PR19](https://github.com/Aymenchaghoub/etaye/pull/19), branch `docs/figma-native-closure`, is pushed and ready for human review against `main`. The PR retains the import run, Windows repairs, guarded correction tooling and final native evidence. Read the exact current GitHub head/checks independently from the dated local verification below.

## Actual final Figma state

The original file/page retains all **456 root IDs in 94 sections**. All **28,001 original editable text IDs** survive; one additional measured line in S-23 brings the final count to **28,002**. Thirteen small native ellipses restore missing disc markers. The eight S-06 roots retain their `26:*` IDs. The previous authorized twelve-root cleanup is unchanged.

Final named native version: **`2423224260331333575`**, “Etaye native visual corrections — 52 states — 2026-11-18”. Figma's `saveVersionHistoryAsync` returned that identity. The editor also continued displaying its reconnect warning; no independent REST/cloud read-back is claimed because the MCP Starter quota is exhausted. The local plugin made no HTTP request and changed no subscription.

The [before observation](2026-11-18-native-closure-before.json), [first-pass observation](2026-11-18-native-correction-pass1.json), [third-pass baseline](2026-11-18-native-correction-pass3.json), [fourth-pass baseline](2026-11-18-native-correction-pass4.json) and [final evidence](2026-11-18-native-closure-final.json) retain exact root identities, fingerprints, changed-node IDs, saved versions, raw local evidence digests and export hashes. The protected foundations `2:74` and components `2:75` have identical full before/after fingerprints. All **404 states outside the correction set** retain their original fingerprints. No root, original text or protected-page content was replaced.

## Targeted correction and native comparison

All 52 states in S-02 (9), S-09 (8), S-18 (9), S-22 (8), S-23 (9), S-39 (7) and I-13 (2) were inspected against their current native inventory and frozen HTML, corrected in place and exported natively at **1,440px**. The complete 523-file HTML snapshot still matches `d2f0800e275c4b156fedbcf23656ffe2bf435f9b6a72faf0f86fc293e2a82ad6`. Reference capture used offline Chromium 157.0.8103.51, viewport 1440×1024 and device scale 1, with the bundled fonts. All **52 native/reference dimensions match**.

| Finding | Actual correction and evidence |
| --- | --- |
| S-22 missing Ajouté/Supprimé badges | Reordered 35 existing badge frames above the overlapping opaque comparison fills; IDs and French copy retained. [Read-only comparison](2026-11-18-native-corrections/pairs/S-22__lecture-seule-pair.png), [long comparison](2026-11-18-native-corrections/pairs/S-22__longue-pair.png). |
| S-02 missing error-list marker | Restored measured disc markers as native ellipses. Final family review also exposed two clipped login focus outlines; their existing inputs now stop clipping those outlines. [Error](2026-11-18-native-corrections/pairs/S-02__patient-invalide-pair.png), [professional focus](2026-11-18-native-corrections/pairs/S-02__connexion-pair.png), [patient focus](2026-11-18-native-corrections/pairs/S-02__patient-pair.png). |
| I-13 focus ring | Removed clipping on the existing focused input; the original outline is visible. [Opened state](2026-11-18-native-corrections/pairs/I-13__ouvert-pair.png). |
| S-39 preview borders | Added the source one-pixel inside stroke to the existing PDF preview rectangles, using RGB 140/135/124. [Options](2026-11-18-native-corrections/pairs/S-39__options-pair.png), [seven-page preview](2026-11-18-native-corrections/pairs/S-39__long-pair.png). |
| S-18/valide 20px height difference | Existing root `30:24650` now measures 1440×1663, matching HTML. [Comparison](2026-11-18-native-corrections/pairs/S-18__valide-pair.png). |
| Wrapping, annotation and citation placement | Reconciled measured geometry and line runs without changing typography or source facts. Native-width inline spacing preserves measured browser gaps and trailing spaces. One S-23 wrapped alert uses a new exact-style native text clone for the second line's different x position. [Long timeline](2026-11-18-native-corrections/pairs/S-09__long-pair.png), [sparse timeline](2026-11-18-native-corrections/pairs/S-09__louis-pair.png), [publication error](2026-11-18-native-corrections/pairs/S-23__echec-publication-pair.png), [missing source](2026-11-18-native-corrections/pairs/S-23__source-manquante-pair.png). |

Visual inspection opened **13 individual matching-width pairs and seven complete family contact sheets**, plus an enlarged S-18 citation crop. The [final JSON](2026-11-18-native-closure-final.json) maps all 52 full-resolution HTML and native PNGs and the delivered pairs/contact sheets to their SHA-256 values. Contact sheets establish overall state/layout inspection; they do not claim detailed reading of every text in all 52 states or complete visual approval of all 456 states.

The initial geometry pass changed 3,558 existing nodes and saved version `2423207943764537466`. A first inline-spacing attempt saved `2423213333398169659`; enlarged QA rejected its excessive tightening. The corrected spacing pass saved `2423216901999122776`, preserving trailing spaces and accounting for text extending beyond inline wrappers. Family QA then caught the two login outlines and the wrapped alert. The fourth pass (`2423222524119712091`) corrected the outlines but its native clone landed at page level; export/count inspection rejected that intermediate result. The final pass adopted that exact already-created text ID into its verified alert wrapper, retained all original content and saved the final version above. The runtime and fixture now explicitly handle page-level clone insertion. Historical raw digests and actual changed IDs are retained, without relabeling intermediate renders as accepted final evidence.

The source-only prototype footer stays outside the editable capture. Native font rasterization and small glyph-metric differences remain observable; the image difference metric is descriptive, not a pass threshold. Native underlines/strikethrough retain the documented importer limitations. These are design previews, not an application renderer, clinical validation, a PDF-generator test or real email-client testing.

## Guarded tooling and Windows repair

The [correction plugin](../../../design/figma-native-closure/README.md) pins exact file/page/root/parent identities and full fingerprints, validates scoped edits, loads exact existing fonts and repeats target checks before mutation. Setter errors roll back scoped changes and newly created nodes. Export refuses drift after correction and verifies untouched fingerprints. It preserves the original import fingerprints; the importer must refuse these intentional later visual edits rather than silently accepting them as original packet output.

Eight original SVGs were restored byte-for-byte against committed blobs and manifest hashes. A narrow `.gitattributes` `-text` rule prevents checkout conversion of those SVGs. Original artwork and licenses are preserved. Local Git uses `core.autocrlf=input` and `core.symlinks=true`. Aymen repaired `.agents/skills` as the exact relative symbolic link `../.claude/skills`; its resolved shared source is verified. WinError 1314 was an actual Windows privilege restriction, separate from GitHub access. The validator still checks actual asset bytes and the real resolved link.

Local portability fixes use explicit UTF-8, POSIX archive paths and resolved containment on Windows, including 8.3 TEMP aliases. The destination regression uses a real junction only when symlink creation lacks OS privilege. No validator requirement was weakened. Machine-specific `.codex/config.toml`, the extracted `winfig` workspace, environments and temporary downloads remain excluded.

## Integration, access and verification

Before synchronization, the complete dirty/untracked workspace was backed up with hashes under ignored `.cache/figma-closure-backup/`; the tracked stash remains. The branch starts at remote main `eb58c81b3be3b91819dd2ca31a4f2412b509b5e3`, preserving later planning, roadmap and background studies. Those studies were not applied to the frozen import. PR16 is merged; unrelated new remote branches are preserved.

Git/gh authentication as Aymenchaghoub, repository ADMIN rights, remote reads and push were verified through the installed local CLI. Session sandbox restrictions initially blocked network/Git metadata writes; elevated terminal execution works. The old connector 403 concerns that integration. No new token or SSH key is needed.

Actual checks are recorded in the [check record](2026-11-18-figma-native-closure-checks.json): 19 Python tooling tests, 25 Windows importer fixtures, four legacy importer fixtures and nine native-correction fixtures pass with nonzero assertions. JavaScript syntax, Ruff E9/F, documentary OpenAPI, architecture lint, workflow policy, `python tools/validate_docs.py` and `git diff --check` pass. The validator covers 45 backlog stories, 96 operations, 87 schemas and 76 design families. No application tests ran.

Windows Gitleaks 8.30.1 uses the verified official archive digest `d29144deff3a68aa93ced33dddf84b7fdc26070add4aa0f4513094c8332afc4e`; its final redacted committed-history result is recorded separately from documentary fixture scanning. Existing default-branch Dependabot alert 54 (SoupSieve 3.0.4, high severity, fix 4.0.0) was observed before this closure and remains outside this change.

The all-ref scan also inspected the preserved background-study branch. Three matches are the established public Figma file ID; the fourth is a source `tokens.css` SHA-256, verified against that branch's original Git blob. Narrow exact-value/path exceptions cover that metadata and its repetition in the check record. No credential rule or validator requirement was disabled. The first new CI run rejected the repeated digest before that last exception was added; its actual failure and later outcomes are retained in the check record.

Copilot could not review because the requester quota was exhausted, CodeRabbit skipped automatic review for this repository, and Qodo reported paused reviews. No inline review findings were returned. No independent review, subscription change or design approval is claimed; the PR is ready for human review.

## Open design gates

The desktop import and these targeted findings are closed. Full responsive, component/token and prototype reconciliation remain required. Broader visual/design acceptance, real application PDF/email behavior, Aymen's approval and later explicit development authorization remain open. All 45 application stories stay in backlog. A ready-for-review documentation PR does not grant design approval or development authorization.
