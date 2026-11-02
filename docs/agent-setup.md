# Agent setup and troubleshooting

Updated 2026-11-02. This guide describes actual installed project tooling, not a working application. Shared policy is AGENTS.md; CLAUDE.md imports it using `@AGENTS.md`. [Official Claude imports](https://code.claude.com/docs/en/memory) and [skills](https://code.claude.com/docs/en/skills), [official Codex skills](https://developers.openai.com/codex/skills/) support this arrangement. No application build/test command has been verified. Documentation/tooling CI was subsequently authorized; its checks are listed below.

## Pinned installation

Official stable BMAD **6.12.4**, core and BMM, installed through npm on 2026-11-02. GitHub latest release and npm latest were both 6.12.4. Do not use the preview current main or the user's personal 6.13 preview plugins. Manifest: `_bmad/_config/manifest.yaml`; installer-managed TOML/YAML remain generated; durable project changes belong in `_bmad/custom/`.

Verified installation invocation:

```bash
npx --yes bmad-method@6.12.4 install --directory "$PWD" --modules bmm --tools claude-code,codex --user-name Aymen --communication-language French --document-output-language English --output-folder docs --set core.project_name=Étaye --set bmm.planning_artifacts=docs/planning-artifacts --set bmm.implementation_artifacts=docs/implementation-artifacts --set bmm.project_knowledge=docs --no-shims --yes
```

Rerunning an installer can regenerate files; inspect Git state first and preserve custom/personal layers. The original two generated skill trees were byte-compared, one moved outside the repository as a temporary preservation copy, then `.agents/skills` linked to `../.claude/skills`. There is one authoritative project skill copy.

Unslop was installed using the skill-installer helper from `heydamir/unslop`, path `skills/unslop`, pinned commit `a6f121eee5f29c2f74976b75456feae8b66afb3a`, destination `.claude/skills`. Its repository-root license was fetched at that commit and retained locally. Author Damir Sokolovsky, CC BY4.0; see THIRD_PARTY_NOTICES.md. SKILL.md plus signs.md/tokens.md were read. No UI redesign performed.

## Effective configuration

`_bmad/custom/config.toml` pins Étaye/Aymen, French conversation, English documents, planning/implementation/project knowledge paths and the project test-artifact path. User-scope installer answers in ignored `_bmad/config.user.toml` do not by themselves configure a fresh clone; the committed team override makes required defaults portable. Personal customization belongs in ignored `*.user.toml`/settings.local.json, never in shared rules.

Resolve before every workflow:

```bash
uv run --no-cache _bmad/scripts/resolve_config.py --project-root "$PWD" --key core
uv run --no-cache _bmad/scripts/resolve_config.py --project-root "$PWD" --key modules.bmm
uv run --no-cache _bmad/scripts/resolve_customization.py --skill .claude/skills/bmad-ux --project-root "$PWD" --key workflow
```

The third command is an example for the actual local workflow selected; use its exact directory. Resolution merges generated team/personal config then custom team/personal overrides. Read returned values, expand `{project-root}` once, never double-prefix. `etaye.test_artifacts` is project metadata, not a claim that the optional TEA module is installed; test strategy is authored under docs/test-artifacts. TEA is not installed.

## Verified discovery and versions

Installed local CLI versions observed: Codex0.166.0, Claude Code2.1.316, uv0.12.24, Node22.23.2, Python3.14.8. These are tooling observations, not the chosen future application runtime. BMAD official prerequisites are Node20.12+/Python3.10+ plus uv.

- Filesystem comparison: `.agents/skills` and `.claude/skills` resolve to the same directory;29 BMAD skills plus Unslop.
- **Codex native discovery verified:** local app-server initialized, `skills/list` called with project cwd and forceReload; returned all30 project skills, enabled, zero discovery errors. No model inference request used.
- **Claude native discovery verified:** SDK initialization in this project returned all29 named BMAD commands and Unslop. Initialization only, no user/model request. Global command inventory was not copied into the repository.
- Import syntax/path and local shared-source link checked. A fresh interactive session must load current instructions; no claim that a previous session automatically reread changed policy. Use the tool's current context/memory inspection to confirm loaded files when resuming.

Available local planning names include bmad-product-brief, bmad-prd, bmad-ux, bmad-architecture, bmad-create-epics-and-stories, bmad-review, bmad-project-context and bmad-sprint-planning. Read their exact SKILL.md/references; discovery names are not permission to execute bmad-build or bmad-build-auto. Those installed delivery skills remain unused in this phase.

## Checks and external export

```bash
python3 tools/validate_docs.py
uvx openapi-spec-validator docs/contracts/openapi.yaml
uv run --no-cache .claude/skills/bmad-architecture/scripts/lint_spine.py --workspace docs/planning-artifacts/architecture
python3 tools/export_claude_chat_kit.py --output /path/outside/repository/Etaye-Figma-Claude
```

These documentary commands were run; results are recorded in review-report.md. `uvx` resolves a validation tool in its isolated cache, not app dependencies. The chat kit is a derived distribution outside Git, as requested; source documents remain authoritative. The current desktop folder contains files00–06 and optional original assets. Do not put tokens into exports.

## Access troubleshooting

GitHub CLI authenticated as Aymenchaghoub and repository created private, without changing global identity. Local commits use the GitHub noreply address. Aymen subsequently explicitly authorized public visibility and initial free repository security/CI setup. If an operation fails, distinguish auth from network and inspect current connection before requesting a new credential.

Figma MCP login succeeded but Starter/View read quota was exhausted. Authorized local REST token then verified file/comments/versions/export access. Token stays outside repo; no token value in logs. `tools/inspect_figma.py --token-file /secure/local/file` is read-only; inspect target file/version and permissions before refreshing evidence. Full variable collections require an unsupported Starter REST capability; node properties/alias IDs were captured. Reuse existing Claude chat's Figma access for the next phase. Do not ask for paid AI or deployment credentials to write designs.

If skills are missing, launch in repository root, verify symlink/actual SKILL.md names/frontmatter, resolve configuration and reload native discovery. Select project path when personal plugins have the same name. Do not repair by mixing versions. If uv is absent, install via official uv instructions before running workflow scripts. A usage-limit interruption is not evidence of completed review; resume from saved files and label limits.

## Authorized documentation CI

Aymen explicitly requested the initial CI and first PR after planning. `.github/workflows/documentation.yml` validates this package, lints first-party Python tools with Ruff and runs a checksum-pinned Gitleaks CLI against committed history. `.github/workflows/codeql.yml` analyzes only `tools/` with CodeQL Python security queries. Neither builds the application. Actions are pinned to full commit SHAs, the ordinary job has contents:read and no repository secrets; CodeQL alone has security-events:write. Dependencies are hash-locked in tools/requirements.txt, derived from tools/requirements.in. Dependabot watches Python tooling and action pins.

Verified local equivalents (CI execution has its own actual run evidence): create `.venv-docs` with `uv venv --python 3.14 .venv-docs`, then `uv pip sync --python .venv-docs/bin/python --require-hashes tools/requirements.txt`. Run `.venv-docs/bin/ruff check --select E9,F tools`, `.venv-docs/bin/python tools/validate_docs.py --report reports/document-validation.json`, `.venv-docs/bin/python -m openapi_spec_validator docs/contracts/openapi.yaml`, the installed lint_spine.py with output under reports/, `tools/check_doc_workflow.py`, `git diff --check` and `bash tools/run_secret_scan.sh` after commits exist. Gitleaks scan failure must block the PR; redact report contents and do not silently suppress findings.

## Resume kit export

Canonical recovery instructions: [session recovery](design/session-recovery.md); copy-ready [short prompt](design/continuation-prompt.md). The exporter only bundles named documentation sources; it does not recover or include the latest corrected Claude mockups. Do not confuse its ZIP with a current design-source backup.

Verified command for local regeneration:

```bash
python3 tools/export_claude_chat_kit.py --output ~/Desktop/Etaye-Claude-Reprise
```

The output is now a **new versioned child folder** on every run; use the returned path, rather than older files at the parent level. Existing user files, older kits and newer external trackers are untouched and never automatically included. The kit contains attachments 00–08, START-HERE.txt, PROMPT-TO-PASTE.txt, manifest.json, the ZIP and SHA256SUMS. Attach that ZIP in the existing chat and paste the prompt; attach 00–08 separately only if ZIP reading is unavailable.

The manifest records UTC generation time, source HEAD commit, dirty-worktree flag, input-file hashes and every payload file's byte count/hash. A dirty flag means files may differ from that commit; use source hashes to identify the actual inputs, and export from a clean committed revision for delivery. The ZIP contains exactly the payload plus the identical manifest. SHA256SUMS hashes the external manifest and ZIP; those three integrity files are excluded from payload hashes to avoid circular self-hashing. The exporter reopens/checks every delivered payload and ZIP entry, rejects known credential patterns and refuses repository-internal destinations (including resolved symlinks). Only allowlisted text sources are read; no local token or arbitrary folder is ingested. Pattern scans do not prove detection of every possible secret; the existing history scan remains a separate check.

Run preservation/tamper/destination/credential-pattern regression checks with `python3 -m unittest discover -s tools -p 'test_*.py'`. These test the documentation exporter, not the application. No generated kit, ZIP or duplicate specification belongs in Git.
