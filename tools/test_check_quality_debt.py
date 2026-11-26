from collections import Counter
from contextlib import redirect_stderr
from io import StringIO
import json
from pathlib import Path
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
            previous = Counter(debt.eslint_diagnostics(root, json.dumps(results)))
            message["line"] = 2
            actual = Counter(debt.eslint_diagnostics(root, json.dumps(results)))
            self.assertNotEqual(actual, previous)
            message["suppressions"] = [{"kind": "directive"}]
            with self.assertRaisesRegex(ValueError, "inline suppression"):
                list(debt.eslint_diagnostics(root, json.dumps(results)))

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
                             [("Example.java", "javac", "call() has been deprecated", "call();", 1)])
            for invalid in (report.replace("target/test-classes", "elsewhere"),
                            report + "[WARNING] unmatched warning\n",
                            report.replace("BUILD SUCCESS", "BUILD FAILURE")):
                with self.assertRaises(ValueError):
                    list(debt.javac_diagnostics(root, invalid))


if __name__ == "__main__":
    unittest.main()
