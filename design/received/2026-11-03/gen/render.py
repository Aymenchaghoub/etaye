"""Render prototype pages at review viewports and build per-state contact sheets.
Usage: python3 render.py [screen-id-or-module ...]   (no args = everything in site/manifest.json)
Outputs review/<ID>/<slug>@<vp>.png (full page, capped) and review/<ID>/<slug>-planche.png."""
import json, os, sys
import os as _os, sys as _sys; _sys.path.insert(0, _os.path.dirname(_os.path.abspath(__file__))); from paths import ROOT as _ROOT, DS as _DS
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ds_check import DETECT
from playwright.sync_api import sync_playwright
from PIL import Image
ROOT = _ROOT; SITE = f"{ROOT}/site"; OUT = f"{ROOT}/review"
VP = {"desktop": (1440, 1024), "tablet": (1024, 768), "mobile": (390, 844), "narrow": (320, 720), "a4": (794, 1123), "mail": (680, 900), "mail-narrow": (320, 800), "zoom": (720, 512)}
CAP = 4000

def sheet(paths, out):
    ims = [Image.open(p) for p in paths]
    widths = {"desktop": 720, "tablet": 512, "mobile": 300, "narrow": 246, "a4": 600, "mail": 500, "mail-narrow": 246, "zoom": 400}
    scaled = []
    for p, im in zip(paths, ims):
        vp = p.rsplit("@", 1)[1][:-4]
        w = widths.get(vp, 400); r = w / im.width
        scaled.append(im.resize((w, int(im.height * r))))
    H = min(max(i.height for i in scaled), 1600); W = sum(i.width for i in scaled) + 16 * (len(scaled) + 1)
    S = Image.new("RGB", (W, H + 16), (150, 150, 150)); x = 16
    for i in scaled:
        S.paste(i.crop((0, 0, i.width, min(i.height, H))), (x, 8)); x += i.width + 16
    S.save(out)

def variantes(b, m, d):
    """Dark theme and wider fallback fonts, scripts disabled, desktop + mobile."""
    src = f"{SITE}/{m['path']}"; html = open(src).read()
    tmp = src[:-5] + ".__sombre.html"; open(tmp, "w").write(html.replace('data-theme="clair"', 'data-theme="sombre"', 1))
    shots = []
    for mode in ("sombre", "fallback"):
        for vp in ("desktop", "mobile"):
            w, h = VP[vp]
            ctx = b.new_context(viewport={"width": w, "height": h}, java_script_enabled=False)
            pg = ctx.new_page()
            if mode == "fallback": pg.route("**/*.woff2", lambda r: r.abort())
            pg.goto(f"file://{tmp if mode == 'sombre' else src}"); pg.wait_for_timeout(200)
            full = pg.evaluate("document.documentElement.scrollHeight")
            out = f"{d}/{m['slug']}~{mode}@{vp}.png"
            pg.screenshot(path=out, full_page=True, clip={"x": 0, "y": 0, "width": w, "height": min(max(full, h), CAP)})
            r = pg.evaluate(DETECT)
            if r["hits"]: print("  ! text overlap", m["id"], m["slug"], mode, vp, " | ".join(r["hits"][:3]))
            if r["pageOver"]: print("  ! horizontal overflow", m["id"], m["slug"], mode, vp)
            shots.append(out); ctx.close()
    os.remove(tmp)
    sheet([x.replace("~", "@~") if False else x for x in shots], f"{d}/{m['slug']}-variantes.png")


def main(filters):
    import glob
    var = "--variantes" in filters
    filters = [f for f in filters if f != "--variantes"]
    man = [m for f in sorted(glob.glob(f"{SITE}/manifest/*.json")) for m in json.load(open(f))]
    sel = [m for m in man if not filters or m["id"] in filters or m["module"] in filters]
    with sync_playwright() as p:
        b = p.chromium.launch()
        for m in sel:
            d = f"{OUT}/{m['id']}"; os.makedirs(d, exist_ok=True); shots = []
            for vp in m["viewports"]:
                w, h = VP[vp]
                ctx = b.new_context(viewport={"width": w, "height": h}, java_script_enabled=False)
                pg = ctx.new_page()
                pg.goto(f"file://{SITE}/{m['path']}"); pg.wait_for_timeout(150)
                full = pg.evaluate("document.documentElement.scrollHeight")
                out = f"{d}/{m['slug']}@{vp}.png"
                pg.screenshot(path=out, full_page=True, clip={"x": 0, "y": 0, "width": w, "height": min(max(full, h), CAP)})
                # overflow check: horizontal scroll means a reflow failure
                ov = pg.evaluate("document.documentElement.scrollWidth > window.innerWidth + 1")
                if ov: print("  ! horizontal overflow", m["id"], m["slug"], vp)
                if vp in ("desktop", "tablet", "mobile", "narrow"):
                    r = pg.evaluate(DETECT)
                    if r["hits"]: print("  ! text overlap", m["id"], m["slug"], vp, " | ".join(r["hits"][:4]))
                    if r["broken"]: print("  ! broken image", m["id"], m["slug"], vp, r["broken"])
                shots.append(out); ctx.close()
            sheet(shots, f"{d}/{m['slug']}-planche.png")
            if var and not set(m["viewports"]) & {"a4", "mail", "mail-narrow"}:
                variantes(b, m, d)
        b.close()
    print("rendered", len(sel), "states")

if __name__ == "__main__":
    main(sys.argv[1:])
