import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import promote_release as promotion
from promote_release import PUBLISH_IMAGES, validate_vulnerabilities
from test_release_artifacts import GIT_SHA, write_image


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


    def test_stage_and_finalize_preserve_the_tested_platform_bytes(self):
        candidate = {
            "gitSha": GIT_SHA,
            "artifacts": {},
        }
        for artifact in PUBLISH_IMAGES.values():
            _, _, digest = write_image(self.root / f"{artifact}.tar")
            sbom = self.root / f"sbom-{artifact}.cdx.json"
            sbom.write_text("{}")
            report = self.root / f"trivy-{artifact}.json"
            candidate["artifacts"][artifact] = {
                "ociManifestDigest": digest,
                "sbom": promotion.describe(sbom),
                "vulnerabilityReport": promotion.describe(report),
            }
        (self.root / promotion.MANIFEST).write_text(json.dumps(candidate))
        output = self.root / "published"
        with patch("promote_release.verify_manifest"), patch("promote_release.upstream_pin"), \
                patch("promote_release.subprocess.run") as run, \
                patch("promote_release.require_registry_manifest") as registry, \
                patch.dict("os.environ", {
                    "GITHUB_RUN_ID": "2", "GITHUB_RUN_ATTEMPT": "1", "GITHUB_ACTOR": "fixture", "GH_TOKEN": "fixture",
                }):
            promotion.stage(self.root, output, GIT_SHA)
        names = [f"ghcr.io/aymenchaghoub/etaye-{name}" for name in PUBLISH_IMAGES]
        self.assertEqual(registry.call_count, 3)
        self.assertEqual(run.call_count, 4)
        for call, name in zip(run.call_args_list[1:], names, strict=True):
            self.assertIn("--preserve-digests", call.args[0])
            self.assertEqual(call.args[0][-1], f"docker://{name}:sha-{GIT_SHA}-run-2-1")
        with patch("promote_release.subprocess.check_output", return_value=b"[]") as verified:
            promotion.finalize(output, GIT_SHA)
        document = json.loads((output / promotion.PUBLISHED_MANIFEST).read_bytes())
        self.assertEqual(document["mode"], "published")
        self.assertEqual(document["provenance"]["status"], "verified")
        self.assertEqual(verified.call_count, 6)
        for name, entry in document["images"].items():
            self.assertEqual(entry["reference"], f"ghcr.io/aymenchaghoub/etaye-{name}@{entry['digest']}")
        self.assertEqual((output / "image-checksums.txt").read_text().count("ghcr.io/"), 3)

    def test_registry_rejects_bytes_that_differ_from_the_candidate(self):
        from hashlib import sha256

        raw = b"exact bytes from the registry"
        reference = "docker://ghcr.io/aymenchaghoub/etaye-api"
        auth = self.root / "auth.json"
        digest = "sha256:" + sha256(raw).hexdigest()
        with patch("promote_release.subprocess.check_output", return_value=raw):
            promotion.require_registry_manifest(reference, digest, auth)
            with self.assertRaisesRegex(ValueError, "manifest bytes differ"):
                promotion.require_registry_manifest(reference, "sha256:" + "a" * 64, auth)

    def test_finalize_rejects_substitution_even_with_a_plausible_digest(self):
        document = {
            "gitSha": GIT_SHA,
            "images": {name: {
                "name": f"ghcr.io/aymenchaghoub/etaye-{name}",
                "digest": "sha256:" + "a" * 64,
                "reference": f"ghcr.io/aymenchaghoub/etaye-{name}:swapped",
            } for name in PUBLISH_IMAGES},
        }
        (self.root / "staged-images.json").write_text(json.dumps(document))
        with self.assertRaisesRegex(ValueError, "immutable identity"):
            promotion.finalize(self.root, GIT_SHA)


if __name__ == "__main__":
    unittest.main()
