"""Exercise the received generator and overlay in a disposable HTML-only rebuild."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from bs4 import BeautifulSoup


ROOT = Path(__file__).resolve().parents[1]


class RebuildDesignHtmlTest(unittest.TestCase):
    def test_rebuild_binds_corrected_html_and_preserves_received_review(self):
        environment = os.environ.copy()
        environment.pop("ETAYE_ROOT", None)

        def run(script, *options):
            result = subprocess.run(
                [sys.executable, str(ROOT / script), *options],
                capture_output=True, text=True, timeout=60, env=environment,
            )
            self.assertEqual(result.returncode, 0, result.stderr + result.stdout)
            return result.stdout

        with tempfile.TemporaryDirectory() as temporary:
            output = run("tools/rebuild_design_receipt.py", "--output", temporary)
            index_path = Path(output.splitlines()[0])
            site = index_path.parent
            root = site.parent
            rebuild = json.loads((root.parent / "REBUILD.json").read_text(encoding="utf-8"))
            self.assertFalse(rebuild["pdfsGenerated"])
            self.assertFalse(rebuild["designApproved"])

            received_review = json.loads((root / "review/evidence.json").read_text(encoding="utf-8"))
            run("design/corrections/apply.py", "--root", str(root))
            run("design/corrections/finalize.py", "--root", str(root))
            self.assertEqual(
                json.loads((root / "review/evidence.received.json").read_text(encoding="utf-8")), received_review,
            )
            changed_review = json.loads((root / "review/evidence.json").read_text(encoding="utf-8"))
            self.assertTrue(changed_review)
            for state in changed_review.values():
                self.assertEqual(state["status"], "Changed — independent re-review required")
                self.assertEqual(state["opened"], [])

            index = BeautifulSoup(index_path.read_text(encoding="utf-8"), "html.parser")
            self.assertEqual(len(index.select("main section")), 10)
            source = site / "ecrans/I-09/source-src-b-001.html"
            portal = site / "ecrans/S-29/extrait-src-a-005.html"
            self.assertIn("Cabinet du Canal", source.read_text(encoding="utf-8"))
            self.assertIn("Extrait partagé", portal.read_text(encoding="utf-8"))
            snapshot = json.loads((root / "review/corrected-snapshot.json").read_text(encoding="utf-8"))
            manifest = {item["path"]: item["sha256"] for item in snapshot["files"]}
            for path in (index_path, source, portal):
                self.assertEqual(manifest[path.relative_to(root).as_posix()], hashlib.sha256(path.read_bytes()).hexdigest())
            self.assertFalse(snapshot["aymenApproval"])
            self.assertFalse(snapshot["figmaImported"])


if __name__ == "__main__":
    unittest.main()
