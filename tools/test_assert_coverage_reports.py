import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from io import StringIO
from pathlib import Path

from assert_coverage_reports import main


class CoverageReportsTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.report = Path(directory.name) / "coverage"

    def test_lcov_retains_uncovered_files_in_total(self):
        self.report.write_text("SF:used.ts\nLF:3\nLH:2\nend_of_record\n"
                               "SF:unused.ts\nLF:2\nLH:0\nend_of_record\n")
        with redirect_stdout(StringIO()) as output:
            self.assertEqual(main(["lcov", str(self.report)]), 0)
        self.assertIn("2/5 (40.00%)", output.getvalue())

    def test_reports_reject_truncation_and_zero_hits(self):
        for kind, body in (
            ("jacoco", '<report><counter type="LINE" covered="1" missed="0"/>'),
            ("lcov", "SF:used.ts\nLH:2\nLF:3\n"),
            ("lcov", "SF:unused.ts\nLH:0\nLF:3\nend_of_record\n"),
        ):
            with self.subTest(kind=kind, body=body):
                self.report.write_text(body)
                with redirect_stderr(StringIO()) as error:
                    self.assertEqual(main([kind, str(self.report)]), 1)
                self.assertIn(f"FAIL {kind}", error.getvalue())
