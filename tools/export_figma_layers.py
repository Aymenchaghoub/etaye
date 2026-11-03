#!/usr/bin/env python3
"""Export corrected offline design into batches for the native editable-layer Figma plugin."""

import argparse
import base64
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import shutil
from urllib.parse import unquote, urlparse

from playwright.sync_api import sync_playwright

REPO = Path(__file__).resolve().parents[1]
PLUGIN = REPO / "design/figma-local-import"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--batch-size", type=int, default=8)
    parser.add_argument("--limit", type=int, help="Explicit partial smoke export only")
    args = parser.parse_args()
    root = args.root.resolve()
    site = root / "site"
    out = args.output.expanduser().resolve()
    if out.is_relative_to(REPO) or out.is_relative_to(root) or root.is_relative_to(out):
        parser.error("Choose a new external folder disjoint from source and repository")
    if not 1 <= args.batch_size <= 20 or (args.limit is not None and args.limit < 1):
        parser.error("batch-size must be 1..20; limit must be positive")
    snapshot = json.loads((root / "review/corrected-snapshot.json").read_text())
    expected = {entry["path"]: entry["sha256"] for entry in snapshot["files"]}
    actual = {
        str(p.relative_to(root)): digest(p) for p in site.rglob("*") if p.is_file()
    }
    if actual != expected:
        parser.error(
            "The complete corrected snapshot has changed; rebuild/reverify first"
        )
    states = [
        m
        for f in sorted((site / "manifest").glob("*.json"))
        for m in json.loads(f.read_text())
    ]
    states.sort(key=lambda m: (m["id"], m["slug"]))
    if args.limit:
        states = states[: args.limit]
    out.mkdir(parents=True, exist_ok=False)
    shutil.copytree(
        PLUGIN, out / "plugin", ignore=shutil.ignore_patterns("__pycache__")
    )
    batches = out / "batches"
    batches.mkdir()
    captured = []

    def assets(node):
        if node["type"] == "image":
            parsed = urlparse(node["src"])
            if parsed.scheme != "file":
                raise ValueError("Only local synthetic design assets are allowed")
            path = Path(unquote(parsed.path)).resolve()
            if not path.is_relative_to(site) or not path.is_file():
                raise ValueError("Asset outside the frozen site")
            data = path.read_bytes()
            if path.suffix.lower() == ".svg":
                node["type"] = "svg"
                node["svg"] = data.decode()
            else:
                # Only discrete content images (for example a real PDF-page preview), never whole UI screenshots.
                node["base64"] = base64.b64encode(data).decode()
            del node["src"]
        for child in node.get("children", []):
            assets(child)

    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page(viewport={"width": 1440, "height": 1024})
        capture = (PLUGIN / "capture.js").read_text()
        for index, state in enumerate(states, 1):
            page.goto((site / state["path"]).as_uri())
            page.evaluate("document.fonts.ready")
            page.evaluate(
                "async()=>Promise.all([...document.images].map(async i=>{i.loading='eager';try{await i.decode()}catch{}}))"
            )
            scene = page.evaluate(capture)
            if not scene.get("node"):
                raise ValueError("Empty rendered state " + state["path"])
            assets(scene["node"])
            captured.append(
                {
                    "id": state["id"] + "/" + state["slug"],
                    "name": state["name"],
                    "viewport": "desktop1440",
                    "scene": scene,
                }
            )
            if len(captured) == args.batch_size or index == len(states):
                name = f"batch-{(index - 1) // args.batch_size + 1:03d}.json"
                (batches / name).write_text(
                    json.dumps(
                        {
                            "format": "etaye-editable-design-v1",
                            "snapshotSha256": snapshot["snapshotSha256"],
                            "frames": captured,
                        },
                        ensure_ascii=False,
                    )
                )
                print(name, index, "/", len(states), flush=True)
                captured = []
        browser.close()
    files = [
        {"path": str(f.relative_to(out)), "sha256": digest(f)}
        for f in sorted(out.rglob("*"))
        if f.is_file()
    ]
    manifest = {
        "createdAt": datetime.now(timezone.utc).isoformat(),
        "snapshotSha256": snapshot["snapshotSha256"],
        "states": len(states),
        "partial": bool(args.limit),
        "viewport": "desktop1440",
        "figmaImported": False,
        "aymenApproval": False,
        "files": files,
    }
    (out / "MANIFEST.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(
        "Export complete. Native Figma execution and visual reconciliation remain unverified."
    )


if __name__ == "__main__":
    main()
