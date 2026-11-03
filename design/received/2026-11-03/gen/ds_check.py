"""Check every Design System card as the gallery shows it: scripts off, both themes,
brand fonts and wider fallback fonts. Flags overlapping text, horizontal overflow and broken images."""
import glob, os, re, sys, json
import os as _os, sys as _sys; _sys.path.insert(0, _os.path.dirname(_os.path.abspath(__file__))); from paths import ROOT as _ROOT, DS as _DS
from playwright.sync_api import sync_playwright
ROOT = _ROOT; COMP = f"{ROOT}/ds/project/components"; OUT = f"{ROOT}/review/ds-cards"
os.makedirs(OUT, exist_ok=True)
DETECT = r"""() => {
 const rects = []; const w = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
 let n; while ((n = w.nextNode())) { if (!n.textContent.trim()) continue; const el = n.parentElement;
   if (el.closest('[inert]')) continue; if (el.closest('details:not([open])') && !el.closest('summary')) continue; const cs = getComputedStyle(el); if (cs.visibility === 'hidden' || cs.display === 'none') continue;
   const r = document.createRange(); r.selectNodeContents(n);
   for (const b of r.getClientRects()) if (b.width > 1 && b.height > 1) rects.push({b, el, t: n.textContent.trim().slice(0, 40)}); }
 const hits = [];
 for (let i = 0; i < rects.length; i++) for (let j = i + 1; j < rects.length; j++) {
   const a = rects[i], c = rects[j]; if (a.el === c.el) continue;
   const ox = Math.min(a.b.right, c.b.right) - Math.max(a.b.left, c.b.left), oy = Math.min(a.b.bottom, c.b.bottom) - Math.max(a.b.top, c.b.top);
   if (ox > 2 && oy > 3) hits.push(a.t + '  ⟷  ' + c.t); }
 const over = []; document.querySelectorAll('body *').forEach(e => { const cs = getComputedStyle(e);
   if (['visible'].includes(cs.overflowX) && e.scrollWidth > e.clientWidth + 2 && e.clientWidth > 0 && !['TABLE','TBODY','THEAD','TR','svg'].includes(e.tagName) && cs.display !== 'inline')
     over.push(e.tagName.toLowerCase() + '.' + [...e.classList].join('.') + ' +' + (e.scrollWidth - e.clientWidth) + 'px'); });
 const broken = [...document.images].filter(i => i.offsetParent && !(i.complete && i.naturalWidth > 0)).map(i => i.alt);
 const pageOver = document.documentElement.scrollWidth > innerWidth + 1;
 return {hits: hits.slice(0, 12), over: over.slice(0, 8), broken, pageOver};
}"""

def wrap(card, theme):
    return (f'<!doctype html><html data-theme="{theme}"><head><meta charset="utf-8"><link rel="stylesheet" href="file://{ROOT}/site/tokens/tokens.css">'
            f'<link rel="stylesheet" href="file://{ROOT}/site/css/etaye.css"><style>body{{margin:0;background:var(--fond)}}</style></head><body>{card}</body></html>')

def main(only):
    comps = sorted(c for c in os.listdir(COMP) if os.path.exists(f"{COMP}/{c}/preview.html") and (not only or c in only))
    report = {}
    with sync_playwright() as p:
        b = p.chromium.launch()
        for c in comps:
            raw = open(f"{COMP}/{c}/preview.html").read(); first = raw.splitlines()[0]
            w = int((re.search(r"width=(\d+)", first) or [0, 900])[1]); h = int((re.search(r"height=(\d+)", first) or [0, 300])[1])
            res = {}
            for theme in ("clair", "sombre"):
                for fonts in ("brand", "fallback"):
                    ctx = b.new_context(viewport={"width": w, "height": h}, java_script_enabled=False)
                    pg = ctx.new_page()
                    if fonts == "fallback": pg.route("**/*.woff2", lambda r: r.abort())
                    f = f"{OUT}/_{c}.html"; open(f, "w").write(wrap(raw.split("\n", 1)[1], theme))
                    pg.goto(f"file://{f}"); pg.wait_for_timeout(250)
                    ctx2 = pg.evaluate(DETECT) if True else None
                    pg.screenshot(path=f"{OUT}/{c}-{theme}-{fonts}.png", full_page=True)
                    res[f"{theme}/{fonts}"] = ctx2; ctx.close()
            report[c] = res
            bad = {k: v for k, v in res.items() if v["hits"] or v["over"] or v["broken"] or v["pageOver"]}
            print(("!! " if bad else "ok ") + c, json.dumps(bad, ensure_ascii=False)[:600] if bad else "")
        b.close()
    json.dump(report, open(f"{OUT}/report.json", "w"), ensure_ascii=False, indent=1)

if __name__ == "__main__":
    main(sys.argv[1:])
