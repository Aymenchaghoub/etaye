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

    def test_a_valid_jacoco_report_passes(self):
        self.report.write_text('<report name="x"><package name="p"><sourcefile name="A.java">'
                               '<line nr="1" mi="0" ci="2"/></sourcefile></package>'
                               '<counter type="LINE" missed="1" covered="3"/></report>')
        with redirect_stdout(StringIO()) as output:
            self.assertEqual(main(["jacoco", str(self.report)]), 0)
        self.assertIn("3/4 (75.00%)", output.getvalue())

    def test_jacoco_reports_with_missing_or_inconsistent_evidence_fail(self):
        lines = '<sourcefile name="A.java"><line nr="1" mi="0" ci="2"/></sourcefile>'
        for label, body in (
            ("no top-level LINE counter", f'<report><package name="p">{lines}</package></report>'),
            ("no source lines", '<report><counter type="LINE" covered="3" missed="1"/></report>'),
            ("wrong root element", f'<coverage><package name="p">{lines}</package>'
                                   '<counter type="LINE" covered="3" missed="1"/></coverage>'),
            ("non-numeric counter", f'<report><package name="p">{lines}</package>'
                                    '<counter type="LINE" covered="x" missed="1"/></report>'),
            ("negative counter", f'<report><package name="p">{lines}</package>'
                                 '<counter type="LINE" covered="-1" missed="1"/></report>'),
            ("missing attribute", f'<report><package name="p">{lines}</package><counter type="LINE" covered="3"/></report>'),
            ("zero covered lines", f'<report><package name="p">{lines}</package>'
                                   '<counter type="LINE" covered="0" missed="9"/></report>'),
        ):
            with self.subTest(label):
                self.report.write_text(body)
                with redirect_stderr(StringIO()) as error:
                    self.assertEqual(main(["jacoco", str(self.report)]), 1)
                self.assertIn("FAIL jacoco", error.getvalue())

    def test_lcov_records_with_inconsistent_evidence_fail(self):
        for label, body in (
            ("hits above the line count", "SF:a.ts\nLF:2\nLH:3\nend_of_record\n"),
            ("negative hits", "SF:a.ts\nLF:2\nLH:-1\nend_of_record\n"),
            ("missing source path", "LF:2\nLH:1\nend_of_record\n"),
            ("missing counters", "SF:a.ts\nend_of_record\n"),
            ("non-numeric counters", "SF:a.ts\nLF:two\nLH:1\nend_of_record\n"),
            ("empty file", ""),
        ):
            with self.subTest(label):
                self.report.write_text(body)
                with redirect_stderr(StringIO()) as error:
                    self.assertEqual(main(["lcov", str(self.report)]), 1)
                self.assertIn("FAIL lcov", error.getvalue())

    def test_a_missing_report_fails_instead_of_raising(self):
        for kind in ("jacoco", "lcov", "cobertura"):
            with self.subTest(kind):
                with redirect_stderr(StringIO()) as error:
                    self.assertEqual(main([kind, str(self.report.parent / "absent")]), 1)
                self.assertIn(f"FAIL {kind}", error.getvalue())

    def test_a_valid_cobertura_report_passes(self):
        self.report.write_text(
            '<coverage lines-valid="3" lines-covered="2">'
            '<packages><package name="tools"><classes>'
            '<class name="a.py" filename="tools/a.py"><lines>'
            '<line number="1" hits="1"/><line number="2" hits="0"/><line number="3" hits="2"/>'
            '</lines></class></classes></package></packages></coverage>'
        )
        with redirect_stdout(StringIO()) as output:
            self.assertEqual(main(["cobertura", str(self.report)]), 0)
        self.assertIn("2/3 (66.67%)", output.getvalue())

    def test_cobertura_reports_with_missing_or_inconsistent_evidence_fail(self):
        for label, body in (
            ("wrong root element", '<report lines-valid="1" lines-covered="1"/>'),
            ("zero covered lines", '<coverage lines-valid="2" lines-covered="0"><packages><package name="t"><classes><class filename="a.py"><lines><line number="1" hits="0"/><line number="2" hits="0"/></lines></class></classes></package></packages></coverage>'),
            ("missing class filename", '<coverage lines-valid="1" lines-covered="1"><packages><package name="t"><classes><class name="a.py"><lines><line number="1" hits="1"/></lines></class></classes></package></packages></coverage>'),
            ("disagreeing counters", '<coverage lines-valid="2" lines-covered="2"><packages><package name="t"><classes><class filename="a.py"><lines><line number="1" hits="1"/><line number="2" hits="0"/></lines></class></classes></package></packages></coverage>'),
            ("duplicate line number", '<coverage lines-valid="2" lines-covered="2"><packages><package name="t"><classes><class filename="a.py"><lines><line number="1" hits="1"/><line number="1" hits="1"/></lines></class></classes></package></packages></coverage>'),
            ("entity declaration", '<!DOCTYPE coverage [<!ENTITY x "1">]><coverage lines-valid="1" lines-covered="1"/>'),
        ):
            with self.subTest(label):
                self.report.write_text(body)
                with redirect_stderr(StringIO()) as error:
                    self.assertEqual(main(["cobertura", str(self.report)]), 1)
                self.assertIn("FAIL cobertura", error.getvalue())
