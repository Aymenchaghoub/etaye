"""Exercise validate_docs full run and path-safety checks."""
from contextlib import redirect_stdout
from io import StringIO
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

import validate_docs


class ValidateDocsFullTests(unittest.TestCase):
    def test_main_validates_repository_and_writes_report(self):
        with tempfile.TemporaryDirectory() as temporary:
            report = Path(temporary) / "validation.json"
            with patch.object(sys, "argv", ["validate_docs.py", "--report", str(report)]), redirect_stdout(StringIO()):
                code = validate_docs.main()
            self.assertEqual(code, 0)
            payload = json.loads(report.read_text(encoding="utf-8"))
            self.assertTrue(payload["ok"])
            self.assertFalse(payload["applicationTestsExecuted"])
            self.assertGreater(payload["checks"]["markdownFiles"], 100)
            self.assertGreater(payload["checks"]["localLinks"], 1000)

    def test_main_rejects_traversal_and_symlink_report_paths(self):
        with tempfile.TemporaryDirectory() as temporary:
            target = Path(temporary) / "target.json"
            target.write_text("{}", encoding="utf-8")
            link = Path(temporary) / "link.json"
            link.symlink_to(target)
            with patch.object(sys, "argv", ["validate_docs.py", "--report", "../escape.json"]):
                with self.assertRaisesRegex(ValueError, "must not contain"):
                    validate_docs.main()
            with patch.object(sys, "argv", ["validate_docs.py", "--report", str(link)]):
                with self.assertRaisesRegex(ValueError, "must not be a symlink"):
                    validate_docs.main()
