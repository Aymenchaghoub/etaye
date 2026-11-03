"""Build the Étaye prototype site from gen/screens_*.py modules.
Each module exposes SCREENS = [{"id": "S-06", "name": "...", "states": [{"slug", "label", "html", "viewports"?}]}].
Output: site/ecrans/<ID>/<slug>.html + site/index.html (+ manifest.json for render.py)."""
import glob, importlib.util, json, os, shutil, sys
import os as _os, sys as _sys; _sys.path.insert(0, _os.path.dirname(_os.path.abspath(__file__))); from paths import ROOT as _ROOT, DS as _DS
HERE = os.path.dirname(os.path.abspath(__file__))
SITE = os.path.join(os.path.dirname(HERE), "site")
sys.path.insert(0, HERE)
import lib

def assets():
    exp = f"{_ROOT}/export/etaye-design-system"
    for d in ("tokens", "fonts", "logos"):
        os.makedirs(f"{SITE}/{d}", exist_ok=True)
        for f in os.listdir(f"{exp}/{d}"):
            shutil.copy(f"{exp}/{d}/{f}", f"{SITE}/{d}/{f}")
    os.makedirs(f"{SITE}/css", exist_ok=True)
    parts = ["ds/bundle-base.css", "ds/bundle-app.css"] + sorted(os.path.relpath(x, _ROOT) for x in glob.glob(f"{_ROOT}/ds/ext-*.css")) + ["icons.css"]
    css = "".join(open(f"{_ROOT}/{p}").read() + "\n" for p in parts)
    open(f"{SITE}/css/etaye.css", "w").write(css)
    open(f"{_DS}/components/bundle.css", "w").write(css)

def load(only=None):
    out = []
    for f in sorted(glob.glob(f"{HERE}/screens_*.py")):
        name = os.path.basename(f)[:-3]
        if only and name not in only: continue
        spec = importlib.util.spec_from_file_location(name, f); m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
        for s in m.SCREENS: s["module"] = name; out.append(s)
    return out

def main(only=None):
    assets()
    screens = load(only)
    man = []
    for s in screens:
        d = f"{SITE}/ecrans/{s['id']}"; os.makedirs(d, exist_ok=True)
        for stt in s["states"]:
            title = stt.get("title") or f"{s['id']} · {s['name']} · {stt['label']}"
            open(f"{d}/{stt['slug']}.html", "w").write(lib.page(title, stt["html"], theme=stt.get("theme", "clair")))
            man.append({"id": s["id"], "name": s["name"], "slug": stt["slug"], "label": stt["label"], "module": s["module"],
                        "path": f"ecrans/{s['id']}/{stt['slug']}.html", "viewports": stt.get("viewports", ["desktop", "tablet", "mobile", "narrow"])})
    os.makedirs(f"{SITE}/manifest", exist_ok=True)
    for mod in sorted({m["module"] for m in man}):
        json.dump([m for m in man if m["module"] == mod], open(f"{SITE}/manifest/{mod}.json", "w"), ensure_ascii=False, indent=1)
    write_index()
    print(len(man), "pages from", len(screens), "families")


AREAS = [("Accès et entrée", ["S-01", "I-26", "S-02", "S-03", "I-03", "S-04", "S-05", "S-36", "S-38", "I-22"]),
         ("Praticien — journée, patients, dossier", ["S-06", "S-07", "S-08", "I-04", "I-20", "S-09", "S-10", "I-27", "S-11", "I-06", "S-12", "I-07", "S-13", "I-08", "I-09"]),
         ("Agenda", ["S-14", "S-15", "I-24", "S-16", "I-10", "I-11", "I-12", "I-13"]),
         ("Préparation, consultation, rédaction et publication", ["S-17", "S-18", "S-19", "S-20", "S-21", "S-22", "S-23", "S-24", "I-05", "I-14", "I-15", "I-16", "I-17", "I-18", "I-21", "S-40", "I-23"]),
         ("Espace patient", ["S-25", "S-26", "S-27", "S-28", "S-29", "S-30", "I-28"]),
         ("Administration et système", ["S-31", "I-01", "S-32", "I-02", "S-33", "S-34", "I-25", "S-35", "S-37", "S-39", "I-19"]),
         ("Documents A4 et e-mails", ["P-01", "P-02", "P-03", "M-01", "M-02", "M-03", "M-04", "M-05"])]


def write_index():
    import glob as g
    man = {}
    for f in g.glob(f"{SITE}/manifest/*.json"):
        for m in json.load(open(f)):
            man.setdefault(m["id"], []).append(m)
    esc = lib.esc
    parts = []
    for area, ids in AREAS:
        rows = []
        for i in ids:
            if i in man:
                st = "".join(f'<li><a href="{m["path"]}">{esc(m["slug"])}</a> <span class="etaye-3" style="font-size:13px">{esc(m["label"])}</span></li>' for m in man[i])
                rows.append(f'<li><strong>{i}</strong> {esc(man[i][0]["name"])}<ul style="margin:4px 0 12px;padding-left:20px">{st}</ul></li>')
            else:
                rows.append(f'<li class="etaye-3"><strong>{i}</strong> · pas encore conçu</li>')
        parts.append(f'<section class="etaye-pile"><h2 class="ui-section">{esc(area)}</h2><ul style="list-style:none;padding:0;margin:0">{"".join(rows)}</ul></section>')
    n = sum(len(v) for v in man.values())
    body = (f'<main class="etaye-page" style="max-width:1100px;margin:0 auto" id="contenu"><header class="etaye-ligne" style="justify-content:space-between">'
            f'<img src="logos/etaye-logo.svg" alt="Étaye" style="height:28px">{lib.demo_notice()}</header>'
            f'<div><h1 style="font:500 28px/36px var(--font-texte);margin:0">Maquettes Étaye</h1>'
            f'<p class="etaye-2" style="margin:8px 0 0">{len(man)} familles · {n} états · données fictives etaye-design-v1. Ajoutez <code>?theme=sombre</code> à une adresse pour le thème sombre. '
            f'Références de conception, pas l\'application ; revue par Claude uniquement, non validée par Aymen ; Figma non importé.</p></div>{"".join(parts)}</main>')
    html = lib.page("Maquettes", body).replace('href="../../', 'href="').replace("../../logos", "logos")
    open(f"{SITE}/index.html", "w").write(html)

if __name__ == "__main__":
    main(sys.argv[1:] or None)
