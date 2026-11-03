# Corrected HTML design, separate from the received reference

This overlay addresses the independent design-reception findings. It is **design/prototype tooling only**: no Spring/React application, domain/API implementation, database, paid AI call, deployment or implementation story. The immutable input is [received/2026-11-03](../received/2026-11-03/README.md); its provenance hashes are checked before reconstruction. Product authorities remain in docs/planning-artifacts and docs/contracts. The overlay does not approve a product proposal or edit those contracts.

## Rebuild into a new external folder

Use Python 3.12+ and Poppler. Install the hash-locked optional dependencies into a separate environment, then install Playwright's Chromium. No account token is needed. From the repository root:

```sh
uv venv --python 3.14 /path/to/external-design-env
uv pip sync --python /path/to/external-design-env/bin/python --require-hashes design/corrections/requirements.txt
/path/to/external-design-env/bin/python -m playwright install chromium
/path/to/external-design-env/bin/python tools/rebuild_design_receipt.py --output /path/outside/repository --corrected --pdf
```

Use the exact returned folder. Open site/parcours.html for ten guided design journeys and site/index.html for the complete state inventory. Guidance is explicitly separate from product controls, describes scenario/audience changes and never simulates server authorization. Critical controls also connect to their designed outcomes. Remaining secondary actions disclose that they are static design references; no real save or service result is fabricated.

## Verify and review

```sh
/path/to/external-design-env/bin/python design/corrections/verify.py --root /returned/etaye-design --output /new/external/evidence-folder --full
```

The verifier requires a new disjoint evidence folder. It checks the complete frozen site/assets snapshot, all local references/fragments, ten guided journeys, actual critical controls, source/correction retention, context, focus, required reasons and nine exact PDF variants with per-page footers. Full mode checks every delivered state at four widths. Targeted dark/fallback rendering has explicit scope. Every failure is nonzero, and interrupted verification is recorded as incomplete. **Screenshots still must be opened**; automatic verification does not approve the visual result.

apply.py and finalize.py only write to an external reconstruction created by the wrapper. Generated files remain outside Git. apply.py preserves the original generator and fixture, adding domain-specific states and correcting the output; finalize.py replaces inherited self-review claims with a complete asset snapshot and adds explicit journeys. Source viewing keeps editor fields alive in memory, uses one source dialog, and restores focus. Native fields and prototype navigation are deliberately limited to fictional design interactions, with no browser-persistent clinical storage.

The Figma plugin is outside the selected Git reference and is not executed or promoted as a finished import. Synchronization remains pending. Common AGENTS.md/CLAUDE.md rules already govern the work; neither was replaced. All stories remain backlog. Aymen must still review the maquettes and explicitly authorize any future development.
