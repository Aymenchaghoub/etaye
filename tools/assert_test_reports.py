#!/usr/bin/env python3
"""Fail CI for missing, incomplete or unsuccessful test reports."""
import argparse
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path


def read_surefire(directory):
    reports = sorted(directory.glob("TEST-*.xml"))
    if not reports:
        raise ValueError("Surefire report directory is missing or contains no TEST-*.xml files")

    fields = ("tests", "failures", "errors", "skipped")
    totals = dict.fromkeys(fields, 0)
    for report in reports:
        try:
            source = report.read_text(encoding="utf-8-sig")
        except (OSError, UnicodeError) as exc:
            raise ValueError(f"Cannot read Surefire report {report.name} as UTF-8") from exc
        if "\x00" in source:
            raise ValueError(f"Invalid UTF-8 XML in Surefire report {report.name}")
        if re.search(r"<!\s*(?:DOCTYPE|ENTITY)\b", source, re.IGNORECASE):
            raise ValueError(f"DOCTYPE or entity declaration in Surefire report {report.name}")
        try:
            suite = ET.fromstring(source)
        except ET.ParseError as exc:
            raise ValueError(f"Malformed XML in Surefire report {report.name}") from exc
        if suite.tag != "testsuite" or any(not re.fullmatch(r"[0-9]+", suite.get(field, "")) for field in fields):
            raise ValueError(f"Invalid testsuite or counters in Surefire report {report.name}")
        counts = {field: int(suite.attrib[field]) for field in fields}
        if counts["tests"] == 0:
            raise ValueError(f"Zero tests in Surefire report {report.name}")
        for field in fields:
            totals[field] += counts[field]
    return totals


def read_playwright(report):
    try:
        data = json.loads(report.read_text(encoding="utf-8"))
    except OSError as exc:
        raise ValueError(f"Cannot read Playwright report {report.name}") from exc
    except (ValueError, RecursionError) as exc:
        raise ValueError(f"Malformed JSON in Playwright report {report.name}") from exc

    stats = data.get("stats") if isinstance(data, dict) else None
    fields = ("expected", "unexpected", "flaky", "skipped")
    if not isinstance(stats, dict) or any(type(stats.get(field)) is not int or stats[field] < 0 for field in fields):
        raise ValueError(f"Invalid stats in Playwright report {report.name}")
    return {"tests": sum(stats[field] for field in fields),
            "unexpected": stats["unexpected"], "flaky": stats["flaky"], "skipped": stats["skipped"]}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("kind", choices=("surefire", "playwright"))
    parser.add_argument("path", type=Path)
    parser.add_argument("--min-tests", type=int, required=True)
    args = parser.parse_args(argv)

    try:
        if args.min_tests < 1:
            raise ValueError("--min-tests must be at least 1")
        read_report = read_surefire if args.kind == "surefire" else read_playwright
        totals = read_report(args.path)
        summary = ", ".join(f"{field}={count}" for field, count in totals.items())
        if totals["tests"] < args.min_tests or any(totals[field] for field in totals if field != "tests"):
            raise ValueError(f"{summary} (required minimum: {args.min_tests}, all tests must pass without skips)")
    except (ValueError, OSError) as exc:
        print(f"FAIL {args.kind}: {exc}")
        return 1

    print(f"PASS {args.kind}: {summary}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
