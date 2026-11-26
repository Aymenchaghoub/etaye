#!/usr/bin/env python3
"""Require complete coverage reports with measured source lines."""

import argparse
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

from assert_changed_line_coverage import load_jacoco, load_lcov, source_path


def _parse_safe_xml(report: Path, kind: str) -> ET.Element:
    text = report.read_text(encoding="utf-8")
    if re.search(r"<!\s*ENTITY\b", text, re.IGNORECASE) or re.search(r"<!\s*DOCTYPE[^>\[]*\[", text, re.IGNORECASE):
        raise ValueError(f"entity declaration or internal DTD subset in {kind} report")
    return ET.fromstring(text)


def jacoco_counts(report: Path) -> tuple[int, int]:
    sources = load_jacoco(report, "")
    return sum(sum(lines.values()) for lines in sources.values()), sum(map(len, sources.values()))


def lcov_counts(report: Path) -> tuple[int, int]:
    sources = load_lcov(report, "")
    return sum(sum(lines.values()) for lines in sources.values()), sum(map(len, sources.values()))


def cobertura_counts(report: Path) -> tuple[int, int]:
    root = _parse_safe_xml(report, "cobertura")
    if root.tag != "coverage":
        raise ValueError("Cobertura report lacks the top-level coverage element")
    lines_valid = int(root.attrib["lines-valid"])
    lines_covered = int(root.attrib["lines-covered"])
    if lines_valid <= 0 or lines_covered < 0 or lines_covered > lines_valid:
        raise ValueError("Cobertura line counts are outside valid bounds")
    classes = root.findall(".//class")
    if not classes or any(not cls.get("filename") for cls in classes):
        raise ValueError("Cobertura report lacks valid class records")
    measured = 0
    covered_lines = 0
    filenames: set[str] = set()
    for cls in classes:
        filename = source_path("", cls.attrib["filename"])
        if filename in filenames:
            raise ValueError(f"duplicate Cobertura source: {filename}")
        filenames.add(filename)
        seen_numbers: set[int] = set()
        for line in cls.findall("./lines/line"):
            number = int(line.attrib["number"])
            hits = int(line.attrib["hits"])
            if number < 1 or hits < 0 or number in seen_numbers:
                raise ValueError("Cobertura line record has invalid, duplicate or negative values")
            seen_numbers.add(number)
            measured += 1
            if hits > 0:
                covered_lines += 1
    if measured == 0 or measured != lines_valid or covered_lines != lines_covered:
        raise ValueError("Cobertura line records disagree with top-level line counters")
    return lines_covered, lines_valid


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("format", choices=("jacoco", "lcov", "cobertura"))
    parser.add_argument("report", type=Path)
    args = parser.parse_args(argv)
    readers = {"jacoco": jacoco_counts, "lcov": lcov_counts, "cobertura": cobertura_counts}
    read_counts = readers[args.format]
    try:
        covered, total = read_counts(args.report)
        if not covered:
            raise ValueError("coverage report contains zero covered lines")
    except (ET.ParseError, KeyError, OSError, ValueError) as exc:
        print(f"FAIL {args.format}: {exc}", file=sys.stderr)
        return 1
    print(f"Covered lines {covered}/{total} ({100 * covered / total:.2f}%)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
