import contextlib
import subprocess
import tempfile
import unittest
from io import StringIO
from pathlib import Path

import assert_changed_line_coverage as gate

# The header is what JaCoCo 0.8 writes, DOCTYPE included.
JACOCO = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">
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
  <counter type="LINE" missed="2" covered="3"/>
</report>
"""
LCOV = "SF:src/record/asof.ts\nDA:1,4\nDA:2,0\nDA:3,1\nLF:3\nLH:2\nend_of_record\n"


class HunkTest(unittest.TestCase):
    def test_added_modified_and_single_line_hunks(self):
        patch = "@@ -1,2 +1,3 @@\n@@ -9 +10 @@\n@@ -20,0 +22,2 @@\n"
        self.assertEqual(gate.parse_hunks(patch), {1, 2, 3, 10, 22, 23})

    def test_a_pure_deletion_adds_no_line(self):
        self.assertEqual(gate.parse_hunks("@@ -10,3 +9,0 @@\n"), set())

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

    def test_a_missing_report_is_rejected(self):
        with self.assertRaises(ValueError):
            gate.load_jacoco(self.report.parent / "absent.xml", "p")


class EvaluationTest(unittest.TestCase):
    coverage = {"a.java": {1: True, 2: True, 3: True, 4: True, 5: False}}

    def test_only_instrumented_changed_lines_count(self):
        rows, unmeasured = gate.evaluate({"a.java": {1, 2, 99, 100}}, self.coverage)
        self.assertEqual(rows, {"a.java": (2, 0, [])})
        self.assertEqual(unmeasured, [])

    def test_missed_lines_are_listed(self):
        rows, _ = gate.evaluate({"a.java": {4, 5}}, self.coverage)
        self.assertEqual(rows["a.java"], (1, 1, [5]))

    def test_a_file_absent_from_the_report_is_named_not_counted(self):
        rows, unmeasured = gate.evaluate({"b.java": {1}}, self.coverage)
        self.assertEqual((rows, unmeasured), ({}, ["b.java"]))


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

    def run_gate(self, report_text, *extra, kind="jacoco", include=None):
        report = self.repo / "report.xml"
        report.write_text(report_text)
        argv = [kind, str(report), "--prefix", "apps/api/src/main/java/" if kind == "jacoco" else "apps/web/",
                "--include", include or "apps/api/src/main/java/**/*.java", "--base", "HEAD^1", "--head", "HEAD",
                "--root", str(self.repo), *extra]
        out, err = StringIO(), StringIO()
        runner = lambda command, **kwargs: subprocess.run(command, cwd=self.repo, **kwargs)  # noqa: E731
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

    def test_a_new_file_is_measured_from_its_first_line(self):
        new = "apps/api/src/main/java/dev/aymen/etaye/records/Service.java"
        self.write(new, "\n".join(f"new {n}" for n in range(1, 31)) + "\n")  # every line is new
        self.git("add", "-A")
        self.git("commit", "-q", "-m", "rewrite")
        code, out, _ = self.run_gate(JACOCO)
        self.assertEqual(code, 1, out)  # lines 12 and 13 are not executed
        self.assertIn("3/5", out)

    def test_deleted_file_adds_no_line(self):
        kept = "apps/api/src/main/java/dev/aymen/etaye/records/Kept.java"
        self.write(kept, "kept\n")
        self.git("add", "-A")
        self.git("commit", "-q", "-m", "kept")
        self.git("rm", "-q", self.source)
        self.git("commit", "-q", "-m", "delete")
        report = JACOCO.replace("</package>", '<sourcefile name="Kept.java"><line nr="1" mi="0" ci="1"/></sourcefile></package>')
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
                              "--root", str(self.repo)], failing)
        self.assertEqual(code, 1)
        self.assertIn("exited with 128", err.getvalue())

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

    def test_report_paths_that_do_not_exist_in_the_checkout_fail_closed(self):
        self.change_lines([10])
        for prefix_problem in ("absolute", "wrong-prefix"):
            with self.subTest(prefix_problem):
                report = self.repo / "report.info"
                source = "/home/runner/work/etaye/apps/web/src/a.ts" if prefix_problem == "absolute" else "src/a.ts"
                report.write_text(f"SF:{source}\nDA:1,1\nend_of_record\n")
                argv = ["lcov", str(report), "--prefix", "elsewhere/", "--include", "**", "--base", "HEAD^1",
                        "--head", "HEAD", "--root", str(self.repo)]
                err = StringIO()
                with contextlib.redirect_stderr(err):
                    code = gate.main(argv, lambda command, **kwargs: subprocess.run(command, cwd=self.repo, **kwargs))
                self.assertEqual(code, 1)
                self.assertIn("exists under", err.getvalue())

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
        code, out, _ = self.run_gate(report, "--min-percent", "70")
        self.assertEqual(code, 1, out)
        self.assertIn("Other.java: below 50% with at least 10 executable changed lines", out)
        self.assertIn("30/42 executable changed lines (71.4%", out)
        # A small untested file does not trip the per-file floor.
        small = ('<?xml version="1.0"?><report name="x"><package name="dev/aymen/etaye/records">'
                 f'<sourcefile name="Service.java">{covered_lines}</sourcefile>'
                 '<sourcefile name="Other.java"><line nr="1" mi="1" ci="0"/><line nr="2" mi="1" ci="0"/>'
                 '</sourcefile></package></report>')
        self.assertEqual(self.run_gate(small, "--min-percent", "70")[0], 0)

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


if __name__ == "__main__":
    unittest.main()
