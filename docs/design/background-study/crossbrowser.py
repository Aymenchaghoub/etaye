#!/usr/bin/env python3
"""Run the background study through several engines, pixel ratios and browser zooms.

Each row is one browser setting; every row measures variant B (paper + blueprint, the strictest: it
adds the blueprint clearance rules), light and dark, with the same real-pixel WCAG measurement as
measure.py. Variant A has no blueprint layer and is a subset of B.

    row 1x         display scale 1, zoom 100 %: desktop, tablet, tablet-landscape, mobile, narrow-320
    row 2x         display scale 2 (HiDPI): the four standard viewports
    row zoom200    display scale 1, browser zoom 200 %: window 1440, 1024 and 768 wide, so the page sees
                   720, 512 and 384 CSS px (mobile is left out: 195 CSS px is below any supported width)

Usage:
    python3 crossbrowser.py --engine webkit --out DIR --json results/cross-browser-webkit.json
    python3 crossbrowser.py --report results/cross-browser-*.json      # print the summary tables

This measures a design proposal rendered from static HTML. It is not an application test, an
accessibility audit or a compliance claim. WebKit here is WebKitGTK on Linux, not Safari.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import engines
import measure

ROWS = {
    "1x": {"dsf": 1, "zoom": 1, "viewports": ["desktop", "tablet", "tablet-landscape", "mobile", "narrow-320"]},
    "2x": {"dsf": 2, "zoom": 1, "viewports": ["desktop", "tablet", "tablet-landscape", "mobile"]},
    "zoom200": {"dsf": 1, "zoom": 2, "viewports": ["desktop", "tablet", "tablet-landscape"]},
}
WATCH = ("notice", "lead", "note", "tok-accent", "tok-encre-3", "primary", "secondary", "primary-boundary",
         "secondary-boundary")


def compact(result: dict) -> dict:
    worst = {i["id"]: i["min"] for i in result["items"] if i["kind"] in ("text", "control")}
    required = {i["id"]: i["required"] for i in result["items"] if i["kind"] in ("text", "control")}
    return {"config": result["config"], "css_viewport": result["window"], "dsf": result["dsf"], "zoom": result["zoom"],
            "worst": worst, "required": required,
            "fails": [k for k, v in worst.items() if v < required[k]],
            "blueprint": result["blueprint"],
            "open": {k: result["paper"][k] for k in ("delta_l_star", "delta_a_star", "delta_b_star", "delta_e_ab")},
            "calm": ({k: result["calm"][k] for k in ("delta_l_star", "delta_e_ab")} if result["calm"] else None),
            "plate_step": result["plate_step"]}


def run(args) -> None:
    server, port = measure.serve()
    try:
        with engines.ENGINES[args.engine](f"http://127.0.0.1:{port}") as eng:
            version = eng.version()
            rows = []
            for name in args.rows.split(","):
                spec = ROWS[name]
                cfgs = []
                for theme in ("light", "dark"):
                    for vp in spec["viewports"]:
                        r = measure.measure_config(eng, "b", theme, vp, args.out / args.engine / name,
                                                   extra=args.extra, dsf=spec["dsf"], zoom=spec["zoom"],
                                                   tag=f"-{name}{args.tag}", keep_bg=not args.drop_bg)
                        cfgs.append(compact(r))
                        print(f"{args.engine} {name} {r['config']}: fails={cfgs[-1]['fails'] or 'none'}", flush=True)
                rows.append({"row": name, "dsf": spec["dsf"], "zoom": spec["zoom"], "configs": cfgs})
        report = {"tool": "crossbrowser.py", "engine": args.engine, "browser": version, "extra_query": args.extra,
                  "rows": rows}
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps(report, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
        total = sum(len(r["configs"]) for r in rows)
        bad = [(c["config"], c["fails"]) for r in rows for c in r["configs"] if c["fails"]]
        print(f"{total} configurations; failures: {bad or 'none'}")
    finally:
        server.shutdown()


def report(paths: list[Path]) -> None:
    for path in paths:
        data = json.loads(path.read_text(encoding="utf-8"))
        print(f"\n## {data['browser']}  (query {data['extra_query'] or 'default'})")
        for row in data["rows"]:
            cfgs = row["configs"]
            worst = {k: min(c["worst"][k] for c in cfgs if k in c["worst"]) for k in WATCH}
            dl = [c["open"]["delta_l_star"] for c in cfgs]
            de = [c["open"]["delta_e_ab"] for c in cfgs]
            clear = [c["blueprint"]["min_clearance_px"] for c in cfgs if c["blueprint"]]
            over = sum(c["blueprint"]["overlaps"] for c in cfgs if c["blueprint"])
            step = {t: [c["plate_step"]["delta_e_ab"] for c in cfgs if f"-{t}-" in c["config"]] for t in ("light", "dark")}
            print(f"- {row['row']} (dsf {row['dsf']}, zoom {row['zoom']}, {len(cfgs)} configs): "
                  f"fails {sum(bool(c['fails']) for c in cfgs)}; blueprint overlaps {over}, min clearance {min(clear)} px; "
                  f"open dL* {min(dl):+.2f}..{max(dl):+.2f}, dE {min(de):.2f}..{max(de):.2f}; plate-to-open dE "
                  f"light {min(step['light']):.2f}..{max(step['light']):.2f}, dark {min(step['dark']):.2f}..{max(step['dark']):.2f}")
            print("    worst: " + ", ".join(f"{k} {v}" for k, v in worst.items()))


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--engine", choices=sorted(engines.ENGINES))
    ap.add_argument("--rows", default=",".join(ROWS))
    ap.add_argument("--out", type=Path, help="directory for PNG renders (kept outside Git)")
    ap.add_argument("--json", type=Path)
    ap.add_argument("--extra", default="", help="extra study query, for example &tone=neutral")
    ap.add_argument("--tag", default="")
    ap.add_argument("--drop-bg", action="store_true", help="delete the background-only renders after measuring")
    ap.add_argument("--report", nargs="+", type=Path, help="print the summary of result files and exit")
    args = ap.parse_args()
    if args.report:
        report(args.report)
        return
    if not (args.engine and args.out and args.json):
        ap.error("--engine, --out and --json are required to run a matrix")
    run(args)


if __name__ == "__main__":
    main()
