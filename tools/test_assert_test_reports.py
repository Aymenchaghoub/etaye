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

    def write_suite(self, filename="TEST-suite.xml", **overrides):
        counts = dict(tests=3, failures=0, errors=0, skipped=0)
        counts.update(overrides)
        attributes = " ".join(f'{name}="{value}"' for name, value in counts.items())
        (self.root / filename).write_text(f"<testsuite {attributes}/>", encoding="utf-8")

    def write_playwright(self, **overrides):
        stats = dict(expected=3, unexpected=0, flaky=0, skipped=0)
        stats.update(overrides)
        path = self.root / "report.json"
        path.write_text(json.dumps({"stats": stats}), encoding="utf-8")
        return path

    def check_report(self, kind, path, expected_code, message, minimum=1):
        output = StringIO()
        with redirect_stdout(output):
            code = main([kind, str(path), "--min-tests", str(minimum)])
        self.assertEqual(code, expected_code, output.getvalue())
        self.assertEqual(len(output.getvalue().splitlines()), 1)
        self.assertIn(message, output.getvalue())

    def test_surefire_sums_all_suites(self):
        self.write_suite()
        self.write_suite("TEST-other.xml", tests=2)
        self.check_report("surefire", self.root, 0, "tests=5", minimum=5)

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

    def test_surefire_rejects_failures_errors_and_skips(self):
        for field in ("failures", "errors", "skipped"):
            with self.subTest(field=field):
                self.write_suite(**{field: 1})
                self.check_report("surefire", self.root, 1, f"{field}=1")

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
        self.check_report("playwright", self.write_playwright(), 0, "tests=3", minimum=3)

    def test_playwright_rejects_missing_or_malformed_report(self):
        path = self.root / "missing.json"
        self.check_report("playwright", path, 1, "Cannot read Playwright report")
        path.write_text("{", encoding="utf-8")
        self.check_report("playwright", path, 1, "Malformed JSON")

    def test_playwright_rejects_invalid_test_count(self):
        self.check_report("playwright", self.write_playwright(expected=0), 1, "tests=0")
        self.check_report("playwright", self.write_playwright(expected=True), 1, "Invalid stats")
        self.check_report("playwright", self.write_playwright(), 1, "required minimum: 4", minimum=4)

    def test_playwright_rejects_unexpected_flaky_and_skipped_tests(self):
        for field in ("unexpected", "flaky", "skipped"):
            with self.subTest(field=field):
                self.check_report("playwright", self.write_playwright(**{field: 1}), 1, f"{field}=1")


if __name__ == "__main__":
    unittest.main()
