"""Exercise design/corrections/verify.py orchestration, snapshot checks, and PDF verification."""
from contextlib import redirect_stdout
import hashlib
from io import StringIO
import json
from pathlib import Path
import sys
import tempfile
import types
import unittest
from unittest.mock import patch


class _FakeLocator:
    def __init__(self, text="Hugo Petit Sami Rahmani Canal Claire Martin Louis Bernard 68,4 kg · saisie non enregistrée expire à la date indiquée non publié"):
        self._text = text
        self.first = self

    def click(self):
        return None

    def check(self):
        return None

    def fill(self, _value):
        return None

    def nth(self, _i):
        return self

    def filter(self, **_kwargs):
        return self

    def inner_text(self):
        return self._text

    def input_value(self):
        return "Correction de démonstration conservée en mémoire."

    def get_attribute(self, name):
        if name == "href":
            return "../S-07/canal.html"
        if name == "data-source":
            return "SRC-A-005"
        return ""

    def count(self):
        return 0

    def is_visible(self):
        return True


class _FakePage:
    def __init__(self, site: Path, state_urls: dict[str, str]):
        self.site = site
        self.url = ""
        self._state_urls = state_urls
        self._viewport = {"width": 1440, "height": 1024}
        self._click_step = 0
        self.keyboard = types.SimpleNamespace(press=self._press)

    def _press(self, _key):
        if "I-09" in self.url:
            self.url = (self.site / "ecrans/S-09/hugo.html").as_uri()
        elif "S-38" in self.url:
            self.url = (self.site / "ecrans/S-02/connexion.html").as_uri()
        elif "I-19" in self.url or "extrait-src-a-005" in self.url:
            self.url = (self.site / "ecrans/S-29/v2.html").as_uri()
        elif "S-23/valide" in self.url:
            self.url = (self.site / "ecrans/S-23/valide.html").as_uri()

    def set_default_timeout(self, _ms):
        return None

    def on(self, _event, _cb):
        return None

    def route(self, _pattern, _cb):
        return None

    def goto(self, url):
        self.url = url

    def set_viewport_size(self, vp):
        self._viewport = vp

    def wait_for_function(self, _expr):
        return None

    def wait_for_url(self, _pattern):
        return None

    def screenshot(self, path, full_page=True):
        Path(path).write_bytes(b"PNG")

    def evaluate(self, script):
        if "scrollWidth" in script:
            return {"width": self._viewport["width"], "scrollWidth": self._viewport["width"], "broken": []}
        if "activeElement.hasAttribute" in script:
            return True
        if "activeElement.tagName" in script:
            return "INPUT"
        if "activeElement.id" in script:
            return "prototype-link-1"
        return None

    def locator(self, selector):
        if "button.etaye-repere" in selector or "button[data-source]" in selector or 'a[href*="I-17"]' in selector:
            return _FakeLocator()
        if selector == "tr":
            loc = _FakeLocator("68,4 kg · saisie non enregistrée")
            loc.count = lambda: 1
            return loc
        if selector == ".etaye-nav__compte" or selector == "dialog[open]":
            return _FakeLocator()
        if selector == "main":
            if "I-23" in self.url:
                return _FakeLocator("Louis Bernard")
            if "S-19" in self.url:
                return _FakeLocator("Lecture seule")
            if "I-16" in self.url:
                return _FakeLocator("non publié")
            return _FakeLocator("Hugo Petit")
        if selector == "body":
            if "S-19" in self.url:
                return _FakeLocator("Contenu restreint")
            return _FakeLocator("expire à la date indiquée")
        if selector == ".etaye-contexte":
            return _FakeLocator("Sami Rahmani · Canal")
        return _FakeLocator()

    def get_by_role(self, _role, name="", exact=False):
        page = self

        class _Action(_FakeLocator):
            def click(self_inner):
                if name == "Se connecter":
                    page.url = (page.site / "ecrans/S-05/un-seul.html").as_uri()
                elif name == "Étape suivante":
                    page._click_step += 1
                    page.url = page._state_urls.get(page._click_step, page.url)
                elif name == "Ouvrir":
                    page.url = (page.site / "ecrans/S-05/choix.html").as_uri()
                elif name == "Patients":
                    page.url = (page.site / "ecrans/S-07/canal.html").as_uri()
                elif name == "Publier pour le patient":
                    page.url = (page.site / "ecrans/S-23/publie.html").as_uri()

        return _Action()


class _FakeContext:
    def __init__(self, site: Path, state_urls: dict[str, str]):
        self._page = _FakePage(site, state_urls)

    def new_page(self):
        return self._page

    def close(self):
        return None


class _FakeBrowser:
    version = "153.0.0.0"

    def __init__(self, site: Path, state_urls: dict[str, str]):
        self._site = site
        self._state_urls = state_urls

    def new_context(self, **_kwargs):
        return _FakeContext(self._site, self._state_urls)

    def close(self):
        return None


class VerifyDesignTests(unittest.TestCase):
    def _build_site(self, root: Path):
        site = root / "site"
        (site / "manifest").mkdir(parents=True)
        (site / "docs").mkdir(parents=True)
        (root / "review").mkdir(parents=True)
        journeys_path = Path(__file__).resolve().parents[1] / "design/corrections/journeys.json"
        journeys = json.loads(journeys_path.read_text(encoding="utf-8"))
        state_ids = {
            step for j in journeys for step in j["steps"]
        } | {
            "S-05/choix", "S-06/nora", "S-06/jour-vide", "S-19/lecture-seule", "S-32/membre",
            "I-17/ouvert", "I-16/ouvert", "I-15/non-soutenu", "I-23/relance", "S-40/louis-en-file",
            "S-29/v2", "S-30/apercu", "I-07/qualitatif", "I-07/correction", "I-21/identite",
            "I-21/mesure", "I-21/disponibilite", "I-11/claire", "S-38/expire", "I-19/patient",
            "S-29/extrait-src-a-005", "I-09/source-src-b-001", "S-10/claire", "S-23/validation",
            "I-15/ouvert", "I-18/retrait", "S-26/annuler", "S-23/valide",
        } | {f"M-{i:02d}/{st}" for i in range(1, 6) for st in ("html", "texte", "sombre")}
        manifest_rows = []
        for key in sorted(state_ids):
            fam, slug = key.split("/")
            rel = f"ecrans/{key}.html"
            p = site / rel
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text("<html><body><p>Synthétique</p></body></html>", encoding="utf-8")
            manifest_rows.append({"id": fam, "slug": slug, "name": slug, "path": rel})
        (site / "manifest/states.json").write_text(json.dumps(manifest_rows), encoding="utf-8")
        for i in range(1, 4):
            for variant in ("court", "long", "incomplet"):
                (site / "docs" / f"P-{i:02d}-{variant}.pdf").write_bytes(b"%PDF-1.4\n")
        files = [
            {"path": p.relative_to(root).as_posix(), "sha256": hashlib.sha256(p.read_bytes()).hexdigest()}
            for p in sorted(site.rglob("*")) if p.is_file()
        ]
        (root / "review/corrected-snapshot.json").write_text(
            json.dumps({"snapshotSha256": "abc", "files": files}), encoding="utf-8"
        )
        return site

    def test_verify_rejects_traversal_and_in_repo_output(self):
        fake_pw = types.ModuleType("playwright")
        fake_sync = types.ModuleType("playwright.sync_api")
        fake_sync.sync_playwright = lambda: None
        fake_pw.sync_api = fake_sync
        with patch.dict(sys.modules, {"playwright": fake_pw, "playwright.sync_api": fake_sync}):
            from design.corrections import verify
            with tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp) / "design"
                root.mkdir()
                with self.assertRaises(SystemExit):
                    verify.main(["--root", "../bad", "--output", str(Path(tmp) / "out")])
                with self.assertRaises(SystemExit):
                    verify.main(["--root", str(root), "--output", str(root / "inside")])

    def test_verify_exercises_journeys_viewports_and_pdfs(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "design"
            site = self._build_site(root)
            out = Path(tmp) / "evidence"
            fake_pw = types.ModuleType("playwright")
            fake_sync = types.ModuleType("playwright.sync_api")
            fake_pypdf = types.ModuleType("pypdf")
            fake_pypdf.PdfReader = lambda _p: types.SimpleNamespace(
                pages=[types.SimpleNamespace(extract_text=lambda: "Démonstration Page 1 / 1")]
            )

            class _FakePW:
                def __enter__(self):
                    return types.SimpleNamespace(chromium=types.SimpleNamespace(launch=lambda: _FakeBrowser(site, {})))

                def __exit__(self, *_args):
                    return False

            fake_sync.sync_playwright = lambda: _FakePW()
            fake_pw.sync_api = fake_sync
            with patch.dict(sys.modules, {"playwright": fake_pw, "playwright.sync_api": fake_sync, "pypdf": fake_pypdf}):
                from design.corrections import verify
                with redirect_stdout(StringIO()):
                    code = verify.main(["--root", str(root), "--output", str(out)])
                self.assertIn(code, (0, 1))
                payload = json.loads((out / "verification.json").read_text(encoding="utf-8"))
                self.assertEqual(len(payload["pdfs"]), 9)
                self.assertTrue(all(p["allPageFooters"] for p in payload["pdfs"]))
                self.assertGreater(len(payload["captures"]), 100)
