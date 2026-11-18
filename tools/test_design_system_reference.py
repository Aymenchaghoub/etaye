"""The design-system reference matches its sources, and drift in either source is caught."""
import json
import shutil
import tempfile
import unittest
from pathlib import Path

import design_system_reference as ref


class DesignSystemReferenceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name)

    def test_committed_document_matches_both_sources(self):
        self.assertEqual(ref.check(), [])

    def test_generated_blocks_are_not_empty_and_cover_every_token(self):
        tokens = ref.read_tokens()
        blocks = ref.render(tokens)
        self.assertEqual(set(blocks), set(ref.BLOCKS))
        self.assertEqual(blocks["colors"].count("\n| `"), 17)
        self.assertEqual(blocks["typography"].count("\n| `"), 14)
        self.assertGreaterEqual(len(tokens["icons"]), 50)

    def test_a_stale_document_is_reported(self):
        doc = self.folder / "design-system.md"
        shutil.copy(ref.DOC, doc)
        doc.write_text(doc.read_text(encoding="utf-8").replace("#2b4c7e", "#2b4c7f", 1), encoding="utf-8")
        problems = ref.check(doc_path=doc)
        self.assertTrue(problems and "stale" in problems[0], problems)

    def test_css_drift_from_figma_is_reported_before_the_document_is_compared(self):
        css = self.folder / "tokens.css"
        css.write_text(ref.TOKENS_CSS.read_text(encoding="utf-8").replace("--accent: #2b4c7e;", "--accent: #2b4c7f;", 1), encoding="utf-8")
        problems = ref.check(css_path=css)
        self.assertTrue(any("accent/accent" in p for p in problems), problems)

    def test_figma_drift_from_css_is_reported(self):
        resolved = json.loads(ref.RESOLVED.read_text(encoding="utf-8"))
        resolved["typography"]["ui-corps"]["fontSize"] = 16.0
        path = self.folder / "resolved.json"
        path.write_text(json.dumps(resolved), encoding="utf-8")
        problems = ref.check(resolved_path=path)
        self.assertTrue(any("typography ui-corps" in p for p in problems), problems)

    def test_a_missing_block_marker_is_rejected(self):
        text = ref.DOC.read_text(encoding="utf-8").replace("<!-- generated:icons:end -->", "", 1)
        with self.assertRaisesRegex(ValueError, "icons"):
            ref.splice(text, ref.render(ref.read_tokens()))


if __name__ == "__main__":
    unittest.main()
