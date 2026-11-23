import copy
import json
import tempfile
import unittest
from contextlib import redirect_stdout
from io import StringIO
from pathlib import Path

from assert_isolation_matrix import main

MANIFEST = Path(__file__).resolve().parents[1] / "apps/api/src/test/resources/isolation/matrix-cases.json"
COMMIT = "0123456789abcdef0123456789abcdef01234567"


def valid_report(manifest):
    parity = {member for members in manifest["parity"].values() for member in members}
    return {
        "schemaVersion": 1, "story": "1.4", "commit": COMMIT, "fixtureVersion": manifest["fixtureVersion"],
        "parity": manifest["parity"],
        "cases": [{**case, "observed": case["expected"], "result": "PASS", "test": "Suite#case",
                   "fingerprint": "7b6aed520c88f9d6" if case["id"] in parity else None}
                  for case in manifest["cases"]],
        "pending": [{"id": row["id"], "resource": row["resource"], "operations": row["operations"],
                     "owners": row["owners"], "status": "PENDING", "trigger": row["trigger"]}
                    for row in manifest["pending"]],
    }


class IsolationMatrixReportTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.directory = Path(directory.name)
        self.manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
        self.report = valid_report(self.manifest)

    def run_tool(self, report=None, *extra):
        path = self.directory / "isolation-matrix.json"
        path.write_text(json.dumps(self.report if report is None else report), encoding="utf-8")
        with redirect_stdout(StringIO()) as output:
            status = main([str(path), "--manifest", str(MANIFEST), *extra])
        return status, output.getvalue()

    def assert_fails(self, message):
        status, output = self.run_tool()
        self.assertEqual(status, 1, output)
        self.assertIn(message, output)

    def test_accepts_a_complete_passing_report(self):
        status, output = self.run_tool(None, "--require-commit")
        self.assertEqual(status, 0, output)
        self.assertIn(f"cases={len(self.manifest['cases'])}", output)
        self.assertIn(f"pending={len(self.manifest['pending'])}", output)

    def test_rejects_a_zero_case_report(self):
        self.report["cases"] = []
        self.assert_fails("holds no measured case")

    def test_rejects_a_missing_mandatory_case(self):
        removed = self.report["cases"].pop(3)
        self.assert_fails(f"Missing mandatory cases: {removed['id']}")

    def test_rejects_a_failing_or_skipped_case(self):
        for result in ("FAIL", "SKIPPED"):
            with self.subTest(result=result):
                self.report = valid_report(self.manifest)
                self.report["cases"][2]["result"] = result
                self.assert_fails("did not pass")

    def test_rejects_an_observation_that_differs_from_the_expectation(self):
        self.report["cases"][1]["observed"] = "200 OK"
        self.assert_fails("did not pass")

    def test_rejects_a_case_whose_expectation_drifted_from_the_manifest(self):
        self.report["cases"][1]["expected"] = "200 OK"
        self.report["cases"][1]["observed"] = "200 OK"
        self.assert_fails("no longer matches the manifest")

    def test_rejects_duplicate_and_unlisted_cases(self):
        self.report["cases"].append(copy.deepcopy(self.report["cases"][0]))
        self.assert_fails("Duplicate case")
        self.report = valid_report(self.manifest)
        self.report["cases"].append({**self.report["cases"][0], "id": "REC-INVENTED"})
        self.assert_fails("absent from the manifest")

    def test_rejects_refusals_that_can_be_told_apart(self):
        hidden = next(case for case in self.report["cases"] if case["id"] == "REC-HIDDEN-MISSING")
        hidden["fingerprint"] = "ffffffffffffffff"
        self.assert_fails("discloses a difference")
        hidden["fingerprint"] = None
        self.assert_fails("without a response fingerprint")

    def test_rejects_identifiers_and_request_material(self):
        self.report["cases"][0]["label"] = "Record 13add2de-e7d5-342c-5ac5-7f718d04f6a4"
        self.assert_fails("no longer matches the manifest")
        self.report = valid_report(self.manifest)
        self.report["note"] = "target 13add2de-e7d5-342c-5ac5-7f718d04f6a4"
        self.assert_fails("must not contain an identifier")
        self.report = valid_report(self.manifest)
        self.report["cases"][0]["headers"] = {"cookie": "x"}
        self.assert_fails("request or response material")

    def test_requires_the_commit_and_fixture_version(self):
        self.report["commit"] = "unknown"
        self.assert_fails("name the commit")
        self.report = valid_report(self.manifest)
        self.report["fixtureVersion"] = "unknown"
        self.assert_fails("name the fixture version")
        self.report = valid_report(self.manifest)
        self.report["commit"] = "main"
        status, output = self.run_tool(None, "--require-commit")
        self.assertEqual(status, 1, output)
        self.assertEqual(self.run_tool()[0], 0)

    def test_rejects_drift_of_commit_fixture_parity_and_pending_rows(self):
        status, output = self.run_tool(None, "--expect-commit", "f" * 40)
        self.assertEqual(status, 1, output)
        self.assertIn("another commit", output)
        self.assertEqual(self.run_tool(None, "--expect-commit", COMMIT)[0], 0)
        self.report["fixtureVersion"] = "autre-jeu"
        self.assert_fails("another fixture version")
        self.report = valid_report(self.manifest)
        del self.report["parity"]
        self.assert_fails("parity groups of the report differ")
        self.report = valid_report(self.manifest)
        self.report["pending"][0]["owners"] = []
        self.assert_fails("pending rows of the report differ")
        self.report = valid_report(self.manifest)
        self.report["pending"].append(dict(self.report["pending"][0]))
        self.assert_fails("pending rows of the report differ")

    def test_keeps_unshipped_resources_pending(self):
        self.report["pending"][0]["status"] = "PASS"
        self.assert_fails("must stay PENDING")
        self.report = valid_report(self.manifest)
        self.report["pending"].pop()
        self.assert_fails("pending rows of the report differ")

    def test_rejects_a_missing_or_malformed_report(self):
        with redirect_stdout(StringIO()) as output:
            self.assertEqual(main([str(self.directory / "absent.json"), "--manifest", str(MANIFEST)]), 1)
        self.assertIn("FAIL isolation matrix", output.getvalue())
        path = self.directory / "broken.json"
        path.write_text("{", encoding="utf-8")
        with redirect_stdout(StringIO()) as output:
            self.assertEqual(main([str(path), "--manifest", str(MANIFEST)]), 1)
        self.assertIn("Malformed JSON", output.getvalue())


if __name__ == "__main__":
    unittest.main()
