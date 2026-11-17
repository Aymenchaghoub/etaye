#!/usr/bin/env python3
"""Render the background study in Chromium and measure WCAG contrast on real pixels.

For every configuration (variant A/B x light/dark x desktop/tablet/mobile) the study page is
rendered twice: normally (the reviewed PNG) and in "measure" mode, where every glyph, border
and logo is hidden but every fill is kept. The measure render therefore shows what really sits
behind each text or control: the grain, the blueprint margin, an opaque panel or a button fill.
The worst pixel under each element is compared with the element's real colour (WCAG 2.x).

Usage (from anywhere, Chromium and Pillow/NumPy required):
    python3 measure.py --out DIR --json results/contrast.json
    python3 measure.py --sweep --out DIR              # grain opacity sensitivity only

This script measures a design proposal rendered from static HTML. It is not an application
test and no clinical, accessibility-audit or compliance claim follows from it.
"""
from __future__ import annotations

import argparse
import http.server
import json
import math
import re
import socket
import subprocess
import threading
from functools import partial
from urllib.parse import quote
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
FRAME = "docs/design/background-study/frame.html"
MIN_WINDOW = 500  # headless Chromium refuses narrower windows
CHROMIUM = "chromium-browser"
VIEWPORTS = {"desktop": (1440, 1024), "tablet": (768, 1024), "mobile": (390, 844)}
THEMES = {"light": "clair", "dark": "sombre"}
TOKENS_CSS = ROOT / "design/received/2026-11-03/export/etaye-design-system/tokens/tokens.css"
# Blueprint geometry (CSS px), kept in sync with background.css / study.html.
RULER, BAND, MARK_BOX, MARK_INSET = 192, 16, 16, 8


def serve() -> tuple[http.server.ThreadingHTTPServer, int]:
    handler = partial(_Quiet, directory=str(ROOT))
    sock = socket.socket()
    sock.bind(("127.0.0.1", 0))
    port = sock.getsockname()[1]
    sock.close()
    server = http.server.ThreadingHTTPServer(("127.0.0.1", port), handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server, port


class _Quiet(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *args):  # noqa: D401 - silence request logging
        pass


def chromium(args: list[str]) -> str:
    base = [CHROMIUM, "--headless=new", "--disable-gpu", "--hide-scrollbars",
            "--force-device-scale-factor=1", "--virtual-time-budget=4000"]
    return subprocess.run(base + args, capture_output=True, text=True, check=True).stdout


def page_url(port: int, variant: str, theme: str, width: int, height: int, extra: str = "") -> str:
    """URL of frame.html, which hosts the study in an iframe of exactly width x height CSS px."""
    query = quote(f"v={variant}&t={THEMES[theme]}{extra}")
    return f"http://127.0.0.1:{port}/{FRAME}?w={width}&h={height}&q={query}"


def read_layout(port: int, variant: str, theme: str, width: int, height: int) -> dict:
    """Layout facts measured inside the iframe, at a viewport of exactly width x height CSS px."""
    win = [f"--window-size={max(width, MIN_WINDOW)},{height}", "--dump-dom"]
    out = chromium(win + [page_url(port, variant, theme, width, height)])
    found = re.search(r'<script type="application/json" id="measure-data">(.*?)</script>', out, re.S)
    if not found:
        raise RuntimeError("layout data missing: the page did not finish loading")
    data = json.loads(found.group(1))
    if (data["vw"], data["vh"]) != (width, height):
        raise RuntimeError(f"viewport is {data['vw']}x{data['vh']}, expected {width}x{height}")
    return data


def shoot(port: int, variant: str, theme: str, width: int, height: int, path: Path, extra: str = "") -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    chromium([f"--window-size={max(width, MIN_WINDOW)},{height}", f"--screenshot={path}",
              page_url(port, variant, theme, width, height, extra)])
    img = Image.open(path)
    if img.size[1] != height or img.size[0] < width:
        raise RuntimeError(f"{path.name}: expected at least {(width, height)}, got {img.size}")
    if img.size[0] != width:
        img.crop((0, 0, width, height)).save(path)  # window wider than the iframe: keep the iframe only


def srgb_to_linear(c: np.ndarray) -> np.ndarray:
    c = c / 255.0
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)


def luminance(rgb: np.ndarray) -> np.ndarray:
    lin = srgb_to_linear(rgb.astype(float))
    return lin[..., 0] * 0.2126 + lin[..., 1] * 0.7152 + lin[..., 2] * 0.0722


def ratio(l1: np.ndarray | float, l2: np.ndarray | float) -> np.ndarray | float:
    hi, lo = np.maximum(l1, l2), np.minimum(l1, l2)
    return (hi + 0.05) / (lo + 0.05)


def parse_rgb(text: str) -> np.ndarray:
    nums = re.findall(r"[\d.]+", text)
    return np.array([float(n) for n in nums[:3]])


def hex_rgb(value: str) -> np.ndarray:
    value = value.lstrip("#")
    return np.array([int(value[i:i + 2], 16) for i in (0, 2, 4)], dtype=float)


def lab(rgb: np.ndarray) -> np.ndarray:
    lin = srgb_to_linear(np.asarray(rgb, dtype=float))
    m = np.array([[0.4124, 0.3576, 0.1805], [0.2126, 0.7152, 0.0722], [0.0193, 0.1192, 0.9505]])
    xyz = (m @ lin) / np.array([0.95047, 1.0, 1.08883])
    f = np.where(xyz > 216 / 24389, np.cbrt(xyz), (24389 / 27 * xyz + 16) / 116)
    return np.array([116 * f[1] - 16, 500 * (f[0] - f[1]), 200 * (f[1] - f[2])])


def region(img: np.ndarray, x: float, y: float, w: float, h: float, inset: float = 0) -> np.ndarray:
    x0, y0 = max(0, math.floor(x + inset)), max(0, math.floor(y + inset))
    x1, y1 = min(img.shape[1], math.ceil(x + w - inset)), min(img.shape[0], math.ceil(y + h - inset))
    return img[y0:y1, x0:x1].reshape(-1, 3)


def ring(img: np.ndarray, x: float, y: float, w: float, h: float, grow: int = 4) -> np.ndarray:
    mask = np.zeros(img.shape[:2], bool)
    mask[max(0, math.floor(y - grow)):math.ceil(y + h + grow), max(0, math.floor(x - grow)):math.ceil(x + w + grow)] = True
    mask[math.floor(y):math.ceil(y + h), math.floor(x):math.ceil(x + w)] = False
    return img[mask]


def blueprint_zones(width: int, height: int) -> list[tuple[float, float, float, float]]:
    """Drawn blueprint rectangles as (x0, y0, x1, y1) in page px."""
    w, h = width, height
    return [
        (0, 0, BAND, RULER), (0, 0, RULER, BAND),
        (w - BAND, h - RULER, w, h), (w - RULER, h - BAND, w, h),
        (w - MARK_INSET - MARK_BOX, MARK_INSET, w - MARK_INSET, MARK_INSET + MARK_BOX),
        (MARK_INSET, h - MARK_INSET - MARK_BOX, MARK_INSET + MARK_BOX, h - MARK_INSET),
    ]


def rect_gap(a: tuple, b: tuple) -> float:
    dx = max(0.0, a[0] - b[2], b[0] - a[2])
    dy = max(0.0, a[1] - b[3], b[1] - a[3])
    return math.hypot(dx, dy)


def overlaps(a: tuple, b: tuple) -> bool:
    """Strict intersection: two rectangles that merely touch do not overlap."""
    return a[0] < b[2] and b[0] < a[2] and a[1] < b[3] and b[1] < a[3]


def measure_config(port: int, variant: str, theme: str, vp: str, out: Path, grain: str | None = None,
                   keep_png: bool = True) -> dict:
    vw, vh = VIEWPORTS[vp]
    first = read_layout(port, variant, theme, vw, vh)
    height = max(vh, first["pageH"])
    layout = read_layout(port, variant, theme, vw, height) if height != vh else first
    extra = f"&grain={grain}" if grain is not None else ""
    name = f"{variant.upper()}-{vp}-{theme}"
    normal, bgonly = out / f"{name}.png", out / f"{name}-bg-only.png"
    if keep_png:
        shoot(port, variant, theme, vw, height, normal, extra)
    shoot(port, variant, theme, vw, height, bgonly, extra + "&m=1")
    img = np.asarray(Image.open(bgonly).convert("RGB"))
    anchor = next(i for i in layout["items"] if i["id"] == "primary")
    centre = img[int(anchor["y"] + anchor["h"] / 2), int(anchor["x"] + anchor["w"] / 2)]
    if np.abs(centre.astype(float) - parse_rgb(anchor["bg"])).max() > 2:
        raise RuntimeError(f"{name}: layout facts and screenshot are misaligned (primary button check)")

    items, rects = [], []
    for it in layout["items"]:
        if it["w"] > 0 and it["h"] > 0:
            rects.append((it["x"], it["y"], it["x"] + it["w"], it["y"] + it["h"]))
        if it["kind"] == "logo":
            items.append({"id": it["id"], "kind": "logo", "note": "logotype, exempt from contrast criteria"})
            continue
        fg = parse_rgb(it["color"])
        pix = region(img, it["x"], it["y"], it["w"], it["h"], it["inset"])
        large = it["fs"] >= 24 or (it["fs"] >= 18.66 and it["fw"] >= 700)
        need = 3.0 if large else 4.5
        c = ratio(luminance(fg), luminance(pix))
        items.append({"id": it["id"], "kind": "text", "px": it["fs"], "weight": it["fw"], "large": large,
                      "required": need, "min": round(float(c.min()), 2), "p1": round(float(np.percentile(c, 1)), 2),
                      "median": round(float(np.median(c)), 2), "meets_required": bool(c.min() >= need),
                      "meets_4_5": bool(c.min() >= 4.5), "on_opaque_panel": it["panel"]})
        if it["id"] in ("primary", "secondary"):
            edge = parse_rgb(it["bg"] if it["id"] == "primary" else it["border"])
            cb = ratio(luminance(edge), luminance(ring(img, it["x"], it["y"], it["w"], it["h"])))
            items.append({"id": it["id"] + "-boundary", "kind": "control", "required": 3.0,
                          "min": round(float(cb.min()), 2), "median": round(float(np.median(cb)), 2),
                          "meets_required": bool(cb.min() >= 3.0)})

    pan = layout["panel"]
    rects.append((pan["x"], pan["y"], pan["x"] + pan["w"], pan["y"] + pan["h"]))
    zones = blueprint_zones(vw, height)
    gaps = [rect_gap(r, z) for r in rects for z in zones] if variant == "b" else []
    crossings = sum(overlaps(r, z) for r in rects for z in zones) if variant == "b" else 0

    keep = np.ones(img.shape[:2], bool)
    for x0, y0, x1, y1 in rects:
        keep[max(0, math.floor(y0 - 6)):math.ceil(y1 + 6), max(0, math.floor(x0 - 6)):math.ceil(x1 + 6)] = False
    for x0, y0, x1, y1 in blueprint_zones(vw, height):
        keep[max(0, math.floor(y0 - 2)):math.ceil(y1 + 2), max(0, math.floor(x0 - 2)):math.ceil(x1 + 2)] = False
    bg = img[keep].astype(float)
    token = hex_rgb("#f6f4ee" if theme == "light" else "#17181a")
    luma = luminance(bg)
    return {
        "config": name, "variant": variant, "theme": theme, "viewport": vp,
        "window": [vw, height], "items": items,
        "blueprint": ({"overlaps": int(crossings), "min_clearance_px": round(min(gaps), 1)} if gaps else None),
        "paper": {"mean_rgb": [round(v, 2) for v in bg.mean(0)], "token_rgb": token.tolist(),
                  "delta_l_star": round(float(lab(bg.mean(0))[0] - lab(token)[0]), 2),
                  "luminance_min": round(float(luma.min()), 4), "luminance_max": round(float(luma.max()), 4),
                  "rgb_min": bg.min(0).tolist(), "rgb_max": bg.max(0).tolist(),
                  "samples": int(len(bg))},
    }


def token_pairs() -> dict:
    """Static token pairs that never touch the layer (own opaque fills): informational."""
    css = TOKENS_CSS.read_text(encoding="utf-8")
    out = {}
    for theme, selector in (("light", r":root, \[data-theme=\"clair\"\] \{(.*?)\}"),
                            ("dark", r"\[data-theme=\"sombre\"\] \{(.*?)\}")):
        block = re.search(selector, css, re.S).group(1)
        t = {k: v for k, v in re.findall(r"--([a-z0-9-]+):\s*(#[0-9a-f]{6})", block)}
        pairs = {"encre-inverse on encre (primary button)": ("encre-inverse", "encre"),
                 "sur-accent on accent": ("sur-accent", "accent"),
                 "critique on critique-fond": ("critique", "critique-fond"),
                 "attention on attention-fond": ("attention", "attention-fond"),
                 "encre-3 on papier (flat token pair)": ("encre-3", "papier"),
                 "filet-controle on papier (flat token pair)": ("filet-controle", "papier")}
        out[theme] = {label: round(float(ratio(luminance(hex_rgb(t[a])), luminance(hex_rgb(t[b])))), 2)
                      for label, (a, b) in pairs.items()}
    return out


def sweep(port: int, out: Path) -> list[dict]:
    rows = []
    for theme in ("light", "dark"):
        for g in ("0", "0.02", "0.03", "0.035", "0.04", "0.045", "0.05", "0.06", "0.08", "0.10"):
            r = measure_config(port, "a", theme, "desktop", out / "sweep", grain=g, keep_png=False)
            worst = {i["id"]: i["min"] for i in r["items"] if i["kind"] in ("text", "control")}
            rows.append({"theme": theme, "grain": float(g), "delta_l_star": r["paper"]["delta_l_star"],
                         "notice_encre3_min": worst["notice"], "lead_encre2_min": worst["lead"],
                         "tok_accent_min": worst["tok-accent"],
                         "secondary_border_min": worst["secondary-boundary"],
                         "primary_edge_min": worst["primary-boundary"]})
    return rows


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", required=True, type=Path, help="directory for PNG renders (kept outside Git)")
    ap.add_argument("--json", type=Path, help="write the measurements here")
    ap.add_argument("--sweep", action="store_true", help="only run the grain-opacity sensitivity table")
    args = ap.parse_args()
    server, port = serve()
    try:
        if args.sweep:
            print(json.dumps(sweep(port, args.out), indent=1))
            return
        results = [measure_config(port, v, t, vp, args.out)
                   for v in ("a", "b") for t in ("light", "dark") for vp in VIEWPORTS]
        report = {"tool": "measure.py", "browser": subprocess.run([CHROMIUM, "--version"], capture_output=True,
                  text=True).stdout.strip(), "device_scale_factor": 1, "token_pairs": token_pairs(),
                  "configs": results}
        if args.json:
            args.json.parent.mkdir(parents=True, exist_ok=True)
            args.json.write_text(json.dumps(report, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
        fails = [(r["config"], i["id"], i["min"], i["required"]) for r in results for i in r["items"]
                 if i.get("meets_required") is False]
        print(f"{len(results)} configurations measured; failures: {fails or 'none'}")
    finally:
        server.shutdown()


if __name__ == "__main__":
    main()
