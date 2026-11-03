#!/usr/bin/env python3
"""Inspect local HTML/asset references in a reconstructed design, without executing it."""

import argparse
from collections import Counter
from html.parser import HTMLParser
import json
from pathlib import Path
from urllib.parse import unquote, urlsplit
import re


class Page(HTMLParser):
    def __init__(self, text):
        super().__init__()
        self.references = []
        self.ids = []
        self.feed(text)

    def handle_starttag(self, tag, attrs):
        attributes = dict(attrs)
        if "id" in attributes:
            self.ids.append(attributes["id"])
        for key in ("href", "src", "poster"):
            if attributes.get(key):
                self.references.append((tag, key, attributes[key]))


def inspect(site):
    site = site.resolve()
    pages = {p: Page(p.read_text()) for p in sorted(site.rglob("*.html"))}
    missing, fragments, duplicate_ids, escapes = [], [], [], []
    placeholders = 0
    checked = 0
    for path, page in pages.items():
        relative = str(path.relative_to(site))
        duplicate_ids += [
            [relative, key] for key, count in Counter(page.ids).items() if count > 1
        ]
        for _, attribute, link in page.references:
            url = urlsplit(link)
            if url.scheme or url.netloc:
                continue
            if link == "#":
                placeholders += 1
                continue
            checked += 1
            target = (path.parent / unquote(url.path)).resolve() if url.path else path
            if not target.is_relative_to(site):
                escapes.append([relative, link])
            elif not target.exists():
                missing.append([relative, attribute, link])
            elif (
                url.fragment
                and target in pages
                and unquote(url.fragment) not in pages[target].ids
            ):
                fragments.append([relative, link])
    for path in site.rglob("*.css"):
        for link in re.findall(r"url\(['\"]?([^\)'\"]+)", path.read_text()):
            url = urlsplit(link)
            if url.scheme or url.netloc:
                continue
            checked += 1
            target = (path.parent / unquote(url.path)).resolve()
            if not target.is_relative_to(site):
                escapes.append([str(path.relative_to(site)), link])
            elif not target.exists():
                missing.append([str(path.relative_to(site)), "css-url", link])
    return {
        "htmlPages": len(pages),
        "referencesChecked": checked,
        "missingFiles": missing,
        "missingFragments": fragments,
        "duplicateIds": duplicate_ids,
        "referencesOutsideSite": escapes,
        "placeholderLinks": placeholders,
        "scope": "Local file/fragment structure only; no application or visual acceptance",
        "ok": bool(pages) and not any([missing, fragments, duplicate_ids, escapes]),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--site", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    result = inspect(args.site.expanduser())
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(
        json.dumps(
            {
                "htmlPages": result["htmlPages"],
                "ok": result["ok"],
                "missingFragments": len(result["missingFragments"]),
            }
        )
    )
    return 0 if result["ok"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
