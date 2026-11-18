# Native import closure — 2026-11-18

Status: integration and Windows tooling verified; targeted visual corrections still pending. This report supersedes the earlier native-run report's access and delivery statements. It does not supersede its dated import/cleanup evidence.

Delivery: [PR19](https://github.com/Aymenchaghoub/etaye/pull/19), branch `docs/figma-native-closure`, is pushed as a **draft**. It is not ready for completed visual-closure review. GitHub Planning package checks passed on `b3511e1`; the final follow-up head must be checked independently. CodeRabbit explicitly skipped review because the PR is a draft; no independent review is claimed.

## Preserved evidence and integration

The [native-run evidence](2026-11-17-windows-native-run.json) records 456 desktop roots in 94 sections, 28,001 editable texts, reuse of the eight S-06 IDs, the exact twelve authorized trial removals and saved version `2423132943961499284`. Its 42 opened comparisons include all nine PDF previews and 19 email states. The original sources and licenses remain unchanged.

The complete dirty checkout and extracted local workspace were backed up before synchronization. A tracked-files stash was retained. The closure branch starts from remote main `eb58c81b3be3b91819dd2ca31a4f2412b509b5e3`, preserving PR15/17/18 planning and background work. PR16 is merged. `.codex/config.toml` contains a machine-specific Computer Use notification path; `winfig/` contains the extracted release, duplicate sources, temporary plugins and raw downloads. Both were inspected and excluded. Selected durable native evidence remains under this review directory.

## Verified access

An integrity audit found nine comparison paths still pointing to the 17 November images while their recorded hashes described the 18 November comparisons. The exact matching files were recovered from the local workspace and retained under `2026-11-18-windows-native/`. Each record now preserves both its original opened path/hash and the correct later comparison path. All 42 comparison SHA-256 values match delivered bytes; all 456 root IDs are unique and their recorded text counts sum to 28,001. This reconciles artifact references, without claiming a new native visual correction.

GitHub CLI is installed outside PATH at `C:/Program Files/GitHub CLI/gh.exe`. Local authentication, repository ADMIN rights, remote reads and dry-run push succeed outside the session sandbox. The sandbox initially blocked HTTPS and local Git metadata writes. The connector's historical `403 Resource not accessible by integration` is separate; neither a new token nor SSH key is needed.

Figma MCP returns its Starter call limit. Figma Desktop is open in the original file/page and shows an unsynced-changes warning. The registered local development plugin completed a read-only inspection of all 456 expected root IDs. Computer Use could not reliably target the native Save dialog to download that observation. Aymen was asked to save it to local `winfig/closure/before.json`; until received and checked, no fresh detailed inventory/export evidence is promoted.

## Windows corrections and checks

All eight original SVGs were compared against their committed blobs and manifest SHA-256 values; only checkout CRLF conversion differed. Original bytes were restored and a narrow `-text` attribute prevents conversion. No asset was redrawn and no license changed. Local `core.autocrlf=input` retains normal text normalization. The validator still hashes actual working-tree bytes.

Windows refused creation of the shared `.agents/skills` symbolic link with WinError 1314, including outside the sandbox. The exact administrator PowerShell repair was requested. The validator's resolved-link requirement remains intact. Exporter path membership now uses POSIX paths, repository containment resolves both sides (including 8.3 TEMP aliases), and HTML audits explicitly decode UTF-8. The symbolic-link destination regression uses a real Windows junction only when OS privilege prevents symlink creation; it still exercises and requires rejection by the resolved containment guard.

Actual local results:

The Linux-specific scanner script cannot execute its downloaded ELF binary on Windows. The official Windows Gitleaks 8.30.1 archive was fetched with gh; SHA-256 `d29144deff3a68aa93ced33dddf84b7fdc26070add4aa0f4513094c8332afc4e` matched the release digest. Its redacted scan of all 25 commits found no leaks. GitHub separately reported existing default-branch Dependabot alert 54 (high severity); it is not a result of this closure and was not resolved here.

- 15 Python tooling preservation/tamper/failure/destination tests passed.
- 25 Windows importer and four legacy importer fixtures passed, with nonzero assertions.
- JavaScript syntax, Ruff E9/F, OpenAPI validation, architecture lint, documentation workflow policy and Git whitespace checks passed.
- Documentary validation verifies 45 backlog stories, 96 operations, 87 schemas and 76 design families; its only remaining error is the local shared-skills link.
- No application, clinical, real email-client or application PDF-generator tests ran. CI results will be recorded from the exact PR head.

## Remaining visual work

Keep the 456 existing root IDs. Before each mutation, inspect the actual relevant descendants and compare native exports with frozen HTML at 1,440 px. The current source inspection independently reproduces S-18/valide's 1,663 px reference height against the old 1,683 px capture. The other findings remain S-22 inline Ajouté/Supprimé badges, S-02 error-list marker, I-13 focus ring, S-39 preview borders, line wrapping and citation placement. No correction is marked complete without before/after native evidence. Protected foundation/component pages and newer unrelated work must remain intact. Changed frames must receive a traceable correction record; the old immutable packet fingerprints must not be silently updated to accept arbitrary drift.

Responsive/component/token/prototype reconciliation, Aymen's approval and later explicit development authorization remain separate open gates. The background studies were preserved as studies and not applied to the frozen import.
