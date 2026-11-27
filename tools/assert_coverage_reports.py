#!/usr/bin/env python3
"""Require complete coverage reports with measured source lines and optional native anti-regression floors."""

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


def jacoco_counts(report: Path) -> tuple[int, int, int | None, int | None]:
    sources = load_jacoco(report, "")
    root = _parse_safe_xml(report, "jacoco")
    branch_counter = root.find("counter[@type='BRANCH']")
    if branch_counter is not None:
        covered_branches = int(branch_counter.attrib["covered"])
        total_branches = covered_branches + int(branch_counter.attrib["missed"])
    else:
        covered_branches = total_branches = None
    return (
        sum(sum(lines.values()) for lines in sources.values()),
        sum(map(len, sources.values())),
        covered_branches,
        total_branches,
    )


def lcov_counts(report: Path) -> tuple[int, int, int, int]:
    sources = load_lcov(report, "")
    brf_total = 0
    brh_total = 0
    for row in report.read_text(encoding="utf-8").splitlines():
        if row.startswith("BRF:"):
            brf_total += int(row[4:])
        elif row.startswith("BRH:"):
            brh_total += int(row[4:])
    return (
        sum(sum(lines.values()) for lines in sources.values()),
        sum(map(len, sources.values())),
        brh_total,
        brf_total,
    )


def cobertura_counts(report: Path) -> tuple[int, int, int | None, int | None]:
    root = _parse_safe_xml(report, "cobertura")
    if root.tag != "coverage":
        raise ValueError("Cobertura report lacks the top-level coverage element")
    lines_valid = int(root.attrib["lines-valid"])
    lines_covered = int(root.attrib["lines-covered"])
    if lines_valid <= 0 or lines_covered < 0 or lines_covered > lines_valid:
        raise ValueError("Cobertura line counts are outside valid bounds")
    branches_valid_attr = root.get("branches-valid")
    branches_covered_attr = root.get("branches-covered")
    if (branches_valid_attr is None) != (branches_covered_attr is None):
        raise ValueError("Cobertura report has incomplete branch attributes")
    covered_branches = total_branches = None
    if branches_valid_attr is not None:
        total_branches = int(branches_valid_attr)
        covered_branches = int(branches_covered_attr)
        if total_branches < 0 or covered_branches < 0 or covered_branches > total_branches:
            raise ValueError("Cobertura branch counts are outside valid bounds")
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
    return lines_covered, lines_valid, covered_branches, total_branches


def _validate_threshold(value: float | None, name: str) -> None:
    if value is not None and not 0 <= value <= 100:
        raise ValueError(f"{name} must be between 0 and 100")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("format", choices=("jacoco", "lcov", "cobertura"))
    parser.add_argument("report", type=Path)
    parser.add_argument("--min-lines", type=float, default=None, help="minimum covered line percentage (0-100)")
    parser.add_argument("--min-branches", type=float, default=None, help="minimum covered branch percentage (0-100)")
    args = parser.parse_args(argv)
    readers = {"jacoco": jacoco_counts, "lcov": lcov_counts, "cobertura": cobertura_counts}
    read_counts = readers[args.format]
    try:
        _validate_threshold(args.min_lines, "--min-lines")
        _validate_threshold(args.min_branches, "--min-branches")
        covered, total, covered_branches, total_branches = read_counts(args.report)
        if not covered:
            raise ValueError("coverage report contains zero covered lines")
        line_percent = 100 * covered / total
        if args.min_lines is not None and line_percent < args.min_lines:
            raise ValueError(f"line coverage {line_percent:.2f}% ({covered}/{total}) is below {args.min_lines:g}%")
        branch_summary = ""
        if covered_branches is not None and total_branches is not None:
            if args.min_branches is not None:
                if total_branches <= 0:
                    raise ValueError("coverage report contains zero valid branches")
                branch_percent = 100 * covered_branches / total_branches
                if branch_percent < args.min_branches:
                    raise ValueError(
                        f"branch coverage {branch_percent:.2f}% ({covered_branches}/{total_branches}) "
                        f"is below {args.min_branches:g}%"
                    )
            if total_branches > 0:
                branch_summary = f", branches {covered_branches}/{total_branches} ({100 * covered_branches / total_branches:.2f}%)"
        elif args.min_branches is not None:
            raise ValueError("coverage report does not contain branch counters")
    except (ET.ParseError, KeyError, OSError, ValueError) as exc:
        print(f"FAIL {args.format}: {exc}", file=sys.stderr)
        return 1
    print(f"Covered lines {covered}/{total} ({line_percent:.2f}%){branch_summary}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
