#!/usr/bin/env python3
"""Publish only the verified cloud artifacts and record registry verification."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile

from release_artifacts import DIGEST, GIT_SHA, MANIFEST, describe, image_archive, upstream_pin, verify_manifest

PUBLISH_IMAGES = {"api": "api", "web": "cloud-web", "migrate": "migrate"}
REPOSITORY = "Aymenchaghoub/etaye"
REGISTRY = "ghcr.io"
IMAGE_NAMES = tuple(f"{REGISTRY}/aymenchaghoub/etaye-{package}" for package in PUBLISH_IMAGES)
ATTESTATION_SUBJECT = re.compile(r"oci://ghcr\.io/aymenchaghoub/etaye-(?:api|web|migrate)@sha256:[0-9a-f]{64}")
WORKFLOW = f"{REPOSITORY}/.github/workflows/release.yml"
PUBLISHED_MANIFEST = "published-manifest.json"
SLSA = "https://slsa.dev/provenance/v1"
CYCLONEDX = "https://cyclonedx.org/bom"
REVIEWED_MIGRATOR_FINDINGS = {
    ("CVE-2025-59250", "com.microsoft.sqlserver:mssql-jdbc", "12.10.2"),
    ("CVE-2026-102554", "com.google.guava:guava", "33.0.0-jre"),
    ("CVE-2026-102554", "com.google.guava:guava", "33.4.0-jre"),
    ("CVE-2026-102554", "com.google.guava:guava", "33.5.0-jre"),
    ("CVE-2026-106451", "at.yawk.lz4:lz4-java", "1.10.1"),
    ("CVE-2026-54399", "org.apache.httpcomponents.core5:httpcore5", "5.3.6"),
    ("CVE-2026-54428", "org.apache.httpcomponents.core5:httpcore5-h2", "5.3.6"),
    ("CVE-2026-68497", "com.fasterxml.jackson.core:jackson-databind", "2.18.8"),
    ("CVE-2026-89407", "com.fasterxml.jackson.core:jackson-core", "2.18.8"),
    ("CVE-2026-89407", "com.fasterxml.jackson.core:jackson-core", "2.21.6"),
    ("CVE-2026-89425", "com.fasterxml.jackson.core:jackson-core", "2.18.8"),
    ("CVE-2026-89425", "com.fasterxml.jackson.core:jackson-core", "2.21.6"),
    ("CVE-2026-91776", "com.fasterxml.jackson.core:jackson-databind", "2.18.8"),
    ("CVE-2026-91776", "com.fasterxml.jackson.core:jackson-databind", "2.21.6"),
    ("CVE-2026-91777", "com.fasterxml.jackson.core:jackson-databind", "2.18.8"),
    ("CVE-2026-91777", "com.fasterxml.jackson.core:jackson-databind", "2.21.6"),
}


def read_json(path: Path) -> dict:
    return json.loads(path.read_bytes())


def write_json(path: Path, document: dict) -> None:
    with path.open("x", encoding="utf-8") as handle:
        json.dump(document, handle, indent=2, sort_keys=True)
        handle.write("\n")


def exact_sha(source_sha: str) -> str:
    """Keep caller-supplied revisions from becoming Git or GitHub command arguments."""
    if not GIT_SHA.fullmatch(source_sha):
        raise ValueError("Expected a full Git SHA")
    return source_sha


def workspace_path(path: Path, label: str) -> Path:
    """Resolve a caller-supplied path and keep it inside the runner workspace."""
    supplied, workspace = Path(path), Path(os.environ.get("RUNNER_TEMP", ""))
    if not supplied.is_absolute() or ".." in supplied.parts or not workspace.is_absolute():
        raise ValueError(f"Expected an absolute {label} inside RUNNER_TEMP")
    resolved, root = supplied.resolve(), workspace.resolve()
    if not resolved.is_relative_to(root):
        raise ValueError(f"Expected an absolute {label} inside RUNNER_TEMP")
    return resolved


def registry_reference(reference: str, digest: str) -> str:
    """Accept only the reference that carries exactly the verified digest of a published image."""
    if not DIGEST.fullmatch(digest) or reference not in {f"docker://{name}@{digest}" for name in IMAGE_NAMES}:
        raise ValueError("Expected the exact reference of a verified image digest")
    return reference


def attestation_subject(subject: str) -> str:
    """Accept only a published image digest or the published release record."""
    if ATTESTATION_SUBJECT.fullmatch(subject):
        return subject
    record = workspace_path(Path(subject), "release record")
    if record.name != PUBLISHED_MANIFEST:
        raise ValueError("Unexpected attestation subject")
    return str(record)


def validate_vulnerabilities(root: Path) -> None:
    for artifact in PUBLISH_IMAGES.values():
        report = read_json(root / f"trivy-{artifact}.json")
        findings = [item for result in report["Results"] for item in result.get("Vulnerabilities") or []]
        for item in findings:
            known = (item["VulnerabilityID"], item["PkgName"], item["InstalledVersion"])
            if artifact != "migrate" or item["Severity"] != "HIGH" or known not in REVIEWED_MIGRATOR_FINDINGS:
                raise ValueError(f"{artifact}: requires review of {known[0]} in {known[1]}")
        if len(findings) > (16 if artifact == "migrate" else 0):
            raise ValueError(f"{artifact}: vulnerability occurrence count increased")


def oci_layout(archive_path: Path, layout: Path, digest: str) -> None:
    """Retain the tested platform manifest and its blobs, with a local OCI selector for Skopeo."""
    if not DIGEST.fullmatch(digest) or image_archive(archive_path)["ociManifestDigest"] != digest:
        raise ValueError("Verified OCI platform digest is required")
    blobs = layout / "blobs" / "sha256"
    blobs.mkdir(parents=True)
    with tarfile.open(archive_path, "r:") as archive:
        index = json.load(archive.extractfile("index.json"))
        descriptor, = [item for item in index["manifests"] if item["digest"] == digest]
        manifest_path = "blobs/sha256/" + digest.removeprefix("sha256:")
        manifest = json.load(archive.extractfile(manifest_path))
        for reference in (descriptor, manifest["config"], *manifest["layers"]):
            hexadecimal = reference["digest"].removeprefix("sha256:")
            output = blobs / hexadecimal
            if not output.exists():
                with archive.extractfile(f"blobs/sha256/{hexadecimal}") as member, output.open("xb") as target:
                    shutil.copyfileobj(member, target)
    write_json(layout / "oci-layout", {"imageLayoutVersion": "1.0.0"})
    descriptor = {
        key: descriptor[key] for key in ("digest", "size", "mediaType")
    } | {"annotations": {"org.opencontainers.image.ref.name": "candidate"}}
    write_json(layout / "index.json", {"schemaVersion": 2, "manifests": [descriptor]})


def require_registry_manifest(reference: str, digest: str, authfile: Path) -> None:
    reference = registry_reference(reference, digest)
    raw = subprocess.check_output(
        ["skopeo", "inspect", "--authfile", str(authfile), "--raw", "--", reference],
    )
    if "sha256:" + hashlib.sha256(raw).hexdigest() != digest:
        raise ValueError("Registry platform manifest bytes differ from the tested image")


def flyway_base_identity(root: Path, source_sha: str) -> dict:
    if not GIT_SHA.fullmatch(source_sha):
        raise ValueError("Expected a full Git SHA")
    # The command reads the revision from the verified candidate manifest, so a caller-supplied
    # argument never reaches Git directly. The manifest was already bound to source_sha above.
    revision = read_json(root / MANIFEST)["gitSha"]
    if revision != source_sha:
        raise ValueError("Candidate manifest does not match the selected source")
    source = subprocess.check_output(
        ["git", "show", "--end-of-options", f"{revision}:infra/demo/migrate.Dockerfile"], text=True,
    )
    match = re.fullmatch(r"FROM flyway/flyway:[0-9.]+@(sha256:[a-f0-9]{64})", source.splitlines()[0])
    if match is None:
        raise ValueError("Expected a pinned Flyway base in the selected source")
    index = root / "upstream-flyway-index.json"
    receipt = describe(index)
    if "sha256:" + receipt["sha256"] != match[1]:
        raise ValueError("Flyway upstream index differs from the source pin")
    platforms = [
        manifest["digest"] for manifest in read_json(index)["manifests"]
        if (manifest.get("platform", {}).get("os"), manifest.get("platform", {}).get("architecture"))
        == ("linux", "amd64")
    ]
    if len(platforms) != 1 or not DIGEST.fullmatch(platforms[0]):
        raise ValueError("Expected one Linux amd64 Flyway base")
    return {
        "indexReference": f"docker.io/flyway/flyway@{match[1]}",
        "platformReference": f"docker.io/flyway/flyway@{platforms[0]}",
        "index": receipt,
    }


def stage(root: Path, output: Path, source_sha: str) -> None:
    exact_sha(source_sha)
    root = workspace_path(root, "candidate directory")
    output = workspace_path(output, "published directory")
    verify_manifest(root, source_sha, upstream_pin(source_sha))
    validate_vulnerabilities(root)
    candidate = read_json(root / MANIFEST)
    migrator_base = flyway_base_identity(root, source_sha)
    run_id, attempt = os.environ["GITHUB_RUN_ID"], os.environ["GITHUB_RUN_ATTEMPT"]
    if not all(re.fullmatch(r"[0-9]+", item) for item in (run_id, attempt)):
        raise ValueError("Invalid GitHub run identity")
    tag = f"sha-{source_sha}-run-{run_id}-{attempt}"
    output.mkdir()
    entries = {}
    with tempfile.TemporaryDirectory() as directory:
        private = Path(directory)
        authfile = private / "auth.json"
        token = os.environ["GH_TOKEN"]
        # Pass the token only to the login subprocess through stdin.
        subprocess.run(
            ["skopeo", "login", "--authfile", str(authfile), "--username", os.environ["GITHUB_ACTOR"],
             "--password-stdin", REGISTRY],
            input=token + "\n", text=True, check=True,
        )
        for package, artifact in PUBLISH_IMAGES.items():
            evidence = candidate["artifacts"][artifact]
            digest = evidence["ociManifestDigest"]
            name = f"{REGISTRY}/aymenchaghoub/etaye-{package}"
            layout = private / artifact
            oci_layout(root / f"{artifact}.tar", layout, digest)
            subprocess.run(
                ["skopeo", "copy", "--authfile", str(authfile), "--preserve-digests",
                 f"oci:{layout}:candidate", f"docker://{name}:{tag}"], check=True,
            )
            reference = f"{name}@{digest}"
            require_registry_manifest(f"docker://{reference}", digest, authfile)
            entries[package] = {
                "name": name, "tag": tag, "digest": digest, "reference": reference, "artifact": artifact,
                "sbom": evidence["sbom"], "vulnerabilityReport": evidence["vulnerabilityReport"],
            }
    shutil.copyfile(root / MANIFEST, output / MANIFEST)
    shutil.copyfile(root / migrator_base["index"]["file"], output / migrator_base["index"]["file"])
    with (output / "image-checksums.txt").open("x", encoding="utf-8") as checksums:
        for entry in entries.values():
            print(entry["digest"].removeprefix("sha256:"), entry["name"], file=checksums)
            for report in (entry["sbom"], entry["vulnerabilityReport"]):
                shutil.copyfile(root / report["file"], output / report["file"])
    write_json(output / "staged-images.json", {
        "gitSha": source_sha, "images": entries, "candidate": describe(output / MANIFEST),
        "flywayUpstream": migrator_base,
        "workflowRun": f"https://github.com/{REPOSITORY}/actions/runs/{run_id}/attempts/{attempt}",
    })


def verify_attestation(subject: str, source_sha: str, predicate: str) -> bytes:
    # Keep the revision guard beside the command, where the security analysis can see it.
    if not GIT_SHA.fullmatch(source_sha):
        raise ValueError("Expected a full Git SHA")
    subject = attestation_subject(subject)
    if predicate not in (SLSA, CYCLONEDX):
        raise ValueError("Unexpected attestation predicate")
    return subprocess.check_output(
        ["gh", "attestation", "verify", "--repo", REPOSITORY,
         "--signer-workflow", WORKFLOW, "--source-ref", "refs/heads/main",
         "--source-digest", source_sha, "--deny-self-hosted-runners",
         "--predicate-type", predicate, "--format", "json", "--", subject],
    )


def finalize(output: Path, source_sha: str) -> None:
    exact_sha(source_sha)
    output = workspace_path(output, "published directory")
    staged = read_json(output / "staged-images.json")
    if staged["gitSha"] != source_sha or set(staged["images"]) != set(PUBLISH_IMAGES):
        raise ValueError("Unexpected staged release identity")
    # Verify attestations against the staged record, not the raw command argument.
    revision = staged["gitSha"]
    for name, entry in staged["images"].items():
        if not DIGEST.fullmatch(entry["digest"]) or entry["name"] != f"{REGISTRY}/aymenchaghoub/etaye-{name}":
            raise ValueError("Unexpected registry subject")
        reference = entry["name"] + "@" + entry["digest"]
        if entry["reference"] != reference:
            raise ValueError("Registry reference differs from immutable identity")
        for label, predicate in (("slsa", SLSA), ("cyclonedx", CYCLONEDX)):
            verified = output / f"{name}-{label}-verification.json"
            verified.write_bytes(verify_attestation(f"oci://{reference}", revision, predicate))
            entry[label] = describe(verified)
    write_json(output / PUBLISHED_MANIFEST, {
        "schemaVersion": 1, "mode": "published", "provenance": {"status": "verified"}, **staged,
    })


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=("stage", "finalize", "verify-manifest"))
    parser.add_argument("candidate", type=Path)
    parser.add_argument("published", type=Path)
    parser.add_argument("git_sha")
    args = parser.parse_args()
    try:
        if args.operation == "stage":
            stage(args.candidate, args.published, args.git_sha)
        elif args.operation == "finalize":
            finalize(args.published, args.git_sha)
        else:
            record = workspace_path(args.published, "published directory") / PUBLISHED_MANIFEST
            published = read_json(record)
            if published.get("gitSha") != exact_sha(args.git_sha):
                raise ValueError("Published manifest does not match the selected source")
            proof = verify_attestation(str(record), published["gitSha"], SLSA)
            (record.parent / "manifest-attestation-verification.json").write_bytes(proof)
    except (KeyError, ValueError, OSError, subprocess.CalledProcessError) as error:
        print(f"Release promotion stopped: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
