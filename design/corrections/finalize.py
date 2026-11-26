#!/usr/bin/env python3
"""Add explicit guided design journeys after the received generator finishes."""

import argparse
import hashlib
import json
from pathlib import Path

from bs4 import BeautifulSoup

HERE = Path(__file__).resolve().parent


def finalize(root):
    site = (root / "site").resolve()
    snapshot = root / "review/corrected-snapshot.json"
    if not site.is_relative_to(root) or not snapshot.parent.resolve().is_relative_to(root):
        raise ValueError("site and review must be inside the reconstruction")
    snapshot.unlink(missing_ok=True)
    journeys = json.loads((HERE / "journeys.json").read_text(encoding="utf-8"))
    states = {
        m["id"] + "/" + m["slug"]: m
        for f in (site / "manifest").glob("*.json")
        for m in json.loads(f.read_text(encoding="utf-8"))
    }
    for journey in journeys:
        for step in journey["steps"]:
            if step not in states:
                raise ValueError("Missing corrected journey step " + step)
    index = BeautifulSoup((site / "parcours.html").read_text(encoding="utf-8"), "html.parser")
    main = index.select_one("main")
    if main is None:
        raise ValueError("parcours.html: missing <main>")

    paths = {m["path"] for m in states.values()}
    for path in sorted(paths):
        p = (site / path).resolve()
        if not p.is_relative_to(site):
            raise ValueError(f"{path}: outside the reconstructed site")
        soup = BeautifulSoup(p.read_text(encoding="utf-8"), "html.parser")
        head = soup.head
        body = soup.body
        if head is None or body is None:
            raise ValueError(f"{path}: missing <head> or <body>")
        for tag in soup.select("[data-guided-only]"):
            tag.decompose()
        if not soup.select_one('script[src$="prototype.js"]'):
            body.append(
                soup.new_tag("script", src="../../js/prototype.js", defer=True)
            )
        if not soup.select_one('link[href$="review-corrections.css"]'):
            head.append(
                soup.new_tag(
                    "link", href="../../css/review-corrections.css", rel="stylesheet"
                )
            )
        script = soup.new_tag(
            "script",
            attrs={
                "type": "application/json",
                "id": "design-journeys",
                "data-guided-only": "",
            },
        )
        script.string = json.dumps(journeys, ensure_ascii=False).replace("</", "<\\/")
        body.append(script)
        p.write_text(str(soup), encoding="utf-8")
    soup = index
    main.clear()
    h = soup.new_tag("h1")
    h.string = "Parcours de conception corrigés"
    main.append(h)
    p = soup.new_tag("p")
    p.string = "10 parcours guidés. Les étapes et changements d’audience sont explicites. Aucune donnée n’est enregistrée ; les écrans sont des maquettes."
    main.append(p)
    for j in journeys:
        sec = soup.new_tag("section")
        title = soup.new_tag("h2")
        title.string = j["title"]
        sec.append(title)
        text = soup.new_tag("p")
        text.string = j["scenario"]
        sec.append(text)
        anchor = soup.new_tag(
            "a", href="ecrans/" + j["steps"][0] + ".html?journey=" + j["id"] + "&step=0"
        )
        anchor.string = "Commencer ce parcours"
        sec.append(anchor)
        ol = soup.new_tag("ol")
        for i, key in enumerate(j["steps"]):
            li = soup.new_tag("li")
            a = soup.new_tag(
                "a", href="ecrans/" + key + ".html?journey=" + j["id"] + "&step=" + str(i)
            )
            a.string = key + " · " + states[key]["name"]
            li.append(a)
            ol.append(li)
        sec.append(ol)
        main.append(sec)
    (site / "parcours.html").write_text(str(soup), encoding="utf-8")
    # Bind each corrected artifact and its dependencies, never inherit Claude's review status.
    entries = []
    for p in sorted(site.rglob("*")):
        if p.is_file():
            if not p.resolve().is_relative_to(site):
                raise ValueError(f"{p.relative_to(site)}: asset outside the reconstructed site")
            with p.open("rb") as source:
                sha256 = hashlib.file_digest(source, "sha256").hexdigest()
            entries.append(
                {
                    "path": str(p.relative_to(root)),
                    "sha256": sha256,
                }
            )
    digest = hashlib.sha256(json.dumps(entries, sort_keys=True).encode()).hexdigest()
    snapshot.write_text(
        json.dumps(
            {
                "snapshotSha256": digest,
                "files": entries,
                "reviewStatus": "Awaiting independent verification",
                "aymenApproval": False,
                "figmaImported": False,
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    print(
        "Corrected journeys:",
        len(journeys),
        "steps:",
        sum(len(j["steps"]) for j in journeys),
        "snapshot",
        digest[:12],
    )


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    root = parser.parse_args(argv).root.resolve()
    if root.is_relative_to(HERE.parents[1]) or not root.parent.name.startswith(
        "etaye-design-rebuild-"
    ):
        parser.error("Use only an external disposable reconstruction")
    try:
        finalize(root)
    except (OSError, ValueError) as exc:
        parser.exit(1, f"{exc}\n")


if __name__ == "__main__":
    main()
