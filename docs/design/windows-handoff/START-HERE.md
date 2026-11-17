# Étaye — resume the native import on Windows

This handoff targets **Windows with Figma Desktop already installed**, as Aymen confirmed. It prepares the next client; it does not claim that the native Windows plugin has already run.

## Current state

- Target: [the existing Étaye file](https://www.figma.com/design/vfh4BSnbRAbaDE1q0CyssL), page `15:2`, “Étaye — Import HTML”. Preserve “Fondations” (`2:74`) and “Composants” (`2:75`).
- Complete source scope: **456 desktop states / 76 families**, delivered as **94 native packets**. Other viewport/theme frames, component/token reconciliation and design approval remain separate work.
- Already imported: all eight S-06 states in section `26:2`; S-06/nora is `26:1282`. Only targeted native visual review has occurred. Re-read the file before relying on these dated IDs.
- MCP stopped at the next packet with the explicit Starter-plan call-limit error. The account-wide quota does not reset when changing PCs. Use the local plugin; no subscription change is authorized.
- Aymen authorized deleting the old trial `15:3`, pilot `19:2` and ten temporary preview placements `22:2`–`22:11`, **after** replacement verification. Exact identity guards are in `EXISTING-FIGMA-STATE.json`. Cleanup has not run.

## Get the files

Download the ZIP and its checksum file from the [Windows design handoff release](https://github.com/Aymenchaghoub/etaye/releases/tag/figma-windows-20261117). Extract to a short local path such as `C:\Etaye-Figma`.

The archive contains:

- `plugin/`: the local native importer and instructions.
- `batches/`: the 94 JSON files to select together; this folder contains only import packets.
- `windows-fonts/`: the exact supplied fonts, complete licenses and source hashes.
- `maquettes/site/parcours.html`: the ten corrected HTML journeys; `site/index.html` lists all states and `site/docs/` contains the nine PDFs.
- `repository-source/`: the dated ordinary-file repository snapshot, preserving source paths. It excludes Git history and discovery symlinks; use a real checkout for Git changes.
- `EXISTING-FIGMA-STATE.json`: actual known native IDs and cleanup candidates.
- `preparation-evidence/`: frozen scene data, generator sources and native responses. These historical preparation scripts may name the original Linux environment; **they are not required to run the ready Windows importer**.
- `MANIFEST.json` and `VERIFY-KIT.ps1`: payload integrity evidence. The archive checksum is published beside the ZIP; inspect/run the read-only verifier if the current PowerShell policy permits it. Do not change the machine execution policy to run it.

## Install the local plugin

1. Sign in to Figma Desktop with access to the existing target file. This is a normal Figma login, not a token import. Preserve a named version or another supported recoverable backup before the import/cleanup.
2. Install the supplied official fonts if Figma cannot discover their exact family/style names. Restart Figma afterward. The plugin must stop on missing fonts; do not substitute another family.
3. In Figma Desktop use Plugins → Development → New plugin, choose Design and a custom UI, and choose a local plugin folder. Keep Figma's actual generated plugin ID.
4. Copy `plugin/code.js` and `plugin/ui.html` into that generated folder. Apply the fields from `plugin/manifest.template.json` while retaining the generated real `id`. The local plugin's private-API flag is needed to verify `fileKey`; its network allowlist is empty. Do not invent a plugin ID or publish the plugin.
5. Open the target file and page. Run the development plugin. Select **all 94 JSON files** inside `batches/`; start the queue after the plugin reports a valid complete selection. It must preflight the target and exact fonts before canvas writes, reuse the verified existing S-06 family, and leave foundations intact.
6. Inspect a representative newly imported family against its HTML at the same 1440px width before treating the conversion as visually acceptable. Inspect long/error/restricted states, all PDF variants and email layouts as applicable. Import success is not visual review or approval.
7. Export the plugin's real node/evidence JSON. If interrupted, keep completed sections and rerun the same complete packet selection; inspect reported conflicts rather than replacing unknown work.
8. Use the explicit guarded cleanup only after all 456 replacement states verify. Keep any conflicting candidate and investigate its identity; never remove original foundations or unrelated work.

## Continue project tracking

Use the Git branch `docs/figma-windows-handoff`, then read `AGENTS.md`, `docs/README.md`, `docs/continuation.md` and the current source manifest. Record actual node IDs, source/capture versions, opened screenshots and unresolved findings in coverage/reception and a dated report. Keep the 37 application stories in backlog and preserve Aymen's design/development approval gates.

The ready-to-paste agent task is in `RESUME-PROMPT.txt`. Native Windows execution, GUI automation availability, the imported result and user approval must be verified on that PC; this handoff cannot establish them in advance.

## Build provenance

The ready ZIP is exported from a clean committed source revision. The exporter rejects a dirty tree, checks the committed canonical raw-packet hashes against all 94 prepared packets and preserves the frozen HTML/font bytes. The final release's ZIP checksum and manifest identify that exact snapshot; later delivery-note commits may postdate the archive's source commit. Only packet 0 carries the large dated native inventories, so selecting all 94 files does not duplicate those baselines 94 times in memory. Existing S-06 and cleanup inventories guard observed names, IDs, parents, boxes, paints and text; new sections additionally retain a native-property fingerprint. Visual review remains necessary, including path fidelity not established by common-property REST evidence.
