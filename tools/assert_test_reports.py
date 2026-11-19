#!/usr/bin/env python3
"""Fail CI for missing, incomplete or unsuccessful test reports."""
import argparse
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path, PurePosixPath


def read_surefire(directory, required_suites=()):
    reports = sorted(directory.glob("TEST-*.xml"))
    if not reports:
        raise ValueError("Surefire report directory is missing or contains no TEST-*.xml files")

    fields = ("tests", "failures", "errors", "skipped")
    totals = dict.fromkeys(fields, 0)
    classnames = set()
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
        cases = suite.findall("testcase")
        if len(cases) != counts["tests"]:
            raise ValueError(
                f"Surefire report {report.name} declares tests={counts['tests']} but contains {len(cases)} testcase elements"
            )
        for element in suite.iter():
            if element.tag in {"skipped", "failure", "error"}:
                raise ValueError(f"Found <{element.tag}> in Surefire report {report.name}")
        classnames.update(case.get("classname") for case in cases)
        for field in fields:
            totals[field] += counts[field]
    missing = sorted(set(required_suites) - classnames)
    if missing:
        raise ValueError(f"Missing required Surefire suites: {', '.join(missing)}")
    return totals


def playwright_entries(parent, key, default=None):
    entries = parent.get(key, default)
    if not isinstance(entries, list) or any(not isinstance(entry, dict) for entry in entries):
        raise ValueError(f"Invalid Playwright {key}")
    return entries


def playwright_file_base(config):
    # The JSON reporter writes file paths relative to rootDir, which can be the test directory.
    if not isinstance(config, dict):
        return PurePosixPath()
    root, config_file = config.get("rootDir"), config.get("configFile")
    if isinstance(root, str) and isinstance(config_file, str):
        try:
            return PurePosixPath(root.replace("\\", "/")).relative_to(
                PurePosixPath(config_file.replace("\\", "/")).parent
            )
        except ValueError:
            pass
    return PurePosixPath()


def read_playwright(report, required_files=()):
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

    files = set()
    count = 0
    suites = list(playwright_entries(data, "suites"))
    while suites:
        suite = suites.pop()
        suites.extend(playwright_entries(suite, "suites", []))
        for spec in playwright_entries(suite, "specs", []):
            file = spec.get("file")
            if not isinstance(file, str) or not file:
                raise ValueError("Invalid Playwright spec file")
            report_file = PurePosixPath(file.replace("\\", "/"))
            for test in playwright_entries(spec, "tests"):
                if test.get("expectedStatus") != "passed" or test.get("status") != "expected":
                    raise ValueError(
                        f"Playwright test in {file} must expect a pass and finish as expected "
                        f"(expectedStatus={test.get('expectedStatus')!r}, status={test.get('status')!r})"
                    )
                results = playwright_entries(test, "results")
                if not results or any(result.get("status") != "passed" for result in results):
                    raise ValueError(f"Playwright test in {file} must have only passed results and at least one result")
                count += 1
                files.add(report_file)

    if stats["expected"] != count or any(stats[field] for field in fields[1:]):
        summary = ", ".join(f"{field}={stats[field]}" for field in fields)
        raise ValueError(f"Playwright stats ({summary}) do not match the {count} passing tests in the results")
    file_base = playwright_file_base(data.get("config"))
    available_paths = {file_base / file for file in files} | {PurePosixPath(file.name) for file in files}
    required_paths = {PurePosixPath(file.replace("\\", "/")) for file in required_files}
    missing = sorted(required_paths - available_paths)
    if missing:
        raise ValueError(f"Missing required Playwright files: {', '.join(map(str, missing))}")
    return {"tests": count,
            "unexpected": stats["unexpected"], "flaky": stats["flaky"], "skipped": stats["skipped"]}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("kind", choices=("surefire", "playwright"))
    parser.add_argument("path", type=Path)
    parser.add_argument("--min-tests", type=int, required=True)
    parser.add_argument("--require-suite", action="append", metavar="CLASSNAME", default=[])
    parser.add_argument("--require-file", action="append", metavar="FILE", default=[])
    args = parser.parse_args(argv)

    try:
        if args.min_tests < 1:
            raise ValueError("--min-tests must be at least 1")
        if args.kind == "surefire":
            if args.require_file:
                raise ValueError("--require-file is only supported for Playwright reports")
            totals = read_surefire(args.path, args.require_suite)
        else:
            if args.require_suite:
                raise ValueError("--require-suite is only supported for Surefire reports")
            totals = read_playwright(args.path, args.require_file)
        summary = ", ".join(f"{field}={count}" for field, count in totals.items())
        if totals["tests"] < args.min_tests or any(totals[field] for field in totals if field != "tests"):
            raise ValueError(f"{summary} (required minimum: {args.min_tests}, all tests must pass without skips)")
    except (ValueError, OSError) as exc:
        message = " ".join(str(exc).splitlines())
        print(f"FAIL {args.kind}: {message}")
        return 1

    print(f"PASS {args.kind}: {summary}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
