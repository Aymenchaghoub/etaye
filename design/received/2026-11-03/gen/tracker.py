"""Write review/html-design-progress.csv: HTML design progress, separate from Figma coverage.

Rows: every S/I/P/M ID from spec/06-coverage.csv (scope baseline, read-only).
Columns follow the coverage guide's "HTML work while Figma is unavailable" section.
Evidence comes ONLY from review/evidence.json (written by hand after captures were actually opened)
and site/manifest/*.json (states that were actually generated). Nothing is inferred.
"""
import csv, glob, json, os, hashlib
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
SITE, REVIEW = f"{ROOT}/site", f"{ROOT}/review"

scope = list(csv.DictReader(open(f"{HERE}/spec/06-coverage.csv")))
states = {}
for f in glob.glob(f"{SITE}/manifest/*.json"):
    for m in json.load(open(f)):
        states.setdefault(m["id"], []).append(m)
ev = json.load(open(f"{REVIEW}/evidence.json")) if os.path.exists(f"{REVIEW}/evidence.json") else {}


def src_version(rows):
    h = hashlib.sha256()
    for m in sorted(rows, key=lambda x: x["path"]):
        p = f"{SITE}/{m['path']}"
        if os.path.exists(p):
            h.update(open(p, "rb").read())
    return h.hexdigest()[:12] if rows else ""


cols = ["id", "kind", "name", "html_status", "source_module", "source_version", "entry_route", "variants",
        "viewports", "themes", "fonts", "scripts", "opened_captures", "visual_findings", "functional_findings",
        "reviewer", "reviewed_at", "figma_sync", "aymen_feedback"]
out = []
for r in scope:
    i = r["id"]
    rows = states.get(i, [])
    e = ev.get(i, {})
    status = e.get("status") or ("Generated, not yet reviewed" if rows else "Not started")
    out.append({
        "id": i, "kind": r["kind"], "name": r["name"], "html_status": status,
        "source_module": rows[0]["module"] if rows else "",
        "source_version": src_version(rows),
        "entry_route": rows[0]["path"] if rows else "",
        "variants": "; ".join(f"{m['slug']}: {m['label']}" for m in rows),
        "viewports": e.get("viewports", ""), "themes": e.get("themes", ""), "fonts": e.get("fonts", ""),
        "scripts": e.get("scripts", ""), "opened_captures": "; ".join(e.get("opened", [])),
        "visual_findings": e.get("visual", ""), "functional_findings": e.get("functional", ""),
        "reviewer": e.get("reviewer", ""), "reviewed_at": e.get("date", ""),
        "figma_sync": "Pending — Figma MCP quota/seat unavailable; no frame imported",
        "aymen_feedback": e.get("aymen", ""),
    })
os.makedirs(REVIEW, exist_ok=True)
with open(f"{REVIEW}/html-design-progress.csv", "w", newline="") as fh:
    w = csv.DictWriter(fh, cols); w.writeheader(); w.writerows(out)
n = lambda s: sum(1 for x in out if x["html_status"].startswith(s))
print(f"{len(out)} rows · reviewed {n('Reviewed')} · generated {n('Generated')} · not started {n('Not started')}")
