"""Pin the coverage gates so that lowering or dropping one is a visible, deliberate change.

The floors live in the build files (apps/api/pom.xml, apps/web/vitest.config.ts) so that a local build and CI fail
the same way. These tests live in the documentation job, a different required check, and record the values the
floors must never go below. Raising a floor needs no edit here; lowering one, or removing a gate, fails this test
and therefore needs an explicit decision recorded in the pull request.
"""
import json
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}

# Baselines measured on 2026-11-25 (docs/operations/ci-cd-quality-audit.md), minus a small margin.
LINT_DEBT_LIMIT = 21  # violations recorded in apps/web/eslint-suppressions.json on 2026-11-25
CHANGED_LINE_MINIMUM = 80
API_FLOORS = {"coverage.line.minimum": 0.88, "coverage.branch.minimum": 0.77,
              "coverage.critical.line.minimum": 0.90, "coverage.critical.branch.minimum": 0.75}
CRITICAL_PACKAGES = {
    "dev.aymen.etaye.infrastructure.security", "dev.aymen.etaye.infrastructure.transaction",
    "dev.aymen.etaye.identity.service", "dev.aymen.etaye.records.service", "dev.aymen.etaye.records.domain",
}
WEB_FLOORS = {"logic": {"lines": 71, "statements": 71, "functions": 61, "branches": 71},
              "rules": {"lines": 95, "statements": 95, "functions": 90, "branches": 87}}


class ApiCoverageGateTest(unittest.TestCase):
    def setUp(self):
        self.pom = ET.parse(ROOT / "apps/api/pom.xml").getroot()

    def test_floors_are_not_lowered(self):
        properties = {element.tag.split("}")[1]: element.text for element in self.pom.find("m:properties", NS)}
        for name, floor in API_FLOORS.items():
            with self.subTest(property=name):
                self.assertGreaterEqual(float(properties[name]), floor)

    def test_the_check_goal_runs_with_a_bundle_rule_and_the_critical_package_rule(self):
        execution = next(
            e for plugin in self.pom.iterfind(".//m:plugin", NS)
            if plugin.findtext("m:artifactId", namespaces=NS) == "jacoco-maven-plugin"
            for e in plugin.iterfind(".//m:execution", NS) if e.findtext("m:id", namespaces=NS) == "check-coverage")
        self.assertEqual([goal.text for goal in execution.iterfind(".//m:goal", NS)], ["check"])
        elements = {rule.findtext("m:element", namespaces=NS) for rule in execution.iterfind(".//m:rule", NS)}
        self.assertEqual(elements, {"BUNDLE", "PACKAGE"})
        included = {include.text for include in execution.iterfind(".//m:include", NS)}
        self.assertTrue(CRITICAL_PACKAGES <= included, f"critical packages dropped: {CRITICAL_PACKAGES - included}")
        counters = {(rule.findtext("m:element", namespaces=NS), limit.findtext("m:counter", namespaces=NS))
                    for rule in execution.iterfind(".//m:rule", NS) for limit in rule.iterfind(".//m:limit", NS)}
        self.assertEqual(counters, {("BUNDLE", "LINE"), ("BUNDLE", "BRANCH"), ("PACKAGE", "LINE"), ("PACKAGE", "BRANCH")})

    def test_a_halting_failure_is_not_disabled(self):
        text = (ROOT / "apps/api/pom.xml").read_text()
        self.assertNotRegex(text, r"<haltOnFailure>\s*false")
        self.assertNotRegex(text, r"<skip>\s*true")

    def test_production_code_compiles_with_warnings_as_errors(self):
        text = (ROOT / "apps/api/pom.xml").read_text()
        self.assertIn("<arg>-Werror</arg>", text)
        self.assertIn("<id>default-compile</id>", text)

    def test_the_build_environment_is_enforced(self):
        text = (ROOT / "apps/api/pom.xml").read_text()
        for rule in ("requireMavenVersion", "requireJavaVersion", "banDuplicatePomDependencyVersions",
                     "dependencyConvergence"):
            with self.subTest(rule=rule):
                self.assertIn(f"<{rule}", text)


class WebCoverageGateTest(unittest.TestCase):
    def setUp(self):
        self.config = (ROOT / "apps/web/vitest.config.ts").read_text()

    def numbers(self, block):
        return {key: float(value) for key, value in re.findall(r"(lines|statements|functions|branches):\s*([0-9.]+)", block)}

    def test_there_is_no_overall_floor_that_unmeasured_components_would_break(self):
        # The .tsx files read 0 % in the unit report, so an overall floor fails the next UI story without a regression.
        thresholds = re.search(r"thresholds:\s*\{(.*?)'src/\*\*/\*\.ts'", self.config, re.S)
        self.assertIsNotNone(thresholds, "the logic-module threshold must be the first threshold")
        self.assertNotRegex(thresholds[1], r"(lines|statements|functions|branches):\s*[0-9]")

    def test_logic_and_rule_module_floors_are_not_lowered(self):
        logic = re.search(r"'src/\*\*/\*\.ts':\s*\{(.*?)\}", self.config, re.S)
        rules = re.search(r"\]:\s*\{(.*?)\}", self.config, re.S)
        self.assertIsNotNone(logic, "the logic-module threshold is missing")
        self.assertIsNotNone(rules, "the rule-module threshold is missing")
        for key, block in (("logic", logic[1]), ("rules", rules[1])):
            for name, floor in WEB_FLOORS[key].items():
                with self.subTest(group=key, metric=name):
                    self.assertGreaterEqual(self.numbers(block)[name], floor)

    def test_every_rule_module_exists(self):
        listed = re.findall(r"'([a-z/-]+)',", re.search(r"const ruleModules = \[(.*?)\];", self.config, re.S)[1])
        self.assertGreaterEqual(len(listed), 9)
        for module in listed:
            with self.subTest(module=module):
                self.assertTrue((ROOT / "apps/web/src" / f"{module}.ts").is_file(), module)

    def test_unit_coverage_still_includes_untested_sources(self):
        self.assertIn("include: ['src/**/*.{ts,tsx}']", self.config)
        self.assertIn("exclude: ['src/api/generated/**'", self.config)

    def test_lint_baseline_stays_recorded_in_the_repository(self):
        self.assertTrue((ROOT / "apps/web/eslint-suppressions.json").is_file())
        self.assertIn('"lint": "eslint . --max-warnings 0"', (ROOT / "apps/web/package.json").read_text())

    def test_the_lint_debt_can_only_shrink(self):
        # `eslint --suppress-all` would hide new violations in the baseline file. Pruning lowers this number;
        # raising the limit is a visible edit of this test and needs a recorded decision (deferred item D-67).
        data = json.loads((ROOT / "apps/web/eslint-suppressions.json").read_text())
        total = sum(rule["count"] for rules in data.values() for rule in rules.values())
        self.assertLessEqual(total, LINT_DEBT_LIMIT, "the recorded lint violations grew: fix the new ones instead")


class WorkflowsKeepTheGatesTest(unittest.TestCase):
    """The workflows run the gates through the build files; none may switch a gate off from the command line."""

    def text(self, name):
        return (ROOT / ".github/workflows" / name).read_text()

    def test_the_api_workflow_runs_the_full_verify_without_bypasses(self):
        text = self.text("api.yml")
        self.assertIn("./mvnw -B -ntp verify", text)
        for bypass in ("-Dcoverage.", "jacoco.skip", "maven.test.skip", "-DskipTests", "-DskipITs",
                       "test.failure.ignore", "enforcer.skip", "-Dmaven.compiler"):
            with self.subTest(bypass=bypass):
                self.assertNotIn(bypass, text)

    def test_the_web_workflow_runs_lint_and_coverage_without_bypasses(self):
        text = self.text("web.yml")
        self.assertIn("pnpm lint", text)
        self.assertIn("pnpm test:coverage", text)
        for bypass in ("--coverage.thresholds", "--passWithNoTests", "--suppress", "--max-warnings", "--no-coverage",
                       "--prune-suppressions", "--pass-on-unpruned-suppressions"):
            with self.subTest(bypass=bypass):
                self.assertNotIn(bypass, text)

    def gate_command(self, name):
        workflow = yaml.safe_load(self.text(name))
        commands = [step["run"] for job in workflow["jobs"].values() for step in job["steps"]
                    if "assert_changed_line_coverage.py" in step.get("run", "")]
        self.assertEqual(len(commands), 1, f"{name} must run the changed-line gate exactly once")
        return " ".join(commands[0].split())

    def test_the_changed_line_gate_keeps_its_thresholds(self):
        for name in ("api.yml", "web.yml"):
            with self.subTest(workflow=name):
                command = self.gate_command(name)
                threshold = re.search(r"--min-percent (\d+)", command)
                self.assertIsNotNone(threshold, "the changed-line gate no longer states its --min-percent")
                self.assertGreaterEqual(int(threshold[1]), CHANGED_LINE_MINIMUM)
                # The per-file floor defaults to 50 percent over ten lines; a workflow may only tighten it.
                percent = re.search(r"--min-file-percent (\d+)", command)
                lines = re.search(r"--min-file-lines (\d+)", command)
                self.assertTrue(percent is None or int(percent[1]) >= 50, "the per-file floor was lowered")
                self.assertTrue(lines is None or int(lines[1]) <= 10, "the per-file floor now ignores small files")


if __name__ == "__main__":
    unittest.main()
