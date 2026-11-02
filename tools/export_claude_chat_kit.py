#!/usr/bin/env python3
"""Build derived English design attachments for a Claude chat."""
from pathlib import Path
import argparse
import hashlib
import json
import re
import shutil
import subprocess
import tempfile
import zipfile
from datetime import datetime, timezone

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


REPOSITORY = "https://github.com/Aymenchaghoub/etaye"
ARCHIVE = "etaye-claude-resume-attachments.zip"
# Defense in depth for explicit documentation inputs, never arbitrary external folders.
SECRET_PATTERN = re.compile(
    r"(?:figd_[A-Za-z0-9_-]{25,}|gh[pousr]_[A-Za-z0-9]{25,}|"
    r"sk-[A-Za-z0-9]{30,}|-----BEGIN (?:RSA |OPENSSH )?PRIVATE KEY-----)"
)


def reject_secrets(name, content):
    """Fail without printing the matched credential or source content."""
    if SECRET_PATTERN.search(content.decode("utf-8")):
        raise ValueError("Potential credential in export input: " + name)


def build_payload():
    """Read only named repository sources; user files and mockups are never bundled."""
    source_hashes = {}

    def read_source(name):
        path = (ROOT / name).resolve()
        if not path.is_relative_to(ROOT) or not path.is_file():
            raise ValueError("Source must be a repository file: " + name)
        content = path.read_bytes()
        reject_secrets(name, content)
        source_hashes[name] = {"sha256": hashlib.sha256(content).hexdigest()}
        return content

    preamble = (
        "# Étaye — derived Claude design attachment\n\n"
        "Named repository sources remain authoritative. These are specification copies, "
        "not the latest corrected mockups or a complete repository export. Read 00/08 "
        "first; ask for necessary missing sources rather than guessing. "
        "No application implementation is authorized.\n"
    )
    payload = {}
    source_map = {}
    for name, sources in GROUPS.items():
        chunks = [preamble]
        for source in sources:
            chunks.append("\n---\n\n# Source: " + source + "\n\n" + read_source(source).decode("utf-8"))
        payload["attachments/" + name] = "\n".join(chunks).encode("utf-8")
        source_map["attachments/" + name] = sources

    copies = {
        "attachments/05-synthetic-data.json": "docs/design/synthetic-data.json",
        "attachments/06-design-coverage.csv": "docs/design/coverage.csv",
        "attachments/07-review-of-previous-design-bundle.md": "docs/design/external-bundle-review.md",
        "attachments/08-session-recovery-and-next-actions.md": "docs/design/session-recovery.md",
        "PROMPT-TO-PASTE.txt": "docs/design/continuation-prompt.md",
    }
    for target, source in copies.items():
        payload[target] = read_source(source)
        source_map[target] = [source]
    intro = """# Étaye — resume the existing Claude design session

Read attachment 08 and this handoff first. Recover current sources before generating more;
keep any newer tracker and use attachment 06 as a scope snapshot, never a reset command.
Aymen authorized continuing HTML design/prototypes while Figma is unavailable. Track HTML
progress separately and record real Figma nodes only after actual synchronization.

**This kit does not contain the latest corrected mockup source bundle.** Recover it from
the existing Claude chat/artifact or request its latest export. Do not restore an old ZIP
over newer work. No token, recent working mockup, screenshot or approval is implied here.

## Scope and missing context

Files 01–04 bundle the named product/rules/access/assumptions/glossary/deferred, UX/token,
screen/interaction and PDF/email specifications. Files 05/06 are the synthetic dataset and
Figma scope tracker; 07 is the historical bundle review; 08 is the canonical recovery note.
PROMPT-TO-PASTE.txt is the short continuation prompt. manifest.json records the actual
source commit, dirty-tree flag, source hashes and exact payload hashes.

AGENTS.md, CLAUDE.md, docs/continuation.md, architecture/ADRs/data model/dictionary,
AI/security, API contracts, epics/stories, traceability and the review report are not
bundled. Read necessary current files from https://github.com/Aymenchaghoub/etaye or ask
Aymen for them if inaccessible. A filesystem path does not grant chat access. Use the
handoff's reading order only with actual repository access; read relevant sources per
batch rather than loading the entire repository. Do not invent omitted product rules.

## Detailed repository handoff

"""
    target = "attachments/00-start-here-and-handoff.md"
    source = "docs/design/handoff-claude.md"
    payload[target] = intro.encode("utf-8") + read_source(source)
    source_map[target] = [source]
    payload["START-HERE.txt"] = (
        "In the existing Claude chat, attach etaye-claude-resume-attachments.zip and paste "
        "PROMPT-TO-PASTE.txt. If ZIP reading is unavailable, attach files 00–08 instead. "
        "Optional technical references remain in the public repository.\n\n"
        "No latest corrected mockup bundle is included. Recover the actual working sources "
        "before continuing. This is planning/design only.\n\n"
        "Each export uses a new subfolder; previous exports and user files are untouched. "
        "manifest.json hashes all payload files; it excludes itself, the ZIP and SHA256SUMS "
        "to avoid circular hashes. The ZIP contains exactly that payload plus the same "
        "manifest. SHA256SUMS covers the ZIP and external manifest.\n"
    ).encode("utf-8")
    # Identify the generating tool as well as its exported source documents.
    read_source("tools/export_claude_chat_kit.py")
    return payload, source_map, source_hashes


def verify_package(folder):
    """Check delivered files, archive membership/bytes and both integrity layers."""
    manifest_bytes = (folder / "manifest.json").read_bytes()
    manifest = json.loads(manifest_bytes)
    payload_names = set(manifest["files"])
    expected = payload_names | {"manifest.json", ARCHIVE, "SHA256SUMS"}
    actual = {str(p.relative_to(folder)) for p in folder.rglob("*") if p.is_file()}
    if actual != expected:
        raise ValueError("Unexpected or missing package files")
    for name, details in manifest["files"].items():
        if Path(name).is_absolute() or ".." in Path(name).parts or "\\" in name:
            raise ValueError("Unsafe package path")
        path = folder / name
        if path.is_symlink() or not path.resolve().is_relative_to(folder.resolve()):
            raise ValueError("Package payload must not follow symlinks")
        content = path.read_bytes()
        if len(content) != details["bytes"] or hashlib.sha256(content).hexdigest() != details["sha256"]:
            raise ValueError("Payload integrity mismatch: " + name)
        reject_secrets(name, content)
    reject_secrets("manifest.json", manifest_bytes)
    expected_members = payload_names | {"manifest.json"}
    with zipfile.ZipFile(folder / ARCHIVE) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or set(names) != expected_members or archive.testzip():
            raise ValueError("Archive membership or CRC mismatch")
        for name in names:
            if archive.read(name) != (folder / name).read_bytes():
                raise ValueError("Archive differs from delivered file: " + name)
    sums = (folder / "SHA256SUMS").read_text().splitlines()
    expected_sums = [hashlib.sha256((folder / name).read_bytes()).hexdigest() + "  " + name
                     for name in ["manifest.json", ARCHIVE]]
    if sums != expected_sums:
        raise ValueError("Archive/manifest checksum mismatch")
    return manifest


def export_package(output):
    """Create a new versioned child; never overwrite or ingest previous exports."""
    output = output.resolve()
    if output.is_relative_to(ROOT):
        raise ValueError("Use a folder outside the repository")
    payload, source_map, sources = build_payload()
    for name, content in payload.items():
        reject_secrets(name, content)
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    dirty = bool(subprocess.check_output(
        ["git", "status", "--porcelain"], cwd=ROOT, text=True).strip())
    now = datetime.now(timezone.utc)
    manifest = {
        "schemaVersion": 1, "generatedAt": now.isoformat(), "repository": REPOSITORY,
        "sourceCommit": commit, "sourceTreeDirty": dirty, "sourceSha256": sources,
        "latestClaudeSourceBundleIncluded": False,
        "mockupLimitation": "Specification export only; latest corrected Claude mockup sources have not been recovered into this kit.",
        "archive": ARCHIVE,
        "integrity": "files covers payload only; ZIP also contains manifest.json. SHA256SUMS covers the ZIP and manifest (no self-hash).",
        "files": {name: {"bytes": len(content), "sha256": hashlib.sha256(content).hexdigest(),
                         "sources": source_map.get(name, [])}
                  for name, content in sorted(payload.items())},
    }
    output.mkdir(parents=True, exist_ok=True)
    prefix = now.strftime("%Y%m%dT%H%M%SZ") + "-" + commit[:12] + "-"
    # Incomplete output must never look like a deliverable kit, even after interruption.
    folder = Path(tempfile.mkdtemp(prefix=".etaye-resume-incomplete-" + prefix, dir=output))
    try:
        for name, content in payload.items():
            path = folder / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
        (folder / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
        with zipfile.ZipFile(folder / ARCHIVE, "w", zipfile.ZIP_DEFLATED) as archive:
            for name in sorted([*payload, "manifest.json"]):
                archive.write(folder / name, name)
        (folder / "SHA256SUMS").write_text("".join(
            hashlib.sha256((folder / name).read_bytes()).hexdigest() + "  " + name + "\n"
            for name in ["manifest.json", ARCHIVE]))
        verify_package(folder)
        completed = output / folder.name.replace(".etaye-resume-incomplete-", "etaye-resume-", 1)
        if completed.exists():
            raise FileExistsError("Refusing to replace an existing export: " + str(completed))
        return folder.rename(completed)
    except BaseException:
        # Remove only this new staging folder; a cleanup failure leaves it marked incomplete.
        shutil.rmtree(folder, ignore_errors=True)
        raise



def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path,
                        help="External parent folder; always creates a new versioned kit subfolder")
    args = parser.parse_args()
    try:
        folder = export_package(args.output)
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        parser.error(str(exc))
    print(json.dumps({"folder": str(folder), "archive": str(folder / ARCHIVE),
                      "attachments": 9, "verified": True, "latestClaudeSourceBundleIncluded": False}))


if __name__ == "__main__":
    main()
