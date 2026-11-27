import json
from hashlib import sha256
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import promote_release as promotion
from test_release_artifacts import GIT_SHA, PLATFORM, digest, write_image


class PromotionPolicyTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        environment = patch.dict("os.environ", {"RUNNER_TEMP": str(self.root)})
        environment.start()
        self.addCleanup(environment.stop)
        for artifact in promotion.PUBLISH_IMAGES.values():
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
        promotion.validate_vulnerabilities(self.root)
        self.write_report("migrate", [finding | {"Severity": "CRITICAL"}])
        with self.assertRaisesRegex(ValueError, "requires review"):
            promotion.validate_vulnerabilities(self.root)
        self.write_report("migrate", [])
        self.write_report("cloud-web", [finding])
        with self.assertRaisesRegex(ValueError, "cloud-web: requires review"):
            promotion.validate_vulnerabilities(self.root)

    def test_increased_occurrences_stop_promotion(self):
        self.write_report("migrate", [{
            "VulnerabilityID": "CVE-2026-106451",
            "PkgName": "at.yawk.lz4:lz4-java",
            "InstalledVersion": "1.10.1",
            "Severity": "HIGH",
        }] * 17)
        with self.assertRaisesRegex(ValueError, "occurrence count increased"):
            promotion.validate_vulnerabilities(self.root)

    def test_stage_and_finalize_preserve_the_tested_platform_bytes(self):
        candidate = {
            "gitSha": GIT_SHA,
            "artifacts": {},
        }
        for artifact in promotion.PUBLISH_IMAGES.values():
            _, _, manifest_digest = write_image(self.root / f"{artifact}.tar")
            sbom = self.root / f"sbom-{artifact}.cdx.json"
            sbom.write_text("{}")
            report = self.root / f"trivy-{artifact}.json"
            candidate["artifacts"][artifact] = {
                "ociManifestDigest": manifest_digest,
                "sbom": promotion.describe(sbom),
                "vulnerabilityReport": promotion.describe(report),
            }
        (self.root / promotion.MANIFEST).write_text(json.dumps(candidate))
        output = self.root / "published"
        index = self.root / "upstream-flyway-index.json"
        content = json.dumps({"manifests": [{"digest": "sha256:" + "d" * 64, "platform": PLATFORM}]}).encode()
        index.write_bytes(content)
        with patch("promote_release.verify_manifest"), patch("promote_release.upstream_pin"), \
                patch("promote_release.subprocess.run") as run, \
                patch("promote_release.require_registry_manifest") as registry, \
                patch("promote_release.subprocess.check_output", return_value=f"FROM flyway/flyway:13.13.0@{digest(content)}"), \
                patch.dict("os.environ", {
                    "GITHUB_RUN_ID": "2", "GITHUB_RUN_ATTEMPT": "1", "GITHUB_ACTOR": "fixture", "GH_TOKEN": "fixture",
                }):
            promotion.stage(self.root, output, GIT_SHA)
        names = [f"ghcr.io/aymenchaghoub/etaye-{name}" for name in promotion.PUBLISH_IMAGES]
        self.assertEqual(registry.call_count, 3)
        self.assertEqual(run.call_count, 4)
        for call, name in zip(run.call_args_list[1:], names, strict=True):
            self.assertIn("--preserve-digests", call.args[0])
            self.assertEqual(call.args[0][-1], f"docker://{name}:sha-{GIT_SHA}-run-2-1")
        with patch("promote_release.subprocess.check_output", return_value=b"[]") as verified:
            promotion.finalize(output, GIT_SHA)
        document = json.loads((output / promotion.PUBLISHED_MANIFEST).read_bytes())
        self.assertEqual(document["mode"], "published")
        self.assertEqual(document["flywayUpstream"]["platformReference"],
                         "docker.io/flyway/flyway@sha256:" + "d" * 64)
        self.assertEqual(document["provenance"]["status"], "verified")
        self.assertEqual(verified.call_count, 6)
        for call in verified.call_args_list:
            self.assertEqual(call.args[0][-2], "--")
        for name, entry in document["images"].items():
            self.assertEqual(entry["reference"], f"ghcr.io/aymenchaghoub/etaye-{name}@{entry['digest']}")
        self.assertEqual((output / "image-checksums.txt").read_text().count("ghcr.io/"), 3)

    def test_registry_rejects_bytes_that_differ_from_the_candidate(self):
        raw = b"exact bytes from the registry"
        image_digest = "sha256:" + sha256(raw).hexdigest()
        reference = f"docker://ghcr.io/aymenchaghoub/etaye-api@{image_digest}"
        auth = self.root / "auth.json"
        with patch("promote_release.subprocess.check_output", return_value=raw) as inspect:
            promotion.require_registry_manifest(reference, image_digest, auth)
            self.assertEqual(inspect.call_args.args[0][-2:], ["--", reference])
            promotion.require_registry_manifest(
                reference.replace("etaye-api@", "etaye-web@"), image_digest, auth,
            )
        with patch("promote_release.subprocess.check_output", return_value=b"other registry bytes"), \
                self.assertRaisesRegex(ValueError, "manifest bytes differ"):
            promotion.require_registry_manifest(reference, image_digest, auth)
        inspect.reset_mock()
        for swapped in (
            reference + ":latest",
            reference.replace("aymenchaghoub/", "aymenchaghoub/other-"),
            reference.replace("etaye-api@", "etaye-api@sha256:short"),
            reference.replace("ghcr.io", "ghcr.io/evil.io"),
            "--authfile=" + reference,
        ):
            with self.subTest(reference=swapped), self.assertRaisesRegex(ValueError, "exact reference"):
                promotion.require_registry_manifest(swapped, image_digest, auth)
        inspect.assert_not_called()

    def test_git_revision_is_validated_before_reaching_git(self):
        with patch("promote_release.subprocess.check_output") as git:
            for revision in (GIT_SHA[:12], "--output=/tmp/x", GIT_SHA.upper(), ""):
                with self.subTest(revision=revision), self.assertRaisesRegex(ValueError, "full Git SHA"):
                    promotion.flyway_base_identity(self.root, revision)
                with self.subTest(stage=revision), self.assertRaisesRegex(ValueError, "full Git SHA"):
                    promotion.stage(self.root, self.root / "published", revision)
        git.assert_not_called()
        self.assertFalse((self.root / "published").exists())

    def test_stage_keeps_every_path_inside_the_runner_workspace(self):
        outside = self.root.parent / (self.root.name + "-outside")
        self.addCleanup(lambda: outside.rmdir() if outside.exists() else None)
        for candidate, output in (
            (self.root, outside),
            (self.root / ".." / "elsewhere", self.root / "published"),
            (self.root, Path("relative/published")),
        ):
            with self.subTest(output=output), self.assertRaisesRegex(ValueError, "RUNNER_TEMP"):
                promotion.stage(candidate, output, GIT_SHA)
        self.assertFalse((self.root / "published").exists())
        self.assertFalse(outside.exists())
        with patch.dict("os.environ", {"RUNNER_TEMP": ""}):
            with self.assertRaisesRegex(ValueError, "RUNNER_TEMP"):
                promotion.stage(self.root, self.root / "published", GIT_SHA)
            with self.assertRaisesRegex(ValueError, "RUNNER_TEMP"):
                promotion.finalize(self.root, GIT_SHA)

    def test_attestation_verification_accepts_only_the_exact_release_subjects(self):
        image = f"oci://ghcr.io/aymenchaghoub/etaye-api@sha256:{'a' * 64}"
        record = str(self.root / promotion.PUBLISHED_MANIFEST)
        with patch("promote_release.subprocess.check_output", return_value=b"[]") as verify:
            for subject in (image, record):
                promotion.verify_attestation(subject, GIT_SHA, promotion.SLSA)
            self.assertEqual([call.args[0][-2:] for call in verify.call_args_list], [["--", image], ["--", record]])
            verify.reset_mock()
            for subject in (
                image + ":latest",
                image.replace("etaye-api@", "other/etaye-api@"),
                "--repo",
                str(self.root / "another.json"),
                str(self.root.parent / promotion.PUBLISHED_MANIFEST),
            ):
                with self.subTest(subject=subject), self.assertRaises(ValueError):
                    promotion.verify_attestation(subject, GIT_SHA, promotion.SLSA)
            with self.subTest(revision=GIT_SHA[:12]), self.assertRaisesRegex(ValueError, "full Git SHA"):
                promotion.verify_attestation(image, GIT_SHA[:12], promotion.SLSA)
            with self.subTest(predicate="unexpected"), self.assertRaisesRegex(ValueError, "attestation predicate"):
                promotion.verify_attestation(image, GIT_SHA, "https://evil.example/p")
        verify.assert_not_called()

    def test_finalize_rejects_substitution_even_with_a_plausible_digest(self):
        document = {
            "gitSha": GIT_SHA,
            "images": {name: {
                "name": f"ghcr.io/aymenchaghoub/etaye-{name}",
                "digest": "sha256:" + "a" * 64,
                "reference": f"ghcr.io/aymenchaghoub/etaye-{name}:swapped",
            } for name in promotion.PUBLISH_IMAGES},
        }
        (self.root / "staged-images.json").write_text(json.dumps(document))
        with self.assertRaisesRegex(ValueError, "immutable identity"):
            promotion.finalize(self.root, GIT_SHA)


if __name__ == "__main__":
    unittest.main()
