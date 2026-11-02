#!/usr/bin/env python3
"""Build derived English design attachments for a Claude chat."""
from pathlib import Path
import argparse
import hashlib
import json

ROOT = Path(__file__).resolve().parents[1]
GROUPS = {
    "01-product-and-rules.md": [
        "docs/planning-artifacts/product-brief/brief.md",
        "docs/planning-artifacts/prd/prd.md",
        "docs/planning-artifacts/functional-specification.md",
        "docs/planning-artifacts/business-rules.md",
        "docs/planning-artifacts/access-matrix.md",
        "docs/planning-artifacts/decisions-assumptions.md",
        "docs/planning-artifacts/glossary.md",
        "docs/planning-artifacts/deferred-work.md",
    ],
    "02-design-system-and-existing-figma.md": [
        "docs/planning-artifacts/ux/DESIGN.md",
        "docs/planning-artifacts/ux/EXPERIENCE.md",
        "docs/design/figma-inventory.md",
        "docs/design/token-reference.md",
        "docs/design/figma/color-table.md",
        "docs/design/coverage-guide.md",
    ],
    "03-screens-and-interactions.md": [
        "docs/design/screen-catalog.md",
        "docs/design/interaction-catalog.md",
    ],
    "04-pdfs-emails-and-fictional-narrative.md": [
        "docs/design/pdf-email-specifications.md",
        "docs/design/document-content-samples.md",
        "docs/design/synthetic-data.md",
    ],
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path,
                        help="External distribution folder; do not commit generated copies")
    OUT = parser.parse_args().output.resolve()
    if OUT.is_relative_to(ROOT):
        parser.error("Use a folder outside the repository to avoid duplicate sources of truth")
    OUT.mkdir(parents=True, exist_ok=True)
    manifest = {"generated": "2026-11-02", "purpose": "Derived chat attachments; repository sources remain authoritative", "files": {}}
    for name, sources in GROUPS.items():
        chunks = ["# Étaye — Claude chat attachment\n\nGenerated distribution copy. Original repository files named below remain authoritative. These attachments include the named product/design sources, not every internally linked file. No source path grants access to the user's computer. See 00 for bundled versus unbundled context; ask Aymen for missing required content instead of guessing. No application implementation is authorized.\n"]
        for source in sources:
            path = ROOT / source
            chunks.append("\n---\n\n# Source: " + source + "\n\n" + path.read_text())
        (OUT / name).write_text("\n".join(chunks))
        manifest["files"][name] = sources
    (OUT / "05-synthetic-data.json").write_bytes((ROOT / "docs/design/synthetic-data.json").read_bytes())
    (OUT / "06-design-coverage.csv").write_bytes((ROOT / "docs/design/coverage.csv").read_bytes())
    intro = """# Étaye — start here in Claude chat

Attach files 00 through 06 together in the existing Claude chat that began the Étaye design system. Reuse its existing Figma context and access. These are an export of the repository planning package, not new sources of product truth. If access has changed, report the concrete limitation; never claim to have changed Figma without an actual edit.

## Copy-ready prompt

Continue the complete Figma design of Étaye using the attached English specifications and coverage tracker. Speak French with Aymen; preserve French interface copy. Read 00 first, then 01 product/rules, 02 design system, 03 screens/interactions, 04 PDFs/emails, and 05 shared synthetic data. Update 06 coverage with real nodes and opened visual-review evidence, and return it as a file. Inspect the existing Figma file before editing: https://www.figma.com/design/vfh4BSnbRAbaDE1q0CyssL/Etaye?node-id=0-1 . Reuse the original logo and verified foundations. Complete reusable components, then all 40 screen families, 28 interactions, 3 A4 PDF templates and 5 email templates, including required error/restricted/responsive states and navigable practitioner/patient/administration/AI-review-publication prototypes. Read the detailed handoff below for gates and review criteria. Do not begin application implementation. Ask promptly if actual Figma editing access is missing. Do not imply that attaching files supplies account permissions.

## Scope of the attached context

Files 01–06 contain the product/rules/access/assumptions/glossary/deferred register, UX foundations and behavior, screen/interaction/PDF/email catalogs, document samples, synthetic dataset and coverage tracker. The repository handoff below is reproduced as a reference; its filesystem reading order assumes repository access.

This chat kit does not include AGENTS.md, CLAUDE.md, docs/continuation.md, architecture/ADRs/data model/dictionary, AI/security specifications, documentary API contracts, epics/stories, traceability or the review report. The public repository is https://github.com/Aymenchaghoub/etaye . If the chat cannot read a required source there, ask Aymen for that file or an export before making a decision that depends on it. Do not claim repository access, infer omitted contracts or invent missing product rules. The attached catalogs cover design composition; these attachments are not a complete repository export.

## Detailed repository handoff

"""
    (OUT / "00-start-here-and-handoff.md").write_text(intro + (ROOT / "docs/design/handoff-claude.md").read_text())
    generated = [*GROUPS, "00-start-here-and-handoff.md", "05-synthetic-data.json", "06-design-coverage.csv"]
    manifest["sha256"] = {
        name: hashlib.sha256((OUT / name).read_bytes()).hexdigest() for name in sorted(generated)
    }
    (OUT / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print("Exported 7 chat attachments; no credentials or application code included.")


if __name__ == "__main__":
    main()
