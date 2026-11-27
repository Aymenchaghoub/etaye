"""Reject incomplete or altered release candidate evidence."""
from contextlib import chdir, redirect_stderr, redirect_stdout
import gzip
import hashlib
from io import BytesIO, StringIO
import json
import os
from pathlib import Path
import stat
import subprocess
import tarfile
import tempfile
import unittest
from unittest.mock import patch

import release_artifacts as release

GIT_SHA = "b" * 40
OTHER_SHA = "c" * 40
INDEX_TYPE = "application/vnd.oci.image.index.v1+json"
MANIFEST_TYPE = "application/vnd.oci.image.manifest.v1+json"
CONFIG_TYPE = "application/vnd.oci.image.config.v1+json"
LAYER_TYPE = "application/vnd.oci.image.layer.v1.tar+gzip"
UNCOMPRESSED_LAYER_TYPE = "application/vnd.oci.image.layer.v1.tar"
PLATFORM = {"os": "linux", "architecture": "amd64"}


def encode(payload):
    return json.dumps(payload).encode()


def digest(payload):
    return "sha256:" + hashlib.sha256(payload).hexdigest()


def write_json(path, data):
    path.write_bytes(encode(data))


def write_image(path, *, bad_digest=False, incorrect_layer=False, duplicate=False, os="linux",
                with_referrer=False, bad_referrer=False, uncompressed=False):
    blobs = {}

    def blob(content, media_type):
        checksum = digest(content)
        blobs["blobs/sha256/" + checksum[7:]] = content
        return {"mediaType": media_type, "digest": checksum, "size": len(content)}

    layer_content = path.name.encode()
    # The classic Docker store keeps the image config but saves layers uncompressed with a new manifest.
    layer_type = UNCOMPRESSED_LAYER_TYPE if uncompressed else LAYER_TYPE
    layer = blob(layer_content if uncompressed else gzip.compress(layer_content, mtime=0), layer_type)
    diff_id = digest(b"unrelated layer" if incorrect_layer else layer_content)
    config = blob(encode({"os": os, "architecture": "amd64", "rootfs": {"diff_ids": [diff_id]}}), CONFIG_TYPE)
    manifest = blob(encode({"schemaVersion": 2, "config": config, "layers": [layer]}), MANIFEST_TYPE)
    descriptor = manifest | {"platform": PLATFORM}
    if bad_digest:
        descriptor["digest"] = "sha256:wrong"
    manifests = [descriptor]
    if with_referrer or bad_referrer:
        ref_manifest = blob(encode({"schemaVersion": 2, "config": config, "layers": [layer]}), MANIFEST_TYPE)
        subject = manifest["digest"] if with_referrer else "sha256:" + "0" * 64
        manifests.append(ref_manifest | {"annotations": {"io.containerd.manifest.subject": subject}})
    index = {"schemaVersion": 2, "mediaType": INDEX_TYPE, "manifests": manifests}
    blobs["index.json"] = encode(index)
    entries = list(blobs.items())
    if duplicate:
        entries.append(("index.json", encode(index)))
    with tarfile.open(path, mode="w:") as archive:
        for name, content in entries:
            entry = tarfile.TarInfo(name)
            entry.size = len(content)
            archive.addfile(entry, BytesIO(content))
    return config["digest"], diff_id, manifest["digest"]


class ReleaseArtifactsTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.manifest = self.root / release.MANIFEST
        runtimes = []
        for service in (*release.PACKAGE_IMAGES, release.UPSTREAM_IMAGE):
            config, layer, manifest = write_image(self.root / f"{service}.tar")
            if service == release.UPSTREAM_IMAGE:
                index = {"manifests": [{"digest": manifest, "platform": PLATFORM}]}
                self.upstream_digest = digest(encode(index))
                write_json(self.root / "upstream-postgres-index.json", index)
            else:
                runtime_index = len(runtimes)
                container_id = str(runtime_index + 1) * 64
                descriptor = {"digest": manifest} if runtime_index % 2 == 0 else None
                runtimes.append(f'{json.dumps(container_id)} "{config}" {json.dumps(descriptor)}')
            components = [{"type": "library", "name": service}]
            properties = [{"name": "aquasecurity:trivy:ImageID", "value": config},
                          {"name": "aquasecurity:trivy:DiffID", "value": layer}]
            write_json(self.root / f"sbom-{service}.cdx.json", {
                "bomFormat": "CycloneDX", "specVersion": "1.7", "components": components,
                "metadata": {
                    "tools": {"components": [{"name": "trivy", "version": "0.76.2"}]},
                    "component": {"properties": properties},
                },
            })
            findings = [{"Severity": "HIGH", "FixedVersion": "1.4.2", "VulnerabilityID": "CVE-test"}]
            write_json(self.root / f"trivy-{service}.json", {
                "ArtifactType": "container_image", "Metadata": {"ImageID": config, "DiffIDs": [layer]},
                "Results": [{"Vulnerabilities": findings if service == "migrate" else []}],
            })
        for phase in ("before", "after"):
            (self.root / f"runtime-{phase}.txt").write_text("\n".join(runtimes), encoding="utf-8")
        for spec, count in release.TESTS.items():
            tests = []
            for number in range(count):
                tests.append({
                    "title": f"Journey {number}", "file": f"{spec}.spec.ts",
                    "tests": [{
                        "projectId": "chromium", "expectedStatus": "passed", "status": "expected",
                        "results": [{"status": "passed"}],
                    }],
                })
            write_json(self.root / f"real-{spec}.json", {
                "config": {"rootDir": "/workspace/e2e", "configFile": "/workspace/playwright.real.config.ts"},
                "suites": [{"title": spec, "specs": tests}],
                "stats": {"expected": count, "unexpected": 0, "flaky": 0, "skipped": 0},
            })
        write_json(self.root / "scanner.json", {
            "Version": "0.76.2", "VulnerabilityDB": {"UpdatedAt": "2026-11-26"}, "JavaDB": {"UpdatedAt": "2026-11-25"},
        })
        (self.root / "scanner-database-sha256.txt").write_text(
            f"{'a' * 64}  db/trivy.db\n{'b' * 64}  java-db/trivy-java.db\n", encoding="utf-8",
        )

    def generate(self):
        return release.generate_manifest(self.root, GIT_SHA, self.upstream_digest)

    def verify(self, **kwargs):
        return release.verify_manifest(self.root, GIT_SHA, self.upstream_digest, **kwargs)

    def test_reports_bind_tested_images_and_upstream_without_published_claims(self):
        manifest = self.generate()
        write_json(self.manifest, manifest)
        self.verify()
        self.assertEqual(manifest["releaseId"], f"candidate-{GIT_SHA}")
        self.assertEqual(manifest["tests"]["playwright"]["patients"]["passed"], 5)
        self.assertEqual(manifest["artifacts"]["migrate"]["vulnerabilityReport"]["fixableOccurrences"], 1)
        self.assertEqual(manifest["artifacts"]["postgres"]["kind"], "project-built-demo")
        upstream = manifest["artifacts"][release.UPSTREAM_IMAGE]
        self.assertEqual(upstream["upstreamIndexRef"], f"docker.io/library/postgres@{self.upstream_digest}")
        self.assertEqual(upstream["baseOf"], "postgres")
        self.assertNotIn("ghcr.io", self.manifest.read_text(encoding="utf-8"))

    def test_dry_run_never_satisfies_published_release_verification(self):
        manifest = self.generate()
        write_json(self.manifest, manifest)
        with self.assertRaisesRegex(ValueError, "never verify a published release"):
            self.verify(require_published=True)
        manifest["mode"] = "published"
        write_json(self.manifest, manifest)
        with self.assertRaisesRegex(ValueError, "never verify a published release"):
            self.verify()

    def test_rejects_malformed_commit_and_wrong_valid_commit(self):
        with self.assertRaisesRegex(ValueError, "a full Git SHA"):
            release.generate_manifest(self.root, "abc", self.upstream_digest)
        write_json(self.manifest, self.generate())
        with self.assertRaisesRegex(ValueError, "expected Git SHA"):
            release.verify_manifest(self.root, OTHER_SHA, self.upstream_digest)

    def test_rejects_manifest_digest_checksum_and_inventory_edits(self):
        for key, value in (("ociManifestDigest", "sha256:wrong"), ("archive", {"sha256": "0" * 64})):
            with self.subTest(key=key):
                candidate = self.generate()
                candidate["artifacts"]["api"][key] = value
                write_json(self.manifest, candidate)
                with self.assertRaisesRegex(ValueError, "Manifest mismatch"):
                    self.verify()
        candidate = self.generate()
        del candidate["artifacts"]["postgres"]
        write_json(self.manifest, candidate)
        with self.assertRaisesRegex(ValueError, "incorrect image inventory"):
            self.verify()

    def test_rejects_reports_and_archives_missing_or_replaced(self):
        for name in ("sbom-api.cdx.json", "trivy-postgres.json", "real-record.json", "upstream-postgres.tar"):
            with self.subTest(name=name):
                path = self.root / name
                content = path.read_bytes()
                path.unlink()
                with self.assertRaisesRegex(ValueError, f"Missing or unsafe release evidence: {name}"):
                    self.generate()
                path.write_bytes(content)

    def test_rejects_changed_report_even_when_its_image_identity_is_correct(self):
        write_json(self.manifest, self.generate())
        with (self.root / "trivy-web.json").open("a", encoding="utf-8") as report:
            report.write("\n")
        with self.assertRaisesRegex(ValueError, "report checksum"):
            self.verify()

    def test_rejects_mismatched_scan_or_sbom_image_identity(self):
        for name, other in (("trivy-api.json", "trivy-web.json"), ("sbom-api.cdx.json", "sbom-web.cdx.json")):
            with self.subTest(name=name):
                path = self.root / name
                original = path.read_bytes()
                path.write_bytes((self.root / other).read_bytes())
                with self.assertRaisesRegex(ValueError, "image identity"):
                    self.generate()
                path.write_bytes(original)

    def test_rejects_mismatched_runtime_and_archives(self):
        path = self.root / "runtime-after.txt"
        evidence = path.read_bytes()
        path.write_bytes(evidence + b"changed")
        with self.assertRaisesRegex(ValueError, "changed during the tests"):
            self.generate()
        path.write_bytes(evidence)
        (self.root / "api.tar").write_bytes((self.root / "web.tar").read_bytes())
        with self.assertRaisesRegex(ValueError, "OCI manifest differs from tested runtime"):
            self.generate()

    def test_accepts_subject_bound_referrer_manifest_and_rejects_unrelated_referrer(self):
        path = self.root / "ref.tar"
        expected_config, expected_diff, expected_manifest = write_image(path, with_referrer=True)
        identity = release.image_archive(path)
        self.assertEqual(identity["ociManifestDigest"], expected_manifest)
        self.assertEqual(identity["imageConfigDigest"], expected_config)
        self.assertEqual(identity["layerDiffIds"], [expected_diff])

        write_image(path, bad_referrer=True)
        with self.assertRaisesRegex(ValueError, "expected one platform image manifest"):
            release.image_archive(path)

    def test_rejects_invalid_oci_layer_digest_duplicate_or_platform(self):
        path = self.root / "bad.tar"
        for options, message in (
            ({"bad_digest": True}, "invalid digest"),
            ({"incorrect_layer": True}, "layer differs"),
            ({"duplicate": True}, "duplicate archive members"),
            ({"os": "windows"}, "expected linux/amd64"),
        ):
            with self.subTest(options=options):
                write_image(path, **options)
                with self.assertRaisesRegex(ValueError, message):
                    release.image_archive(path)

    def test_rejects_symlinks_and_incorrect_upstream_digest(self):
        file = self.root / "sbom-api.cdx.json"
        file.unlink()
        file.symlink_to("sbom-web.cdx.json")
        with self.assertRaisesRegex(ValueError, "Missing or unsafe release evidence"):
            self.generate()
        with self.assertRaisesRegex(ValueError, "does not match the Dockerfile pin"):
            release.generate_manifest(self.root, GIT_SHA, digest(b"another index"))

    def test_cli_rejects_invalid_revision_and_verifies_actual_checkout_pin(self):
        with redirect_stderr(StringIO()) as errors:
            code = release.main(["verify", str(self.root), "--git-sha", "abc"])
        self.assertEqual(code, 1)
        self.assertIn("full lowercase Git SHA", errors.getvalue())

        # The upstream pin comes from the Git object, not from the working tree or the supplied evidence.
        with tempfile.TemporaryDirectory() as checkout, chdir(checkout):
            subprocess.run(["git", "init", "-q"], check=True)
            dockerfile = Path("infra/demo/postgres.Dockerfile")
            dockerfile.parent.mkdir(parents=True)
            dockerfile.write_text(f"FROM postgres:18@{self.upstream_digest}\n", encoding="utf-8")
            subprocess.run(["git", "add", "."], check=True)
            subprocess.run([
                "git", "-c", "user.name=Fixture", "-c", "user.email=fixture@example.test",
                "-c", "commit.gpgsign=false", "-c", "core.hooksPath=/dev/null", "commit", "-qm", "Fixture",
            ], check=True)
            revision = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
            dockerfile.write_text("FROM postgres:18\n", encoding="utf-8")
            with redirect_stdout(StringIO()):
                for operation in ("generate", "verify"):
                    self.assertEqual(release.main([operation, str(self.root), "--git-sha", revision]), 0)

    def pinned_checkout(self):
        """Commit a Dockerfile pinned to this test's upstream index in the current directory."""
        subprocess.run(["git", "init", "-q"], check=True)
        dockerfile = Path("infra/demo/postgres.Dockerfile")
        dockerfile.parent.mkdir(parents=True)
        dockerfile.write_text(f"FROM postgres:18@{self.upstream_digest}\n", encoding="utf-8")
        subprocess.run(["git", "add", "."], check=True)
        subprocess.run([
            "git", "-c", "user.name=Fixture", "-c", "user.email=fixture@example.test",
            "-c", "commit.gpgsign=false", "-c", "core.hooksPath=/dev/null", "commit", "-qm", "Fixture",
        ], check=True)
        return subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()

    def test_upstream_platform_requires_the_pinned_index_and_one_platform(self):
        index = self.root / "upstream-postgres-index.json"
        expected = release.upstream_platform(self.root, self.upstream_digest)
        self.assertRegex(expected, r"^sha256:[0-9a-f]{64}$")
        with tempfile.TemporaryDirectory() as checkout, chdir(checkout):
            revision = self.pinned_checkout()
            with redirect_stdout(StringIO()) as output:
                code = release.main(["upstream-platform", str(self.root), "--git-sha", revision])
            self.assertEqual((code, output.getvalue()), (0, expected + "\n"))
            with redirect_stdout(StringIO()) as output:
                code = release.main(["upstream-platform", str(self.root), "--git-sha", GIT_SHA[:12]])
            self.assertEqual((code, output.getvalue()), (1, ""))

        # An attestation manifest has an unknown platform and is never the base platform.
        attestation = {"digest": "sha256:" + "e" * 64, "platform": {"os": "unknown", "architecture": "unknown"}}
        with_index = {"manifests": [{"digest": expected, "platform": PLATFORM}, attestation]}
        write_json(index, with_index)
        self.assertEqual(release.upstream_platform(self.root, digest(encode(with_index))), expected)

        # Zero or two Linux amd64 manifests are rejected, and so is an index that is not the pinned bytes.
        for manifests in ([attestation], with_index["manifests"] + [{"digest": "sha256:" + "f" * 64, "platform": PLATFORM}]):
            write_json(index, {"manifests": manifests})
            with self.subTest(manifests=manifests), self.assertRaisesRegex(ValueError, "one Linux amd64"):
                release.upstream_platform(self.root, digest(encode({"manifests": manifests})))
        with self.assertRaisesRegex(ValueError, "does not match the Dockerfile pin"):
            release.upstream_platform(self.root, digest(b"another index"))

    def test_rejects_upstream_archive_saved_through_the_classic_docker_store(self):
        # Run 46001297479: Docker Engine 28 saved the pulled base with a reserialized manifest and
        # uncompressed layers. The content is equivalent, but it is no longer the pinned platform bytes.
        path = self.root / f"{release.UPSTREAM_IMAGE}.tar"
        pinned = path.read_bytes()
        write_image(path, uncompressed=True)
        with self.assertRaisesRegex(ValueError, "does not match the pinned platform digest"):
            self.generate()
        path.write_bytes(pinned)
        self.generate()

    def test_upstream_base_is_copied_from_the_registry_with_its_bytes_preserved(self):
        script = Path(release.__file__).with_name("archive_upstream_base.sh")
        stubs = self.root / "bin"
        stubs.mkdir()
        calls = self.root / "skopeo-calls.txt"
        stub = stubs / "skopeo"
        stub.write_text(
            f'#!/usr/bin/env bash\nprintf "%s\\n" "$@" >> "{calls}"\n[[ -z "${{SKOPEO_FAIL:-}}" ]]\n',
            encoding="utf-8",
        )
        stub.chmod(stub.stat().st_mode | stat.S_IXUSR)
        index = self.root / "upstream-postgres-index.json"
        archive = self.root / "upstream-postgres.tar"
        pinned = index.read_bytes()
        platform = json.loads(pinned)["manifests"][0]["digest"]
        environment = patch.dict("os.environ", {"PATH": f"{stubs}{os.pathsep}{os.environ['PATH']}"})
        with tempfile.TemporaryDirectory() as checkout, chdir(checkout):
            revision = self.pinned_checkout()
            command = ["bash", str(script), str(index), "docker.io/library/postgres", str(archive), revision]
            with environment:
                completed = subprocess.run(command, capture_output=True, text=True)
                self.assertEqual(completed.returncode, 0, completed.stderr)
                self.assertEqual(calls.read_text(encoding="utf-8").splitlines(), [
                    "copy", "--preserve-digests",
                    f"docker://docker.io/library/postgres@{platform}",
                    f"oci-archive:{archive}:upstream-postgres",
                ])

                # An index edited after the registry read never reaches Skopeo.
                write_json(index, {"manifests": [{"digest": "sha256:" + "0" * 64, "platform": PLATFORM}]})
                edited = subprocess.run(command, capture_output=True, text=True)
                self.assertNotEqual(edited.returncode, 0)
                self.assertIn("does not match the Dockerfile pin", edited.stderr)
                self.assertEqual(len(calls.read_text(encoding="utf-8").splitlines()), 4)

                # A copy that cannot keep the registry bytes fails the rehearsal.
                index.write_bytes(pinned)
                with patch.dict("os.environ", {"SKOPEO_FAIL": "1"}):
                    self.assertNotEqual(subprocess.run(command, capture_output=True, text=True).returncode, 0)

    def test_docker_archive_index_enables_legacy_scanners_on_oci_archives(self):
        # Trivy 0.76 opens a file only as a Docker archive (manifest.json) or an OCI layout directory,
        # never as an OCI tar archive. add_docker_archive_index appends a Docker-format manifest.json
        # derived from the unchanged OCI platform manifest so the scanner reads the registry bytes.
        path = self.root / "oci-only.tar"
        config_digest, diff_id, manifest_digest = write_image(path)
        # Before: no manifest.json in the archive.
        with tarfile.open(path, mode="r:") as archive:
            self.assertNotIn("manifest.json", archive.getnames())

        release.add_docker_archive_index(path)

        # After: manifest.json present and the archive still passes image_archive verification.
        identity = release.image_archive(path)
        self.assertEqual(identity["ociManifestDigest"], manifest_digest)
        self.assertEqual(identity["imageConfigDigest"], config_digest)
        self.assertEqual(identity["layerDiffIds"], [diff_id])

        # The Docker index references the correct config and layer blobs.
        with tarfile.open(path, mode="r:") as archive:
            self.assertIn("manifest.json", archive.getnames())
            with archive.extractfile("manifest.json") as handle:
                docker_index = json.load(handle)
        self.assertEqual(len(docker_index), 1)
        entry = docker_index[0]
        self.assertEqual(entry["Config"], "blobs/sha256/" + config_digest.removeprefix("sha256:"))
        self.assertIsNone(entry["RepoTags"])

        # Calling it again on an archive that already has manifest.json is rejected.
        with self.assertRaisesRegex(ValueError, "already holds a Docker archive index"):
            release.add_docker_archive_index(path)
