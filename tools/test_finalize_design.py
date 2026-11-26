from contextlib import redirect_stderr, redirect_stdout
from io import StringIO
import json
from pathlib import Path
import tempfile
import unittest

from bs4 import BeautifulSoup

from design.corrections import finalize


class FinalizeDesignTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="etaye-design-rebuild-")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name) / "etaye-design"
        self.site = self.root / "site"
        (self.site / "manifest").mkdir(parents=True)
        self.snapshot = self.root / "review/corrected-snapshot.json"
        self.snapshot.parent.mkdir()
        self.index = self.site / "parcours.html"
        self.index.write_text("<html><body><main>Received index</main></body></html>", encoding="utf-8")
        self.journeys = json.loads((finalize.HERE / "journeys.json").read_text(encoding="utf-8"))
        self.states = {}
        for key in {key for journey in self.journeys for key in journey["steps"]}:
            family, slug = key.split("/")
            path = f"ecrans/{key}.html"
            self.states[key] = {"id": family, "slug": slug, "name": "État synthétique", "path": path}
            page = self.site / path
            page.parent.mkdir(parents=True, exist_ok=True)
            page.write_text("<html><head></head><body><p>État synthétique</p></body></html>", encoding="utf-8")
        self.manifest = self.site / "manifest/states.json"
        self.manifest.write_text(json.dumps(list(self.states.values())), encoding="utf-8")
        self.example = self.site / self.states[self.journeys[0]["steps"][0]]["path"]

    def test_build_preserves_content_and_binds_a_deterministic_snapshot(self):
        with redirect_stdout(StringIO()):
            finalize.main(["--root", str(self.root)])
        page = BeautifulSoup(self.example.read_text(encoding="utf-8"), "html.parser")
        self.assertEqual(page.p.string, "État synthétique")
        self.assertEqual(len(page.select('script[src$="prototype.js"]')), 1)
        self.assertEqual(len(page.select('link[href$="review-corrections.css"]')), 1)
        self.assertEqual(json.loads(page.select_one("#design-journeys").string), self.journeys)
        index = BeautifulSoup(self.index.read_text(encoding="utf-8"), "html.parser")
        self.assertEqual(len(index.select("main section")), len(self.journeys))
        self.assertEqual(index.select_one("main h1").string, "Parcours de conception corrigés")
        first_snapshot = self.snapshot.read_bytes()
        evidence = json.loads(first_snapshot)
        self.assertFalse(evidence["aymenApproval"])
        self.assertFalse(evidence["figmaImported"])
        self.assertIn("site/parcours.html", {entry["path"] for entry in evidence["files"]})

        with redirect_stdout(StringIO()):
            finalize.main(["--root", str(self.root)])
        self.assertEqual(self.snapshot.read_bytes(), first_snapshot)
        (self.site / "asset.txt").write_text("new asset", encoding="utf-8")
        with redirect_stdout(StringIO()):
            finalize.main(["--root", str(self.root)])
        self.assertNotEqual(json.loads(self.snapshot.read_bytes())["snapshotSha256"], evidence["snapshotSha256"])

    def test_missing_index_main_is_reported_before_overwriting_pages(self):
        original = self.example.read_bytes()
        self.snapshot.write_text('{"reviewStatus": "stale"}\n', encoding="utf-8")
        self.index.write_text("<html><body>Missing main</body></html>", encoding="utf-8")
        with redirect_stderr(StringIO()) as error, self.assertRaises(SystemExit) as result:
            finalize.main(["--root", str(self.root)])
        self.assertEqual(result.exception.code, 1)
        self.assertIn("parcours.html: missing <main>", error.getvalue())
        self.assertFalse(self.snapshot.exists())
        self.assertEqual(self.example.read_bytes(), original)

    def test_page_without_head_or_body_cannot_generate_evidence(self):
        for html in ("<html><head></head></html>", "<html><body></body></html>"):
            with self.subTest(html=html):
                self.example.write_text(html, encoding="utf-8")
                with redirect_stderr(StringIO()) as error, self.assertRaises(SystemExit) as result:
                    finalize.main(["--root", str(self.root)])
                self.assertEqual(result.exception.code, 1)
                self.assertIn("missing <head> or <body>", error.getvalue())
                self.assertIn(self.example.name, error.getvalue())
                self.assertFalse(self.snapshot.exists())

    def test_missing_journey_step_cannot_generate_evidence(self):
        self.manifest.write_text("[]", encoding="utf-8")
        with redirect_stderr(StringIO()) as error, self.assertRaises(SystemExit) as result:
            finalize.main(["--root", str(self.root)])
        self.assertEqual(result.exception.code, 1)
        self.assertIn("Missing corrected journey step", error.getvalue())
        self.assertFalse(self.snapshot.exists())

    def test_file_linked_outside_the_reconstruction_is_rejected(self):
        foreign = self.root.parent / "outside.html"
        foreign.write_text("<html><head></head><body>External file</body></html>", encoding="utf-8")
        original = foreign.read_bytes()
        self.example.unlink()
        self.example.symlink_to(foreign)
        with redirect_stderr(StringIO()) as error, self.assertRaises(SystemExit) as result:
            finalize.main(["--root", str(self.root)])
        self.assertEqual(result.exception.code, 1)
        self.assertIn("outside the reconstructed site", error.getvalue())
        self.assertEqual(foreign.read_bytes(), original)
        self.assertFalse(self.snapshot.exists())


if __name__ == "__main__":
    unittest.main()
