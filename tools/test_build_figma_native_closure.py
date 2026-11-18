"""The native correction build preserves old outputs and rejects baseline drift."""
import json
from pathlib import Path
import tempfile
import unittest

from build_figma_native_closure import ROOT, build


class NativeCorrectionBuildTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name)
        self.before = ROOT / "docs/design/reviews/2026-11-18-native-closure-before.json"
        self.plan = ROOT / "design/figma-native-closure/correction-plan.json"

    def test_real_plan_builds_and_existing_output_is_preserved(self):
        output = self.folder / "plugin"
        build(self.before, self.plan, output)
        before = {p.name: p.read_bytes() for p in output.iterdir()}
        self.assertIn("generatedCodeSha256", json.loads((output / "provenance.json").read_text(encoding="utf-8")))
        with self.assertRaises(FileExistsError):
            build(self.before, self.plan, output)
        self.assertEqual(before, {p.name: p.read_bytes() for p in output.iterdir()})

    def test_changed_fingerprint_is_rejected_before_creating_output(self):
        data = json.loads(self.plan.read_text(encoding="utf-8"))
        data["plans"][0]["beforeFingerprint"] = "changed"
        path = self.folder / "plan.json"
        path.write_text(json.dumps(data), encoding="utf-8")
        output = self.folder / "must-not-exist"
        with self.assertRaisesRegex(ValueError, "does not match"):
            build(self.before, path, output)
        self.assertFalse(output.exists())

    def test_duplicate_file_cannot_become_the_target(self):
        data = json.loads(self.before.read_text(encoding="utf-8"))
        data["fileKey"] = "a-duplicate-file"
        path = self.folder / "before.json"
        path.write_text(json.dumps(data), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "Incomplete native baseline"):
            build(path, self.plan, self.folder / "must-not-exist")
