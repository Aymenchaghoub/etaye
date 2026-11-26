#!/usr/bin/env python3
"""Require complete coverage reports with measured source lines."""

import argparse
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


def _parse_safe_xml(report: Path, kind: str) -> ET.Element:
    text = report.read_text(encoding="utf-8")
    if re.search(r"<!\s*ENTITY\b", text, re.IGNORECASE) or re.search(r"<!\s*DOCTYPE[^>\[]*\[", text, re.IGNORECASE):
        raise ValueError(f"entity declaration or internal DTD subset in {kind} report")
    return ET.fromstring(text)


def jacoco_counts(report: Path) -> tuple[int, int]:
    root = _parse_safe_xml(report, "jacoco")
    counter = root.find("counter[@type='LINE']")
    if root.tag != "report" or counter is None or root.find(".//sourcefile/line") is None:
        raise ValueError("JaCoCo report lacks source lines or the top-level LINE counter")
    covered = int(counter.attrib["covered"])
    missed = int(counter.attrib["missed"])
    if missed < 0 or covered < 0:
        raise ValueError("JaCoCo line counts must be nonnegative")
    return covered, covered + missed


def lcov_counts(report: Path) -> tuple[int, int]:
    text = report.read_text(encoding="utf-8").rstrip() + "\n"
    *records, tail = text.split("\nend_of_record\n")
    if not records or tail.strip():
        raise ValueError("LCOV report lacks complete source records")
    covered = total = 0
    for record in records:
        fields = dict(line.split(":", 1) for line in record.splitlines() if ":" in line)
        if not fields.get("SF"):
            raise ValueError("LCOV record lacks its source path")
        hits = int(fields["LH"])
        lines = int(fields["LF"])
        if hits < 0 or hits > lines:
            raise ValueError("LCOV line hits are outside the recorded line count")
        covered += hits
        total += lines
    return covered, total


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
    for cls in classes:
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
