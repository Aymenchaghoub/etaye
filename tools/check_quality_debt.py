#!/usr/bin/env python3
"""Check the exact ESLint and javac diagnostics accepted as existing debt."""
import argparse
from collections import Counter
from functools import partial
import json
from pathlib import Path
import re
import subprocess
import sys
from typing import NamedTuple

from assert_changed_line_coverage import changed_lines

BASELINES = {
    "eslint": Path("apps/web/eslint-debt.json"),
    "javac": Path("apps/api/compiler-debt.json"),
}
JAVAC_WARNING = re.compile(r"^\[WARNING\] (.+\.java):\[(\d+),(\d+)\] (.+)$", re.MULTILINE)


class Diagnostic(NamedTuple):
    identity: tuple[str, str, str, str, int]
    line: int


def diagnostic(root, path, line, column, rule, message):
    relative = Path(path).relative_to(root)
    source = (root / relative).read_text(encoding="utf-8").split("\n")[line - 1]
    identity = (relative.as_posix(), rule, message, source.strip(), column - (len(source) - len(source.lstrip())))
    return Diagnostic(identity, line)


def eslint_diagnostics(root, text):
    results = json.loads(text)
    if not isinstance(results, list) or not results:
        raise ValueError("ESLint did not report any files")
    for result in results:
        if result["messages"]:
            raise ValueError(f"ESLint found unsuppressed diagnostics in {result['filePath']}")
        for message in result["suppressedMessages"]:
            suppressions = message["suppressions"]
            if not suppressions or any(item["kind"] != "file" for item in suppressions):
                raise ValueError(f"unexpected inline suppression in {result['filePath']}")
            text_without_line = re.sub(r"\(at line \d+\)", "(at referenced line)", message["message"])
            yield diagnostic(root, result["filePath"], message["line"], message["column"],
                             message["ruleId"], text_without_line)


def javac_diagnostics(root, text):
    for directory in ("classes", "test-classes"):
        if not re.search(rf"^\[INFO\] Compiling [1-9]\d* sources? files? .* to (?:[^\n]*/)?target/{directory}$",
                         text, re.MULTILINE):
            raise ValueError(f"javac did not report a fresh compilation to {directory}")
    if "[INFO] BUILD SUCCESS" not in text:
        raise ValueError("the compiler debt build did not finish successfully")
    warnings = re.findall(r"^\[WARNING\] .*", text, re.MULTILINE)
    matches = list(JAVAC_WARNING.finditer(text))
    if len(warnings) != len(matches):
        raise ValueError("javac emitted an unrecognized warning; inspect the compiler log")
    for match in matches:
        path, line, column, message = match.groups()
        yield diagnostic(root, path, int(line), int(column), "javac", message)


def require_decreasing(current, previous):
    additions = current - previous
    if additions:
        raise ValueError(f"the baseline adds or restores debt: {next(iter(additions))!r}")


def reject_changed_occurrences(diagnostics, base, root):
    # An old warning and a new warning can have identical text in the same file.
    # Resolve actual occurrences against changed lines so moving debt cannot keep the check green.
    paths = {warning.identity[0] for warning in diagnostics}
    changed = changed_lines(base, "HEAD", paths.__contains__, partial(subprocess.run, cwd=root))
    for warning in diagnostics:
        path, rule, message, _, _ = warning.identity
        if warning.line in changed.get(path, ()):
            raise ValueError(f"diagnostic on a changed line: {path}:{warning.line} [{rule}] {message}")


def compare(actual, expected):
    for label, difference in (("new diagnostic", actual - expected), ("stale debt", expected - actual)):
        for (path, rule, message, source, column), count in sorted(difference.items()):
            print(f"{label} ({count}): {path} [{rule}] {message} | {source} (column {column})", file=sys.stderr)
    return actual == expected


def read_baseline(data):
    return Counter(tuple(item) for item in json.loads(data))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("tool", choices=BASELINES)
    parser.add_argument("report", type=Path)
    parser.add_argument("--base", help="Git revision whose baseline must not grow")
    args = parser.parse_args()
    try:
        root = Path(subprocess.check_output(["git", "rev-parse", "--show-toplevel"], text=True).strip())
        path = BASELINES[args.tool]
        baseline = read_baseline((root / path).read_text(encoding="utf-8"))
        if args.base:
            # A successful ls-tree with no entry is possible while bootstrapping the baseline.
            if subprocess.check_output(["git", "ls-tree", args.base, "--", path], cwd=root):
                previous = subprocess.check_output(["git", "show", f"{args.base}:{path}"], cwd=root)
                require_decreasing(baseline, read_baseline(previous))
        parse = eslint_diagnostics if args.tool == "eslint" else javac_diagnostics
        diagnostics = list(parse(root, args.report.read_text(encoding="utf-8")))
        actual = Counter(warning.identity for warning in diagnostics)
        if not compare(actual, baseline):
            return 1
        if args.base:
            reject_changed_occurrences(diagnostics, args.base, root)
        print(f"{args.tool}: {actual.total()} recorded diagnostics, no new or stale debt")
        return 0
    except (ValueError, OSError, RuntimeError, subprocess.CalledProcessError, KeyError, IndexError, TypeError) as exc:
        print(f"FAIL quality debt check: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
