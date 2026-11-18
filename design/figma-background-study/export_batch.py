#!/usr/bin/env python3
"""Build batches/background-study-v1.json for the Figma development plugin of this folder.

The batch carries the stand-in content of docs/design/background-study/study.html (variant B, adopted tone) at the
four study viewports in light and dark, exported as native text/frame geometry by the existing exporter
(design/figma-local-import/capture.js), plus the two grain tiles and the layer constants. The background layers
themselves are drawn by the plugin from those constants, bound to the file's own color variables.

    python3 design/figma-background-study/export_batch.py            # writes the batch, prints the checks
    python3 design/figma-background-study/export_batch.py --check    # rebuild in memory and compare with the file

Requires Chromium (command line), Pillow and websocket-client. No network beyond 127.0.0.1, no token.
This is design tooling: no application code and no Figma call.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import io
import json
import os
import re
import shutil
import signal
import socket
import subprocess
import sys
import tempfile
import time
import urllib.request
from pathlib import Path

import websocket
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
STUDY_DIR = ROOT / "docs/design/background-study"
sys.path.insert(0, str(STUDY_DIR))
import measure  # noqa: E402  (static server, viewports, constants)

OUT = Path(__file__).resolve().parent / "batches/background-study-v1.json"
CAPTURE_JS = ROOT / "design/figma-local-import/capture.js"
TOKENS_CSS = measure.TOKENS_CSS
FILE_KEY = "vfh4BSnbRAbaDE1q0CyssL"
FORMAT = "etaye-background-study-v1"
THEMES = {"light": "clair", "dark": "sombre"}
BASE = {"light": "surface/papier", "dark": "surface/fond"}  # adopted tone (2026-11-18)
# CSS token -> Figma variable name (README "Latest visual identity"; verified against the Figma collections on 2026-11-17).
FIGMA_NAME = {"fond": "surface/fond", "surface": "surface/surface", "surface-2": "surface/surface-2", "papier": "surface/papier",
              "encre": "texte/encre", "encre-2": "texte/encre-2", "encre-3": "texte/encre-3", "encre-inverse": "texte/encre-inverse",
              "filet": "filet/filet", "filet-controle": "filet/filet-controle", "accent": "accent/accent",
              "accent-fond": "accent/accent-fond", "sur-accent": "accent/sur-accent"}
NAMES = {"desktop": "bureau 1440", "tablet": "tablette 768", "tablet-landscape": "tablette paysage 1024", "mobile": "mobile 390"}
THEME_LABEL = {"light": "clair", "dark": "sombre"}


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def free_port() -> int:
    sock = socket.socket()
    sock.bind(("127.0.0.1", 0))
    port = sock.getsockname()[1]
    sock.close()
    return port


class Chrome:
    """Headless Chromium driven over the DevTools protocol, one page, exact CSS viewport."""

    def __init__(self):
        self.profile = tempfile.mkdtemp(prefix="etaye-bgkit-")
        port = free_port()
        self.proc = subprocess.Popen(
            ["chromium-browser", "--headless=new", "--disable-gpu", "--hide-scrollbars", "--no-first-run", f"--user-data-dir={self.profile}",
             f"--remote-debugging-port={port}", "about:blank"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)
        target = None
        for _ in range(150):
            try:
                pages = json.load(urllib.request.urlopen(f"http://127.0.0.1:{port}/json/list", timeout=2))
                target = next(p for p in pages if p.get("type") == "page")
                break
            except Exception:  # noqa: BLE001 - the debugging port is not open yet
                time.sleep(0.2)
        if not target:
            self.close()
            raise RuntimeError("Chromium did not expose a debugging page")
        self.ws = websocket.create_connection(target["webSocketDebuggerUrl"], suppress_origin=True, timeout=60)
        self.n = 0

    def call(self, method: str, **params):
        self.n += 1
        self.ws.send(json.dumps({"id": self.n, "method": method, "params": params}))
        while True:
            msg = json.loads(self.ws.recv())
            if msg.get("id") == self.n:
                if "error" in msg:
                    raise RuntimeError(f"{method}: {msg['error']}")
                return msg["result"]

    def evaluate(self, expression: str):
        r = self.call("Runtime.evaluate", expression=expression, returnByValue=True, awaitPromise=True)
        if "exceptionDetails" in r:
            raise RuntimeError(f"page script failed: {r['exceptionDetails'].get('text')} {r['exceptionDetails'].get('exception', {}).get('description', '')[:200]}")
        return r["result"].get("value")

    def open(self, url: str, width: int, height: int):
        self.call("Emulation.setDeviceMetricsOverride", width=width, height=height, deviceScaleFactor=1, mobile=False)
        self.call("Page.navigate", url=url)
        for _ in range(200):
            time.sleep(0.1)
            if self.evaluate("document.documentElement.getAttribute('data-ready') || (document.documentElement.localName === 'svg' ? '1' : '')") == "1":
                return
        raise RuntimeError(f"{url}: the page never became ready")

    def close(self):
        try:
            getattr(self, "ws", None) and self.ws.close()
        finally:
            if self.proc.poll() is None:
                os.killpg(self.proc.pid, signal.SIGKILL)
            shutil.rmtree(self.profile, ignore_errors=True)


def read_tokens() -> tuple[dict, dict]:
    """Per theme: {css token: '#rrggbb'} and {'#rrggbb': [Figma names]} (CSS order)."""
    css = TOKENS_CSS.read_text(encoding="utf-8")
    blocks = {"light": re.search(r':root, \[data-theme="clair"\] \{(.*?)\}', css, re.S).group(1),
              "dark": re.search(r'\[data-theme="sombre"\] \{(.*?)\}', css, re.S).group(1)}
    values, names = {}, {}
    for theme, block in blocks.items():
        values[theme] = {k: v for k, v in re.findall(r"--([a-z0-9-]+):\s*(#[0-9a-f]{6})", block)}
        names[theme] = {}
        for token, value in values[theme].items():
            if token in FIGMA_NAME:
                names[theme].setdefault(value, []).append(FIGMA_NAME[token])
    return values, names


def read_constants() -> dict:
    """Layer constants, cross-checked against background.css so the batch cannot drift from the specification."""
    css = (STUDY_DIR / "background.css").read_text(encoding="utf-8")
    grab = lambda pattern: float(re.search(pattern, css).group(1))  # noqa: E731
    k = {"grainOpacity": grab(r"--bg-grain-opacity:\s*(\.?\d+(?:\.\d+)?)"), "tickLight": grab(r"\.etaye-bg \{[^}]*?--bg-tick-opacity:\s*(\.?\d+(?:\.\d+)?)"),
         "tickDark": grab(r'\[data-theme="sombre"\] \.etaye-bg \{ --bg-tick-opacity:\s*(\.?\d+(?:\.\d+)?)'),
         "calmOpacity": grab(r"--bg-calm-opacity:\s*(\.?\d+(?:\.\d+)?)"),
         "ruler": measure.RULER, "band": measure.BAND, "markBox": measure.MARK_BOX, "markInset": measure.MARK_INSET}
    assert (k["grainOpacity"], k["tickLight"], k["tickDark"], k["calmOpacity"]) == (0.12, 0.32, 0.38, 0.8), k
    return k


def plate_geometry(width: int, facts: dict) -> dict:
    """Calm plate of the hero block: solid inset, then a feather; tiers of background.css."""
    inset, feather = (4, 4) if width < 640 else (8, 24) if width < 1100 else (8, 32)
    p = facts["plate"]
    assert p["inflate"] == inset + feather, (p, inset, feather)  # the page itself reports the same reach
    return {"x": p["x"], "y": p["y"], "w": p["w"], "h": p["h"], "inset": inset, "feather": feather}


def clean_scene(node: dict, theme: str) -> dict | None:
    """Drop the study's own background layer and swap the logo images for instances of the original components."""
    if node["name"].startswith("div#bg"):
        return None
    if node["type"] == "image":
        logo = {"logo-light.svg": "Logo/Clair", "logo-dark.svg": "Logo/Sombre"}.get(node["src"].rsplit("/", 1)[-1])
        if not logo:
            raise RuntimeError(f"unexpected image {node['src']}")
        return {"type": "instance", "component": logo, "name": logo, "x": node["x"], "y": node["y"], "w": node["w"], "h": node["h"]}
    if node["type"] not in ("frame", "text"):
        raise RuntimeError(f"unexpected node type {node['type']} ({node['name']})")
    out = dict(node)
    if "children" in node:
        out["children"] = [c for c in (clean_scene(child, theme) for child in node["children"]) if c]
    return out


def make_tile(chrome: Chrome, base_url: str, ink: str) -> tuple[bytes, dict]:
    """256 px tile of the grain: RGB = the theme's ink token, alpha = the rendered alpha of paper-grain.svg."""
    chrome.call("Emulation.setDefaultBackgroundColorOverride", color={"r": 0, "g": 0, "b": 0, "a": 0})
    chrome.open(f"{base_url}/docs/design/background-study/assets/paper-grain.svg", 256, 256)
    shot = chrome.call("Page.captureScreenshot", format="png", clip={"x": 0, "y": 0, "width": 256, "height": 256, "scale": 1})
    chrome.call("Emulation.setDefaultBackgroundColorOverride")
    alpha = Image.open(io.BytesIO(base64.b64decode(shot["data"]))).convert("RGBA").getchannel("A")
    lo, hi = alpha.getextrema()
    assert alpha.size == (256, 256) and lo < hi and hi > 0, ("grain alpha looks wrong", alpha.size, lo, hi)
    colour = tuple(int(ink[i:i + 2], 16) for i in (1, 3, 5))
    tile = Image.merge("RGBA", (*(Image.new("L", (256, 256), c) for c in colour), alpha))
    buf = io.BytesIO()
    tile.save(buf, format="PNG", optimize=True)
    mean = sum(alpha.tobytes()) / (255 * 256 * 256)
    return buf.getvalue(), {"alphaMin": lo, "alphaMax": hi, "alphaMean": round(mean, 5)}


def build() -> tuple[dict, dict]:
    values, names = read_tokens()
    constants = read_constants()
    capture = CAPTURE_JS.read_text(encoding="utf-8")
    server, port = measure.serve()
    chrome = Chrome()
    base_url = f"http://127.0.0.1:{port}"
    try:
        tiles, tile_stats = {}, {}
        for theme in THEMES:
            png, stats = make_tile(chrome, base_url, values[theme]["encre"])
            tiles[theme] = base64.b64encode(png).decode()
            tile_stats[theme] = {**stats, "sha256": sha256(png), "bytes": len(png)}
        frames = []
        for theme, label in THEMES.items():
            for key, (w, h) in measure.VIEWPORTS.items():
                q = f"v=b&t={label}&paper=off&bp=off&calm=off"
                height = h
                for _ in range(2):  # the page is min-height 100vh: reload at the page height when it is taller
                    chrome.open(f"{base_url}/docs/design/background-study/study.html?{q}", w, height)
                    facts = json.loads(chrome.evaluate("document.getElementById('measure-data').textContent"))
                    if (facts["vw"], facts["vh"]) != (w, height):
                        raise RuntimeError(f"viewport {facts['vw']}x{facts['vh']}, expected {w}x{height}")
                    if facts["pageH"] <= height:
                        break
                    height = facts["pageH"]
                scene = chrome.evaluate("(" + capture + ")()")
                root = clean_scene(scene["node"], theme)
                assert round(scene["width"]) == w and round(scene["height"]) == height, (scene["width"], scene["height"], w, height)
                root.pop("fill", None)
                frames.append({"id": f"B-{key}-{theme}", "name": f"Fond B · {NAMES[key]} · {THEME_LABEL[theme]}", "theme": theme, "viewport": key,
                               "width": w, "height": height, "base": BASE[theme], "plate": plate_geometry(w, facts), "scene": {"node": root}})
    finally:
        chrome.close()
        server.shutdown()

    # Grid in the section: light left, dark right, one row per viewport.
    column = max(f["width"] for f in frames) + 160
    y = 0
    for key in measure.VIEWPORTS:
        row = [f for f in frames if f["viewport"] == key]
        for f in row:
            f["x"], f["y"] = (0 if f["theme"] == "light" else column), y
        y += max(f["height"] for f in row) + 160
    files = {str(p.relative_to(ROOT)): sha256(p.read_bytes()) for p in
             (STUDY_DIR / "study.html", STUDY_DIR / "background.css", STUDY_DIR / "assets/paper-grain.svg", TOKENS_CSS, CAPTURE_JS)}
    source = sha256(json.dumps(files, sort_keys=True).encode())
    batch = {"format": FORMAT, "fileKey": FILE_KEY, "sourceSha256": source, "sourceFiles": files, "constants": constants,
             "tokens": {theme: names[theme] for theme in THEMES}, "tiles": tiles, "frames": frames}
    return batch, {"tiles": tile_stats, "tokenValues": values}


def check_tone(batch: dict, info: dict) -> list[str]:
    """Composite the tile at the grain opacity over the adopted base and compare with the measured open-area mean."""
    contrast = json.loads((STUDY_DIR / "results/contrast.json").read_text(encoding="utf-8"))
    report = []
    for theme in THEMES:
        values = info["tokenValues"][theme]
        base = [int(values["papier" if theme == "light" else "fond"][i:i + 2], 16) for i in (1, 3, 5)]
        ink = [int(values["encre"][i:i + 2], 16) for i in (1, 3, 5)]
        mean_a = info["tiles"][theme]["alphaMean"] * batch["constants"]["grainOpacity"]
        predicted = [b + mean_a * (i - b) for b, i in zip(base, ink)]
        cfg = next(c for c in contrast["configs"] if c["variant"] == "b" and c["viewport"] == "desktop" and c["theme"] == theme)
        measured = cfg["paper"]["mean_rgb"]
        worst = max(abs(p - m) for p, m in zip(predicted, measured))
        report.append(f"{theme}: tile composite {[round(v, 2) for v in predicted]} vs measured open-area mean {measured}, largest gap {worst:.2f} levels")
        assert worst < 1.5, report[-1]
    return report


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true", help="rebuild in memory and compare with the committed batch")
    args = ap.parse_args()
    batch, info = build()
    text = json.dumps(batch, ensure_ascii=False, separators=(",", ":")) + "\n"
    for line in check_tone(batch, info):
        print(line)
    for theme, stats in info["tiles"].items():
        print(f"tile {theme}: {stats}")
    if args.check:
        same = OUT.read_text(encoding="utf-8") == text
        print("committed batch is", "identical" if same else "DIFFERENT", "to a fresh export")
        sys.exit(0 if same else 1)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(text, encoding="utf-8")
    nodes = sum(1 for f in batch["frames"] for _ in iter_nodes(f["scene"]["node"]))
    print(f"wrote {OUT.relative_to(ROOT)}: {len(batch['frames'])} frames, {nodes} content nodes, {OUT.stat().st_size} bytes, sha256 {sha256(text.encode())}")


def iter_nodes(node):
    yield node
    for child in node.get("children", []):
        yield from iter_nodes(child)


if __name__ == "__main__":
    main()
