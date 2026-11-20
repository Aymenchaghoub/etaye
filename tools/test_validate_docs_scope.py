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

    def authorize(self):
        self.configure('authorized_stories = ["10.1"]')
        self.write("docs/planning-artifacts/decisions-assumptions.md", "| UD-41 | Authorized for story 10.1 only |\n")
        for name in ("apps/api/pom.xml", "apps/api/mvnw", "apps/web/package.json", "infra/local/docker-compose.yml",
                     "package.json", "pnpm-workspace.yaml", "pnpm-lock.yaml", "tsconfig.base.json",
                     ".github/workflows/api.yml", ".github/workflows/web.yml"):
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

    def test_invalid_config_fails_closed(self):
        self.write("apps/web/package.json")
        for settings, gate, expected in [
            ('authorized_stories = ["10.1", "10.2"]', "false", "unknown authorized story"),
            ('authorized_stories = ["10.1", "10.1"]', "false", "duplicate story IDs"),
            ('authorized_stories = ["10.1"]', "true", "development_authorized must be false"),
            ('authorized_stories = "10.1"', "false", "must be a list"),
            ('authorized_stories = [10.1]', "false", "must be a list"),
            ('authorized_storiess = ["10.1"]', "false", "Unknown keys"),
            ('phase = true', "false", "must be strings"),
            ('authorized_stories = [', "false", "Invalid authorization"),
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
                   "infra/local/verify-init-roles.sh")
        unlisted = ("apps/api/extra.txt", "apps/web/public/extra.txt", "infra/local/extra.sh",
                    "apps/web/package.json/extra.txt", "apps/api/src",
                    "apps/api/src/main/evil.sh", "apps/api/.mvn/wrapper/maven-wrapper.jar",
                    "apps/api/src/main/java/dev/aymen/etaye/App.class",
                    "apps/api/src/test/java/dev/another/OtherTest.java",
                    "apps/api/src/main/resources/archive.zip", "apps/api/src/main/resources/nested/evil.sh",
                    "apps/api/src/test/resources/.env", "apps/web/src/nested/.env",
                    "apps/web/tests/snapshot.png", "apps/web/e2e/script.sh",
                    "apps/api/src/main/resources/binary.txt")
        for name in (*allowed, *unlisted):
            if name != "apps/api/src" and not name.startswith("apps/web/package.json/"):
                self.write(name)
        (self.root / "apps/api/src/main/resources/binary.txt").write_bytes(b"\0\xff")
        entries = "".join(f"100644 {'0' * 40} 0\t{name}\0" for name in (*allowed, *unlisted))
        index = subprocess.CompletedProcess([], 0, entries.encode())
        with patch("validate_docs.subprocess.run", return_value=index):
            _, errors = self.check_scope()
        self.assertEqual(errors, [f"Unexpected tracked path for story 10.1: {name}" for name in unlisted])

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
        allowed_actions = {"actions/checkout", "actions/setup-java", "actions/setup-node", "actions/upload-artifact"}

        def check_node(node):
            if isinstance(node, dict):
                self.assertNotIn("services", node)
                if "permissions" in node:
                    self.assertEqual(node["permissions"], {"contents": "read"})
                if "uses" in node:
                    self.assertRegex(node["uses"], r"^[\w./-]+@[0-9a-fA-F]{40}$")
                    self.assertIn(node["uses"].split("@")[0], allowed_actions)
                for value in node.values():
                    check_node(value)
            elif isinstance(node, list):
                for value in node:
                    check_node(value)

        for name in ("api.yml", "web.yml"):
            with self.subTest(path=name):
                path = ROOT / ".github/workflows" / name
                workflow = yaml.load(path.read_text(encoding="utf-8"), Loader=UniqueKeySafeLoader)
                self.assertEqual(workflow["permissions"], {"contents": "read"})
                self.assertIs(workflow["concurrency"]["cancel-in-progress"], True)
                for job in workflow["jobs"].values():
                    self.assertGreater(job["timeout-minutes"], 0)
                    checkout = next(step for step in job["steps"] if step.get("uses", "").startswith("actions/checkout@"))
                    self.assertIs(checkout["with"]["persist-credentials"], False)
                check_node(workflow)


if __name__ == "__main__":
    unittest.main()
