import json
from pathlib import Path
import tempfile
import unittest

from promote_release import PUBLISH_IMAGES, validate_vulnerabilities


class PromotionPolicyTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        for artifact in PUBLISH_IMAGES.values():
            self.write_report(artifact, [])

    def write_report(self, artifact, findings):
        report = {"Results": [{"Vulnerabilities": findings}]}
        (self.root / f"trivy-{artifact}.json").write_text(json.dumps(report), encoding="utf-8")

    def test_only_reviewed_migrator_findings_can_pass(self):
        finding = {
            "VulnerabilityID": "CVE-2026-106451",
            "PkgName": "at.yawk.lz4:lz4-java",
            "InstalledVersion": "1.10.1",
            "Severity": "HIGH",
        }
        self.write_report("migrate", [finding])
        validate_vulnerabilities(self.root)
        self.write_report("migrate", [finding | {"Severity": "CRITICAL"}])
        with self.assertRaisesRegex(ValueError, "requires review"):
            validate_vulnerabilities(self.root)
        self.write_report("migrate", [])
        self.write_report("cloud-web", [finding])
        with self.assertRaisesRegex(ValueError, "cloud-web: requires review"):
            validate_vulnerabilities(self.root)

    def test_increased_occurrences_stop_promotion(self):
        self.write_report("migrate", [{
            "VulnerabilityID": "CVE-2026-106451",
            "PkgName": "at.yawk.lz4:lz4-java",
            "InstalledVersion": "1.10.1",
            "Severity": "HIGH",
        }] * 17)
        with self.assertRaisesRegex(ValueError, "occurrence count increased"):
            validate_vulnerabilities(self.root)


if __name__ == "__main__":
    unittest.main()
