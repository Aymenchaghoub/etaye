"""Exercise Figma export/inspect/closure tooling on synthetic offline fixtures."""
from contextlib import redirect_stdout
import hashlib
from io import BytesIO, StringIO
import json
from pathlib import Path
import sys
import tempfile
import types
import unittest
from unittest.mock import patch

import build_figma_native_closure as closure
import inspect_figma as inspector


ROOT = Path(__file__).resolve().parents[1]


class _FakeResponse(BytesIO):
    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return False


class _FakePage:
    def goto(self, _url):
        return None

    def evaluate(self, script):
        if "document.fonts" in script or "document.images" in script:
            return None
        return {"node": {"type": "frame", "children": [{"type": "image", "src": "icon.svg"}]}}


class _FakeBrowser:
    def new_page(self, **_kwargs):
        return _FakePage()

    def close(self):
        return None


class _FakePlaywright:
    def __enter__(self):
        return types.SimpleNamespace(chromium=types.SimpleNamespace(launch=lambda: _FakeBrowser()))

    def __exit__(self, *_args):
        return False


class FigmaToolingTests(unittest.TestCase):
    def test_inspect_figma_processes_cached_snapshot_and_mocked_exports(self):
        with tempfile.TemporaryDirectory() as temporary:
            tmp = Path(temporary)
            token_file = tmp / "token.txt"
            token_file.write_text("figd_synthetic", encoding="utf-8")
            cached = tmp / "cached.json"
            cached.write_text(json.dumps({
                "name": "Etaye Synthetic",
                "version": "1",
                "lastModified": "2026-11-26T00:00:00Z",
                "document": {},
                "styles": {},
                "components": {},
                "componentSets": {},
            }), encoding="utf-8")
            out_dir = tmp / "out"

            def fake_urlopen(req, timeout=45):
                url = req if isinstance(req, str) else req.full_url
                if url.endswith("/comments"):
                    return _FakeResponse(json.dumps({"comments": [{"id": "c1", "message": "ok"}]}).encode())
                if url.endswith("/versions"):
                    return _FakeResponse(json.dumps({"versions": [{"id": "v1", "label": "init"}], "pagination": True}).encode())
                if "images/" in url:
                    fmt = "svg" if "format=svg" in url else "png"
                    key = "4:13" if fmt == "svg" else "3:2"
                    return _FakeResponse(json.dumps({"images": {key: f"https://example.test/asset.{fmt}"}}).encode())
                return _FakeResponse(b"<svg/>" if url.endswith(".svg") else b"PNGDATA")

            with patch.object(inspector, "OUT", out_dir), \
                 patch.object(inspector, "urlopen", side_effect=fake_urlopen), \
                 patch.object(sys, "argv", ["inspect_figma.py", "--token-file", str(token_file), "--cached-file", str(cached)]), \
                 redirect_stdout(StringIO()) as stdout:
                inspector.main()
            summary = json.loads(stdout.getvalue())
            self.assertTrue(summary["ok"])
            self.assertEqual(summary["exports"], 2)
            self.assertTrue((out_dir / "manifest.json").is_file())
            self.assertTrue((out_dir / "file-snapshot.json").is_file())

    def test_export_figma_layers_exports_batch_from_minimal_site(self):
        fake_pw = types.ModuleType("playwright")
        fake_sync = types.ModuleType("playwright.sync_api")
        fake_sync.sync_playwright = lambda: _FakePlaywright()
        fake_pw.sync_api = fake_sync
        with tempfile.TemporaryDirectory() as temporary, \
             patch.dict(sys.modules, {"playwright": fake_pw, "playwright.sync_api": fake_sync}):
            import importlib
            exporter = importlib.import_module("export_figma_layers")
            tmp = Path(temporary)
            root = tmp / "design-tree"
            site = root / "site"
            (site / "manifest").mkdir(parents=True)
            (site / "ecrans/S-01").mkdir(parents=True)
            (root / "review").mkdir(parents=True)
            html = site / "ecrans/S-01/demo.html"
            html.write_text("<!doctype html><html><body><main><h1>Étaye</h1></main></body></html>", encoding="utf-8")
            icon = site / "ecrans/S-01/icon.svg"
            icon.write_text("<svg xmlns='http://www.w3.org/2000/svg'/>", encoding="utf-8")
            _FakePage.evaluate = lambda _self, script: (
                None if ("document.fonts" in script or "document.images" in script)
                else {"node": {"type": "frame", "children": [{"type": "image", "src": icon.as_uri()}]}}
            )
            (site / "manifest/states.json").write_text(
                json.dumps([{"id": "S-01", "slug": "demo", "name": "Demo", "path": "ecrans/S-01/demo.html"}]),
                encoding="utf-8",
            )
            files = [
                {"path": str(p.relative_to(root)), "sha256": hashlib.sha256(p.read_bytes()).hexdigest()}
                for p in sorted(site.rglob("*")) if p.is_file()
            ]
            (root / "review/corrected-snapshot.json").write_text(
                json.dumps({"snapshotSha256": "abc123", "files": files}), encoding="utf-8"
            )
            out = tmp / "export-out"
            with patch.object(sys, "argv", [
                "export_figma_layers.py", "--root", str(root), "--output", str(out), "--limit", "1"
            ]), redirect_stdout(StringIO()):
                exporter.main()
            manifest = json.loads((out / "MANIFEST.json").read_text(encoding="utf-8"))
            self.assertEqual(manifest["states"], 1)
            self.assertTrue(manifest["partial"])
            self.assertFalse(manifest["figmaImported"])
            batch = json.loads((out / "batches/batch-001.json").read_text(encoding="utf-8"))
            self.assertEqual(batch["frames"][0]["scene"]["node"]["children"][0]["type"], "svg")

    def test_build_figma_native_closure_validates_and_emits_plugin(self):
        with tempfile.TemporaryDirectory() as temporary:
            tmp = Path(temporary)
            manifest = json.loads((ROOT / "docs/design/figma/manifest.json").read_text(encoding="utf-8"))
            roots = [{"nodeId": f"n{i}", "state": f"S-{i:03d}", "fingerprint": f"fp{i}"} for i in range(456)]
            before_path = tmp / "before.json"
            before_path.write_text(json.dumps({
                "fileKey": manifest["fileKey"], "pageId": "15:2", "roots": roots, "protectedPages": []
            }), encoding="utf-8")
            plans = [{"rootId": f"n{i}", "state": f"S-{i:03d}", "beforeFingerprint": f"fp{i}"} for i in range(52)]
            plan_path = tmp / "plan.json"
            plan_path.write_text(json.dumps({"issues": [], "plans": plans}), encoding="utf-8")
            flow_path = tmp / "flow.json"
            flow_path.write_text("{}", encoding="utf-8")
            out = tmp / "plugin-out"
            closure.build(before_path, plan_path, out, flow_path)
            self.assertTrue((out / "code.js").is_file())
            self.assertTrue((out / "provenance.json").is_file())

            plan_path.write_text(json.dumps({"issues": ["bad"], "plans": plans}), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "52-state correction plan"):
                closure.build(before_path, plan_path, tmp / "plugin-bad")
