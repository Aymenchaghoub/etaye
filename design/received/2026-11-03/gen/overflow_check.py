"""Horizontal overflow check at 320, 390 and 1024 px. Usage: python3 overflow_check.py screens_brief [screens_consult ...]"""
import json, sys
from playwright.sync_api import sync_playwright
import os, sys as _s; _s.path.insert(0, os.path.dirname(os.path.abspath(__file__))); from paths import ROOT
SITE = f"{ROOT}/site"
mods = sys.argv[1:]
pages = [m for mod in mods for m in json.load(open(f"{SITE}/manifest/{mod}.json"))]
JS = """() => { const W = document.documentElement.clientWidth; const out = [];
 if (document.documentElement.scrollWidth > W + 1) out.push('page scrollWidth ' + document.documentElement.scrollWidth);
 for (const e of document.querySelectorAll('body *')) { if (e.closest('[inert]')) continue; const r = e.getBoundingClientRect();
   if (r.width && r.right > W + 1 && getComputedStyle(e).position !== 'fixed') { out.push((e.className||e.tagName).toString().slice(0,50) + ' right=' + Math.round(r.right)); if (out.length > 4) break; } }
 return out; }"""
with sync_playwright() as p:
    b = p.chromium.launch(); 
    for vw in (320, 390, 1024):
        pg = b.new_page(viewport={"width": vw, "height": 800}, java_script_enabled=True)
        for m in pages:
            pg.goto(f"file://{SITE}/{m['path']}"); r = pg.evaluate(JS)
            if r: print(vw, m['id'], m['slug'], r[:3])
        pg.close()
    b.close()
print("checked", len(pages))
