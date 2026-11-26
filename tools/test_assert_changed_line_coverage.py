import contextlib
import json
import os
import xml.etree.ElementTree as ET
import subprocess
import tempfile
import unittest
from io import StringIO
from pathlib import Path

import assert_changed_line_coverage as gate

# The header is what JaCoCo 0.8 writes, DOCTYPE included.
JACOCO_FIXTURE = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">
<report name="etaye-api">
  <package name="dev/aymen/etaye/records">
    <sourcefile name="Service.java">
      <line nr="10" mi="0" ci="3" mb="0" cb="0"/>
      <line nr="11" mi="0" ci="2" mb="1" cb="1"/>
      <line nr="12" mi="4" ci="0" mb="0" cb="0"/>
      <line nr="13" mi="1" ci="0" mb="2" cb="0"/>
      <line nr="20" mi="0" ci="5" mb="0" cb="0"/>
    </sourcefile>
  </package>
</report>
"""
LCOV = "SF:src/record/asof.ts\nDA:1,4\nDA:2,0\nDA:3,1\nLF:3\nLH:2\nend_of_record\n"


def complete_jacoco(fixture):
    """Fill the source, class, package and report counters for a minimal fixture."""
    root = ET.fromstring(fixture)
    for line in root.findall(".//line"):
        for attr in ("mb", "cb"):
            line.attrib.setdefault(attr, "0")

    def add_counters(element, lines):
        counts = gate.jacoco_totals([tuple(int(line.get(k)) for k in ("mi", "ci", "mb", "cb")) for line in lines])
        for kind, (missed, covered) in counts.items():
            ET.SubElement(element, "counter", type=kind, missed=str(missed), covered=str(covered))

    for package in root.findall("package"):
        for source in package.findall("sourcefile"):
            add_counters(source, source.findall("line"))
            add_counters(ET.SubElement(package, "class"), source.findall("line"))
        add_counters(package, package.findall(".//line"))
    add_counters(root, root.findall(".//line"))
    return '<!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">' + ET.tostring(root, encoding="unicode")


JACOCO = complete_jacoco(JACOCO_FIXTURE)


class HunkTest(unittest.TestCase):
    def test_added_modified_and_single_line_hunks(self):
        patch = "@@ -1,2 +1,3 @@\n@@ -9 +10 @@\n@@ -20,0 +22,2 @@\n"
        self.assertEqual(gate.parse_hunks(patch), {1, 2, 3, 10, 22, 23})

    def test_a_pure_deletion_adds_no_line(self):
        self.assertEqual(gate.parse_hunks("@@ -10,3 +9,0 @@\n"), set())

    def test_a_new_file_header_covers_every_line_from_the_first(self):
        self.assertEqual(gate.parse_hunks("@@ -0,0 +1,30 @@\n"), set(range(1, 31)))
        self.assertEqual(gate.parse_hunks("@@ -0,0 +1 @@\n"), {1})

    def test_a_unicode_line_separator_cannot_forge_a_hunk(self):
        forged = "+added text\u2028@@ -1 +999 @@ forged\n@@ -3 +3 @@\n"
        self.assertEqual(gate.parse_hunks(forged), {3})

    def test_context_rows_and_headers_are_ignored(self):
        self.assertEqual(gate.parse_hunks("diff --git a/x b/x\n+++ b/x\n+added\n-removed\n"), set())

    def test_ranges_are_compact(self):
        self.assertEqual(gate.ranges([1, 2, 3, 7, 9, 10]), "1-3, 7, 9-10")
        self.assertEqual(gate.ranges([]), "")


class LoaderTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.report = Path(directory.name) / "report"

    def write(self, text):
        self.report.write_text(text)
        return self.report

    def test_jacoco_lines_map_to_repository_paths(self):
        coverage = gate.load_jacoco(self.write(JACOCO), "apps/api/src/main/java/")
        lines = coverage["apps/api/src/main/java/dev/aymen/etaye/records/Service.java"]
        self.assertEqual(lines, {10: True, 11: True, 12: False, 13: False, 20: True})

    def test_a_partially_covered_branch_line_counts_as_executed(self):
        coverage = gate.load_jacoco(self.write(JACOCO), "p")
        self.assertTrue(coverage["p/dev/aymen/etaye/records/Service.java"][11])

    def test_lcov_lines(self):
        coverage = gate.load_lcov(self.write(LCOV), "apps/web/")
        self.assertEqual(coverage, {"apps/web/src/record/asof.ts": {1: True, 2: False, 3: True}})

    def test_malformed_reports_are_rejected(self):
        for loader, body in (
            (gate.load_jacoco, ""),
            (gate.load_jacoco, "<report><package name='a'>"),
            (gate.load_jacoco, "<report/>"),
            (gate.load_jacoco, "<!DOCTYPE report [<!ENTITY x 'y'>]><report><package name='a'/></report>"),
            (gate.load_jacoco, "<!DOCTYPE report [ ]><report><package name='a'/></report>"),
            (gate.load_jacoco, "<report><!ENTITY x 'y'><package name='a'/></report>"),
            (gate.load_jacoco, "<report><package name='a'><sourcefile name='A.java'><line nr='x' mi='0' ci='1'/>"
                               "</sourcefile></package></report>"),
            (gate.load_jacoco, "<report><package name='a'><sourcefile name='A.java'><line nr='1'/>"
                               "</sourcefile></package></report>"),
            (gate.load_lcov, ""),
            (gate.load_lcov, "SF:a.ts\nDA:1,1\n"),
            (gate.load_lcov, "DA:1,1\nend_of_record\n"),
            (gate.load_lcov, "SF:a.ts\nDA:one,1\nend_of_record\n"),
        ):
            with self.subTest(loader=loader.__name__, body=body[:40]):
                with self.assertRaises(ValueError):
                    loader(self.write(body), "p")

    def test_partial_duplicate_and_malformed_counter_records_fail(self):
        for load, data in (
            (gate.load_jacoco, JACOCO.replace('<line nr="12" mi="4" ci="0" mb="0" cb="0" />', "")),
            (gate.load_jacoco, JACOCO.replace('nr="12"', 'nr="11"')),
            (gate.load_jacoco, JACOCO.replace('mi="4"', 'mi="-4"')),
            (gate.load_jacoco, JACOCO.replace("</sourcefile>", '</sourcefile><sourcefile name="Service.java"/>')),
            (gate.load_lcov, LCOV.replace("DA:2,0\n", "")),
            (gate.load_lcov, LCOV.replace("DA:2,0", "DA:1,0")),
            (gate.load_lcov, LCOV.replace("DA:2,0", "DA:2,-1")),
            (gate.load_lcov, LCOV + LCOV),
            (gate.load_lcov, LCOV.replace("src/record/", "../record/")),
            (gate.load_lcov, LCOV.replace("src/record/", "/tmp/")),
        ):
            with self.subTest(data=data):
                with self.assertRaises(ValueError):
                    load(self.write(data), "p")

    def test_a_missing_report_is_rejected(self):
        with self.assertRaises(ValueError):
            gate.load_jacoco(self.report.parent / "absent.xml", "p")


class EvaluationTest(unittest.TestCase):
    coverage = {"a.java": {1: True, 2: True, 3: True, 4: True, 5: False}}

    def test_only_instrumented_changed_lines_count(self):
        rows = gate.evaluate({"a.java": {1, 2, 99, 100}}, self.coverage)
        self.assertEqual(rows, {"a.java": (2, 0, [])})

    def test_missed_lines_are_listed(self):
        rows = gate.evaluate({"a.java": {4, 5}}, self.coverage)
        self.assertEqual(rows["a.java"], (1, 1, [5]))

    def test_a_changed_file_absent_from_the_report_fails(self):
        with self.assertRaisesRegex(ValueError, "omitted"):
            gate.evaluate({"b.java": {1}}, self.coverage)


class GateTest(unittest.TestCase):
    """The command, against a real Git history and real report files."""

    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.repo = Path(directory.name)
        self.git("init", "-q", "-b", "main")
        self.git("config", "user.email", "ci@example.test")
        self.git("config", "user.name", "CI")
        self.git("config", "commit.gpgsign", "false")
        self.source = "apps/api/src/main/java/dev/aymen/etaye/records/Service.java"
        self.write(self.source, "\n".join(f"line {n}" for n in range(1, 31)) + "\n")
        self.write("docs/note.md", "n\n")
        self.git("add", "-A")
        self.git("commit", "-q", "-m", "base")

    def git(self, *args):
        return subprocess.run(["git", *args], cwd=self.repo, capture_output=True, check=True, text=True)

    def write(self, name, text):
        path = self.repo / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)

    def edit_lines(self, numbers, name=None):
        name = name or self.source
        lines = (self.repo / name).read_text().splitlines()
        for number in numbers:
            lines[number - 1] = f"changed {number}"
        self.write(name, "\n".join(lines) + "\n")

    def change_lines(self, numbers, name=None):
        self.edit_lines(numbers, name)
        self.git("add", "-A")
        self.git("commit", "-q", "-m", "change")

    def run_gate(self, report_text, *extra, kind="jacoco", include=None, stale=False, wrong_revision=False, original_lcov=None):
        report = self.repo / "report.xml"
        started = self.repo / "started"
        started.write_text(self.git("rev-parse", "HEAD^1" if wrong_revision else "HEAD").stdout)
        report.write_text(report_text)
        # Set a deterministic ordering even on filesystems with coarse timestamp resolution.
        stamp = started.stat().st_mtime_ns + (-1 if stale else 1) * 1_000_000_000
        os.utime(report, ns=(stamp, stamp))
        argv = [kind, str(report), "--prefix", "apps/api/src/main/java/" if kind == "jacoco" else "apps/web/",
                "--include", include or "apps/api/src/main/java/**/*.java", "--base", "HEAD^1", "--head", "HEAD",
                "--root", str(self.repo), "--started", str(started), *extra]
        if kind == "lcov":
            original = self.repo / "original.info"
            original.write_text(report_text if original_lcov is None else original_lcov)
            statements = {}
            for path, lines in gate.load_lcov(original, "apps/web/").items():
                absolute = str(self.repo / path)
                statements[absolute] = {
                    "path": absolute,
                    "s": {str(n): int(hit) for n, hit in lines.items()},
                    "statementMap": {str(n): {"start": {"line": n}} for n in lines},
                }
            line_map = self.repo / "coverage-final.json"
            line_map.write_text(json.dumps(statements))
            os.utime(line_map, ns=(stamp, stamp))
            argv += ["--line-map", str(line_map)]
        out, err = StringIO(), StringIO()
        runner = lambda command, **kwargs: subprocess.run(command, **({"cwd": self.repo} | kwargs))  # noqa: E731
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = gate.main(argv, runner)
        return code, out.getvalue(), err.getvalue()

    def test_fully_covered_change_passes(self):
        self.change_lines([10, 11, 20])
        code, out, _ = self.run_gate(JACOCO)
        self.assertEqual(code, 0, out)
        self.assertIn("PASS changed-line coverage: 3/3 executable changed lines (100.0%", out)

    def test_untested_addition_fails_and_names_the_lines(self):
        self.change_lines([10, 12, 13])
        code, out, _ = self.run_gate(JACOCO)
        self.assertEqual(code, 1)
        self.assertIn("1/3", out)
        self.assertIn("uncovered lines 12-13", out)
        self.assertIn("FAIL changed-line coverage", out)

    def test_threshold_boundary(self):
        self.change_lines([10, 11, 20, 12])  # 3 of 4 executable lines are covered: 75 %
        self.assertEqual(self.run_gate(JACOCO, "--min-percent", "75")[0], 0)
        self.assertEqual(self.run_gate(JACOCO, "--min-percent", "75.1")[0], 1)
        self.assertEqual(self.run_gate(JACOCO, "--min-percent", "0")[0], 0)

    def test_default_threshold_is_eighty_percent(self):
        self.change_lines([10, 11, 20, 12])
        self.assertEqual(self.run_gate(JACOCO)[0], 1)

    def test_a_change_without_executable_lines_has_nothing_to_measure(self):
        self.change_lines([1, 2, 3])  # lines the report does not instrument: comments, declarations
        code, out, _ = self.run_gate(JACOCO)
        self.assertEqual(code, 0)
        self.assertIn("no executable changed line to measure", out)

    def test_documentation_only_change_has_nothing_to_measure(self):
        self.change_lines([1], name="docs/note.md")
        code, out, _ = self.run_gate(JACOCO)
        self.assertEqual(code, 0)
        self.assertIn("0 changed source file(s)", out)

    def test_excluded_and_out_of_scope_files_are_ignored(self):
        self.change_lines([12])
        code, out, _ = self.run_gate(JACOCO, "--exclude", "apps/api/src/main/java/**/Service.java")
        self.assertEqual(code, 0)
        self.assertIn("0 changed source file(s)", out)

    def test_a_file_added_by_the_change_is_measured_from_its_first_line(self):
        added = "apps/api/src/main/java/dev/aymen/etaye/records/Added.java"
        self.write(added, "\n".join(f"new {n}" for n in range(1, 6)) + "\n")
        self.git("add", "-A")
        self.git("commit", "-q", "-m", "add a file")
        report = complete_jacoco(JACOCO_FIXTURE.replace("</package>", '<sourcefile name="Added.java"><line nr="1" mi="0" ci="2"/>'
                                '<line nr="2" mi="3" ci="0"/></sourcefile></package>'))
        code, out, _ = self.run_gate(report)
        self.assertEqual(code, 1, out)
        self.assertIn("Added.java: 1/2  uncovered lines 2", out)

    def test_deleted_file_adds_no_line(self):
        kept = "apps/api/src/main/java/dev/aymen/etaye/records/Kept.java"
        self.write(kept, "kept\n")
        self.git("add", "-A")
        self.git("commit", "-q", "-m", "kept")
        self.git("rm", "-q", self.source)
        self.git("commit", "-q", "-m", "delete")
        report = complete_jacoco(JACOCO_FIXTURE.replace("Service.java", "Kept.java").replace(
            "\n".join(line for line in JACOCO_FIXTURE.splitlines() if "<line" in line), '<line nr="1" mi="0" ci="1"/>'))
        self.assertEqual(self.run_gate(report)[0], 0)

    def test_malformed_missing_or_empty_report_fails(self):
        self.change_lines([10])
        for body in ("", "<report", "<report/>"):
            with self.subTest(body=body):
                code, _, err = self.run_gate(body)
                self.assertEqual(code, 1)
                self.assertIn("FAIL changed-line coverage", err)

    def test_an_unavailable_change_list_fails_closed(self):
        report = self.repo / "report.xml"
        report.write_text(JACOCO)
        failing = lambda command, **kwargs: subprocess.CompletedProcess(command, 128, b"", b"")  # noqa: E731
        err = StringIO()
        with contextlib.redirect_stderr(err):
            code = gate.main(["jacoco", str(report), "--prefix", "apps/api/src/main/java/", "--include", "**",
                              "--root", str(self.repo), "--started", str(self.repo / "started")], failing)
        self.assertEqual(code, 1)
        self.assertIn("different revision", err.getvalue())

    def test_invalid_threshold_is_rejected(self):
        self.change_lines([10])
        self.assertEqual(self.run_gate(JACOCO, "--min-percent", "101")[0], 1)

    def test_a_diff_attribute_does_not_hide_the_changed_lines(self):
        self.write(".gitattributes", "*.java -diff\n")
        self.git("add", "-A")
        self.git("commit", "-q", "-m", "attributes")
        self.change_lines([10, 12, 13])  # two of three executable lines are not executed
        code, out, _ = self.run_gate(JACOCO)
        self.assertEqual(code, 1, out)
        self.assertIn("1/3", out)

    def test_report_with_one_valid_source_and_one_stale_path_fails(self):
        self.change_lines([10])
        extra = '<sourcefile name="OldName.java"/>'
        report = complete_jacoco(JACOCO_FIXTURE.replace("</package>", extra + "</package>"))
        code, _, err = self.run_gate(report)
        self.assertEqual(code, 1)
        self.assertIn("OldName.java", err)
        self.assertIn("does not exist", err)

    def test_stale_report_and_wrong_revision_fail(self):
        self.change_lines([10])
        for options, message in (({"stale": True}, "predates"), ({"wrong_revision": True}, "different revision")):
            code, _, err = self.run_gate(JACOCO, **options)
            self.assertEqual(code, 1)
            self.assertIn(message, err)

    def test_an_omitted_java_business_source_fails_even_with_valid_coverage_for_another(self):
        other = self.source.replace("Service.java", "Missing.java")
        self.write(other, "package dev.aymen.etaye.records; class Missing { int business() { return 1; } }\n")
        self.edit_lines([10])
        self.git("add", "-A")
        self.git("commit", "-qm", "partially reported")
        code, _, err = self.run_gate(JACOCO)
        self.assertEqual(code, 1)
        self.assertIn("in-scope source omitted", err)
        self.assertIn("Missing.java", err)

    def test_a_classless_java_unit_and_an_empty_interface_record_pass(self):
        self.write(self.source.replace("Service.java", "Notes.java"), "// comment class Foo {}\nimport java.util.List;\n")
        self.write(self.source.replace("Service.java", "Port.java"), "interface Port { void run(); }\n")
        self.git("add", "-A")
        self.git("commit", "-qm", "non-executable units")
        report = complete_jacoco(JACOCO_FIXTURE.replace("</package>", '<sourcefile name="Port.java"/></package>'))
        code, out, _ = self.run_gate(report)
        self.assertEqual(code, 0)
        self.assertIn("no executable changed line", out)

    def test_an_omitted_web_business_source_fails_but_an_empty_type_record_passes(self):
        self.write("apps/web/src/record/asof.ts", "a\nb\nc\n")
        self.write("apps/web/src/record/extra.ts", "export interface Port { id: number }\n")
        self.git("add", "-A")
        self.git("commit", "-qm", "web sources")
        options = {"kind": "lcov", "include": "apps/web/src/**/*.ts"}
        # The other file has 2/3 coverage; lower the test's threshold to isolate the inventory failure.
        code, _, err = self.run_gate(LCOV, "--min-percent", "60", **options)
        self.assertEqual(code, 1)
        self.assertIn("extra.ts", err)
        empty = "SF:src/record/extra.ts\nLF:0\nLH:0\nend_of_record\n"
        self.assertEqual(self.run_gate(LCOV + empty, "--min-percent", "60", **options)[0], 0)

    def test_a_wholly_untested_file_fails_even_when_the_total_passes(self):
        second = "apps/api/src/main/java/dev/aymen/etaye/records/Other.java"
        self.write(second, "\n".join(f"other {n}" for n in range(1, 31)) + "\n")
        self.git("add", "-A")
        self.git("commit", "-q", "-m", "other")
        covered_lines = "".join(f'<line nr="{n}" mi="0" ci="1"/>' for n in range(1, 31))
        untested_lines = "".join(f'<line nr="{n}" mi="1" ci="0"/>' for n in range(1, 13))
        report = ('<?xml version="1.0"?><report name="x"><package name="dev/aymen/etaye/records">'
                  f'<sourcefile name="Service.java">{covered_lines}</sourcefile>'
                  f'<sourcefile name="Other.java">{untested_lines}</sourcefile></package></report>')
        # One commit changes both: 30 covered lines in Service.java and 12 untested lines in Other.java (71.4 %).
        self.edit_lines(range(1, 31))
        self.edit_lines(range(1, 13), name=second)
        self.git("add", "-A")
        self.git("commit", "-q", "-m", "both")
        code, out, _ = self.run_gate(complete_jacoco(report), "--min-percent", "70")
        self.assertEqual(code, 1, out)
        self.assertIn("Other.java: below 50% with at least 10 executable changed lines", out)
        self.assertIn("30/42 executable changed lines (71.4%", out)
        # A small untested file does not trip the per-file floor.
        small = ('<?xml version="1.0"?><report name="x"><package name="dev/aymen/etaye/records">'
                 f'<sourcefile name="Service.java">{covered_lines}</sourcefile>'
                 '<sourcefile name="Other.java"><line nr="1" mi="1" ci="0"/><line nr="2" mi="1" ci="0"/>'
                 '</sourcefile></package></report>')
        self.assertEqual(self.run_gate(complete_jacoco(small), "--min-percent", "70")[0], 0)

    def test_lcov_reports_are_supported(self):
        web = "apps/web/src/record/asof.ts"
        self.write(web, "a\nb\nc\nd\n")
        self.git("add", "-A")
        self.git("commit", "-q", "-m", "web")
        self.change_lines([1, 3], name=web)
        code, out, _ = self.run_gate(LCOV, kind="lcov", include="apps/web/src/**/*.ts")
        self.assertEqual(code, 0, out)
        self.assertIn("2/2", out)
        self.change_lines([2], name=web)
        self.assertEqual(self.run_gate(LCOV, kind="lcov", include="apps/web/src/**/*.ts")[0], 1)

        # The LCOV record is internally consistent after dropping its only missed line.
        partial = LCOV.replace("DA:2,0\n", "").replace("LF:3", "LF:2")
        code, _, err = self.run_gate(partial, kind="lcov", include="apps/web/src/**/*.ts", original_lcov=LCOV)
        self.assertEqual(code, 1)
        self.assertIn("statement map disagree", err)


if __name__ == "__main__":
    unittest.main()
