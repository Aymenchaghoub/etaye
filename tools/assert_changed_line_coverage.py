#!/usr/bin/env python3
"""Require measured coverage on the lines a change adds or modifies.

An overall coverage floor lets one well-tested module hide a large untested addition. This gate compares the
changed lines of a pull request with a JaCoCo XML or LCOV report and fails when too few of the *executable*
changed lines were executed. Lines the coverage tool does not instrument (comments, imports, declarations) are
not counted. It fails closed: a missing or malformed report, or a change list that cannot be computed, is an error,
never a pass.

All in-scope Git sources must appear in the report, including zero-line records. Javac compilation units
containing only comments, a package declaration or imports can legitimately produce no class.
"""
import argparse
from functools import partial
import json
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
        patch = runner(["git", "--literal-pathspecs", "diff", "--text", "--unified=0", "--no-renames", "--no-color",
                        "--no-ext-diff", "--no-textconv",
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


def source_path(prefix, source):
    components = source.split("/")
    if any(part in ("", ".", "..") for part in components) or "\\" in source:
        raise ValueError(f"invalid relative source path in coverage report: {source!r}")
    return (PurePosixPath(prefix) / source).as_posix()


def verify_counters(element, totals):
    for kind, expected in totals.items():
        entries = element.findall(f"counter[@type='{kind}']")
        try:
            actual = [(int(entry.attrib["missed"]), int(entry.attrib["covered"])) for entry in entries]
        except (KeyError, ValueError) as exc:
            raise ValueError(f"malformed JaCoCo {kind} counter") from exc
        if actual != [expected] and (actual or expected != (0, 0)):
            raise ValueError(f"inconsistent JaCoCo {kind} counters in {element.tag} {element.get('name')!r}")


def jacoco_totals(rows):
    return {
        "LINE": (sum(ci == 0 for _, ci, _, _ in rows), sum(ci > 0 for _, ci, _, _ in rows)),
        "INSTRUCTION": (sum(mi for mi, _, _, _ in rows), sum(ci for _, ci, _, _ in rows)),
        "BRANCH": (sum(mb for _, _, mb, _ in rows), sum(cb for _, _, _, cb in rows)),
    }


def load_jacoco(report, prefix):
    """Repository-relative source path -> {line number: executed}. Only instrumented lines appear."""
    text = _safe_read(report)
    # JaCoCo writes a public DOCTYPE that expat never fetches. Internal DTD subsets can declare entities.
    if re.search(r"<!\s*ENTITY\b", text, re.IGNORECASE) or re.search(r"<!\s*DOCTYPE[^>\[]*\[", text, re.IGNORECASE):
        raise ValueError("entity declaration or internal DTD subset in the JaCoCo report")
    try:
        root = ET.fromstring(text)
    except ET.ParseError as exc:
        raise ValueError("malformed XML in the JaCoCo report") from exc
    if root.tag != "report" or not root.findall("package"):
        raise ValueError("the JaCoCo report has no packages")
    coverage, report_rows, packages = {}, [], set()
    for package in root.findall("package"):
        directory = package.get("name")
        if directory is None or directory in packages:
            raise ValueError("missing or duplicate JaCoCo package name")
        packages.add(directory)
        package_rows = []
        for source in package.findall("sourcefile"):
            name = source.get("name", "")
            if not name.endswith(".java") or "/" in name:
                raise ValueError(f"invalid JaCoCo source name: {name!r}")
            path = source_path(prefix, f"{directory}/{name}" if directory else name)
            if path in coverage:
                raise ValueError(f"duplicate JaCoCo source: {path}")
            lines, source_rows = {}, []
            for line in source.findall("line"):
                try:
                    number, mi, ci, mb, cb = (int(line.attrib[key]) for key in ("nr", "mi", "ci", "mb", "cb"))
                except (KeyError, ValueError) as exc:
                    raise ValueError(f"malformed line counters in JaCoCo source {path}") from exc
                if number < 1 or number in lines or min(mi, ci, mb, cb) < 0 or mi + ci == 0:
                    raise ValueError(f"invalid or duplicate JaCoCo line {path}:{number}")
                lines[number] = ci > 0
                source_rows.append((mi, ci, mb, cb))
            verify_counters(source, jacoco_totals(source_rows))
            coverage[path] = lines
            package_rows.extend(source_rows)
        totals = jacoco_totals(package_rows)
        verify_counters(package, totals)
        # Class summaries count instructions even when their source line records are missing.
        for kind in ("INSTRUCTION", "BRANCH"):
            counters = package.findall(f"class/counter[@type='{kind}']")
            actual = tuple(sum(int(counter[key]) for counter in (c.attrib for c in counters))
                           for key in ("missed", "covered"))
            if actual != totals[kind]:
                raise ValueError(f"JaCoCo class and source {kind} totals disagree in {directory}")
        report_rows.extend(package_rows)
    verify_counters(root, jacoco_totals(report_rows))
    if not coverage:
        raise ValueError("the JaCoCo report has no source records")
    return coverage


def load_lcov(report, prefix):
    text = _safe_read(report).rstrip() + "\n"
    *records, tail = text.split("\nend_of_record\n")
    if not records or tail.strip():
        raise ValueError("LCOV report lacks complete source records")
    coverage = {}
    for record in records:
        source, lines, totals = None, {}, {}
        branches, branch_totals = {}, {}
        for row in record.split("\n"):
            key, _, value = row.partition(":")
            if key == "SF":
                if source is not None:
                    raise ValueError("duplicate LCOV SF record")
                source = source_path(prefix, value)
            elif key == "DA":
                fields = value.split(",")
                try:
                    number, hits = map(int, fields[:2])
                except ValueError as exc:
                    raise ValueError(f"malformed LCOV DA record: {row!r}") from exc
                if source is None or len(fields) > 3 or number < 1 or hits < 0 or number in lines:
                    raise ValueError(f"invalid or duplicate LCOV DA record: {row!r}")
                lines[number] = hits > 0
            elif key in ("LF", "LH"):
                if source is None or key in totals:
                    raise ValueError(f"invalid or duplicate LCOV {key} record")
                totals[key] = int(value)
            elif key == "BRDA":
                line, block, branch, taken = value.split(",")
                number, block_id, branch_id = int(line), int(block), int(branch)
                location = (number, block_id, branch_id)
                hits = 0 if taken == "-" else int(taken)
                if source is None or number < 1 or min(block_id, branch_id, hits) < 0 or location in branches:
                    raise ValueError(f"invalid or duplicate LCOV BRDA record: {row!r}")
                branches[location] = hits > 0
            elif key in ("BRF", "BRH"):
                if source is None or key in branch_totals:
                    raise ValueError(f"invalid or duplicate LCOV {key} record")
                branch_totals[key] = int(value)
        if source is None or source in coverage:
            raise ValueError(f"missing or duplicate LCOV source: {source}")
        if totals != {"LF": len(lines), "LH": sum(lines.values())}:
            raise ValueError(f"inconsistent LCOV LF/LH counters in {source}")
        if branch_totals != {"BRF": len(branches), "BRH": sum(branches.values())}:
            raise ValueError(f"inconsistent LCOV BRF/BRH counters in {source}")
        coverage[source] = lines
    return coverage


def verify_lcov_line_map(report, coverage, root):
    """Cross-check LCOV with Vitest's JSON serialization of the statement map."""
    records = json.loads(_safe_read(report))
    expected = {}
    for filename, record in records.items():
        absolute = Path(filename)
        if not absolute.is_absolute() or record["path"] != filename:
            raise ValueError(f"invalid Vitest statement map path: {filename!r}")
        path = absolute.relative_to(root.resolve()).as_posix()
        if path in expected or record["s"].keys() != record["statementMap"].keys():
            raise ValueError(f"inconsistent Vitest statement map in {path}")
        lines = {}
        for key, hits in record["s"].items():
            number = record["statementMap"][key]["start"]["line"]
            if type(number) is not int or number < 1 or type(hits) is not int or hits < 0:
                raise ValueError(f"invalid Vitest statement counter in {path}")
            lines[number] = lines.get(number, False) or hits > 0
        expected[path] = lines
    for path in sorted(coverage.keys() | expected.keys()):
        if coverage.get(path) != expected.get(path):
            raise ValueError(f"LCOV and the Vitest statement map disagree for {path}")


JAVA_WITHOUT_TYPES = re.compile(
    r"\s*(?:(?:package|import(?:\s+static)?)\s+[\w$]+(?:\s*\.\s*(?:[\w$]+|\*))*\s*;\s*|;\s*)*"
)


def has_no_java_types(source):
    # Successful javac is a prerequisite. Only accept the lexical subset known to produce no class.
    if "\\u" in source:
        return False
    without_comments = re.sub(r"//[^\r\n]*|/\*[\s\S]*?\*/", " ", source)
    return JAVA_WITHOUT_TYPES.fullmatch(without_comments) is not None


def require_resolvable(coverage, root, head, prefix, in_scope, kind, runner):
    listing = runner(["git", "--literal-pathspecs", "ls-tree", "-r", "--name-only", "-z", head, "--", prefix],
                     cwd=root, capture_output=True, check=False)
    if listing.returncode:
        raise RuntimeError(f"cannot list tracked coverage sources at {head}")
    tracked = {name.decode("utf-8", "surrogateescape") for name in listing.stdout.split(b"\0") if name}
    for path, lines in coverage.items():
        if path not in tracked or not (root / path).is_file():
            raise ValueError(f"coverage source {path!r} does not exist under {prefix!r} at {head}")
        if lines and max(lines) > len((root / path).read_text(encoding="utf-8").split("\n")):
            raise ValueError(f"coverage line outside source file {path}")
    for path in sorted(tracked - coverage.keys()):
        if not in_scope(path):
            continue
        if kind == "jacoco" and has_no_java_types((root / path).read_text(encoding="utf-8")):
            coverage[path] = {}
            continue
        raise ValueError(f"in-scope source omitted from coverage: {path}")


def require_fresh(report, started, head, root, prefix, runner):
    revision = runner(["git", "rev-parse", "--verify", f"{head}^{{commit}}"],
                      cwd=root, capture_output=True, check=False)
    if revision.returncode or started.read_bytes().strip() != revision.stdout.strip():
        raise ValueError("coverage run marker is missing or names a different revision")
    if report.stat().st_mtime_ns <= started.stat().st_mtime_ns:
        raise ValueError("coverage report predates this test run")
    checkout = runner(["git", "diff", "--quiet", head, "--", prefix], cwd=root, capture_output=True, check=False)
    if checkout.returncode:
        raise ValueError("coverage source checkout differs from the requested revision")


def evaluate(changed, coverage):
    """Per file (covered, missed, uncovered lines) over changed lines the report instruments."""
    rows = {}
    for path, lines in sorted(changed.items()):
        if path not in coverage:
            raise ValueError(f"changed source omitted from coverage: {path}")
        measured = coverage[path]
        covered = sorted(line for line in lines if measured.get(line) is True)
        missed = sorted(line for line in lines if measured.get(line) is False)
        if covered or missed:
            rows[path] = (len(covered), len(missed), missed)
    return rows


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
    parser.add_argument("--line-map", type=Path, help="Vitest JSON statement map (required with LCOV)")
    parser.add_argument("--started", type=Path, required=True, help="file containing the test run's Git revision, written before testing")
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
        require_fresh(args.report, args.started, args.head, args.root, args.prefix, runner)
        coverage = (load_jacoco if args.format == "jacoco" else load_lcov)(args.report, args.prefix)
        require_resolvable(coverage, args.root, args.head, args.prefix, in_scope, args.format, runner)
        if args.format == "lcov":
            if args.line_map is None:
                raise ValueError("--line-map is required for LCOV")
            require_fresh(args.line_map, args.started, args.head, args.root, args.prefix, runner)
            verify_lcov_line_map(args.line_map, coverage, args.root)
        changed = changed_lines(args.base, args.head, in_scope, partial(runner, cwd=args.root))
        rows = evaluate(changed, coverage)
    except (ValueError, RuntimeError, OSError, KeyError, TypeError, AttributeError) as exc:
        print(f"FAIL changed-line coverage: {exc}", file=sys.stderr)
        return 1

    covered = sum(row[0] for row in rows.values())
    missed = sum(row[1] for row in rows.values())
    for path, (hit, miss, lines) in rows.items():
        suffix = f"  uncovered lines {ranges(lines)}" if lines else ""
        print(f"  {path}: {hit}/{hit + miss}{suffix}")
    if covered + missed == 0:
        print(f"PASS changed-line coverage: no executable changed line mapped by the coverage tool ({len(changed)} changed source file(s))")
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
