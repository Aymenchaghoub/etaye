---
title: 'Portable Windows Figma import handoff'
type: 'chore'
created: '2026-11-17'
status: 'done'
route: 'oneshot'
scope: 'design-tooling-only; no application story'
review_loop_iteration: 0
context:
  - '{project-root}/AGENTS.md'
  - '{project-root}/docs/design/coverage-guide.md'
  - '{project-root}/docs/operations/delivery-rules.md'
---

<frozen-after-approval reason="User explicitly requested preparation, push and a Windows continuation prompt; Figma Desktop is already installed.">

## Intent

The Starter MCP quota stopped the complete desktop-state import after the corrected eight-state S-06 family. Aymen requested preparing and pushing everything needed to resume from another Windows PC with Figma Desktop installed. Deliver a self-contained local-plugin kit and versioned source/evidence/handoff, with no token, temporary upload URL or Linux-only dependency in the kit. The local route must work independently of the account-wide MCP quota.

Preserve the original foundations and components. Reuse the corrected editable scene model for all 456 desktop states in 76 families. Actual current completed section is 26:2; previous trial 15:3, pilot 19:2 and ten temporary preview placements are explicitly authorized for deletion after replacement verification. Keep import progress, visual review, Aymen approval and application development separate. No Spring/React implementation, application story advancement, paid service or subscription change is authorized.

The kit must contain ready native batches, exact fonts/licenses, corrected HTML/PDF comparison references, a manifest, recorded existing IDs, a precise English handoff and a French prompt ready to paste. Native import should preflight exact fonts and target file, accept all batches from one file selection, resume already completed states without duplication, roll back only the current newly created section on failure and export actual node evidence. Cleanup must be guarded by expected IDs and identity and only follow verification of the complete replacement state set.

Given a missing font, invalid batch or wrong target, starting import must leave the canvas unchanged. Given the recorded eight-state family, resumption must reuse verified roots. Given a failed later batch, earlier complete batches and original content must survive. Given an incomplete replacement set, cleanup must not execute. The Windows native execution remains explicitly pending until performed on the destination PC.

</frozen-after-approval>

## Implementation Notes

- One cohesive design-tooling handoff, not an epic story. No intent questions remain: Windows and installed Figma Desktop are user-confirmed; push and later guarded cleanup are explicitly authorized. Preparation/publication introduces no irreversible application change. Existing dirty work is retained under the explicit prepare-and-push instruction on `docs/figma-windows-handoff`.
- Reuse existing native plugin conventions in `design/figma-local-import/`, enhanced scene/renderer evidence in `/tmp/etaye-figma-full-20261117-xxooutpd/`, and scoped native assertions already exercised through MCP. Keep the old plugin stable; add the bounded Windows queue importer separately under `design/figma-windows-import/`.
- Put only sources, small reports and instructions in Git. Publish the generated self-contained archive as a GitHub release asset, with exact hashes and a provenance manifest; no generated gallery/archive/large scene collection in Git history. The public repository and all synthetic content are already authorized.
- Verify plugin failure/resume/cleanup behavior with meaningful isolated native-API fixtures, archive membership/hash integrity, source scene counts, document/link checks, JS syntax, existing tooling suites and the supported documentation CI. Record native Windows/UI limits truthfully.

## Review Triage Log

The oneshot route activates Blind Hunter only; other dispatch review layers were not configured for this route. The context-free reviewer first hit a usage limit after one finding; following Aymen's continue instruction it resumed and returned its complete floor calculation and eleven findings. No native Windows review is claimed.

1. **high / patch:** native leaf nodes lack `findAll`; rollback could fail before removal. A realistic leaf fixture reproduced two failures. The helper now treats leaves as single nodes; cancellation and append-failure regressions pass.
2. **high / patch:** text/font multisets alone accepted moved or repainted replacements. Existing sections now require the dated full descendant inventory of common native properties; new sections retain and recheck a SHA-256 fingerprint covering geometry, hierarchy, vectors, paints, effects and text. Existing REST evidence covers observable boxes/paint/text/hierarchy, not an independently verified vector-path equivalence; visual acceptance remains separate.
3. **high / patch:** cleanup could delete newly added descendants inside an old trial. Recorded candidate inventories now bind the complete observed descendant set and its common properties before any deletion; additions/changes refuse cleanup.
4. **medium / patch:** digest labels/dimensions did not bind raw compiled packet bytes. The canonical 94 packet SHA-256s are committed and enforced by the exporter. All 456 frozen scenes were deterministically recompiled; all 94 hashes still match the pin, with independently verified lossless decoders. Corrupt packet regression rejects before output.
5. **medium / patch:** aggregate456/ 76 counts accepted invented variants. The plugin now pins each packet's exact ordered state inventory and source packet digest, with a negative invented-state case.
6. **medium / patch:** 41 actual underline color mismatches were ignored. Preserve captured decoration color through the supported native property, with regression coverage; native visual execution remains pending.
7. **medium / patch:** five asymmetric dashed-border nodes lost their style. Render partial border dash geometry explicitly and retain a regression case. No full native fidelity claim follows.
8. **medium / patch:** evidence lacked capture/packet identity. Export the validated capture digest and ordered canonical source packet SHA-256s with the real node results.
9. **low / patch:** reused sections discarded link evidence. Reconstruct source tuple-path/state/target mappings on reuse; label absent descendant native IDs explicitly rather than inventing them. This is source lineage, not prototype wiring.
10. **medium / patch:** a dirty preliminary archive omitted untracked repository sources. Final export now refuses any dirty source tree before output; the earlier disposable preflight remains labeled preliminary. The delivered archive is built after all authorized sources are committed.
11. **low / patch:** pilot-era guidance contradicted the eight-state observation. Coverage guidance, the lower current phase/next action and pilot headings now direct to the current Windows handoff, preserving the earlier pilot as historical evidence.

## Delivery verification

The post-commit secret scan initially reported five occurrences of the already verified public Figma file identifier. The existing exact-value exception was extended to only the five new source/report paths; authentication-token rules remain active. The final history scan is rerun before push.

Executed: 15 Python tooling tests, 24 new importer fixture tests, four existing importer fixture tests, JS syntax, Python lint, documentary OpenAPI/architecture/link/workflow checks and the pinned secret scan. All 94 actual packets, eight existing roots, 5,527 inventory records and ten unique PNG hashes were checked. The final clean-source archive and publication identity are recorded separately after the source commit; this spec marks tooling preparation complete, not native Windows import complete. Native Windows execution, PowerShell verification and user design acceptance remain pending; all application stories remain backlog.

Published clean-source kit: [delivery register](../design/windows-handoff/DELIVERY.json), release `figma-windows-20261117`, source `a4bd5d3c7a6ee92f7f7cc8aa6ce87db427690621`, [PR16](https://github.com/Aymenchaghoub/etaye/pull/16). Local ZIP/manifest/checksum hashes match GitHub's actual uploaded digests. This delivery-note commit intentionally postdates the archive snapshot; native Windows and complete import remain pending.
