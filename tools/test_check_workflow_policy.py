import tempfile
import textwrap
import unittest
from pathlib import Path

import check_workflow_policy as policy

ROOT = Path(__file__).resolve().parents[1]
SHA = "3d3c42e5aac5ba805825da76410c181273ba90b1"

GOOD = f"""\
name: Example
on:
  pull_request:
  push:
    branches: [main]
permissions:
  contents: read
concurrency:
  group: example-${{{{ github.ref }}}}
  cancel-in-progress: ${{{{ github.event_name == 'pull_request' }}}}
jobs:
  verify:
    name: API verify
    runs-on: ubuntu-24.04
    timeout-minutes: 20
    steps:
      - uses: actions/checkout@{SHA} # v7.0.1
        with:
          persist-credentials: false
      - name: Run
        env:
          EVENT: ${{{{ github.event_name }}}}
        run: echo "$EVENT"
      - uses: actions/upload-artifact@{SHA} # v7.0.2
        with:
          name: evidence
          path: out
          retention-days: 7
"""


def problems(text, name="example.yml"):
    return policy.check_workflow(name, textwrap.dedent(text) if text.startswith("\n") else text)


def mutate(old, new):
    assert old in GOOD, old
    return GOOD.replace(old, new)


class WorkflowInvariantTest(unittest.TestCase):
    def assert_flags(self, text, fragment):
        found = problems(text)
        self.assertTrue(any(fragment in line for line in found), f"expected {fragment!r} in {found}")

    def test_valid_workflow_passes(self):
        self.assertEqual(problems(GOOD), [])

    def test_unpinned_action_is_rejected(self):
        self.assert_flags(mutate(f"actions/checkout@{SHA} # v7.0.1", "actions/checkout@v4"),
                          "not pinned to a full commit SHA")
        self.assert_flags(mutate(f"actions/checkout@{SHA} # v7.0.1", f"actions/checkout@{SHA[:39]}"),
                          "not pinned to a full commit SHA")

    def test_pin_without_a_version_comment_is_rejected(self):
        self.assert_flags(mutate(f"actions/checkout@{SHA} # v7.0.1", f"actions/checkout@{SHA}"),
                          "no version comment")
        self.assert_flags(mutate(f"actions/checkout@{SHA} # v7.0.1", f"actions/checkout@{SHA} # latest"),
                          "no version comment")

    def test_flow_mapping_action_reference_keeps_the_same_pin_rules(self):
        account = "example/sample-action"
        replace_step = f"      - {{uses: '{account}@v1'}}"
        text = mutate("      - name: Run", replace_step + "\n      - name: Run")
        self.assert_flags(text, "not pinned to a full commit SHA")
        pinned = text.replace(f"{account}@v1", f"{account}@{SHA}")
        self.assert_flags(pinned, "no version comment")
        pinned = pinned.replace(f"{account}@{SHA}'}}", f"{account}@{SHA}'}} # v1.2.3")
        self.assertEqual(problems(pinned), [])

    def test_scope_reference_is_checked_when_the_shell_call_continues_on_another_line(self):
        script = '        run: |\n          python3 tools/ci_scope.py \\\n            --event pull_request --scope sandbox\n'
        self.assert_flags(mutate('        run: echo "$EVENT"\n', script), "unknown scope 'sandbox'")

    def test_local_actions_need_no_pin(self):
        text = mutate("      - name: Run", "      - uses: ./.github/actions/local\n      - name: Run")
        self.assertEqual(problems(text), [])

    def test_dangerous_triggers_are_rejected(self):
        self.assert_flags(mutate("  pull_request:\n", "  pull_request_target:\n"), "pull_request_target")
        self.assert_flags(mutate("  pull_request:\n", "  workflow_run:\n"), "workflow_run")

    def test_missing_or_broad_top_level_permissions_are_rejected(self):
        self.assert_flags(mutate("permissions:\n  contents: read\n", ""), "missing top-level permissions")
        self.assert_flags(mutate("  contents: read\n", "  contents: write\n"), "top-level permissions grant more")
        self.assert_flags(mutate("permissions:\n  contents: read\n", "permissions: write-all\n"),
                          "top-level permissions grant more")

    def test_empty_top_level_permissions_are_valid(self):
        self.assertEqual(problems(mutate("permissions:\n  contents: read\n", "permissions: {}\n")), [])

    def test_invalid_permission_shape_reports_a_policy_failure(self):
        self.assert_flags(mutate("permissions:\n  contents: read\n", "permissions: [read]\n"),
                          "top-level permissions grant more")

    def test_job_write_scopes_are_limited(self):
        allowed = mutate("    timeout-minutes: 20\n",
                         "    timeout-minutes: 20\n    permissions:\n      contents: read\n      security-events: write\n")
        self.assertEqual(problems(allowed), [])
        for scope in ("contents", "pull-requests", "actions"):
            denied = mutate("    timeout-minutes: 20\n",
                            f"    timeout-minutes: 20\n    permissions:\n      {scope}: write\n")
            with self.subTest(scope=scope):
                self.assert_flags(denied, "outside the allowed write scopes")

    def test_checkout_must_not_persist_credentials(self):
        self.assert_flags(mutate("          persist-credentials: false\n", ""), "persist-credentials: false")
        self.assert_flags(mutate("persist-credentials: false", "persist-credentials: true"),
                          "persist-credentials: false")

    def test_continue_on_error_is_rejected_for_jobs_and_steps(self):
        self.assert_flags(mutate("    timeout-minutes: 20\n", "    timeout-minutes: 20\n    continue-on-error: true\n"),
                          "must not use continue-on-error")
        self.assert_flags(mutate("      - name: Run\n", "      - name: Run\n        continue-on-error: true\n"),
                          "must not use continue-on-error")
        self.assertEqual(problems(mutate("      - name: Run\n", "      - name: Run\n        continue-on-error: false\n")),
                         [])

    def test_timeout_is_required_and_bounded(self):
        self.assert_flags(mutate("    timeout-minutes: 20\n", ""), "needs timeout-minutes")
        self.assert_flags(mutate("timeout-minutes: 20", "timeout-minutes: 31"), "needs timeout-minutes")
        self.assert_flags(mutate("timeout-minutes: 20", "timeout-minutes: 0"), "needs timeout-minutes")
        self.assertEqual(problems(mutate("timeout-minutes: 20", "timeout-minutes: 30")), [])

    def test_floating_runner_labels_are_rejected(self):
        self.assert_flags(mutate("ubuntu-24.04", "ubuntu-latest"), "pin its runner image")
        self.assert_flags(mutate("ubuntu-24.04", "${{ matrix.os }}"), "pin its runner image")

    def test_expression_in_shell_script_is_rejected(self):
        text = mutate('run: echo "$EVENT"', 'run: echo "${{ github.event.pull_request.title }}"')
        self.assert_flags(text, "interpolates an expression into a shell script")

    def test_expression_in_env_is_accepted(self):
        self.assertEqual(problems(GOOD), [])

    def test_artifact_retention_is_required_and_bounded(self):
        self.assert_flags(mutate("          retention-days: 7\n", ""), "retention-days")
        self.assert_flags(mutate("retention-days: 7", "retention-days: 90"), "retention-days")

    def test_concurrency_is_required_for_pull_request_workflows(self):
        text = mutate("concurrency:\n  group: example-${{ github.ref }}\n  cancel-in-progress: ${{ github.event_name == 'pull_request' }}\n", "")
        self.assert_flags(text, "missing concurrency")

    def test_required_check_workflow_must_not_filter_pull_requests(self):
        self.assert_flags(mutate("  pull_request:\n", "  pull_request:\n    paths: ['apps/**']\n"),
                          "must not filter pull requests with paths")
        # A workflow that owns no required check may filter.
        other = mutate("  pull_request:\n", "  pull_request:\n    paths: ['apps/**']\n").replace(
            "API verify", "Something else")
        self.assertEqual(problems(other), [])

    def test_a_required_job_must_not_be_skippable(self):
        self.assert_flags(mutate("    timeout-minutes: 20\n", "    timeout-minutes: 20\n    if: github.event_name == 'never'\n"),
                          "must not use if")
        self.assert_flags(mutate("    timeout-minutes: 20\n", "    timeout-minutes: 20\n    needs: [other]\n"),
                          "must not use needs")
        other = mutate("    timeout-minutes: 20\n", "    timeout-minutes: 20\n    if: always()\n").replace(
            "API verify", "Something else")
        self.assertEqual(problems(other), [])

    def test_pull_request_filters_that_narrow_a_required_check_are_rejected(self):
        for trigger in ("    branches: [release]\n", "    branches-ignore: [main]\n", "    types: [opened]\n"):
            with self.subTest(trigger=trigger):
                self.assertTrue(problems(mutate("  pull_request:\n", "  pull_request:\n" + trigger)), trigger)
        self.assertEqual(problems(mutate("  pull_request:\n", "  pull_request:\n    branches: [main]\n")), [])

    def test_a_required_check_must_run_on_pull_requests(self):
        push_only = mutate("on:\n  pull_request:\n  push:\n    branches: [main]\n", "on:\n  push:\n    branches: [main]\n")
        self.assert_flags(push_only, "must run on pull_request")
        # A null-valued `pull_request:` is the plain, unfiltered form and stays valid.
        self.assertEqual(problems(GOOD), [])
        other = push_only.replace("API verify", "Something else")
        self.assertEqual(problems(other), [])

    def test_a_push_workflow_must_not_cancel_unconditionally(self):
        unconditional = mutate("cancel-in-progress: ${{ github.event_name == 'pull_request' }}", "cancel-in-progress: true")
        self.assert_flags(unconditional, "must not cancel a run unconditionally")
        self.assertEqual(problems(GOOD), [])
        pull_request_only = mutate("  push:\n    branches: [main]\n", "")
        self.assertEqual(problems(pull_request_only), [])

    def test_secrets_are_rejected_on_pull_request_workflows(self):
        text = mutate("          EVENT: ${{ github.event_name }}\n",
                      "          EVENT: ${{ github.event_name }}\n          TOKEN: ${{ secrets.SONAR_TOKEN }}\n")
        self.assert_flags(text, "must not read repository secrets")
        allowed = mutate("          EVENT: ${{ github.event_name }}\n",
                         "          EVENT: ${{ github.event_name }}\n          TOKEN: ${{ secrets.GITHUB_TOKEN }}\n")
        self.assertEqual(problems(allowed), [])

    def test_sonarqube_secret_exception_enforces_narrow_guards(self):
        sonar_base = f"""\
name: SonarQube Cloud
on:
  pull_request:
    branches: [main]
  push:
    branches: [main]
permissions:
  contents: read
concurrency:
  group: sonarqube-${{{{ github.ref }}}}
  cancel-in-progress: ${{{{ github.event_name == 'pull_request' }}}}
jobs:
  scan:
    name: SonarQube Scan
    if: needs.preflight.outputs.trusted == 'true'
    runs-on: ubuntu-24.04
    timeout-minutes: 15
    steps:
      - uses: actions/checkout@{SHA} # v7.0.1
        with:
          persist-credentials: false
      - name: Scan
        env:
          SONAR_TOKEN: ${{{{ secrets.SONAR_TOKEN }}}}
        run: echo ok
"""
        self.assertEqual(policy.check_workflow("sonarqube.yml", sonar_base), [])
        self.assertTrue(any("may only read ['SONAR_TOKEN']" in msg for msg in policy.check_workflow(
            "sonarqube.yml", sonar_base.replace("secrets.SONAR_TOKEN", "secrets.OTHER_TOKEN"))))
        self.assertTrue(any("branches to [main]" in msg for msg in policy.check_workflow(
            "sonarqube.yml", sonar_base.replace("    branches: [main]\n", "", 1))))
        self.assertTrue(any("job scan env must not expose" in msg for msg in policy.check_workflow(
            "sonarqube.yml", sonar_base.replace("    timeout-minutes: 15\n", "    timeout-minutes: 15\n    env:\n      SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}\n"))))
        self.assertTrue(any("without gating on trusted == 'true'" in msg for msg in policy.check_workflow(
            "sonarqube.yml", sonar_base.replace("    if: needs.preflight.outputs.trusted == 'true'\n", ""))))
        self.assertTrue(any("without gating on trusted == 'true'" in msg for msg in policy.check_workflow(
            "sonarqube.yml",
            sonar_base.replace(
                "    if: needs.preflight.outputs.trusted == 'true'",
                "    if: needs.preflight.outputs.trusted == 'true' || always()",
            ))))
        self.assertTrue(any("workflow-level env must not expose" in msg for msg in policy.check_workflow(
            "sonarqube.yml",
            sonar_base.replace("permissions:\n", "env:\n  SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}\npermissions:\n"))))
        self.assertTrue(any("may only read ['SONAR_TOKEN']" in msg for msg in policy.check_workflow(
            "sonarqube.yml", sonar_base.replace("secrets.SONAR_TOKEN", "secrets['OTHER_TOKEN']"))))
        self.assertTrue(any("may only read ['SONAR_TOKEN']" in msg for msg in policy.check_workflow(
            "sonarqube.yml", sonar_base.replace("secrets.SONAR_TOKEN", "toJSON(secrets)"))))
        self.assertTrue(any("job preflight must not reference" in msg for msg in policy.check_workflow(
            "sonarqube.yml",
            sonar_base.replace(
                "jobs:\n",
                "jobs:\n  preflight:\n    name: SonarQube preflight\n    runs-on: ubuntu-24.04\n    timeout-minutes: 5\n    steps:\n      - run: echo ok\n        env:\n          T: ${{ secrets.SONAR_TOKEN }}\n",
            ))))

    def test_unknown_classifier_scope_is_rejected(self):
        text = mutate('run: echo "$EVENT"', 'run: python3 tools/ci_scope.py --scope mobile --event "$EVENT"')
        self.assert_flags(text, "unknown scope 'mobile'")
        valid = mutate('run: echo "$EVENT"', 'run: python3 tools/ci_scope.py --scope api --event "$EVENT"')
        self.assertEqual(problems(valid), [])

    def test_invalid_yaml_and_empty_workflows_are_reported(self):
        self.assertTrue(problems("jobs: [unclosed"))
        self.assertTrue(problems("name: no jobs\non: push\n"))

    def test_malformed_step_structures_are_reported_not_raised(self):
        self.assert_flags(mutate("        with:\n          persist-credentials: false\n", "        with:\n"),
                          "persist-credentials: false")
        self.assert_flags(mutate("      - name: Run\n", "      - just a string\n      - name: Run\n"),
                          "is not a mapping")
        problems(mutate("    steps:\n", "    steps: nope\n    old-steps:\n"))  # must not raise

    def test_string_and_list_trigger_forms_are_understood(self):
        text = mutate("on:\n  pull_request:\n  push:\n    branches: [main]\n", "on: pull_request_target\n")
        self.assert_flags(text, "pull_request_target")
        text = mutate("on:\n  pull_request:\n  push:\n    branches: [main]\n", "on: [push, workflow_run]\n")
        self.assert_flags(text, "workflow_run")


class RepositoryTest(unittest.TestCase):
    def directory(self, files):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        for name, text in files.items():
            (Path(temporary.name) / name).write_text(text)
        return Path(temporary.name)

    def test_a_missing_required_check_name_is_reported(self):
        found = policy.check_repository(self.directory({"a.yml": GOOD}), required_names=("API verify", "Web verify"))
        self.assertEqual(len(found), 1)
        self.assertIn("'Web verify' must be produced by exactly one job, found none", found[0])

    def test_a_duplicated_required_check_name_is_reported(self):
        found = policy.check_repository(self.directory({"a.yml": GOOD, "b.yml": GOOD}),
                                        required_names=("API verify",))
        self.assertIn("found ['a.yml', 'b.yml']", found[0])

    def test_every_workflow_file_is_discovered(self):
        bad = mutate("  pull_request:\n", "  pull_request_target:\n")
        found = policy.check_repository(self.directory({"a.yml": GOOD, "new-one.yaml": bad}),
                                        required_names=("API verify",))
        self.assertTrue(any(line.startswith("new-one.yaml:") for line in found), found)

    def test_an_empty_directory_is_an_error(self):
        self.assertTrue(policy.check_repository(self.directory({})))

    def test_the_repository_workflows_satisfy_the_policy(self):
        self.assertIn("Dependency review", policy.REQUIRED_JOB_NAMES)
        self.assertEqual(policy.check_repository(ROOT / ".github/workflows"), [])
        names = {path.name for path in (ROOT / ".github/workflows").glob("*.yml")}
        self.assertTrue({"api.yml", "web.yml", "dependency-review.yml"} <= names,
                        "the policy must cover the workflows the previous check ignored")


if __name__ == "__main__":
    unittest.main()
