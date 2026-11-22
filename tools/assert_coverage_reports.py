#!/usr/bin/env python3
"""Require complete coverage reports with measured source lines."""

import argparse
from pathlib import Path
import xml.etree.ElementTree as ET


def jacoco_counts(report: Path) -> tuple[int, int]:
    root = ET.parse(report).getroot()
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


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("format", choices=("jacoco", "lcov"))
    parser.add_argument("report", type=Path)
    args = parser.parse_args(argv)
    read_counts = jacoco_counts if args.format == "jacoco" else lcov_counts
    try:
        covered, total = read_counts(args.report)
        if not covered:
            raise ValueError("coverage report contains zero covered lines")
    except (ET.ParseError, KeyError, OSError, ValueError) as exc:
        parser.error(str(exc))
    print(f"Covered lines {covered}/{total} ({100 * covered / total:.2f}%)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
