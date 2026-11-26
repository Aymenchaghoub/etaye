"""A valid file path alone must not mask a missing target fragment or asset."""

from contextlib import redirect_stdout
from io import StringIO
import json
from pathlib import Path
import tempfile
import unittest
import subprocess
import sys

from audit_design_receipt import inspect, main as audit_main


class DesignReferenceTests(unittest.TestCase):
    def test_prototype_transition_and_safe_return_are_audited(self):
        with tempfile.TemporaryDirectory() as temporary:
            site = Path(temporary)
            (site / "index.html").write_text(
                '<button data-next="missing.html">Next</button><body data-safe-return="outside.html"></body>'
            )
            result = inspect(site)
            self.assertFalse(result["ok"])
            self.assertEqual(
                {row[1] for row in result["missingFiles"]},
                {"data-next", "data-safe-return"},
            )

    def test_corrected_rebuild_requires_pdfs_before_creating_output(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "must-not-be-created"
            run = subprocess.run(
                [
                    sys.executable,
                    str(Path(__file__).with_name("rebuild_design_receipt.py")),
                    "--output",
                    str(output),
                    "--corrected",
                ],
                capture_output=True,
                text=True,
            )
            self.assertNotEqual(run.returncode, 0)
            self.assertIn("--corrected requires --pdf", run.stderr)
            self.assertFalse(output.exists())

    def test_refs_assets_and_fragments(self):
        with tempfile.TemporaryDirectory() as temporary:
            site = Path(temporary)
            (site / "index.html").write_text(
                '<a href="detail.html#result">Open</a><img src="logo.svg">'
            )
            (site / "detail.html").write_text('<h1 id="result">Result</h1>')
            (site / "logo.svg").write_text('<svg xmlns="http://www.w3.org/2000/svg"/>')
            self.assertTrue(inspect(site)["ok"])
            (site / "detail.html").write_text('<h1 id="different">Result</h1>')
            (site / "logo.svg").unlink()
            result = inspect(site)
            self.assertFalse(result["ok"])
            self.assertEqual(len(result["missingFragments"]), 1)
            self.assertEqual(len(result["missingFiles"]), 1)

    def test_empty_scope_and_css_escape_are_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            site = Path(temporary)
            self.assertFalse(inspect(site)["ok"])
            (site / "index.html").write_text('<link href="style.css" rel="stylesheet">')
            (site / "style.css").write_text("body { background: url(../outside.png); }")
            self.assertFalse(inspect(site)["ok"])
            self.assertEqual(len(inspect(site)["referencesOutsideSite"]), 1)

    def test_audit_does_not_read_html_or_css_sources_outside_the_site(self):
        with tempfile.TemporaryDirectory() as temporary:
            parent = Path(temporary)
            site = parent / "site"
            site.mkdir()
            (site / "index.html").write_text("<p>Valid local source</p>")
            for name in ("foreign.html", "foreign.css"):
                foreign = parent / name
                foreign.write_bytes(b"\xff")  # The audit would raise if it read this source.
                (site / name).symlink_to(foreign)
            result = inspect(site)
            self.assertFalse(result["ok"])
            self.assertEqual(result["htmlPages"], 1)
            self.assertEqual({name for name, _ in result["referencesOutsideSite"]}, {"foreign.html", "foreign.css"})

    def test_cli_writes_report_and_rejects_traversal_or_symlink(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            site = root / "site"
            site.mkdir()
            (site / "index.html").write_text("<p>OK</p>", encoding="utf-8")
            report = root / "out/report.json"
            with redirect_stdout(StringIO()) as out:
                code = audit_main(["--site", str(site), "--report", str(report)])
            self.assertEqual(code, 0)
            self.assertTrue(json.loads(report.read_text(encoding="utf-8"))["ok"])
            self.assertIn('"ok": true', out.getvalue())

            with self.assertRaisesRegex(ValueError, "must not contain"):
                audit_main(["--site", str(site), "--report", "../bad.json"])
            link = root / "link.json"
            link.symlink_to(report)
            with self.assertRaisesRegex(ValueError, "must not be a symlink"):
                audit_main(["--site", str(site), "--report", str(link)])


if __name__ == "__main__":
    unittest.main()
