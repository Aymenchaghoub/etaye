#!/usr/bin/env python3
"""Require measured coverage on the lines a change adds or modifies.

An overall coverage floor lets one well-tested module hide a large untested addition. This gate compares the
changed lines of a pull request with a JaCoCo XML or LCOV report and fails when too few of the *executable*
changed lines were executed. Lines the coverage tool does not instrument (comments, imports, declarations) are
not counted. It fails closed: a missing or malformed report, or a change list that cannot be computed, is an error,
never a pass.

Changed source files that the report does not mention have no executable code to measure (JaCoCo lists every
compiled class; Vitest lists every included file) and are named in the output so a reviewer can see them.
"""
import argparse
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path, PurePosixPath

from ci_scope import compile_glob

HUNK = re.compile(r"^@@ -\d+(?:,\d+)? \+(?P<start>\d+)(?:,(?P<count>\d+))? @@")


def parse_hunks(patch):
    """Line numbers, in the new file, of every added or modified line of a zero-context patch."""
    lines = set()
    # Split on "\n" only: str.splitlines also breaks on U+2028, so an added line could forge a hunk header.
    for row in patch.split("\n"):
        match = HUNK.match(row)
        if match:
            start = int(match["start"])
            count = int(match["count"]) if match["count"] is not None else 1
            lines.update(range(start, start + count))  # a pure deletion has count 0 and adds nothing
    return lines


def changed_lines(base, head, in_scope, runner=subprocess.run):
    """Map each in-scope changed file to its added or modified lines. Raises RuntimeError when Git fails."""
    listing = runner(["git", "diff", "--name-only", "--no-renames", "-z", "--end-of-options", base, head],
                     capture_output=True, check=False)
    if listing.returncode != 0:
        raise RuntimeError(f"git diff {base} {head} exited with {listing.returncode}")
    result = {}
    for raw in listing.stdout.split(b"\0"):
        if not raw:
            continue
        name = raw.decode("utf-8", "surrogateescape")
        if not in_scope(name):
            continue
        # --text: a `-diff` attribute must not hide the hunks behind "Binary files differ".
        patch = runner(["git", "-c", "core.quotepath=off", "diff", "--text", "--unified=0", "--no-renames", "--no-color",
                        "--end-of-options", base, head, "--", name], capture_output=True, check=False)
        if patch.returncode != 0:
            raise RuntimeError(f"git diff of {name} exited with {patch.returncode}")
        lines = parse_hunks(patch.stdout.decode("utf-8", "replace"))
        if lines:
            result[name] = lines
    return result


def _safe_read(report):
    try:
        text = report.read_text(encoding="utf-8")
    except (OSError, UnicodeError) as exc:
        raise ValueError(f"cannot read coverage report {report}: {exc.__class__.__name__}") from exc
    if not text.strip():
        raise ValueError(f"coverage report {report} is empty")
    return text


def load_jacoco(report, prefix):
    """repo-relative source path -> {line number: executed}. Only instrumented lines appear."""
    text = _safe_read(report)
    # JaCoCo writes `<!DOCTYPE report PUBLIC ... "report.dtd">`, which expat never fetches. Entity declarations and
    # an internal DTD subset are the vectors for entity expansion, so those are refused.
    if re.search(r"<!\s*ENTITY\b", text, re.IGNORECASE) or re.search(r"<!\s*DOCTYPE[^>\[]*\[", text, re.IGNORECASE):
        raise ValueError("entity declaration or internal DTD subset in the JaCoCo report")
    try:
        root = ET.fromstring(text)
    except ET.ParseError as exc:
        raise ValueError("malformed XML in the JaCoCo report") from exc
    if root.tag != "report" or not root.findall("package"):
        raise ValueError("the JaCoCo report has no packages")
    coverage = {}
    for package in root.findall("package"):
        directory = package.get("name")
        if directory is None:
            raise ValueError("a JaCoCo package has no name")
        for source in package.findall("sourcefile"):
            name = source.get("name")
            if not name:
                raise ValueError("a JaCoCo source file has no name")
            path = PurePosixPath(prefix) / directory / name
            lines = {}
            for line in source.findall("line"):
                try:
                    number, covered_instructions = int(line.attrib["nr"]), int(line.attrib["ci"])
                    int(line.attrib["mi"])
                except (KeyError, ValueError) as exc:
                    raise ValueError(f"malformed line counters in JaCoCo source {name}") from exc
                lines[number] = covered_instructions > 0
            coverage[path.as_posix()] = lines
    return coverage


def load_lcov(report, prefix):
    text = _safe_read(report).rstrip() + "\n"
    *records, tail = text.split("\nend_of_record\n")
    if not records or tail.strip():
        raise ValueError("LCOV report lacks complete source records")
    coverage = {}
    for record in records:
        source = None
        lines = {}
        for row in record.splitlines():
            if row.startswith("SF:"):
                source = row[3:].strip()
            elif row.startswith("DA:"):
                try:
                    number, hits = (int(part) for part in row[3:].split(",")[:2])
                except ValueError as exc:
                    raise ValueError(f"malformed DA record {row!r} in the LCOV report") from exc
                lines[number] = hits > 0
        if not source:
            raise ValueError("LCOV record lacks its source path")
        coverage[(PurePosixPath(prefix) / source).as_posix()] = lines
    return coverage


def require_resolvable(coverage, root):
    """Fail when no path of the report exists in the checkout: a drifted prefix or absolute paths would otherwise turn
    every changed file into "not measured" and let the gate pass without measuring anything."""
    if coverage and not any((root / path).is_file() for path in coverage):
        sample = next(iter(coverage))
        raise ValueError(f"none of the {len(coverage)} source paths of the report exists under {root} "
                         f"(for example {sample}); check --prefix and the report format")


def evaluate(changed, coverage):
    """Per file (covered, missed, uncovered lines) over changed lines the report instruments, plus unmeasured files."""
    rows, unmeasured = {}, []
    for path, lines in sorted(changed.items()):
        measured = coverage.get(path)
        if measured is None:
            unmeasured.append(path)
            continue
        covered = sorted(line for line in lines if measured.get(line) is True)
        missed = sorted(line for line in lines if measured.get(line) is False)
        if covered or missed:
            rows[path] = (len(covered), len(missed), missed)
    return rows, unmeasured


def ranges(numbers):
    spans, start, previous = [], None, None
    for number in numbers:
        if start is None:
            start = previous = number
        elif number == previous + 1:
            previous = number
        else:
            spans.append((start, previous))
            start = previous = number
    if start is not None:
        spans.append((start, previous))
    return ", ".join(str(a) if a == b else f"{a}-{b}" for a, b in spans)


def main(argv=None, runner=subprocess.run):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("format", choices=("jacoco", "lcov"))
    parser.add_argument("report", type=Path)
    parser.add_argument("--prefix", required=True, help="repository path that the report's source paths are relative to")
    parser.add_argument("--include", action="append", required=True, metavar="GLOB",
                        help="repository-relative source files subject to the gate")
    parser.add_argument("--exclude", action="append", default=[], metavar="GLOB")
    parser.add_argument("--min-percent", type=float, default=80.0)
    parser.add_argument("--min-file-percent", type=float, default=50.0,
                        help="a changed file with at least --min-file-lines executable changed lines must reach this")
    parser.add_argument("--min-file-lines", type=int, default=10)
    parser.add_argument("--root", type=Path, default=Path("."), help="repository root, to resolve report paths")
    parser.add_argument("--base", default="HEAD^1")
    parser.add_argument("--head", default="HEAD")
    args = parser.parse_args(argv)

    includes = [compile_glob(pattern) for pattern in args.include]
    excludes = [compile_glob(pattern) for pattern in args.exclude]

    def in_scope(path):
        return any(rx.fullmatch(path) for rx in includes) and not any(rx.fullmatch(path) for rx in excludes)

    try:
        if not 0 <= args.min_percent <= 100 or not 0 <= args.min_file_percent <= 100:
            raise ValueError("--min-percent and --min-file-percent must be between 0 and 100")
        coverage = (load_jacoco if args.format == "jacoco" else load_lcov)(args.report, args.prefix)
        require_resolvable(coverage, args.root)
        changed = changed_lines(args.base, args.head, in_scope, runner)
        rows, unmeasured = evaluate(changed, coverage)
    except (ValueError, RuntimeError, OSError) as exc:
        print(f"FAIL changed-line coverage: {exc}", file=sys.stderr)
        return 1

    covered = sum(row[0] for row in rows.values())
    missed = sum(row[1] for row in rows.values())
    for path, (hit, miss, lines) in rows.items():
        suffix = f"  uncovered lines {ranges(lines)}" if lines else ""
        print(f"  {path}: {hit}/{hit + miss}{suffix}")
    for path in unmeasured:
        print(f"  {path}: no executable lines in the report (not measured)")
    if covered + missed == 0:
        print(f"PASS changed-line coverage: no executable changed line to measure ({len(changed)} changed source file(s))")
        return 0
    percent = 100 * covered / (covered + missed)
    # An aggregate can hide a new file that no test touches: 400 covered lines dilute a 30-line untested class.
    untested = [path for path, (hit, miss, _) in rows.items()
                if hit + miss >= args.min_file_lines and 100 * hit / (hit + miss) < args.min_file_percent]
    for path in untested:
        print(f"  {path}: below {args.min_file_percent:g}% with at least {args.min_file_lines} executable changed lines")
    verdict = "PASS" if percent >= args.min_percent and not untested else "FAIL"
    print(f"{verdict} changed-line coverage: {covered}/{covered + missed} executable changed lines "
          f"({percent:.1f}%, required {args.min_percent:g}%)")
    return 0 if verdict == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
