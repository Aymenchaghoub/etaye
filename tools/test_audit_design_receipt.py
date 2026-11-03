"""A valid file path alone must not mask a missing target fragment or asset."""

from pathlib import Path
import tempfile
import unittest

from audit_design_receipt import inspect


class DesignReferenceTests(unittest.TestCase):
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


if __name__ == "__main__":
    unittest.main()
