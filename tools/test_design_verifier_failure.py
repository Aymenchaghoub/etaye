"""An unreadable initial audit must leave explicit incomplete evidence."""

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


class InitialAuditFailureTests(unittest.TestCase):
    def test_invalid_utf8_leaves_failed_report_without_starting_browser(self):
        with tempfile.TemporaryDirectory() as temporary:
            parent = Path(temporary)
            root = parent / "design"
            site = root / "site"
            site.mkdir(parents=True)
            (site / "index.html").write_bytes(b"\xff")
            # Optional browser dependency is outside this failure path; an invocation is an error.
            stub = parent / "dependency-stub" / "playwright"
            stub.mkdir(parents=True)
            (stub / "__init__.py").write_text("")
            (stub / "sync_api.py").write_text(
                "def sync_playwright():\n    raise AssertionError('Browser must not start before audit')\n"
            )
            environment = os.environ.copy()
            environment["PYTHONPATH"] = str(stub.parent)
            output = parent / "evidence"
            script = (
                Path(__file__).resolve().parents[1] / "design/corrections/verify.py"
            )
            run = subprocess.run(
                [
                    sys.executable,
                    str(script),
                    "--root",
                    str(root),
                    "--output",
                    str(output),
                ],
                env=environment,
                capture_output=True,
                text=True,
            )
            self.assertNotEqual(run.returncode, 0)
            self.assertIn("UnicodeDecodeError", run.stderr)
            result = json.loads((output / "verification.json").read_text())
            self.assertFalse(result["ok"])
            self.assertIsNone(result["links"])
            self.assertIn(
                "Verification interrupted; not an acceptance result", result["errors"]
            )


if __name__ == "__main__":
    unittest.main()
