#!/usr/bin/env python3
"""Package the approved entry background for one offline Figma Desktop run."""
import argparse
import base64
import hashlib
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from io import BytesIO
import json
from pathlib import Path
import shutil
import subprocess
import sys
from tempfile import TemporaryDirectory
from threading import Thread

from PIL import Image
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
ENTRY = HERE.parent
BASELINE = ROOT / "docs/design/reviews/2026-11-18-native-closure-final.json"
ARTWORK_ONLY = """
body * { visibility: hidden !important; }
body > svg, body > svg *, .etaye-bg, .etaye-bg *,
.etaye-calm::before { visibility: visible !important; }
"""


class QuietHandler(SimpleHTTPRequestHandler):
    def log_message(self, *_args):
        pass


def render_artwork(site, roots):
    server = ThreadingHTTPServer(("127.0.0.1", 0), partial(QuietHandler, directory=site))
    Thread(target=server.serve_forever, daemon=True).start()
    origin = f"http://127.0.0.1:{server.server_port}"
    artwork = {}
    states = []
    try:
        with sync_playwright() as playwright:
            browser = playwright.chromium.launch()
            page = browser.new_page(viewport={"width": 1440, "height": 1024})
            for root in roots:
                url = f"{origin}/ecrans/{root['state']}.html?theme=clair"
                assert page.goto(url).ok, url
                page.evaluate("document.fonts.ready")
                host = page.locator("[data-entry-background]")
                box = host.bounding_box()
                paper = host.locator(".etaye-bg__paper")
                mask_url = paper.evaluate("node => getComputedStyle(node, '::after').maskImage")
                assert "paper-grain.svg" in mask_url, url
                page.add_style_tag(content=ARTWORK_ONLY)
                png = page.screenshot(clip=box, animations="disabled")
                w, h = Image.open(BytesIO(png)).size
                assert (w, h) == (box["width"], box["height"]), (url, box, w, h)
                sha256 = hashlib.sha256(png).hexdigest()
                artwork.setdefault(sha256, base64.b64encode(png).decode())
                states.append({
                    "root": root,
                    "type": host.get_attribute("data-entry-background"),
                    "width": w,
                    "height": h,
                    "image": sha256,
                })
            browser.close()
    finally:
        server.shutdown()
        server.server_close()
    return states, artwork


def build(output):
    baseline = json.loads(BASELINE.read_text(encoding="utf-8"))
    snapshot = json.loads((ROOT / "design/offline-reference/snapshot.json").read_text(encoding="utf-8"))
    assert baseline["sourceSnapshot"] == snapshot["snapshotSha256"]
    assert len(baseline["roots"]) == 456
    roots = sorted(
        (node for node in baseline["roots"] if node["state"].split("/")[0] in {"S-01", "S-02", "S-25"}),
        key=lambda node: ({"S-25": 0, "S-02": 1, "S-01": 2}[node["state"].split("/")[0]], node["state"]),
    )
    assert len(roots) == 20 and len({node["nodeId"] for node in roots}) == 20
    with TemporaryDirectory(prefix="etaye-entry-figma-") as temp:
        site = Path(temp) / "site"
        subprocess.run([sys.executable, str(ENTRY / "build.py"), "--output", str(site)], check=True)
        states, artwork = render_artwork(site, roots)

    scope = {key: baseline[key] for key in ("fileKey", "pageId", "protectedPages", "finalVersion", "sourceSnapshot")}
    packet = {"scope": scope, "states": states, "artwork": artwork}
    packet_bytes = (json.dumps(packet, ensure_ascii=False, separators=(",", ":")) + "\n").encode()
    helpers = (ROOT / "design/figma-windows-import/code.js").read_text(encoding="utf-8")
    start = helpers.index("const stable =")
    end = helpers.index("\n}", helpers.index("function nativeFingerprint(")) + 2
    code = helpers[start:end] + "\n" + (HERE / "runtime.js").read_text(encoding="utf-8")

    output.mkdir(parents=True, exist_ok=False)
    (output / "code.js").write_text(code, encoding="utf-8")
    shutil.copyfile(HERE / "ui.html", output / "ui.html")
    shutil.copyfile(HERE / "README.md", output / "README.md")
    (output / "entry-background.json").write_bytes(packet_bytes)
    template = json.loads((ROOT / "design/figma-background-study/manifest.template.json").read_text(encoding="utf-8"))
    template["name"] = "Étaye — Fond des écrans d’entrée"
    (output / "manifest.template.json").write_text(json.dumps(template, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (output / "SHA256SUMS.txt").write_text("".join(
        f"{hashlib.file_digest(path.open('rb'), 'sha256').hexdigest()}  {path.name}\n"
        for path in sorted(output.iterdir())
    ), encoding="utf-8")
    print(f"{output}: {len(states)} states, {len(artwork)} decorative images, {len(packet_bytes):,} packet bytes")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    opts = parser.parse_args()
    output = opts.output.resolve()
    if output.is_relative_to(ROOT) or output.exists():
        parser.error("Use a new output folder outside the repository")
    build(output)
