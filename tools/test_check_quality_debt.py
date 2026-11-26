from collections import Counter
from contextlib import chdir, redirect_stderr, redirect_stdout
from io import StringIO
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

import check_quality_debt as debt


class QualityDebtTest(unittest.TestCase):
    def test_a_same_count_replacement_and_a_restored_diagnostic_are_rejected(self):
        old = ("source.ts", "rule", "message", "old()", 1)
        new = ("source.ts", "rule", "message", "new()", 1)
        for current, previous in ((Counter([new]), Counter([old])), (Counter([old, old]), Counter([old]))):
            with self.assertRaisesRegex(ValueError, "adds or restores"):
                debt.require_decreasing(current, previous)
        debt.require_decreasing(Counter([old]), Counter([old, old]))

    def test_new_diagnostic_and_stale_debt_are_each_rejected(self):
        old = ("source.ts", "rule", "message", "old()", 1)
        new = ("source.ts", "rule", "message", "new()", 1)
        for actual in (Counter(), Counter([old, new]), Counter([new])):
            log = StringIO()
            with redirect_stderr(log):
                self.assertFalse(debt.compare(actual, Counter([old])))
            self.assertRegex(log.getvalue(), "new diagnostic|stale debt")

    def test_eslint_bulk_suppression_cannot_hide_another_expression(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / "example.ts"
            path.write_text("old()\nnew()\n")
            message = {"ruleId": "example", "message": "problem", "line": 1, "column": 1,
                       "suppressions": [{"kind": "file"}]}
            results = [{"filePath": str(path), "messages": [], "suppressedMessages": [message]}]
            previous = Counter(warning.identity for warning in debt.eslint_diagnostics(root, json.dumps(results)))
            message["line"] = 2
            actual = Counter(warning.identity for warning in debt.eslint_diagnostics(root, json.dumps(results)))
            self.assertNotEqual(actual, previous)
            message["suppressions"] = [{"kind": "directive"}]
            with self.assertRaisesRegex(ValueError, "inline suppression"):
                list(debt.eslint_diagnostics(root, json.dumps(results)))

    def test_an_identical_warning_can_shift_down_but_cannot_move_to_new_code(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)

            def git(*args):
                subprocess.run(["git", *args], cwd=root, capture_output=True, check=True)

            git("init", "-q", "-b", "main")
            git("config", "user.name", "CI")
            git("config", "user.email", "ci@example.test")
            git("config", "commit.gpgsign", "false")
            path = root / "Example.java"
            path.write_text("void first() {\n  old();\n}\nvoid second() {\n  other();\n}\n")
            git("add", "Example.java")
            git("commit", "-qm", "base")
            identity = ("Example.java", "javac", "deprecated", "old();", 1)
            path.write_text("// Comment\n" + path.read_text())
            git("commit", "-qam", "shift existing diagnostic")
            debt.reject_changed_occurrences([debt.Diagnostic(identity, 3)], "HEAD^", root)
            path.write_text("// Comment\nvoid first() {\n  fixed();\n}\nvoid second() {\n  old();\n}\n")
            git("commit", "-qam", "move the diagnostic to new code")
            with self.assertRaisesRegex(ValueError, "diagnostic on a changed line"):
                debt.reject_changed_occurrences([debt.Diagnostic(identity, 6)], "HEAD^", root)

    def test_javac_reports_new_deprecation_and_requires_both_fresh_compilations(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "Example.java"
            source.write_text("  call();\n")
            report = (
                "[INFO] Compiling 1 source file with javac to target/classes\n"
                "[INFO] Compiling 1 source file with javac to target/test-classes\n"
                f"[WARNING] {source}:[1,3] call() has been deprecated\n"
                "[INFO] BUILD SUCCESS\n"
            )
            self.assertEqual(list(debt.javac_diagnostics(root, report)),
                             [debt.Diagnostic(("Example.java", "javac", "call() has been deprecated", "call();", 1), 1)])
            for invalid in (report.replace("target/test-classes", "elsewhere"),
                            report + "[WARNING] unmatched warning\n",
                            report.replace("BUILD SUCCESS", "BUILD FAILURE"),
                            report.replace("[INFO] BUILD SUCCESS", "copied text [INFO] BUILD SUCCESS")):
                with self.assertRaises(ValueError):
                    list(debt.javac_diagnostics(root, invalid))

    def test_source_must_be_inside_checkout_and_location_must_be_positive(self):
        with tempfile.TemporaryDirectory() as directory:
            outside = Path(directory) / "outside.ts"
            outside.write_text("outside();\n")
            root = Path(directory) / "repository"
            root.mkdir()
            source = root / "source.ts"
            source.write_text("new();\n")
            alias = root / "alias.ts"
            alias.symlink_to(outside)
            for path in (outside, alias, root / ".." / outside.name):
                with self.subTest(path=path), self.assertRaisesRegex(ValueError, "outside the checkout"):
                    debt.diagnostic(root, path, 1, 1, "example", "message")
            for line, column in ((0, 1), (1, 0), (1, -1), (True, 1)):
                with self.subTest(line=line, column=column), self.assertRaisesRegex(ValueError, "positive integers"):
                    debt.diagnostic(root, source, line, column, "example", "message")
            for path in (root, root / "missing.ts"):
                with self.subTest(path=path), self.assertRaisesRegex(ValueError, "not a regular file"):
                    debt.diagnostic(root, path, 1, 1, "example", "message")

    def test_cli_compares_debt_to_a_resolved_commit_and_fails_for_an_unknown_commit(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)

            def git(*args):
                return subprocess.run(["git", *args], cwd=root, capture_output=True, check=True, text=True).stdout.strip()

            git("init", "-q", "-b", "main")
            git("config", "user.name", "CI")
            git("config", "user.email", "ci@example.test")
            git("config", "commit.gpgsign", "false")
            baseline = root / debt.BASELINES["eslint"]
            baseline.parent.mkdir(parents=True)
            baseline.write_text("[]", encoding="utf-8")
            source = root / "example.ts"
            source.write_text("const example = true;\n", encoding="utf-8")
            git("add", ".")
            git("commit", "-qm", "base")
            git("tag", "-a", "snapshot", "-m", "example base")

            report = root / "eslint.json"
            report.write_text(json.dumps([
                {"filePath": str(source), "messages": [], "suppressedMessages": []},
            ]), encoding="utf-8")
            with chdir(root), redirect_stderr(StringIO()) as errors, redirect_stdout(StringIO()) as output:
                self.assertEqual(debt.main(["eslint", str(report), "--base", "snapshot"]), 0, errors.getvalue())
                self.assertIn("0 recorded diagnostics", output.getvalue())
                self.assertEqual(debt.main(["eslint", str(report), "--base", "unknown-reference"]), 1)
                self.assertIn("FAIL quality debt check", errors.getvalue())
                for bad_ref in ("--base=--option", "../escape", "head@{0}", "bad ref"):
                    with self.subTest(bad_ref=bad_ref):
                        argv = ["eslint", str(report), bad_ref] if bad_ref.startswith("--base=") else ["eslint", str(report), "--base", bad_ref]
                        self.assertEqual(debt.main(argv), 1)
                        self.assertIn("invalid Git revision", errors.getvalue())

    def test_rejects_incomplete_and_malformed_eslint_results(self):
        for invalid, error in (("[]", ValueError), ("{}", ValueError), ("not JSON", json.JSONDecodeError)):
            with self.subTest(invalid=invalid), self.assertRaises(error):
                list(debt.eslint_diagnostics(Path.cwd(), invalid))
        with self.assertRaisesRegex(ValueError, "unsuppressed diagnostics"):
            list(debt.eslint_diagnostics(Path.cwd(), json.dumps([{
                "filePath": "example.ts", "messages": [{"ruleId": "no-undef"}], "suppressedMessages": [],
            }])))


if __name__ == "__main__":
    unittest.main()
