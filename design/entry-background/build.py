#!/usr/bin/env python3
"""Make an external preview from the frozen offline mockups."""
import argparse
from copy import copy
from pathlib import Path
import shutil

from bs4 import BeautifulSoup

ROOT = Path(__file__).resolve().parents[2]
STUDY = ROOT / "docs/design/background-study"
FROZEN_SITE = ROOT / "design/offline-reference/site"
SCREENS = ("S-25", "S-02", "S-01")


def add_classes(element, *classes):
    element["class"] = [*element.get("class", []), *classes]


def decorate(page, screen, symbols, background):
    html = BeautifulSoup(page.read_text(encoding="utf-8"), "html.parser")
    main = html.main
    if screen == "S-25":
        host = main.find("div", recursive=False)
        host["data-entry-background"] = "patient-heading"
        add_classes(host.find("h1"), "etaye-calm")
    elif screen == "S-02":
        host = main.parent
        host["data-entry-background"] = "login"
        add_classes(main.find("div", recursive=False), "etaye-calm")
        for note in main.find_all("p", class_="etaye-3", recursive=False):
            add_classes(note, "etaye-calm")
        add_classes(host.footer, "etaye-calm")
    else:
        host = main.parent
        host["data-entry-background"] = "showcase"
        add_classes(main, "etaye-calm")

    add_classes(host, "etaye-bg-host")
    host.insert(0, copy(background))
    html.body.insert(0, copy(symbols))
    for filename in ("background.css", "entry.css"):
        html.head.append(html.new_tag("link", rel="stylesheet", href=f"../../background/{filename}"))
    page.write_text(str(html), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True, help="New folder outside the repository")
    args = parser.parse_args()
    output = args.output.resolve()
    if output.is_relative_to(ROOT) or output.exists():
        parser.error("Use a new output folder outside the repository")

    study = BeautifulSoup((STUDY / "study.html").read_text(encoding="utf-8"), "html.parser")
    symbols = study.select_one("body > svg")
    background = study.select_one(".etaye-bg")
    del background["id"]

    shutil.copytree(FROZEN_SITE, output)
    assets = output / "background"
    (assets / "assets").mkdir(parents=True)
    shutil.copyfile(STUDY / "background.css", assets / "background.css")
    shutil.copyfile(STUDY / "assets/paper-grain.svg", assets / "assets/paper-grain.svg")
    shutil.copyfile(Path(__file__).with_name("entry.css"), assets / "entry.css")
    index = BeautifulSoup('<!doctype html><html lang="en"><head><meta charset="utf-8">'
                          '<meta name="viewport" content="width=device-width,initial-scale=1">'
                          '<title>Étaye entry background review</title></head>'
                          '<body><main><h1>Étaye entry background review</h1></main></body></html>', "html.parser")
    for screen in SCREENS:
        heading = index.new_tag("h2")
        heading.string = screen
        index.main.append(heading)
        states = index.new_tag("ul")
        for page in sorted((output / "ecrans" / screen).glob("*.html")):
            decorate(page, screen, symbols, background)
            entry = index.new_tag("li")
            entry.append(f"{page.stem} · ")
            for theme, label in (("clair", "Light"), ("sombre", "Dark")):
                link = index.new_tag("a", href=f"{page.relative_to(output).as_posix()}?theme={theme}")
                link.string = label
                entry.append(link)
                entry.append(" ")
            states.append(entry)
        index.main.append(states)
    preview = output / "entry-background.html"
    preview.write_text(str(index), encoding="utf-8")
    print(preview)


if __name__ == "__main__":
    main()
