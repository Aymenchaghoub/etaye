#!/usr/bin/env python3
"""Rebuild the received design in a new external folder; never modify the reference."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

REPO = Path(__file__).resolve().parents[1]
SOURCE = REPO / "design/received/2026-11-03"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--output", type=Path, required=True, help="External parent folder"
    )
    parser.add_argument(
        "--pdf", action="store_true", help="Also run the received PDF generator"
    )
    parser.add_argument(
        "--corrected",
        action="store_true",
        help="Apply the separate reviewed design overlay",
    )
    args = parser.parse_args()
    parent = args.output.expanduser().resolve()
    if parent.is_relative_to(REPO):
        parser.error("Use an external output folder to preserve the source reference")
    if args.corrected and not args.pdf:
        parser.error(
            "--corrected requires --pdf: corrected pages link to generated PDFs and previews"
        )
    if sys.version_info < (3, 12):
        parser.error("The received generator needs Python 3.12 or newer")
    provenance = json.loads((SOURCE / "provenance.json").read_text())
    for entry in provenance["files"]:
        path = SOURCE / entry["path"]
        if hashlib.sha256(path.read_bytes()).hexdigest() != entry["sha256"]:
            raise ValueError(f"Received reference changed: {entry['path']}")
    parent.mkdir(parents=True, exist_ok=True)
    work = Path(tempfile.mkdtemp(prefix="etaye-design-rebuild-", dir=parent))
    tree = work / "etaye-design"
    shutil.copytree(SOURCE, tree, ignore=shutil.ignore_patterns("__pycache__"))
    (tree / "design-system/components").mkdir(parents=True)
    # The receipt used pre-existing page previews. A second build discovers newly rendered PDFs.
    correction = REPO / "design/corrections/apply.py"
    steps = ["build.py"]
    if args.corrected:
        steps += [correction]
    if args.pdf:
        steps += ["pdf.py", "build.py"]
        if args.corrected:
            steps += [correction]
    steps += ["parcours.py", "lint.py", "tracker.py"]
    if args.corrected:
        steps += [REPO / "design/corrections/finalize.py"]
    log = work / "rebuild.log"
    environment = os.environ.copy()
    environment.pop("ETAYE_ROOT", None)
    try:
        with log.open("w") as output:
            for step in steps:
                output.write(f"Running {step}\n")
                output.flush()
                command = (
                    [sys.executable, str(step), "--root", str(tree)]
                    if isinstance(step, Path)
                    else [sys.executable, step]
                )
                subprocess.run(
                    command,
                    cwd=tree / "gen",
                    check=True,
                    stdout=output,
                    stderr=subprocess.STDOUT,
                    env=environment,
                )
    except BaseException:
        # Preserve diagnostic output, but never label a failed build as complete.
        (work / "INCOMPLETE.txt").write_text(
            "Reconstruction failed. Inspect rebuild.log.\n"
        )
        raise
    (work / "REBUILD.json").write_text(
        json.dumps(
            {
                "archiveSha256": provenance["archiveSha256"],
                "pdfsGenerated": args.pdf,
                "correctionOverlayApplied": args.corrected,
                "checksAreNonBlockingReceivedScripts": True,
                "referenceFilesVerified": len(provenance["files"]),
                "entry": "etaye-design/site/parcours.html",
                "designApproved": False,
                "figmaImported": False,
            },
            indent=2,
        )
        + "\n"
    )
    print(tree / "site/parcours.html")
    print(
        "Rebuilt design only; run independent verification before accepting any checker result."
    )


if __name__ == "__main__":
    main()
