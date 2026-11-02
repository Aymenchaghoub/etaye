# Recover the interrupted Étaye design session

Status: Ready for continuation, not design approval. Updated: 2026-11-02. This is the canonical recovery note; attachment08 is its derived copy. Product rules remain in the [handoff](handoff-claude.md) and its authoritative references. Use the [short prompt](continuation-prompt.md) in the existing Claude chat.

## Evidence and last checkpoint

**Verified by Codex:** [PR1](https://github.com/Aymenchaghoub/etaye/pull/1) merged on 2026-11-02 at 19:27:55 UTC into commit `48a3acf6f17271336a770fc8ecad5f9070ca77b5`. Post-merge [Documentation](https://github.com/Aymenchaghoub/etaye/actions/runs/41510181392) and [CodeQL](https://github.com/Aymenchaghoub/etaye/actions/runs/41510181006) succeeded. The Desktop recovery package existed and all 118 recorded file hashes matched when inspected for this consolidation. It contains specifications/reference evidence, **not the latest corrected Claude mockup sources**. No recent design render, worker output or Figma import was independently inspected by Codex in this consolidation.

**Reported by Claude through Aymen's transcript, still to verify against actual files:**

| Area | Reported checkpoint | Recovery check |
|---|---|---|
| Shared base | Shells, components and data created | Inventory current sources and reconcile specification changes. |
| EcranAujourdhui | Collision corrected by wrapping/reflow and a 24px desktop column gap | Open the latest render at matching widths; retain the fix without hiding meaningful content. |
| Logos and typography | Logos inlined in six mockups, dark variant works without scripts; two unintended serif lines corrected | Check actual source, light/dark variants, font loading and wider fallbacks. |
| EcranDossier / EcranPortailPatient | Layout corrected; old illustrative data remains | Reconcile with the [shared dataset](synthetic-data.json), rather than accepting layout success as content correctness. |
| Complete surface set | S/I and PDF/email work stopped; prototypes and independent review/coverage not started | A few component previews do not establish full surface/state coverage. |

Five workers were explicitly marked Failed: patient record; scheduling/consultation; brief/AI drafting; validation/publication; A4 PDF templates. Access/system, patient portal/email and administration workers had no final outcome in the transcript. All eight may have partial saved work: neither failure nor silence proves absence or completion. The reported shared usage limit/reset time is historical, not a verified current allowance. Do not loop failed calls or activate paid services.

## Recover before generating more

1. Inventory current artifact/source versions, saved worker outputs, modification evidence and any newer progress tracker. Earlier filenames `bundle-app.css`, `lib.py`, `screens_s06.py` and `BRIEF.md` are leads, not guaranteed locations or product authorities.
2. Save a recoverable snapshot/source ZIP before consolidation. Preserve newer or unmerged work. If the latest files are inaccessible, ask Aymen for their latest export; do not claim recovery or reconstruct silently from an old bundle.
3. Check the seven files Claude reported overwritten by a stale-page publication: `bundle.css` plus EcranAujourdhui, EcranDossier, EcranPortailPatient, Navigation, CompteRendu and RendezVous previews. Extensions/paths are unknown. Compare against current files before another publication.
4. Keep one writer for shared CSS, tokens, navigation and global bundles. Publish from consolidated current sources, reopen the published artifact and compare it to the reviewed render. A saved old page must not overwrite newer work.

The earlier downloaded ZIP is identified in the [external bundle review](external-bundle-review.md); it is not a backup of the latest corrected design and must not replace it. The original collision screenshots remain references in Claude's chat; this kit does not pretend to contain unavailable images or recent working sources.

## Preserve and reconcile

Preserve the original Étaye identity, geometry, French UI copy and verified tokens. Keep the reported Today spacing/wrapping, logo and typography corrections, subject to inspecting their current results. Reconcile the obsolete record/portal content with the authoritative [synthetic narrative](synthetic-data.md), dated sources and [document samples](document-content-samples.md). Do not restore the invented patient list or potassium value Claude reported removing, or invent data to fill layouts. Use the current [permission matrix](../planning-artifacts/access-matrix.md) and [interaction catalog](interaction-catalog.md); PD-001 remains a proposal, not Aymen approval. Missing data never means normal; validation never implies publication.

## Continue in bounded batches

Aymen authorized continuation of the design. Default to serial work; do not restart eight workers simultaneously. If useful and allowance permits, use at most two narrowly scoped helpers with separate outputs. Shared assets remain under one coordinating writer. This is a working safeguard, not a claim about platform limits.

Follow the [handoff work order](handoff-claude.md#work-order): recover/stabilize components and existing screens first, then practitioner journeys, access/patient journeys, administration/system states, PDF/email layouts, prototypes and review. Use small surface families with their interactions and error/restricted states. Consult relevant current sources per batch rather than loading the entire raw snapshot. Continue without repeatedly asking whether to start the next already-authorized batch.

After each batch save actual changed files, S/I/P/M IDs and variants, captures opened, findings/fixes, remaining work and the exact next action. Export the current sources at meaningful milestones. A quota-limited reviewer is an unfinished review, never independent completion.

## Review, tracking and stopping point

Continue offline HTML/CSS and limited prototype navigation while Figma is unavailable, as authorized design artifacts. Apply the [coverage guide](coverage-guide.md): keep HTML progress separate, merge newer evidence by stable ID, and record real Figma nodes only after actual synchronization. Do not reset a newer tracker with attachment06. Document the import gap and ask promptly for necessary access; no paid activation without discussion. HTML publication alone is not a Figma edit.

Retain automatic collision checks, then actually open captures: light/dark, brand/wider fallback fonts, scripts enabled/disabled where relevant, long/empty/error content, desktop 1440×1024, tablet 1024×768, mobile 390×844, 320px reflow/200% zoom and all A4 pages/grayscale. Inspect density, text, contrast, sources, actions, focus and overflow. An automatic pass or export request does not prove visual/functional correctness. Keep font/icon/Unslop attribution, including Damir Sokolovsky.

Return current source ZIP, navigation entry/prototypes, opened visual evidence, coverage and separate HTML progress, review findings and a next-action note. Complete designs must still be reviewed with Aymen. Do not scaffold Spring/React, implement APIs/domain/database/AI integrations, start a story, deploy or activate an application pipeline. Design completion does not authorize development.
