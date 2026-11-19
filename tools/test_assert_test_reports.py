"""The test-report command rejects absent, incomplete and unsuccessful results."""
import json
import tempfile
import unittest
from contextlib import redirect_stdout
from io import StringIO
from pathlib import Path

from assert_test_reports import main


class AssertTestReportsTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)

    def write_suite(self, filename="TEST-suite.xml", *, cases=None, classname="example.Suite", **overrides):
        counts = dict(tests=3, failures=0, errors=0, skipped=0)
        counts.update(overrides)
        attributes = " ".join(f'{name}="{value}"' for name, value in counts.items())
        if cases is None:
            cases = "".join(f'<testcase name="test{index}" classname="{classname}"/>' for index in range(counts["tests"]))
        (self.root / filename).write_text(f"<testsuite {attributes}>{cases}</testsuite>", encoding="utf-8")

    def write_playwright(self, *, files=("stubbed-probe.spec.ts",) * 3, test_overrides=None, **overrides):
        stats = dict(expected=3, unexpected=0, flaky=0, skipped=0)
        stats.update(overrides)
        test = {"expectedStatus": "passed", "status": "expected", "results": [{"status": "passed"}]}
        test.update(test_overrides or {})
        specs = [{"file": file, "tests": [test]} for file in files]
        data = {
            "config": {"rootDir": "/workspace/apps/web/tests", "configFile": "/workspace/apps/web/playwright.config.ts"},
            "suites": [{"suites": [{"specs": specs}]}] if specs else [],
            "stats": stats,
        }
        path = self.root / "report.json"
        path.write_text(json.dumps(data), encoding="utf-8")
        return path

    def check_report(self, kind, path, expected_code, message, minimum=1, options=()):
        output = StringIO()
        with redirect_stdout(output):
            code = main([kind, str(path), "--min-tests", str(minimum), *options])
        self.assertEqual(code, expected_code, output.getvalue())
        self.assertEqual(len(output.getvalue().splitlines()), 1)
        self.assertIn(message, output.getvalue())

    def test_surefire_sums_all_suites(self):
        self.write_suite()
        self.write_suite("TEST-other.xml", tests=2, classname="example.Other")
        self.check_report("surefire", self.root, 0, "tests=5", minimum=5,
                          options=("--require-suite", "example.Suite", "--require-suite", "example.Other"))

    def test_surefire_requires_report_files(self):
        for directory in (self.root, self.root / "absent"):
            with self.subTest(directory=directory):
                self.check_report("surefire", directory, 1, "missing or contains no TEST-*.xml")

    def test_surefire_rejects_zero_tests_in_any_suite(self):
        self.write_suite()
        self.write_suite("TEST-empty.xml", tests=0)
        self.check_report("surefire", self.root, 1, "Zero tests in Surefire report TEST-empty.xml")

    def test_surefire_requires_minimum_test_count(self):
        self.write_suite()
        self.check_report("surefire", self.root, 1, "required minimum: 4", minimum=4)

    def test_surefire_rejects_missing_testcase_elements(self):
        self.write_suite(tests=5, cases="")
        self.check_report("surefire", self.root, 1, "declares tests=5 but contains 0 testcase elements")

    def test_surefire_rejects_testcase_count_mismatch(self):
        for declared in (1, 3):
            with self.subTest(declared=declared):
                self.write_suite(tests=declared, cases="<testcase/><testcase/>")
                self.check_report("surefire", self.root, 1, "contains 2 testcase elements")

    def test_surefire_requires_each_named_suite(self):
        self.write_suite("TEST-example.Missing.xml")
        self.check_report("surefire", self.root, 1, "Missing required Surefire suites: example.Missing",
                          options=("--require-suite", "example.Suite", "--require-suite", "example.Missing"))

    def test_surefire_rejects_failures_errors_and_skips(self):
        for field in ("failures", "errors", "skipped"):
            with self.subTest(field=field):
                self.write_suite(**{field: 1})
                self.check_report("surefire", self.root, 1, f"{field}=1")

    def test_surefire_rejects_failure_error_and_skip_elements_even_with_green_totals(self):
        for tag in ("failure", "error", "skipped"):
            with self.subTest(tag=tag):
                self.write_suite(tests=1, cases=f"<testcase><{tag}/></testcase>")
                self.check_report("surefire", self.root, 1, f"Found <{tag}>")

    def test_surefire_rejects_malformed_xml(self):
        (self.root / "TEST-malformed.xml").write_text("<testsuite>", encoding="utf-8")
        self.check_report("surefire", self.root, 1, "Malformed XML")

    def test_surefire_rejects_doctype_and_entity_expansion(self):
        bomb = '<!DOCTYPE testsuite [<!ENTITY a "xxxx"><!ENTITY b "&a;&a;&a;&a;">]><testsuite>&b;</testsuite>'
        report = self.root / "TEST-entities.xml"
        report.write_text(bomb, encoding="utf-8")
        self.check_report("surefire", self.root, 1, "DOCTYPE or entity declaration")
        report.write_bytes(bomb.encode("utf-16-le"))
        self.check_report("surefire", self.root, 1, "Invalid UTF-8 XML")

    def test_playwright_accepts_green_report(self):
        path = self.write_playwright(files=("stubbed-probe.spec.ts", "other.spec.ts", "other.spec.ts"))
        self.check_report("playwright", path, 0, "tests=3", minimum=3, options=(
            "--require-file", "tests/stubbed-probe.spec.ts", "--require-file", "other.spec.ts",
        ))

    def test_playwright_rejects_missing_or_malformed_report(self):
        path = self.root / "missing.json"
        self.check_report("playwright", path, 1, "Cannot read Playwright report")
        path.write_text("{", encoding="utf-8")
        self.check_report("playwright", path, 1, "Malformed JSON")

    def test_playwright_rejects_invalid_test_count(self):
        self.check_report("playwright", self.write_playwright(expected=0), 1, "do not match the 3 passing tests")
        self.check_report("playwright", self.write_playwright(expected=True), 1, "Invalid stats")
        self.check_report("playwright", self.write_playwright(), 1, "required minimum: 4", minimum=4)

    def test_playwright_rejects_inconsistent_outcome_stats(self):
        for field in ("unexpected", "flaky", "skipped"):
            with self.subTest(field=field):
                self.check_report("playwright", self.write_playwright(**{field: 1}), 1, f"{field}=1")

    def test_playwright_rejects_empty_suites_with_positive_stats(self):
        self.check_report("playwright", self.write_playwright(files=()), 1, "do not match the 0 passing tests")

    def test_playwright_rejects_expected_failure(self):
        path = self.write_playwright(test_overrides={"expectedStatus": "failed", "results": [{"status": "failed"}]})
        self.check_report("playwright", path, 1, "expectedStatus='failed'")

    def test_playwright_rejects_unexpected_flaky_and_skipped_tests(self):
        for status in ("unexpected", "flaky", "skipped"):
            with self.subTest(status=status):
                path = self.write_playwright(test_overrides={"status": status})
                self.check_report("playwright", path, 1, f"status='{status}'")

    def test_playwright_requires_passing_results(self):
        for results in ([], [{"status": "skipped"}], [{"status": "failed"}, {"status": "passed"}]):
            with self.subTest(results=results):
                path = self.write_playwright(test_overrides={"results": results})
                self.check_report("playwright", path, 1, "must have only passed results")

    def test_playwright_requires_each_named_file(self):
        self.check_report("playwright", self.write_playwright(), 1, "Missing required Playwright files: other.spec.ts",
                          options=("--require-file", "tests/stubbed-probe.spec.ts", "--require-file", "other.spec.ts"))

    def test_playwright_resolves_required_path_relative_to_config(self):
        path = self.write_playwright(files=("tests/stubbed-probe.spec.ts",), expected=1)
        self.check_report("playwright", path, 1, "Missing required Playwright files: tests/stubbed-probe.spec.ts",
                          options=("--require-file", "tests/stubbed-probe.spec.ts"))


if __name__ == "__main__":
    unittest.main()
