import contextlib
import json
import os
import re
import subprocess
import tempfile
import unittest
from io import StringIO
from unittest.mock import patch
from pathlib import Path

import ci_scope

ALL, NONE = ci_scope.ALL, ci_scope.NONE
classify_path, decide, run = ci_scope.classify_path, ci_scope.decide, ci_scope.run

ROOT = Path(__file__).resolve().parents[1]


def scopes_of(*paths):
    """Scopes that a change touching exactly these paths requires."""
    decision = decide(list(paths))
    return {scope for scope, (relevant, _) in decision.items() if relevant}


class PathScenarioTest(unittest.TestCase):
    def test_api_only_change(self):
        self.assertEqual(scopes_of("apps/api/src/main/java/dev/aymen/etaye/Foo.java"), {"api", "package", "sonar-api"})
        self.assertEqual(scopes_of("apps/api/pom.xml"), {"api", "package", "sonar-api"})

    def test_web_only_change(self):
        self.assertEqual(scopes_of("apps/web/src/record/asof.ts"), {"web", "package", "sonar-web"})
        self.assertEqual(scopes_of("apps/web/eslint.config.js"), {"web", "package", "sonar-web"})

    def test_contract_change_reaches_web_and_package_but_not_api(self):
        # The API does not read the contract today; D-56 and the contract-conformance deferral track that gap.
        self.assertEqual(scopes_of("docs/contracts/openapi.yaml"), {"web", "package", "sonar-web"})

    def test_api_tests_are_not_part_of_the_delivery_package(self):
        self.assertEqual(scopes_of("apps/api/src/test/java/dev/aymen/etaye/FooTest.java"), {"api", "sonar-api"})
        self.assertEqual(scopes_of("apps/api/src/test/resources/isolation/matrix-cases.json"),
                         {"api", "web", "sonar-api", "sonar-web"})

    def test_migration_change_reaches_api_and_package(self):
        self.assertEqual(scopes_of("apps/api/src/main/resources/db/migration/V13__x.sql"),
                         {"api", "package", "sonar-api"})

    def test_shared_typescript_configuration_and_lockfile(self):
        self.assertEqual(scopes_of("tsconfig.base.json"), {"web", "package", "sonar-web"})
        self.assertEqual(scopes_of("pnpm-lock.yaml"), {"web", "package", "pnpm", "sonar-web"})
        self.assertEqual(scopes_of("apps/web/package.json"), {"web", "package", "pnpm", "sonar-web"})
        self.assertEqual(scopes_of("package.json"), {"web", "package", "pnpm", "sonar-web"})

    def test_design_tokens_icons_and_logos(self):
        for path in (
            "design/tokens/adr-014.json",
            "design/received/2026-11-03/icons.css",
            "design/received/2026-11-03/export/etaye-design-system/fonts/Inter.woff2",
            "design/offline-reference/site/logos/etaye-logo.svg",
            "tools/fixtures/etaye-tokens.golden.css",
        ):
            with self.subTest(path=path):
                self.assertEqual(scopes_of(path), {"web", "package", "sonar-web"})
        self.assertEqual(scopes_of("tools/generate_web_tokens.py"),
                         {"web", "package", "sonar-tooling"})

    def test_isolation_manifest_is_read_by_api_and_web(self):
        self.assertEqual(scopes_of("apps/api/src/test/resources/isolation/matrix-cases.json"),
                         {"api", "web", "sonar-api", "sonar-web"})

    def test_ci_scripts_and_workflows_verify_themselves(self):
        self.assertEqual(scopes_of("tools/ci_scope.py"), set(ci_scope.SCOPES))
        self.assertEqual(scopes_of("tools/assert_test_reports.py"), {"api", "web", "package", "sonar-tooling"})
        self.assertEqual(scopes_of("tools/assert_coverage_reports.py"),
                         {"api", "web", "sonar-api", "sonar-web", "sonar-tooling"})
        self.assertEqual(scopes_of("tools/run_package_smoke.sh"), {"package"})
        self.assertEqual(scopes_of("tools/run_image_scan.sh"), {"package"})
        self.assertEqual(scopes_of("tools/install_corepack.sh"), {"web", "package", "pnpm", "sonar-web"})
        self.assertEqual(scopes_of("tools/assert_isolation_matrix.py"), {"api", "sonar-tooling"})
        self.assertEqual(scopes_of(".github/workflows/api.yml"), {"api"})
        self.assertEqual(scopes_of(".github/workflows/web.yml"), {"web"})
        self.assertEqual(scopes_of(".github/workflows/dependency-review.yml"), {"pnpm"})
        self.assertEqual(scopes_of(".github/workflows/sonarqube.yml"),
                         {"sonar-api", "sonar-web", "sonar-tooling"})

    def test_sonarqube_config_files_trigger_only_their_project(self):
        self.assertEqual(scopes_of("apps/web/sonar-project.properties"), {"sonar-web"})
        self.assertEqual(scopes_of("tools/pyproject.toml"), {"sonar-tooling"})
        self.assertEqual(scopes_of("tools/sonar-requirements.txt"), {"sonar-tooling"})
        self.assertEqual(scopes_of("tools/sub/helper.py"), {"sonar-tooling"})
        self.assertEqual(scopes_of("design/corrections/verify.py"), {"sonar-tooling"})
        self.assertEqual(scopes_of("design/corrections/prototype.js"), set())

    def test_docker_and_compose_reach_the_package_only(self):
        self.assertEqual(scopes_of("infra/demo/compose.yml"), {"package"})
        self.assertEqual(scopes_of("infra/local/init-roles.sh"), {"package"})
        self.assertEqual(scopes_of(".dockerignore"), {"package"})

    def test_documentation_and_agent_tooling_change_nothing(self):
        for path in (
            "docs/planning-artifacts/prd/prd.md",
            "docs/design/reviews/2026-11-25-story-3-3-runtime/index.html",
            "design/received/2026-11-03/design-system/notes.md",
            "README.md",
            ".claude/skills/bmad-build/SKILL.md",
            "_bmad/custom/config.toml",
            ".github/workflows/documentation.yml",
            ".github/dependabot.yml",
        ):
            with self.subTest(path=path):
                self.assertEqual(scopes_of(path), set())
        self.assertEqual(scopes_of("tools/validate_docs.py"), {"sonar-tooling"})

    def test_unknown_paths_run_everything(self):
        for path in ("eslint.config.js", ".node-version", ".npmrc", ".editorconfig", "packages/ui/src/x.ts",
                     "sonar-project.properties", "newdir/anything"):
            with self.subTest(path=path):
                self.assertEqual(classify_path(path), (ALL, None))
                self.assertEqual(scopes_of(path), set(ci_scope.SCOPES))

    def test_gitattributes_is_an_explicit_decision_that_verifies_everything(self):
        self.assertEqual(classify_path(".gitattributes"), (ALL, ".gitattributes"))

    def test_one_relevant_path_among_many_ignored_ones_is_enough(self):
        self.assertEqual(scopes_of("docs/a.md", "docs/b.md", "apps/web/src/x.ts"), {"web", "package", "sonar-web"})

    def test_an_empty_change_is_not_trusted(self):
        self.assertEqual(scopes_of(), set(ci_scope.SCOPES))

    def test_glob_segments(self):
        self.assertEqual(classify_path("apps/api"), (ALL, None), "a bare directory name is not a file path")
        self.assertEqual(classify_path("apps/webx/package.json")[0], {"web", "package", "pnpm", "sonar-web"})
        self.assertEqual(scopes_of("design/received/2026-11-03/licenses/Inter-OFL.txt"), {"web", "package", "sonar-web"})
        self.assertEqual(scopes_of("design/received/2026-11-03/licenses/README.md"), set())

    def test_every_rule_scope_is_known(self):
        for pattern, scopes in ci_scope.RULES:
            self.assertTrue(set(scopes) <= set(ci_scope.SCOPES), pattern)
        self.assertEqual(NONE, frozenset())


class GlobTest(unittest.TestCase):
    def matches(self, pattern, path):
        return bool(ci_scope.compile_glob(pattern).fullmatch(path))

    def test_double_star_slash_matches_zero_or_more_directories(self):
        self.assertTrue(self.matches("apps/web/src/**/*.ts", "apps/web/src/main.ts"))
        self.assertTrue(self.matches("apps/web/src/**/*.ts", "apps/web/src/record/asof.ts"))
        self.assertTrue(self.matches("apps/web/src/**/*.ts", "apps/web/src/a/b/c.ts"))
        self.assertFalse(self.matches("apps/web/src/**/*.ts", "apps/web/src/record/asof.tsx"))
        self.assertFalse(self.matches("apps/web/src/**/*.ts", "apps/web/other/main.ts"))

    def test_single_star_stays_in_one_segment(self):
        self.assertTrue(self.matches("docs/*.md", "docs/a.md"))
        self.assertFalse(self.matches("docs/*.md", "docs/sub/a.md"))
        self.assertTrue(self.matches("apps/api/**", "apps/api/pom.xml"))
        self.assertFalse(self.matches("apps/api/**", "apps/apix/pom.xml"))

    def test_regex_metacharacters_are_literal(self):
        self.assertTrue(self.matches("a+b(1).txt", "a+b(1).txt"))
        self.assertFalse(self.matches("a.txt", "axtxt"))


class WorkflowReferencesTest(unittest.TestCase):
    """A script a workflow runs, directly or through another script or import, must trigger that workflow."""

    WORKFLOW_SCOPES = {
        "api.yml": {"verify": "api"},
        "web.yml": {"verify": "web"},
        "package.yml": {"package": "package"},
        "dependency-review.yml": {"review": "pnpm"},
        "sonarqube.yml": {
            "preflight": "sonar-api",
            "sonar-api": "sonar-api",
            "sonar-web": "sonar-web",
            "sonar-tooling": "sonar-tooling",
        },
    }
    REFERENCE = re.compile(r"tools/[A-Za-z0-9_./-]+\.(?:py|sh)")

    def test_every_script_reached_from_a_workflow_is_classified_for_it(self):
        import yaml

        for workflow, job_scopes in self.WORKFLOW_SCOPES.items():
            data = yaml.safe_load((ROOT / ".github/workflows" / workflow).read_text())
            for job_id, scope in job_scopes.items():
                scopes = ("sonar-api", "sonar-web", "sonar-tooling") if workflow == "sonarqube.yml" and job_id == "preflight" else (scope,)
                queue = set(self.REFERENCE.findall(json.dumps(data["jobs"][job_id])))
                seen = set()
                while queue:
                    path = queue.pop()
                    if path in seen or not (ROOT / path).is_file():
                        continue
                    seen.add(path)
                    for target_scope in scopes:
                        with self.subTest(workflow=workflow, job=job_id, scope=target_scope, script=path):
                            self.assertIn(target_scope, classify_path(path)[0],
                                          f"{path} runs in {workflow} ({job_id}); a change to it must trigger {target_scope}")
                    if path == "tools/ci_scope.py":
                        continue  # its rule table names every script by design; it is classified for all scopes
                    text = (ROOT / path).read_text()
                    queue |= set(self.REFERENCE.findall(text))
                    if path.endswith(".py"):
                        queue |= {f"tools/{module}.py" for module in re.findall(r"^(?:import|from)\s+(\w+)", text, re.M)}
                self.assertTrue(seen, f"{workflow} ({job_id}) references no script; the scope mapping is stale")


class TrackedFilesAreClassifiedTest(unittest.TestCase):
    def test_every_tracked_file_has_an_explicit_rule(self):
        listed = subprocess.run(["git", "ls-files", "-z"], cwd=ROOT, capture_output=True, check=False)
        self.assertEqual(listed.returncode, 0, "git ls-files must work: the CI checkout is a Git repository")
        paths = [name.decode("utf-8") for name in listed.stdout.split(b"\0") if name]
        self.assertGreater(len(paths), 100)
        unclassified = [path for path in paths if classify_path(path)[1] is None]
        self.assertEqual(
            unclassified, [],
            "These tracked files match no rule in tools/ci_scope.py. Unknown paths run every verification; "
            "name the scopes the file affects, or add it to a rule that ignores it, so the decision is explicit.",
        )


class GitScenarioTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.repo = Path(directory.name)
        self.git("init", "-q", "-b", "main")
        self.git("config", "user.email", "ci@example.test")
        self.git("config", "user.name", "CI")
        self.git("config", "commit.gpgsign", "false")
        self.write("README.md", "start\n")
        self.write("apps/web/src/a.ts", "a\n")
        self.write("docs/note.md", "n\n")
        self.commit("base")

    def git(self, *args, check=True):
        return subprocess.run(["git", *args], cwd=self.repo, capture_output=True, check=check, text=True)

    def write(self, name, text):
        path = self.repo / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)

    def commit(self, message):
        self.git("add", "-A")
        self.git("commit", "-q", "-m", message)

    def runner(self, command, **kwargs):
        return subprocess.run(command, cwd=self.repo, **kwargs)

    def pull_request_merge(self, branch_action):
        """Build the commit that GitHub checks out for a pull request: a merge of the head into the base."""
        self.git("checkout", "-q", "-b", "feature")
        branch_action()
        self.commit("feature")
        self.git("checkout", "-q", "main")
        self.write("README.md", "main moved on\n")
        self.commit("main moved on")
        self.git("merge", "--no-ff", "-q", "feature", "-m", "merge")

    def decision(self, scope, event="pull_request"):
        return run(scope, event, "HEAD^1", "HEAD", self.runner)

    def test_documentation_only_pull_request_skips_every_scope(self):
        self.pull_request_merge(lambda: self.write("docs/new.md", "doc\n"))
        for scope in ci_scope.SCOPES:
            with self.subTest(scope=scope):
                self.assertFalse(self.decision(scope)[0])

    def test_merge_commit_compares_with_the_base_tip_not_the_whole_history(self):
        # The base moved on with a documentation file; only the feature's own file must count.
        self.pull_request_merge(lambda: self.write("apps/web/src/b.ts", "b\n"))
        self.assertTrue(self.decision("web")[0])
        self.assertFalse(self.decision("api")[0])

    def test_deleted_file_counts(self):
        self.pull_request_merge(lambda: (self.repo / "apps/web/src/a.ts").unlink())
        self.assertTrue(self.decision("web")[0])

    def test_rename_out_of_a_relevant_directory_counts_as_a_deletion(self):
        self.pull_request_merge(lambda: self.git("mv", "apps/web/src/a.ts", "docs/a.ts"))
        self.assertTrue(self.decision("web")[0])

    def test_rename_into_a_relevant_directory_counts_as_an_addition(self):
        self.pull_request_merge(lambda: self.git("mv", "docs/note.md", "apps/web/src/note.md"))
        self.assertTrue(self.decision("web")[0])

    def test_push_of_a_squash_commit_uses_its_parent(self):
        self.write("apps/web/src/c.ts", "c\n")
        self.commit("squash")
        self.assertTrue(self.decision("web", "push")[0])
        self.write("docs/another.md", "d\n")
        self.commit("docs squash")
        self.assertFalse(self.decision("web", "push")[0])

    def push_payload(self, before):
        path = self.repo / "event.json"
        path.write_text(json.dumps({"before": before}))
        return path

    def run_push(self, scope, before):
        with contextlib.chdir(self.repo), contextlib.redirect_stdout(StringIO()) as printed, \
                patch.dict(os.environ, {"GITHUB_EVENT_PATH": str(self.push_payload(before))}):
            output = self.repo / "github-output"
            output.write_text("")
            ci_scope.main(["--scope", scope, "--event", "push", "--github-output", str(output)])
        return output.read_text().strip(), printed.getvalue()

    def test_a_push_of_several_commits_is_compared_with_the_revision_before_it(self):
        base = self.git("rev-parse", "HEAD").stdout.strip()
        self.write("apps/web/src/b.ts", "b\n")
        self.commit("web change")
        self.write("docs/last.md", "last\n")
        self.commit("documentation-only last commit")
        # HEAD^1..HEAD sees the documentation file alone; the push as a whole changed the web application.
        self.assertFalse(self.decision("web", "push")[0])
        self.assertEqual(self.run_push("web", base)[0], "relevant=true")
        self.assertEqual(self.run_push("api", base)[0], "relevant=false")

    def test_a_squash_push_still_uses_its_parent(self):
        before = self.git("rev-parse", "HEAD").stdout.strip()
        self.write("docs/only.md", "x\n")
        self.commit("docs squash")
        self.assertEqual(self.run_push("web", before)[0], "relevant=false")

    def test_a_push_whose_before_revision_is_unavailable_fails_closed(self):
        self.write("docs/only.md", "x\n")
        self.commit("docs")
        for before in ("1" * 40, "0" * 40, "", None):
            with self.subTest(before=before):
                self.assertEqual(self.run_push("web", before)[0], "relevant=true")

    def test_missing_parent_history_fails_closed(self):
        # A root commit has no HEAD^1: the change list is unavailable, so everything is verified.
        relevant, reason = run("api", "push", "HEAD^1", "HEAD", self.runner)
        self.assertTrue(relevant)
        self.assertIn("change list unavailable", reason)

    def test_unreachable_git_fails_closed(self):
        def broken(*_args, **_kwargs):
            raise FileNotFoundError("git")

        relevant, reason = run("web", "pull_request", "HEAD^1", "HEAD", broken)
        self.assertTrue(relevant)
        self.assertIn("change list unavailable", reason)

    def test_failed_diff_exit_status_fails_closed(self):
        def failing(command, **_kwargs):
            return subprocess.CompletedProcess(command, 128, b"", b"fatal")

        self.assertTrue(run("api", "pull_request", "a", "b", failing)[0])

    def test_empty_diff_fails_closed(self):
        self.git("commit", "-q", "--allow-empty", "-m", "empty")
        relevant, reason = self.decision("api")
        self.assertTrue(relevant)
        self.assertIn("no changed path", reason)

    def test_manual_and_scheduled_runs_verify_everything(self):
        for event in ("workflow_dispatch", "schedule", "", "something_new"):
            with self.subTest(event=event):
                self.assertTrue(run("web", event, "HEAD^1", "HEAD", self.runner)[0])

    def test_path_with_unusual_characters(self):
        self.pull_request_merge(lambda: self.write("apps/web/src/café à déjà.ts", "x\n"))
        self.assertTrue(self.decision("web")[0])

    def test_unknown_scope_is_rejected(self):
        with self.assertRaises(ValueError):
            run("mobile", "pull_request", "HEAD^1", "HEAD", self.runner)

    def test_command_line_writes_the_step_output(self):
        self.pull_request_merge(lambda: self.write("docs/only.md", "x\n"))
        output = self.repo / "github-output"
        with contextlib.chdir(self.repo), contextlib.redirect_stdout(StringIO()) as printed:
            self.assertEqual(ci_scope.main(["--scope", "api", "--event", "pull_request",
                                            "--github-output", str(output)]), 0)
        self.assertEqual(output.read_text(), "relevant=false\n")
        self.assertIn("api verification needed: false", printed.getvalue())
        with contextlib.chdir(self.repo), contextlib.redirect_stdout(StringIO()):
            ci_scope.main(["--scope", "api", "--event", "workflow_dispatch", "--github-output", str(output)])
        self.assertEqual(output.read_text(), "relevant=false\nrelevant=true\n")

    def test_option_like_revision_names_are_not_interpreted(self):
        relevant, reason = run("api", "pull_request", "--output=/tmp/should-not-exist", "HEAD", self.runner)
        self.assertTrue(relevant)
        self.assertFalse(os.path.exists("/tmp/should-not-exist"))
        self.assertIn("change list unavailable", reason)


if __name__ == "__main__":
    unittest.main()
