"""Regression checks for external-kit preservation and delivered archive integrity."""
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import export_claude_chat_kit as exporter


class ResumeKitTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="etaye-resume-check-")
        self.addCleanup(self.temp.cleanup)
        self.output = Path(self.temp.name)

    def test_repeated_exports_preserve_user_files_and_prior_kits(self):
        user_file = self.output / "attachments/06-design-coverage.csv"
        user_file.parent.mkdir()
        user_file.write_text("newer user tracker: preserve its evidence\n")
        old_zip = self.output / exporter.ARCHIVE
        old_zip.write_bytes(b"previous user export")
        first = exporter.export_package(self.output)
        before = {str(p.relative_to(self.output)): p.read_bytes()
                  for p in self.output.rglob("*") if p.is_file()}
        second = exporter.export_package(self.output)
        self.assertNotEqual(first, second)
        for name, content in before.items():
            self.assertEqual((self.output / name).read_bytes(), content)
        manifest = exporter.verify_package(second)
        self.assertFalse(manifest["latestClaudeSourceBundleIncluded"])
        self.assertEqual(manifest["sourceCommit"], subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=exporter.ROOT, text=True).strip())
        self.assertIsInstance(manifest["sourceTreeDirty"], bool)
        numbered = [n for n in manifest["files"] if n.startswith("attachments/")]
        self.assertEqual({n.split("/")[1][:2] for n in numbered},
                         {f"{i:02d}" for i in range(9)})
        for name, digest in manifest["sourceSha256"].items():
            self.assertEqual(hashlib.sha256((exporter.ROOT / name).read_bytes()).hexdigest(), digest)
        with zipfile.ZipFile(second / exporter.ARCHIVE) as archive:
            self.assertNotIn(user_file.read_text().encode(), [archive.read(n) for n in archive.namelist()])
            self.assertEqual(json.loads(archive.read("manifest.json")), manifest)

    def test_payload_and_zip_tampering_are_rejected(self):
        folder = exporter.export_package(self.output)
        prompt = folder / "PROMPT-TO-PASTE.txt"
        original = prompt.read_bytes()
        prompt.write_text("changed after manifest generation")
        with self.assertRaisesRegex(ValueError, "integrity mismatch"):
            exporter.verify_package(folder)
        prompt.write_bytes(original)
        with zipfile.ZipFile(folder / exporter.ARCHIVE, "a") as archive:
            archive.writestr("unexpected.txt", "not part of the delivery")
        with self.assertRaisesRegex(ValueError, "membership"):
            exporter.verify_package(folder)

    def test_repository_and_symlinked_repository_destinations_are_rejected(self):
        for output in [exporter.ROOT, exporter.ROOT / "docs/should-not-be-created"]:
            with self.assertRaisesRegex(ValueError, "outside the repository"):
                exporter.export_package(output)
        link = self.output / "repo-link"
        link.symlink_to(exporter.ROOT, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, "outside the repository"):
            exporter.export_package(link)

    def test_credential_pattern_stops_export_before_any_output_is_written(self):
        # Artificial pattern assembled here; never a real token or logged value.
        dummy = ("fig" + "d_" + "A" * 32).encode()
        with patch.object(exporter, "build_payload", return_value=({"test.txt": dummy}, {}, {})):
            with self.assertRaisesRegex(ValueError, "Potential credential"):
                exporter.export_package(self.output)
        self.assertEqual(list(self.output.iterdir()), [])


if __name__ == "__main__":
    unittest.main()
