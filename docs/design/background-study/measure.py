#!/usr/bin/env python3
"""Render the background study in Chromium and measure WCAG contrast on real pixels.

For every configuration (variant A/B x light/dark x desktop/tablet/tablet-landscape/mobile) the study page is
rendered twice: normally (the reviewed PNG) and in "measure" mode, where every glyph, border
and logo is hidden but every fill is kept. The measure render therefore shows what really sits
behind each text or control: the grain, the blueprint margin, an opaque panel or a button fill.
The worst pixel under each element is compared with the element's real colour (WCAG 2.x).

Usage (from anywhere, Chromium and Pillow/NumPy required):
    python3 measure.py --out DIR --json results/contrast.json
    python3 measure.py --sweep --out DIR              # calm-plate opacity sensitivity only
    python3 measure.py --out DIR --engine firefox --dsf 2 --variants b   # other engine / pixel ratio
    python3 measure.py --out DIR --zoom 2 --viewports desktop,tablet     # 200 % browser zoom
Other engines and settings are driven by engines.py; crossbrowser.py runs the whole matrix.

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
import threading
from functools import partial
from pathlib import Path

import numpy as np
from PIL import Image

import engines

ROOT = Path(__file__).resolve().parents[3]
VIEWPORTS = {"desktop": (1440, 1024), "tablet": (768, 1024), "tablet-landscape": (1024, 768), "mobile": (390, 844)}
# Window sizes in device-independent px. With a browser zoom the page sees window / zoom (CSS viewport).
# Extra viewports are never part of the default run; crossbrowser.py asks for them by name.
EXTRA_VIEWPORTS = {"narrow-320": (320, 568)}  # smallest CSS width of WCAG 2.2 reflow (1.4.10)
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


def measure_config(eng: engines.Engine, variant: str, theme: str, vp: str, out: Path, extra: str = "",
                   keep_png: bool = True, keep_bg: bool = True, dsf: float = 1, zoom: float = 1,
                   tag: str = "") -> dict:
    """Render one configuration and measure it. Layout facts are in CSS px; every pixel index is
    CSS px x S, where S = dsf x zoom is the number of device pixels per CSS px."""
    ww, wh = {**VIEWPORTS, **EXTRA_VIEWPORTS}[vp]  # window, device-independent px
    cw, ch = round(ww / zoom), round(wh / zoom)  # CSS viewport the page sees
    S = dsf * zoom
    q = f"v={variant}&t={THEMES[theme]}{extra}"

    def facts(h: int) -> dict:
        data = eng.layout(q, cw, h, dsf, zoom)
        if (data["vw"], data["vh"]) != (cw, h):
            raise RuntimeError(f"viewport is {data['vw']}x{data['vh']}, expected {cw}x{h}")
        if abs(data["dpr"] - S) > 0.01:
            raise RuntimeError(f"devicePixelRatio is {data['dpr']}, expected {S}")
        return data

    first = facts(ch)
    height = max(ch, first["pageH"])
    layout = facts(height) if height != ch else first
    name = f"{variant.upper()}-{vp}-{theme}{tag}"
    normal, bgonly = out / f"{name}.png", out / f"{name}-bg-only.png"
    if keep_png:
        eng.shoot(q, cw, height, dsf, zoom, normal)
    eng.shoot(q + "&m=1", cw, height, dsf, zoom, bgonly)
    img = np.asarray(Image.open(bgonly).convert("RGB"))
    if not keep_bg:
        bgonly.unlink()
    if img.shape[:2] != (round(height * S), round(cw * S)):
        raise RuntimeError(f"{name}: render is {img.shape[1]}x{img.shape[0]} px, expected {round(cw * S)}x{round(height * S)}")
    anchor = next(i for i in layout["items"] if i["id"] == "primary")
    centre = img[int((anchor["y"] + anchor["h"] / 2) * S), int((anchor["x"] + anchor["w"] / 2) * S)]
    if np.abs(centre.astype(float) - parse_rgb(anchor["bg"])).max() > 2:
        raise RuntimeError(f"{name}: layout facts and screenshot are misaligned (primary button check)")

    def px(r: tuple) -> tuple:
        return tuple(v * S for v in r)

    items, rects = [], []                        # rects: CSS px
    for it in layout["items"]:
        if it["w"] > 0 and it["h"] > 0:
            rects.append((it["x"], it["y"], it["x"] + it["w"], it["y"] + it["h"]))
        if it["kind"] == "logo":
            items.append({"id": it["id"], "kind": "logo", "note": "logotype, exempt from contrast criteria"})
            continue
        fg = parse_rgb(it["color"])
        pix = region(img, it["x"] * S, it["y"] * S, it["w"] * S, it["h"] * S, it["inset"] * S)
        large = it["fs"] >= 24 or (it["fs"] >= 18.66 and it["fw"] >= 700)
        need = 3.0 if large else 4.5
        c = ratio(luminance(fg), luminance(pix))
        items.append({"id": it["id"], "kind": "text", "px": it["fs"], "weight": it["fw"], "large": large,
                      "required": need, "min": round(float(c.min()), 2), "p1": round(float(np.percentile(c, 1)), 2),
                      "median": round(float(np.median(c)), 2), "meets_required": bool(c.min() >= need),
                      "meets_4_5": bool(c.min() >= 4.5), "on_opaque_panel": it["panel"]})
        if it["id"] in ("primary", "secondary"):
            edge = parse_rgb(it["bg"] if it["id"] == "primary" else it["border"])
            cb = ratio(luminance(edge), luminance(ring(img, it["x"] * S, it["y"] * S, it["w"] * S, it["h"] * S, 4 * S)))
            items.append({"id": it["id"] + "-boundary", "kind": "control", "required": 3.0,
                          "min": round(float(cb.min()), 2), "median": round(float(np.median(cb)), 2),
                          "meets_required": bool(cb.min() >= 3.0)})

    pan = layout["panel"]
    rects.append((pan["x"], pan["y"], pan["x"] + pan["w"], pan["y"] + pan["h"]))
    pl = layout["plate"]
    plate_rect = (pl["x"], pl["y"], pl["x"] + pl["w"], pl["y"] + pl["h"])
    reach = (plate_rect[0] - pl["inflate"], plate_rect[1] - pl["inflate"],
             plate_rect[2] + pl["inflate"], plate_rect[3] + pl["inflate"])  # plate plus its feather
    rects.append(reach)
    zones = blueprint_zones(cw, height)
    gaps = [rect_gap(r, z) for r in rects for z in zones] if variant == "b" else []
    crossings = sum(overlaps(r, z) for r in rects for z in zones) if variant == "b" else 0

    def clip(r: tuple, pad: float) -> tuple[slice, slice]:
        x0, y0, x1, y1 = px(r)
        return (slice(max(0, math.floor(y0 - pad * S)), math.ceil(y1 + pad * S)),
                slice(max(0, math.floor(x0 - pad * S)), math.ceil(x1 + pad * S)))

    keep = np.ones(img.shape[:2], bool)
    for r in rects:
        keep[clip(r, 6)] = False
    for z in zones:
        keep[clip(z, 2)] = False
    open_keep = keep.copy()
    open_keep[clip(reach, 0)] = False
    bg = img[open_keep].astype(float)
    calm_keep = np.zeros(img.shape[:2], bool)
    calm_keep[clip(plate_rect, 0)] = True
    for r in rects[:-1]:  # drop text and panel boxes (the last rect is the plate reach)
        calm_keep[clip(r, 3)] = False
    calm = img[calm_keep].astype(float)
    token = hex_rgb("#f6f4ee" if theme == "light" else "#17181a")
    luma = luminance(bg)

    def tone(mean_rgb: np.ndarray) -> dict:
        d = lab(mean_rgb) - lab(token)
        return {"delta_l_star": round(float(d[0]), 2), "delta_a_star": round(float(d[1]), 2),
                "delta_b_star": round(float(d[2]), 2), "delta_e_ab": round(float(np.linalg.norm(d)), 2)}

    return {
        "config": name, "variant": variant, "theme": theme, "viewport": vp,
        "window": [cw, height], "dsf": dsf, "zoom": zoom, "items": items,
        "blueprint": ({"overlaps": int(crossings), "min_clearance_px": round(min(gaps), 1)} if gaps else None),
        "calm": ({"mean_rgb": [round(v, 2) for v in calm.mean(0)], **tone(calm.mean(0)),
                  "rgb_min": calm.min(0).tolist(), "rgb_max": calm.max(0).tolist(), "samples": int(len(calm))}
                 if len(calm) else None),
        "plate_step": ({k: round(float(v), 2) for k, v in zip(
            ("delta_l_star", "delta_a_star", "delta_b_star"), lab(bg.mean(0)) - lab(calm.mean(0)))}
            | {"delta_e_ab": round(float(np.linalg.norm(lab(bg.mean(0)) - lab(calm.mean(0)))), 2)}
            if len(calm) else None),  # tone step between the open area and the calm plate (CIELAB)
        "paper": {"mean_rgb": [round(v, 2) for v in bg.mean(0)], "token_rgb": token.tolist(), **tone(bg.mean(0)),
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


def sweep(eng: engines.Engine, out: Path) -> list[dict]:
    """Calm-plate opacity sensitivity at the working grain (12 %), plus the no-plate baseline."""
    rows = []
    for theme in ("light", "dark"):
        for label, extra in (("no plates", "&calm=off"), ("0.5", "&calmop=0.5"), ("0.6", "&calmop=0.6"),
                             ("0.7", "&calmop=0.7"), ("0.75", "&calmop=0.75"), ("0.8", "&calmop=0.8"),
                             ("0.9", "&calmop=0.9")):
            r = measure_config(eng, "b", theme, "desktop", out / "sweep", extra=extra, keep_png=False)
            worst = {i["id"]: i["min"] for i in r["items"] if i["kind"] in ("text", "control")}
            rows.append({"theme": theme, "plate_opacity": label, "open_dl_star": r["paper"]["delta_l_star"],
                         "calm_dl_star": (r["calm"] or {}).get("delta_l_star"),
                         "notice_encre3_min": worst["notice"], "lead_encre2_min": worst["lead"],
                         "tok_accent_min": worst["tok-accent"], "secondary_border_min": worst["secondary-boundary"]})
    return rows


def failures(results: list[dict]) -> list[tuple]:
    return [(r["config"], i["id"], i["min"], i["required"]) for r in results for i in r["items"]
            if i.get("meets_required") is False]


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", required=True, type=Path, help="directory for PNG renders (kept outside Git)")
    ap.add_argument("--json", type=Path, help="write the measurements here")
    ap.add_argument("--sweep", action="store_true", help="only run the calm-plate opacity sensitivity table")
    ap.add_argument("--engine", choices=sorted(engines.ENGINES), default="chromium")
    ap.add_argument("--dsf", type=float, default=1, help="display scale factor (1 or 2)")
    ap.add_argument("--zoom", type=float, default=1, help="browser page zoom (2 = 200 %%)")
    ap.add_argument("--variants", default="a,b")
    ap.add_argument("--viewports", default=",".join(VIEWPORTS))
    ap.add_argument("--themes", default="light,dark")
    ap.add_argument("--extra", default="", help="extra study query, for example &tone=neutral")
    ap.add_argument("--tag", default="", help="suffix for PNG names")
    ap.add_argument("--no-bg-png", action="store_true", help="do not keep the background-only renders")
    args = ap.parse_args()
    server, port = serve()
    try:
        with engines.ENGINES[args.engine](f"http://127.0.0.1:{port}") as eng:
            if args.sweep:
                print(json.dumps(sweep(eng, args.out), indent=1))
                return
            results = [measure_config(eng, v, t, vp, args.out, extra=args.extra, dsf=args.dsf, zoom=args.zoom,
                                      tag=args.tag, keep_bg=not args.no_bg_png)
                       for v in args.variants.split(",") for t in args.themes.split(",")
                       for vp in args.viewports.split(",")]
            report = {"tool": "measure.py", "browser": eng.version(), "device_scale_factor": args.dsf,
                      "zoom": args.zoom, "extra_query": args.extra, "token_pairs": token_pairs(), "configs": results}
        if args.json:
            args.json.parent.mkdir(parents=True, exist_ok=True)
            args.json.write_text(json.dumps(report, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"{len(results)} configurations measured; failures: {failures(results) or 'none'}")
    finally:
        server.shutdown()


if __name__ == "__main__":
    main()
