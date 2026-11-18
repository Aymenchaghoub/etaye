# Figma background-study plugin kit — preparation report

Date: 2026-11-18. Author: Claude (Claude Code session). Status: **prepared tooling, never run in Figma; proposal evidence, not a design approval** (sections 1 to 7 are the preparation record; section 8 is the update after PR 19 and PR 20 were merged). It prepares item 3 of the [cross-browser and tone report](2026-11-18-background-cross-browser-tone.md). The kit is in [design/figma-background-study](../../../design/figma-background-study/README.md). Nothing here changes DESIGN, the token reference, the coverage tracker, any Figma node or application code, and no development is authorized.

## 1. Trigger and decisions

**User decision (2026-11-18):** the Figma update work waits until draft [PR 19](https://github.com/Aymenchaghoub/etaye/pull/19) (native import closure, corrections still being made by Codex on Aymen's Windows PC) is finalized and merged, so that scripts are written on a tested base. **Proposal accepted (2026-11-18):** build it as a Figma Desktop development plugin rather than as MCP calls, because the Starter MCP quota allows about two calls ("pour le mcp ca va prendre trop de temps a cause de quota"). Aymen asked for it to be prepared "comme la PR 19", after which he will launch Codex on Windows to finalize the import.

**Interpretation, to confirm:** "prepared like PR 19" is read as the same pattern (local development plugin, prepared batch, offline fake-API test, downloadable evidence, handoff instructions), prepared now but **not run**. The kit is independent of PR 19's code, so it can be merged before or after it, and it only writes into a new section on its own page.

## 2. What was built

- `code.js`, `ui.html`, `manifest.template.json`: the plugin. It validates the batch, runs a preflight (file key, dedicated page, protected pages, duplicate batch, exact fonts, existing color variables, original logo components), then creates one section of 8 frames and removes it entirely on any failure, stop or change of target.
- `export_batch.py`: builds `batches/background-study-v1.json` from `study.html` (variant B, adopted tone) at the four study viewports in light and dark, using the existing `capture.js` exporter over the DevTools protocol, and the two 256 px grain tiles from `paper-grain.svg`. Deterministic: a fresh export equals the committed file.
- `test-plugin.cjs`: 8 offline tests against a fake Plugin API.
- `README.md`: purpose, prerequisites, installation, the first-run inspection list, rebuild commands, limits.
- `.gitleaks.toml`: two path-scoped allowlist blocks appended (the public Figma file identifier in three kit files, as for the earlier importers; and the SHA-256 provenance digests of the generated batch). `.github/workflows/documentation.yml`: the plugin syntax check and tests, and the Ruff check extended to the kit and the study tools.

## 3. Verification

| Check | Result |
|---|---|
| Plugin tests | `node --test design/figma-background-study/test-plugin.cjs`: 8 of 8 pass |
| Do the tests have teeth | four deliberate breakages (dark base changed, rollback removed, grain opacity changed, file-key check disabled) were each caught |
| Batch determinism | `export_batch.py --check`: the committed batch is identical to a fresh export |
| Tile fidelity | the tile composited at 12 % over the adopted base predicts the measured open-area mean of the HTML render within 0.32 levels (light) and 0.08 (dark) |
| Lint and repository tests | `ruff check --select E9,F` on the kit and the study tools passes; `python3 -m unittest discover -s tools` 15 tests pass; `python3 tools/validate_docs.py` 0 errors; `git diff --check` |
| Secret scan | pinned gitleaks 8.30.1 on the working tree: no leak. Before the scoped exceptions the tree showed 4 findings, all the public Figma file identifier or SHA-256 digests in the kit |
| Figma | **nothing run**: no MCP call, no REST read, no plugin run |

SHA-256 of the delivered files: `batches/background-study-v1.json` `3c60b78a5e06f4f9f294190d113698c64164b5d47fe1c74816d327b65d758bf8`; `code.js` `a7cdaa34556c4ccb09c6d44b3748dc4c3ffcf43a24896221ed3b4011d2d7cdeb`; `ui.html` `c35452a92fcfe94e57a2349cba98b953be38e5da547ce9e17b513f67f1e6e28e`; `manifest.template.json` `19aae5fb67008734ae93c1ffee78ce9336a9d627be19c3196fc86741f08b3b5b`; `export_batch.py` `a3ed8928664b74343b8fe97199f752012d1d58431b2ab5509ae9198d2026987d`; `test-plugin.cjs` `f94e6afc5261d9eab1b48c7f6f3f7c8f7a41d5d95ae106162da7f0b93b49c57f`.

## 4. Defects and findings in this round

- A first test run failed on a cross-realm array comparison (arrays created by the plugin inside the sandbox compared with arrays of the test); the test now compares the serialized matrices.
- The secret scanner flagged the public Figma file identifier and the provenance digests of the batch; handled by scoped allowlist blocks, not by hiding the values.
- The fake Plugin API implements only what the plugin calls. It cannot validate gradient-transform matrices, mask behavior, layer blur appearance, tiling or variable binding as Figma renders them.

## 5. Limits and flags

- **Never run in Figma Desktop.** The likeliest defects are the vertical gradient matrix and mask order of the ruler fade, the layer-blur approximation of the calm plate, and tile behavior; the README lists the first-run inspection points. Variable and component names (`Couleurs · Clair`, `Logo/Clair` `4:13`, …) come from the study README and earlier verification; the plugin checks them and stops.
- The grain is a PNG tile per theme, not a native noise effect (Figma's `NOISE` cannot bind a color variable), so its ink color does not follow a variable.
- The plugin does not create pages: the dedicated page `Étaye — Fond papier (étude)` must be created by hand, because the Starter page limit was not verified.
- Phase 2 (applying the background to S-25, S-02, S-01) is not part of this kit: it needs the final imported screens and responsive frames that do not exist yet.
- The kit stacks on the branch of PR 20 (the adopted tone exists only there), which itself waits for PR 19.

## 6. Open choices and access

- Confirm the interpretation of "comme la PR 19" (section 1).
- Run order on Windows: finish the PR 19 corrections and save their evidence, then run this plugin; Codex or Aymen can follow the kit README.
- Access unchanged: Figma MCP quota and REST limit (about 2026-11-22) are not needed for this kit. The explicit development authorization has **not** been given.

## 7. Next actions

1. Aymen (or Codex on Windows) runs the plugin after the PR 19 import is final, inspects the section against the HTML renders, and keeps the evidence JSON; any defect found is fixed in `code.js` and re-tested offline.
2. After a real, inspected run only: decide Phase 2 and record node IDs in the design records. DESIGN and the token reference change only after Aymen's validation.
3. Merge order: PR 19, then PR 20, then the kit PR (rebased on `main`); a squash merge of PR 20 means the kit branch needs `git rebase --onto main` first.

## 8. Update after PR 19 and PR 20 were merged (2026-11-18)

**User decisions:** "ok" to merging PR 19 (head `e69eca5`, three required checks green; squash `d72e62e`). After the proposal to merge PR 20 (squash `1dfac35`, three required checks green on `5cc9640`): "oui je veux que tu t'arrete pas si t'arrive pas a faire une main saine pret a faire l'import figma final puis la dev directement". Read as: keep going until `main` is healthy and ready for the final Figma run, with development named as the sequence that follows. It is **not** a development authorization: the design gates (responsive, component, token and prototype reconciliation, Aymen's approval) and an explicit authorization are still required.

**Verified observation (Figma, read-only, after both merges):** one REST call each to `/v1/files/:key/meta` and `/versions` returned HTTP 200. The current version is `2423224260331333575`, last touched 2026-11-18T15:05:14Z, which is the final named version of the closure report; the five correction versions of that report (`…207943764537466`, `…213333398169659`, `…216901999122776`, `…222524119712091`, `…224260331333575`) are listed with the same identities and labels, and no later edit is visible. This replaces the "independent read-back not claimed" limit of PR 19 for the version identity only. Node-level reads stay blocked (tier-1 REST limit until about 2026-11-22, MCP "Out of calls"), so no page, variable or component was read.

**Verified observation (screenshot sent by Aymen):** the file is "Etaye" in Drafts with a Free plan label; four pages exist (Couverture, Étaye — Import HTML, which is open on node `15:2`, Fondations, Composants); the dedicated study page does not exist yet; the imported sections are still named "import complet à revoir…" (truncated in the layer list); the MCP panel reads "Out of calls". The capture is at 2 % zoom and shows no variable collection, so names remain checked by the plugin preflight. **To verify:** whether the Starter plan allows a fifth page. If Figma refuses to create it, stop and report; the plugin's file-key check intentionally refuses a duplicate file.

**Checks on a trial merge of the kit branch with `main`:** PR 19 changed none of the files the kit depends on (`design/figma-local-import`, the study, the received fonts, the manifest and resolved tokens); the plugin syntax check and the tests pass; a fresh batch export equals the committed batch; `validate_docs.py` reports 0 errors. The ids, collection and logo names the plugin checks are present in the committed snapshot, manifest and resolved tokens.

**Change made (improvement from PR 19's real run):** a uniform CSS border is now a native inside stroke bound to the existing `filet/*` variable instead of four edge rectangles. PR 19's report documents inside strokes as the fix that restored the S-39 preview borders, and its importer sets `strokeAlign = 'INSIDE'`; the previous rectangles would stick out of the 8 px rounded corners of two nodes per frame (the secondary button and the preview panel). Offline: 8 of 8 tests pass; the previous code fails the new assertion; created nodes 1,783 to 1,719, bound colors 302 to 254, raw and ambiguous colors stay 0; the batch is unchanged. **Not verified:** how Figma Desktop draws it, because the plugin has still never run.

**Configuration:** the two allowlist blocks added for the kit in `.gitleaks.toml` were removed because `main` now carries equivalent ones for the same paths; the pinned gitleaks 8.30.1 scan of the whole history, including the kit commit, finds no leak. The workflow keeps both PR 19's native-closure steps and the kit's plugin step.

**Open:** create the study page and run the plugin on Figma Desktop (Aymen or Codex), inspect with the README list, keep the evidence JSON; decide Phase 2 only after that. Development remains unauthorized.
