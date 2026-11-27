"""Exercise the documentary authorization boundary and application workflow policy."""
import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from validate_docs import check_application_scope

try:
    import yaml
except ModuleNotFoundError:
    yaml = None

ROOT = Path(__file__).resolve().parents[1]
BUILD_DIRECTORIES = ("node_modules", "target", "test-results", "playwright-report", "dist")
DOC_WORKFLOWS = ("documentation.yml", "codeql.yml", "architecture-pages.yml")


if yaml is not None:
    class UniqueKeySafeLoader(yaml.SafeLoader):
        def construct_mapping(self, node, deep=False):
            mapping = super().construct_mapping(node, deep=deep)
            if len(mapping) != len(node.value):
                raise yaml.constructor.ConstructorError(None, None, "duplicate mapping key", node.start_mark)
            return mapping


class ApplicationScopeTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.configure()
        for name in DOC_WORKFLOWS:
            self.write(f".github/workflows/{name}")

    def write(self, name, contents=""):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(contents, encoding="utf-8")

    def configure(self, settings="", gate="false"):
        self.write("_bmad/custom/config.toml", f"[etaye]\ndevelopment_authorized = {gate}\n{settings}\n")

    def authorize(self, *, story_10_2=False):
        stories = '["10.1", "10.2"]' if story_10_2 else '["10.1"]'
        self.configure(f"authorized_stories = {stories}")
        decisions = "| UD-41 | Authorized for story 10.1 only |\n"
        if story_10_2:
            decisions += "| UD-45 | AUTHORIZED FOR **STORY 10.2 ONLY** |\n"
        self.write("docs/planning-artifacts/decisions-assumptions.md", decisions)
        for name in ("apps/api/pom.xml", "apps/api/mvnw", "apps/web/package.json", "infra/local/docker-compose.yml",
                     "package.json", "pnpm-workspace.yaml", "pnpm-lock.yaml", "tsconfig.base.json",
                     "apps/web/sonar-project.properties",
                     ".github/workflows/api.yml", ".github/workflows/web.yml", ".github/workflows/dependency-review.yml",
                     ".github/workflows/package.yml", ".github/workflows/release.yml", ".github/workflows/sonarqube.yml"):
            self.write(name)
        self.write(".gitignore", "".join(f"{name}/\n" for name in BUILD_DIRECTORIES))

    def check_scope(self):
        errors = []
        scope = check_application_scope(self.root, errors)
        return scope, errors

    def test_strict_scope_accepts_docs_with_empty_or_absent_story_list(self):
        for config in ("", "authorized_stories = []"):
            with self.subTest(config=config):
                self.configure(config)
                scope, errors = self.check_scope()
                self.assertEqual(scope, {"mode": "planning-only", "authorizedStories": []})
                self.assertEqual(errors, [])

    def test_strict_scope_rejects_each_application_artifact(self):
        for name in ("apps/api/pom.xml", "package.json", "pom.xml", "src/App.java", "compose.yaml",
                     "docker-compose.yml", ".github/workflows/extra.yml"):
            with self.subTest(path=name):
                self.write(name)
                _, errors = self.check_scope()
                expected = "Unexpected application workflow" if name.startswith(".github") else name.split("/")[0]
                self.assertIn(expected, "\n".join(errors))
                path = self.root / name
                path.unlink()
                if name in {"apps/api/pom.xml", "src/App.java"}:
                    for parent in path.parents:
                        if parent == self.root:
                            break
                        parent.rmdir()

    def test_strict_scope_rejects_local_infra_with_empty_or_absent_story_list(self):
        self.write("infra/local/docker-compose.yml")
        for config in ("", "authorized_stories = []"):
            with self.subTest(config=config):
                self.configure(config)
                _, errors = self.check_scope()
                self.assertEqual(errors, ["Application implementation artifact created: infra"])

    def test_authorized_scope_accepts_story_tree_and_ignores_untracked_build_output(self):
        self.authorize()
        for name in BUILD_DIRECTORIES:
            self.write(f"apps/web/{name}/output")
        scope, errors = self.check_scope()
        self.assertEqual(scope["mode"], "story-10.1")
        self.assertEqual(scope["authorizedStories"], ["10.1"])
        self.assertIn("skipped", scope["trackedBuildOutputCheck"])
        self.assertEqual(errors, [])

    def test_authorized_scope_rejects_artifacts_outside_the_story(self):
        cases = {
            "apps/mobile/package.json": "requires exactly apps/api and apps/web",
            ".github/workflows/third-app.yml": "Unexpected workflows",
            "infra/cloud/compose.yaml": "only allows infra/local",
            "pom.xml": "Application implementation artifact created: pom.xml",
        }
        for name, expected in cases.items():
            with self.subTest(path=name):
                self.authorize()
                self.write(name)
                _, errors = self.check_scope()
                self.assertIn(expected, "\n".join(errors))
                path = self.root / name
                path.unlink()
                if name.startswith(("apps/mobile/", "infra/cloud/")):
                    path.parent.rmdir()

    def test_combined_scope_accepts_story_10_1_tree_with_both_decisions(self):
        self.authorize(story_10_2=True)
        scope, errors = self.check_scope()
        self.assertEqual(scope["mode"], "stories-10.1-10.2")
        self.assertEqual(scope["authorizedStories"], ["10.1", "10.2"])
        self.assertIn("skipped", scope["trackedBuildOutputCheck"])
        self.assertEqual(errors, [])

    def test_story_1_2_scope_requires_its_own_decision(self):
        self.authorize(story_10_2=True)
        self.configure('authorized_stories = ["10.1", "10.2", "1.2"]')
        scope, errors = self.check_scope()
        self.assertEqual(scope["mode"], "stories-10.1-10.2-1.2")
        self.assertEqual(scope["authorizedStories"], ["10.1", "10.2", "1.2"])
        self.assertEqual(len(errors), 1)
        self.assertIn("Story 1.2 authorization requires UD-48", errors[0])

        decisions = self.root / "docs/planning-artifacts/decisions-assumptions.md"
        with decisions.open("a", encoding="utf-8") as stream:
            stream.write("| UD-48 | Authorized for **story 1.2 only**. |\n")
        _, errors = self.check_scope()
        self.assertEqual(errors, [])

    def test_story_1_1_scope_requires_its_own_decision(self):
        self.authorize(story_10_2=True)
        self.configure('authorized_stories = ["10.1", "10.2", "1.2", "1.1"]')
        decisions = self.root / "docs/planning-artifacts/decisions-assumptions.md"
        with decisions.open("a", encoding="utf-8") as stream:
            stream.write("| UD-48 | Authorized for **story 1.2 only**. |\n")
        scope, errors = self.check_scope()
        self.assertEqual(scope["mode"], "stories-10.1-10.2-1.2-1.1")
        self.assertEqual(scope["authorizedStories"], ["10.1", "10.2", "1.2", "1.1"])
        self.assertEqual(len(errors), 1)
        self.assertIn("Story 1.1 authorization requires UD-50", errors[0])

        with decisions.open("a", encoding="utf-8") as stream:
            stream.write("| UD-50 | Authorized for **story 1.1 only**. |\n")
        _, errors = self.check_scope()
        self.assertEqual(errors, [])

    def test_story_1_3_scope_requires_its_own_decision(self):
        self.authorize(story_10_2=True)
        self.configure('authorized_stories = ["10.1", "10.2", "1.2", "1.1", "1.3"]')
        decisions = self.root / "docs/planning-artifacts/decisions-assumptions.md"
        with decisions.open("a", encoding="utf-8") as stream:
            stream.write("| UD-48 | Authorized for **story 1.2 only**. |\n")
            stream.write("| UD-50 | Authorized for **story 1.1 only**. |\n")

        scope, errors = self.check_scope()
        self.assertEqual(scope["mode"], "stories-10.1-10.2-1.2-1.1-1.3")
        self.assertEqual(scope["authorizedStories"], ["10.1", "10.2", "1.2", "1.1", "1.3"])
        self.assertEqual(len(errors), 1)
        self.assertIn("Story 1.3 authorization requires UD-53", errors[0])

        with decisions.open("a", encoding="utf-8") as stream:
            stream.write("| UD-53 | Authorized for **story 1.3 only**. |\n")
        _, errors = self.check_scope()
        self.assertEqual(errors, [])

    def test_story_9_1_scope_requires_its_own_decision(self):
        self.authorize(story_10_2=True)
        self.configure('authorized_stories = ["10.1", "10.2", "1.2", "1.1", "1.3", "9.1"]')
        decisions = self.root / "docs/planning-artifacts/decisions-assumptions.md"
        with decisions.open("a", encoding="utf-8") as stream:
            stream.write("| UD-48 | Authorized for **story 1.2 only**. |\n")
            stream.write("| UD-50 | Authorized for **story 1.1 only**. |\n")
            stream.write("| UD-53 | Authorized for **story 1.3 only**. |\n")

        scope, errors = self.check_scope()
        self.assertEqual(scope["mode"], "stories-10.1-10.2-1.2-1.1-1.3-9.1")
        self.assertEqual(scope["authorizedStories"], ["10.1", "10.2", "1.2", "1.1", "1.3", "9.1"])
        self.assertEqual(len(errors), 1)
        self.assertIn("Story 9.1 authorization requires UD-56", errors[0])

        with decisions.open("a", encoding="utf-8") as stream:
            stream.write("| UD-56 | Authorized for **story 9.1 only**. |\n")
        _, errors = self.check_scope()
        self.assertEqual(errors, [])

    def test_story_1_4_scope_requires_its_own_decision(self):
        self.authorize(story_10_2=True)
        self.configure('authorized_stories = ["10.1", "10.2", "1.2", "1.1", "1.3", "9.1", "1.4"]')
        decisions = self.root / "docs/planning-artifacts/decisions-assumptions.md"
        with decisions.open("a", encoding="utf-8") as stream:
            stream.write("| UD-48 | Authorized for **story 1.2 only**. |\n")
            stream.write("| UD-50 | Authorized for **story 1.1 only**. |\n")
            stream.write("| UD-53 | Authorized for **story 1.3 only**. |\n")
            stream.write("| UD-56 | Authorized for **story 9.1 only**. |\n")
            # The conditional UD-59 row alone must not satisfy the gate.
            stream.write("| UD-59 | Authorized for **story 1.4 only** once the design condition is met. |\n")

        scope, errors = self.check_scope()
        self.assertEqual(scope["mode"], "stories-10.1-10.2-1.2-1.1-1.3-9.1-1.4")
        self.assertEqual(scope["authorizedStories"], ["10.1", "10.2", "1.2", "1.1", "1.3", "9.1", "1.4"])
        self.assertEqual(len(errors), 1)
        self.assertIn("Story 1.4 authorization requires UD-61", errors[0])

        with decisions.open("a", encoding="utf-8") as stream:
            stream.write("| UD-61 | Authorized for **story 1.4 only**. |\n")
        _, errors = self.check_scope()
        self.assertEqual(errors, [])

    def test_story_3_1_scope_requires_its_own_decision(self):
        self.authorize(story_10_2=True)
        self.configure('authorized_stories = ["10.1", "10.2", "1.2", "1.1", "1.3", "9.1", "1.4", "3.1"]')
        decisions = self.root / "docs/planning-artifacts/decisions-assumptions.md"
        with decisions.open("a", encoding="utf-8") as stream:
            stream.write("| UD-48 | Authorized for **story 1.2 only**. |\n")
            stream.write("| UD-50 | Authorized for **story 1.1 only**. |\n")
            stream.write("| UD-53 | Authorized for **story 1.3 only**. |\n")
            stream.write("| UD-56 | Authorized for **story 9.1 only**. |\n")
            stream.write("| UD-61 | Authorized for **story 1.4 only**. |\n")
            # The scope-only UD-63 and design-only UD-64 rows must not satisfy the gate.
            stream.write("| UD-63 | Scope of story 3.1 decided; development is not authorized. |\n")
            stream.write("| UD-64 | Design of story 3.1 approved; authorization stays separate. |\n")

        scope, errors = self.check_scope()
        self.assertEqual(scope["mode"], "stories-10.1-10.2-1.2-1.1-1.3-9.1-1.4-3.1")
        self.assertEqual(scope["authorizedStories"], ["10.1", "10.2", "1.2", "1.1", "1.3", "9.1", "1.4", "3.1"])
        self.assertEqual(len(errors), 1)
        self.assertIn("Story 3.1 authorization requires UD-65", errors[0])

        with decisions.open("a", encoding="utf-8") as stream:
            stream.write("| UD-65 | Authorized for **story 3.1 only**, which also covers story 4.2. |\n")
        _, errors = self.check_scope()
        self.assertEqual(len(errors), 1)
        self.assertIn("Story 3.1 authorization requires UD-65", errors[0])

    def test_story_3_1_scope_accepts_its_decision(self):
        self.authorize(story_10_2=True)
        self.configure('authorized_stories = ["10.1", "10.2", "1.2", "1.1", "1.3", "9.1", "1.4", "3.1"]')
        decisions = self.root / "docs/planning-artifacts/decisions-assumptions.md"
        with decisions.open("a", encoding="utf-8") as stream:
            for story, decision in (("1.2", "UD-48"), ("1.1", "UD-50"), ("1.3", "UD-53"), ("9.1", "UD-56"),
                                    ("1.4", "UD-61"), ("3.1", "UD-65")):
                stream.write(f"| {decision} | Authorized for **story {story} only**. |\n")
        _, errors = self.check_scope()
        self.assertEqual(errors, [])

    def test_story_3_2_scope_requires_its_own_decision(self):
        self.authorize(story_10_2=True)
        self.configure('authorized_stories = ["10.1", "10.2", "1.2", "1.1", "1.3", "9.1", "1.4", "3.1", "3.2"]')
        decisions = self.root / "docs/planning-artifacts/decisions-assumptions.md"
        with decisions.open("a", encoding="utf-8") as stream:
            for story, decision in (("1.2", "UD-48"), ("1.1", "UD-50"), ("1.3", "UD-53"), ("9.1", "UD-56"),
                                    ("1.4", "UD-61"), ("3.1", "UD-65")):
                stream.write(f"| {decision} | Authorized for **story {story} only**. |\n")
            # The scope-only UD-67 and design-only UD-68 rows must not satisfy the gate.
            stream.write("| UD-67 | Scope of story 3.2 decided; development is not authorized. |\n")
            stream.write("| UD-68 | Design of story 3.2 approved; authorization stays separate. |\n")

        scope, errors = self.check_scope()
        self.assertEqual(scope["mode"], "stories-10.1-10.2-1.2-1.1-1.3-9.1-1.4-3.1-3.2")
        self.assertEqual(scope["authorizedStories"], ["10.1", "10.2", "1.2", "1.1", "1.3", "9.1", "1.4", "3.1", "3.2"])
        self.assertEqual(len(errors), 1)
        self.assertIn("Story 3.2 authorization requires UD-69", errors[0])

        with decisions.open("a", encoding="utf-8") as stream:
            stream.write("| UD-69 | Authorized for **story 3.2 only**, which also covers story 3.3. |\n")
        _, errors = self.check_scope()
        self.assertEqual(len(errors), 1)
        self.assertIn("Story 3.2 authorization requires UD-69", errors[0])

    def test_story_3_2_scope_accepts_its_decision(self):
        self.authorize(story_10_2=True)
        self.configure('authorized_stories = ["10.1", "10.2", "1.2", "1.1", "1.3", "9.1", "1.4", "3.1", "3.2"]')
        decisions = self.root / "docs/planning-artifacts/decisions-assumptions.md"
        with decisions.open("a", encoding="utf-8") as stream:
            for story, decision in (("1.2", "UD-48"), ("1.1", "UD-50"), ("1.3", "UD-53"), ("9.1", "UD-56"),
                                    ("1.4", "UD-61"), ("3.1", "UD-65"), ("3.2", "UD-69")):
                stream.write(f"| {decision} | Authorized for **story {story} only**. |\n")
        _, errors = self.check_scope()
        self.assertEqual(errors, [])

    def test_story_3_3_scope_requires_its_own_decision(self):
        self.authorize(story_10_2=True)
        self.configure('authorized_stories = ["10.1", "10.2", "1.2", "1.1", "1.3", "9.1", "1.4", "3.1", "3.2", "3.3"]')
        decisions = self.root / "docs/planning-artifacts/decisions-assumptions.md"
        with decisions.open("a", encoding="utf-8") as stream:
            for story, decision in (("1.2", "UD-48"), ("1.1", "UD-50"), ("1.3", "UD-53"), ("9.1", "UD-56"),
                                    ("1.4", "UD-61"), ("3.1", "UD-65"), ("3.2", "UD-69")):
                stream.write(f"| {decision} | Authorized for **story {story} only**. |\n")
            # The scope-only UD-71 and design-only UD-72 rows must not satisfy the gate.
            stream.write("| UD-71 | Scope of story 3.3 decided; development is not authorized. |\n")
            stream.write("| UD-72 | Design of story 3.3 approved; authorization stays separate. |\n")

        scope, errors = self.check_scope()
        self.assertEqual(scope["mode"], "stories-10.1-10.2-1.2-1.1-1.3-9.1-1.4-3.1-3.2-3.3")
        self.assertEqual(scope["authorizedStories"],
                         ["10.1", "10.2", "1.2", "1.1", "1.3", "9.1", "1.4", "3.1", "3.2", "3.3"])
        self.assertEqual(len(errors), 1)
        self.assertIn("Story 3.3 authorization requires UD-73", errors[0])

        with decisions.open("a", encoding="utf-8") as stream:
            stream.write("| UD-73 | Authorized for **story 3.3 only**, which also covers story 3.4. |\n")
        _, errors = self.check_scope()
        self.assertEqual(len(errors), 1)
        self.assertIn("Story 3.3 authorization requires UD-73", errors[0])

    def test_story_3_3_scope_accepts_its_decision(self):
        self.authorize(story_10_2=True)
        self.configure('authorized_stories = ["10.1", "10.2", "1.2", "1.1", "1.3", "9.1", "1.4", "3.1", "3.2", "3.3"]')
        decisions = self.root / "docs/planning-artifacts/decisions-assumptions.md"
        with decisions.open("a", encoding="utf-8") as stream:
            for story, decision in (("1.2", "UD-48"), ("1.1", "UD-50"), ("1.3", "UD-53"), ("9.1", "UD-56"),
                                    ("1.4", "UD-61"), ("3.1", "UD-65"), ("3.2", "UD-69"), ("3.3", "UD-73")):
                stream.write(f"| {decision} | Authorized for **story {story} only**. |\n")
        _, errors = self.check_scope()
        self.assertEqual(errors, [])

    def test_demo_delivery_paths_require_story_9_1_scope(self):
        self.authorize(story_10_2=True)
        decisions = self.root / "docs/planning-artifacts/decisions-assumptions.md"
        with decisions.open("a", encoding="utf-8") as stream:
            stream.write("| UD-48 | Authorized for story 1.2 only. |\n"
                         "| UD-50 | Authorized for story 1.1 only. |\n"
                         "| UD-53 | Authorized for story 1.3 only. |\n"
                         "| UD-56 | Authorized for story 9.1 only. |\n")
        self.write(".git")
        allowed = ("apps/web/warmup.html", "apps/web/playwright.demo.config.ts",
                   "apps/web/demo-e2e/journey.spec.ts", "infra/demo/compose.yml")
        denied = "infra/demo/.env"
        for name in (*allowed, denied):
            self.write(name)
        entries = "".join(f"100644 {'0' * 40} 0\t{name}\0" for name in (*allowed, denied))
        index = subprocess.CompletedProcess([], 0, entries.encode())
        with patch("validate_docs.subprocess.run", return_value=index):
            self.configure('authorized_stories = ["10.1", "10.2", "1.2", "1.1", "1.3"]')
            _, errors = self.check_scope()
            self.assertEqual(len(errors), len(allowed) + 2)
            self.assertIn("only allows infra/local under infra/", errors[0])
            self.configure('authorized_stories = ["10.1", "10.2", "1.2", "1.1", "1.3", "9.1"]')
            _, errors = self.check_scope()
            self.assertEqual(errors, [
                "Unexpected tracked path for stories 10.1, 10.2, 1.2, 1.1, 1.3 and 9.1: infra/demo/.env"
            ])

    def test_isolation_replay_path_requires_story_1_4_scope(self):
        self.authorize(story_10_2=True)
        decisions = self.root / "docs/planning-artifacts/decisions-assumptions.md"
        with decisions.open("a", encoding="utf-8") as stream:
            stream.write("| UD-48 | Authorized for story 1.2 only. |\n"
                         "| UD-50 | Authorized for story 1.1 only. |\n"
                         "| UD-53 | Authorized for story 1.3 only. |\n"
                         "| UD-56 | Authorized for story 9.1 only. |\n"
                         "| UD-61 | Authorized for **story 1.4 only**. |\n")
        self.write(".git")
        replay = "apps/web/demo-e2e/isolation.spec.ts"
        self.write(replay)
        index = subprocess.CompletedProcess([], 0, f"100644 {'0' * 40} 0\t{replay}\0".encode())
        with patch("validate_docs.subprocess.run", return_value=index):
            self.configure('authorized_stories = ["10.1", "10.2", "1.2", "1.1", "1.3", "9.1"]')
            _, errors = self.check_scope()
            self.assertEqual(errors, [
                "Unexpected tracked path for stories 10.1, 10.2, 1.2, 1.1, 1.3 and 9.1: " + replay
            ])
            self.configure('authorized_stories = ["10.1", "10.2", "1.2", "1.1", "1.3", "9.1", "1.4"]')
            _, errors = self.check_scope()
            self.assertEqual(errors, [])

    def test_authorized_scopes_reject_top_level_packages(self):
        for story_10_2 in (False, True):
            with self.subTest(story_10_2=story_10_2):
                self.authorize(story_10_2=story_10_2)
                self.write("packages/client/package.json")
                _, errors = self.check_scope()
                self.assertEqual(errors, ["Application implementation artifact created: packages"])

    def test_azure_foundation_scope_requires_decision_and_exact_artifacts(self):
        self.authorize()
        foundation = "infra/azure/foundation.bicep"
        extra = "infra/azure/private.bicepparam"
        self.write(foundation, "targetScope = 'subscription'\n")
        _, errors = self.check_scope()
        self.assertTrue(any("only allows infra/local" in error for error in errors))

        register = self.root / "docs/planning-artifacts/decisions-assumptions.md"
        with register.open("a", encoding="utf-8") as decisions:
            decisions.write("| UD-76 | Aymen authorized local cloud deployment preparation |\n")
        self.write(".git")
        entries = "".join(f"100644 {'0' * 40} 0\t{name}\0" for name in (foundation, extra))
        index = subprocess.CompletedProcess([], 0, entries.encode())
        with patch("validate_docs.subprocess.run", return_value=index):
            _, errors = self.check_scope()
        self.assertEqual(errors, [f"Unexpected tracked path for story 10.1: {extra}"])

        self.write(extra)
        _, errors = self.check_scope()
        self.assertIn(f"Unexpected Azure foundation artifact: {extra}", errors)

    def test_invalid_config_fails_closed(self):
        self.write("apps/web/package.json")
        invalid_list = '[etaye] authorized_stories must be'
        for settings, gate, expected in [
            ('authorized_stories = ["10.1", "10.3"]', "false", "unknown authorized story"),
            ('authorized_stories = ["10.1", "10.2 "]', "false", "unknown authorized story"),
            ('authorized_stories = ["10.1", "10.02"]', "false", "unknown authorized story"),
            ('authorized_stories = ["10.1", "10.1"]', "false", "duplicate story IDs"),
            ('authorized_stories = ["10.1", "10.2", "10.2"]', "false", "duplicate story IDs"),
            ('authorized_stories = ["10.2"]', "false", invalid_list),
            ('authorized_stories = ["10.2", "10.1"]', "false", invalid_list),
            ('authorized_stories = ["10.1", "1.2"]', "false", invalid_list),
            ('authorized_stories = ["10.1"]', "true", "development_authorized must be false"),
            ('authorized_stories = ["10.1"]', "0", "development_authorized must be false"),
            ('authorized_stories = "10.1"', "false", "must be a list"),
            ('authorized_stories = [10.1]', "false", "must be a list"),
            ('authorized_storiess = ["10.1"]', "false", "Unknown keys"),
            ('phase = true', "false", "must be strings"),
            ('authorized_stories = [', "false", "Invalid authorization"),
            ('authorized_stories = []\nauthorized_stories = []', "false", "Invalid authorization"),
        ]:
            with self.subTest(settings=settings, gate=gate):
                self.configure(settings, gate=gate)
                scope, errors = self.check_scope()
                self.assertEqual(scope, {"mode": "invalid-authorization", "authorizedStories": []})
                self.assertIn(expected, "\n".join(errors))
                self.assertIn("Application implementation artifact created: apps", errors)

    def test_authorized_scope_requires_structure_and_gitignore_rules(self):
        self.authorize()
        (self.root / "apps/api/pom.xml").unlink()
        self.write(".gitignore", "node_modules/\ntarget/\ntest-results/\nplaywright-report/\n")
        _, errors = self.check_scope()
        self.assertIn("Missing or invalid story 10.1 file: apps/api/pom.xml", errors)
        self.assertIn("Missing build output rule in .gitignore: dist", errors)

    def test_tracked_build_output_fails_even_if_gitignore_lists_it(self):
        self.authorize()
        self.write(".git")
        tracked = "apps/web/dist/bundle name.js"
        entries = "".join(f"100644 {'0' * 40} 0\t{name}\0" for name in ("apps/web/src/App.tsx", tracked))
        index = subprocess.CompletedProcess([], 0, entries.encode())
        with patch("validate_docs.subprocess.run", return_value=index) as run:
            scope, errors = self.check_scope()
        run.assert_called_once_with(["git", "ls-files", "-s", "-z"], cwd=self.root, capture_output=True, check=True)
        self.assertEqual(scope["trackedBuildOutputCheck"], "checked with git ls-files")
        self.assertEqual(errors, [f"Tracked build output: {tracked}"])

    def test_git_unavailable_is_reported(self):
        self.authorize()
        self.write(".git")
        with patch("validate_docs.subprocess.run", side_effect=FileNotFoundError):
            scope, errors = self.check_scope()
        self.assertIn("skipped", scope["trackedBuildOutputCheck"])
        self.assertEqual(errors, [])

    def test_authorized_scope_rejects_unlisted_tracked_entries(self):
        self.authorize()
        self.write(".git")
        allowed = ("apps/api/.mvn/wrapper/maven-wrapper.properties",
                   "apps/api/src/main/java/dev/aymen/etaye/EtayeApiApplication.java",
                   "apps/api/src/main/resources/db/migration/V1__initial_schema.sql",
                   "apps/api/src/test/java/dev/aymen/etaye/security/SecurityIntegrationTest.java",
                   "apps/api/src/test/resources/application-test.yml",
                   "apps/api/src/test/resources/fixtures/nested/patient.json",
                   "apps/web/src/components/Panel.tsx", "apps/web/src/styles.css",
                   "apps/web/tests/nested/probe.spec.ts", "apps/web/e2e/outage.spec.ts",
                   "apps/web/vitest.config.ts", "apps/web/eslint.config.js", "apps/web/eslint-suppressions.json",
                     "apps/web/eslint-debt.json", "apps/api/compiler-debt.json", "apps/web/sonar-project.properties",
                   "infra/local/verify-init-roles.sh")
        unlisted = ("apps/api/extra.txt", "apps/web/public/extra.txt", "infra/local/extra.sh",
                    "apps/web/package.json/extra.txt", "apps/api/src",
                    "apps/api/src/main/evil.sh", "apps/api/.mvn/wrapper/maven-wrapper.jar",
                    "apps/api/src/main/java/dev/aymen/etaye/App.class",
                    "apps/api/src/test/java/dev/another/OtherTest.java",
                    "apps/api/src/main/resources/archive.zip", "apps/api/src/main/resources/nested/evil.sh",
                    "apps/api/src/test/resources/.env", "apps/web/src/nested/.env",
                    "apps/web/tests/snapshot.png", "apps/web/e2e/script.sh",
                    "apps/api/src/main/resources/binary.txt", "apps/web/eslint.config.mjs",
                    "apps/web/eslint-suppressions.json.bak")
        for name in (*allowed, *unlisted):
            if name != "apps/api/src" and not name.startswith("apps/web/package.json/"):
                self.write(name)
        (self.root / "apps/api/src/main/resources/binary.txt").write_bytes(b"\0\xff")
        entries = "".join(f"100644 {'0' * 40} 0\t{name}\0" for name in (*allowed, *unlisted))
        index = subprocess.CompletedProcess([], 0, entries.encode())
        with patch("validate_docs.subprocess.run", return_value=index):
            _, errors = self.check_scope()
        self.assertEqual(errors, [f"Unexpected tracked path for story 10.1: {name}" for name in unlisted])

    def test_tracked_codegen_paths_are_allowed_only_in_combined_scope(self):
        self.write(".git")
        paths = ("apps/web/scripts/generate-client.mjs", "apps/web/openapi-ts.config.ts",
                 "apps/web/scripts/x.ts", "apps/web/openapi-gen.config.mjs", "apps/web/codegen.config.ts")
        entries = "".join(f"100644 {'0' * 40} 0\t{name}\0" for name in paths)
        index = subprocess.CompletedProcess([], 0, entries.encode())
        for story_10_2 in (False, True):
            with self.subTest(story_10_2=story_10_2):
                self.authorize(story_10_2=story_10_2)
                with patch("validate_docs.subprocess.run", return_value=index):
                    _, errors = self.check_scope()
                expected = [] if story_10_2 else [f"Unexpected tracked path for story 10.1: {name}" for name in paths]
                self.assertEqual(errors, expected)

    def test_combined_scope_rejects_unlisted_codegen_paths(self):
        self.authorize(story_10_2=True)
        self.write(".git")
        paths = ("apps/web/scripts/sub/x.mjs", "apps/web/scripts/X.MJS", "apps/web/scripts/noext",
                 "apps/web/other.config.ts", "apps/web/scripts/../x.mjs")
        entries = "".join(f"100644 {'0' * 40} 0\t{name}\0" for name in paths)
        index = subprocess.CompletedProcess([], 0, entries.encode())
        with patch("validate_docs.subprocess.run", return_value=index):
            _, errors = self.check_scope()
        self.assertEqual(errors, [f"Unexpected tracked path for stories 10.1 and 10.2: {name}" for name in paths])

    def test_authorized_scope_rejects_tracked_symlinks_with_allowed_names(self):
        self.authorize()
        links = ("apps/api/src/main/java/dev/aymen/etaye/Link.java", "apps/web/src/Link.tsx",
                 "infra/local/init-roles.sh")
        for name in links:
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            os.symlink(self.root / ".gitignore", path)

        subprocess.run(["git", "init", "--quiet", "--template="], cwd=self.root, check=True)
        subprocess.run(["git", "-c", "core.symlinks=true", "update-index", "--add", "--", *links],
                       cwd=self.root, check=True)
        # The index must still reject a link if its worktree copy has been replaced by a regular file.
        (self.root / links[0]).unlink()
        self.write(links[0])
        _, errors = self.check_scope()
        self.assertEqual(errors, [f"Tracked symlink for story 10.1: {name}" for name in links])

    def test_combined_scope_rejects_tracked_symlink_at_allowed_script_path(self):
        self.authorize(story_10_2=True)
        name = "apps/web/scripts/generate-client.mjs"
        path = self.root / name
        path.parent.mkdir()
        os.symlink(self.root / ".gitignore", path)
        subprocess.run(["git", "init", "--quiet", "--template="], cwd=self.root, check=True)
        subprocess.run(["git", "-c", "core.symlinks=true", "update-index", "--add", "--", name],
                       cwd=self.root, check=True)
        _, errors = self.check_scope()
        self.assertEqual(errors, [f"Tracked symlink for stories 10.1 and 10.2: {name}"])

    def test_combined_scope_rejects_tracked_gitlink_at_allowed_script_path(self):
        self.authorize(story_10_2=True)
        name = "apps/web/scripts/generate-client.mjs"
        subprocess.run(["git", "init", "--quiet", "--template=", "--object-format=sha1"], cwd=self.root, check=True)
        sha = "1" * 40  # A gitlink may refer to a commit absent from this repository.
        subprocess.run(["git", "update-index", "--add", "--cacheinfo", f"160000,{sha},{name}"],
                       cwd=self.root, check=True)
        _, errors = self.check_scope()
        self.assertEqual(errors, [f"Tracked non-regular entry for stories 10.1 and 10.2: {name}"])

    def test_authorized_scope_requires_ud_41_even_without_git(self):
        self.authorize()
        register = "docs/planning-artifacts/decisions-assumptions.md"
        reference = "| UD-28 | Partly lifted by UD-41 for story 10.1 only. |\n"
        for decision in ("", "| UD-41 | Authorized story 10.10 | See story 10.1. |\n",
                         "| UD-41 | Authorized story 10.1.123 |\n",
                         "UD-41 authorizes story 10.1.\n",
                         "| UD-41 | Development is not authorized for story 10.1 only. |\n",
                         "| UD-41 | Development is no longer authorized for story 10.1. |\n",
                         "| UD-41 | Development is no longer authorized for story 10.1 only. |\n",
                         "| UD-41 | Authorized for story 10.1 only. Also authorized for story 10.2. |\n",
                         "| UD-41 | Development is authorized for story 10.1 only, but refused |\n",
                         "| UD-41 | Pending decision for story 10.1 | Authorized for story 10.1 only |\n"):
            with self.subTest(decision=decision):
                self.write(register, reference + decision)
                scope, errors = self.check_scope()
                self.assertIn("skipped", scope["trackedBuildOutputCheck"])
                self.assertEqual(errors, [f"Story 10.1 authorization requires UD-41 in {register} to authorize only story 10.1"])

    def test_combined_scope_requires_ud_45_in_the_decision_cell(self):
        self.authorize(story_10_2=True)
        register = "docs/planning-artifacts/decisions-assumptions.md"
        reference = "| UD-41 | Authorized for story 10.1 only |\n"
        for decision in ("", "| UD-45 | Authorized for story 10.20 only | See story 10.2. |\n",
                         "| UD-45 | Authorized for story 10.2 only. Also authorized for story 10.1. |\n",
                         "UD-45 authorizes story 10.2.\n",
                         "| UD-45 | Development is not authorized for story 10.2 only. |\n",
                         "| UD-45 | Development is no longer authorized for story 10.2 only. |\n",
                         "| UD-45 | Development is authorized for story 10.2 only, but refused |\n",
                         "| UD-45 | Pending decision | Authorized for story 10.2 only |\n"):
            with self.subTest(decision=decision):
                self.write(register, reference + decision)
                _, errors = self.check_scope()
                self.assertEqual(errors, [f"Story 10.2 authorization requires UD-45 in {register} to authorize only story 10.2"])

    def test_combined_scope_still_requires_ud_41(self):
        self.authorize(story_10_2=True)
        register = "docs/planning-artifacts/decisions-assumptions.md"
        self.write(register, "| UD-45 | Authorized for story 10.2 only |\n")
        _, errors = self.check_scope()
        self.assertEqual(errors, [f"Story 10.1 authorization requires UD-41 in {register} to authorize only story 10.1"])

    def test_required_decision_rows_must_be_unique(self):
        register = "docs/planning-artifacts/decisions-assumptions.md"
        for story_10_2, decision_id, story in ((False, "UD-41", "10.1"), (True, "UD-41", "10.1"),
                                               (True, "UD-45", "10.2")):
            with self.subTest(story_10_2=story_10_2, decision_id=decision_id):
                self.authorize(story_10_2=story_10_2)
                decisions = (self.root / register).read_text(encoding="utf-8")
                error = f"Story {story} authorization requires {decision_id} in {register} to authorize only story {story}"
                for cell in (f" Authorized for story {story} only |", ""):
                    with self.subTest(cell=cell):
                        self.write(register, decisions + f" \t|\t{decision_id}\t|{cell}\n")
                        _, errors = self.check_scope()
                        self.assertEqual(errors, [error])

    def test_hidden_decision_rows_do_not_count_toward_authorization(self):
        register = "docs/planning-artifacts/decisions-assumptions.md"
        story_10_1 = "| UD-41 | Authorized for story 10.1 only |\n"
        authorized = "| UD-45 | Authorized for story 10.2 only |\n"
        revoked = "| UD-45 | Authorization revoked for story 10.2 |\n"
        error = f"Story 10.2 authorization requires UD-45 in {register} to authorize only story 10.2"
        self.authorize(story_10_2=True)
        for start, end in (("```markdown\n", "```\n"), ("<!--\n", "-->\n")):
            for visible, expected in ((revoked, [error]), (authorized, []), ("", [error])):
                with self.subTest(start=start, visible=visible):
                    self.write(register, story_10_1 + start + authorized + end + visible)
                    _, errors = self.check_scope()
                    self.assertEqual(errors, expected)

    def test_html_comments_in_decision_cells_are_ignored(self):
        self.authorize()
        register = "docs/planning-artifacts/decisions-assumptions.md"
        error = f"Story 10.1 authorization requires UD-41 in {register} to authorize only story 10.1"
        for cell, expected in (("<!-- Authorized for story 10.1 only -->Pending", [error]),
                               ("Authorized for story 10.1 only <!-- refused | 10.2 -->", [])):
            with self.subTest(cell=cell):
                self.write(register, f"| UD-41 | {cell} |\n")
                _, errors = self.check_scope()
                self.assertEqual(errors, expected)

    def test_negated_authorizations_are_rejected(self):
        self.authorize(story_10_2=True)
        register = "docs/planning-artifacts/decisions-assumptions.md"
        story_10_1 = "| UD-41 | Authorized for story 10.1 only |\n"
        for negation in ("not currently authorized", "not yet authorized", "never authorized",
                         "no longer authorized", "not currently or previously authorized"):
            with self.subTest(negation=negation):
                self.write(register, story_10_1 + f"| UD-45 | Development is {negation} for story 10.2 only. |\n")
                _, errors = self.check_scope()
                self.assertEqual(errors, [f"Story 10.2 authorization requires UD-45 in {register} to authorize only story 10.2"])

    def test_authorized_scope_accepts_real_ud_41_row(self):
        self.authorize()
        register = "docs/planning-artifacts/decisions-assumptions.md"
        rows = (ROOT / register).read_text(encoding="utf-8").splitlines()
        row = next(line for line in rows if line.startswith("| UD-41 |"))
        self.write(register, row + "\n")
        _, errors = self.check_scope()
        self.assertEqual(errors, [])


@unittest.skipIf(yaml is None, "PyYAML is unavailable")
class ApplicationWorkflowTests(unittest.TestCase):
    def test_workflow_yaml_rejects_duplicate_with_keys(self):
        workflow = """\
jobs:
  test:
    steps:
      - uses: actions/checkout
        with:
          persist-credentials: true
        with:
          persist-credentials: false
"""
        with self.assertRaisesRegex(yaml.constructor.ConstructorError, "duplicate mapping key"):
            yaml.load(workflow, Loader=UniqueKeySafeLoader)

    def test_workflows_use_allowlisted_pinned_actions_with_read_only_permissions(self):
        allowed_actions = {"actions/checkout", "actions/setup-java", "actions/setup-node",
                           "actions/upload-artifact", "actions/dependency-review-action",
                           "astral-sh/setup-uv"}

        def check_node(node, *, release=False):
            if isinstance(node, dict):
                self.assertNotIn("services", node)
                if "permissions" in node:
                    if not release:
                        self.assertEqual(node["permissions"], {"contents": "read"})
                if "uses" in node:
                    self.assertRegex(node["uses"], r"^[\w./-]+@[0-9a-fA-F]{40}$")
                    actions = allowed_actions | {"actions/attest", "actions/download-artifact"} if release else allowed_actions
                    self.assertIn(node["uses"].split("@")[0], actions)
                for value in node.values():
                    check_node(value, release=release)
            elif isinstance(node, list):
                for value in node:
                    check_node(value, release=release)

        for name in ("api.yml", "web.yml", "dependency-review.yml", "package.yml", "sonarqube.yml"):
            with self.subTest(path=name):
                path = ROOT / ".github/workflows" / name
                workflow = yaml.load(path.read_text(encoding="utf-8"), Loader=UniqueKeySafeLoader)
                self.assertEqual(workflow["permissions"], {"contents": "read"})
                # Pull request runs supersede each other; a merge to main is never cancelled (see api.yml).
                self.assertIn(workflow["concurrency"]["cancel-in-progress"],
                              (True, "${{ github.event_name == 'pull_request' }}"))
                for job in workflow["jobs"].values():
                    self.assertGreater(job["timeout-minutes"], 0)
                    checkout = next(step for step in job["steps"] if step.get("uses", "").startswith("actions/checkout@"))
                    self.assertIs(checkout["with"]["persist-credentials"], False)
                check_node(workflow)

        release_workflow = yaml.load((ROOT / ".github/workflows/release.yml").read_text(encoding="utf-8"),
                                     Loader=UniqueKeySafeLoader)
        check_node(release_workflow, release=True)
        self.assertEqual(release_workflow["permissions"], {"contents": "read"})
        self.assertEqual(release_workflow["jobs"]["candidate"]["permissions"],
                         {"contents": "read", "actions": "read"})
        self.assertEqual(release_workflow["jobs"]["publish"]["permissions"],
                         {"contents": "read", "packages": "write", "attestations": "write", "id-token": "write"})


if __name__ == "__main__":
    unittest.main()
