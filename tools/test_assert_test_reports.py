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

    def write_playwright(self, *, files=("stubbed-probe.spec.ts",) * 3, test_overrides=None,
                         suites=None, errors=(), **overrides):
        stats = dict(expected=3, unexpected=0, flaky=0, skipped=0)
        stats.update(overrides)
        test = {"projectId": "", "expectedStatus": "passed", "status": "expected", "results": [{"status": "passed"}]}
        test.update(test_overrides or {})
        specs = [{"file": file, "title": f"test{index}", "tests": [test]} for index, file in enumerate(files)]
        data = {
            "config": {"rootDir": "/workspace/apps/web/tests", "configFile": "/workspace/apps/web/playwright.config.ts"},
            "suites": suites if suites is not None else (
                [{"title": "file suite", "suites": [{"title": "describe suite", "specs": specs}]}] if specs else []
            ),
            "errors": errors,
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
        self.check_report("surefire", self.root, 1, "declares tests=5 but contains 0 unique testcase elements")

    def test_surefire_rejects_testcase_count_mismatch(self):
        cases = '<testcase classname="Suite" name="one"/><testcase classname="Suite" name="two"/>'
        for declared in (1, 3):
            with self.subTest(declared=declared):
                self.write_suite(tests=declared, cases=cases)
                self.check_report("surefire", self.root, 1, "contains 2 unique testcase elements")

    def test_surefire_rejects_duplicate_testcases_in_one_suite(self):
        case = '<testcase classname="Suite" name="test"/>'
        self.write_suite(tests=2, cases=case * 2)
        self.check_report("surefire", self.root, 1, "Duplicate Surefire testcase")

    def test_surefire_rejects_duplicate_testcases_across_reports(self):
        self.write_suite()
        self.write_suite("TEST-copy.xml")
        self.check_report("surefire", self.root, 1, "Duplicate Surefire testcase")

    def test_surefire_rejects_nested_testcase_elements(self):
        self.write_suite(tests=1, cases='<wrapper><testcase classname="Suite" name="test"/></wrapper>')
        self.check_report("surefire", self.root, 1, "Nested Surefire testcase")

    def test_surefire_requires_case_name_and_classname(self):
        for attributes in ('name="test"', 'classname="Suite"', 'name=" " classname="Suite"',
                           'name="test" classname=""'):
            with self.subTest(attributes=attributes):
                self.write_suite(tests=1, cases=f"<testcase {attributes}/>")
                self.check_report("surefire", self.root, 1, "requires a name and classname")

    def test_surefire_requires_each_named_suite(self):
        self.write_suite("TEST-example.Missing.xml")
        self.check_report("surefire", self.root, 1, "Missing required Surefire suites: example.Missing",
                          options=("--require-suite", "example.Suite", "--require-suite", "example.Missing"))

    def test_surefire_matches_required_suite_by_suite_name_for_archunit_style_reports(self):
        # Real ArchUnit reports: the suite name is the full class name, the testcases carry the simple name.
        (self.root / "TEST-example.ArchSuite.xml").write_text(
            '<testsuite name="example.ArchSuite" tests="2" failures="0" errors="0" skipped="0">'
            '<testcase name="ruleOne" classname="ArchSuite"/><testcase name="ruleTwo" classname="ArchSuite"/></testsuite>',
            encoding="utf-8")
        self.check_report("surefire", self.root, 0, "tests=2", minimum=2, options=("--require-suite", "example.ArchSuite"))
        self.check_report("surefire", self.root, 1, "Missing required Surefire suites: example.Other",
                          minimum=2, options=("--require-suite", "example.Other"))

    def test_surefire_rejects_failures_errors_and_skips(self):
        for field in ("failures", "errors", "skipped"):
            with self.subTest(field=field):
                self.write_suite(**{field: 1})
                self.check_report("surefire", self.root, 1, f"{field}=1")

    def test_surefire_rejects_failure_error_and_skip_elements_even_with_green_totals(self):
        for tag in ("failure", "error", "skipped"):
            with self.subTest(tag=tag):
                self.write_suite(tests=1, cases=f'<testcase classname="Suite" name="test"><{tag}/></testcase>')
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

    def test_playwright_rejects_global_errors(self):
        path = self.write_playwright(errors=[{"message": "worker teardown failed"}])
        self.check_report("playwright", path, 1, "Playwright report contains global errors")

    def test_playwright_identifies_tests_by_file_full_title_and_project(self):
        test = {"projectId": "chromium", "expectedStatus": "passed",
                "status": "expected", "results": [{"status": "passed"}]}
        spec = {"file": "stubbed-probe.spec.ts", "title": "probe", "tests": [test]}
        other_file = dict(spec, file="other.spec.ts")
        other_project = dict(test, projectId="firefox")
        suites = [{"title": "outer", "suites": [
            {"title": "group A", "specs": [dict(spec, tests=[test, other_project]), other_file]},
            {"title": "group B", "specs": [spec]},
        ]}]
        path = self.write_playwright(suites=suites, expected=4)
        self.check_report("playwright", path, 0, "tests=4", minimum=4)
        suites[0]["suites"].append({"title": "group B", "specs": [spec]})
        path = self.write_playwright(suites=suites, expected=5)
        self.check_report("playwright", path, 1, "Duplicate Playwright test", minimum=5)

    def test_playwright_requires_spec_title(self):
        test = {"projectId": "", "expectedStatus": "passed", "status": "expected", "results": [{"status": "passed"}]}
        for title in ({}, {"title": " "}):
            with self.subTest(title=title):
                spec = {"file": "stubbed-probe.spec.ts", "tests": [test], **title}
                path = self.write_playwright(suites=[{"title": "group", "specs": [spec]}], expected=1)
                self.check_report("playwright", path, 1, "Invalid Playwright spec title")

    def test_playwright_requires_project_id(self):
        path = self.write_playwright(test_overrides={"projectId": None})
        self.check_report("playwright", path, 1, "Invalid Playwright project ID")

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
