#!/usr/bin/env python3
"""Generate and verify a local release candidate manifest from exact package artifacts."""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import tarfile

from assert_test_reports import read_playwright

PACKAGE_IMAGES = ("api", "web", "migrate", "postgres")
UPSTREAM_IMAGE = "upstream-postgres"
TESTS = {"record": 3, "measurements": 3, "patients": 5}
GIT_SHA = re.compile(r"[0-9a-f]{40}")
DIGEST = re.compile(r"sha256:[0-9a-f]{64}")
HEX_SHA256 = re.compile(r"[0-9a-f]{64}")
IMAGE_MEDIA_TYPES = {
    "application/vnd.oci.image.manifest.v1+json",
    "application/vnd.docker.distribution.manifest.v2+json",
}
MODE = "dry-run"
MANIFEST = "release-manifest.json"


def file_at(root: Path, name: str) -> Path:
    path = root / name
    if path.is_symlink() or not path.is_file():
        raise ValueError(f"Missing or unsafe release evidence: {name}")
    return path


def sha256(handle) -> str:
    digest = hashlib.sha256()
    for chunk in iter(lambda: handle.read(1024 * 1024), b""):
        digest.update(chunk)
    return digest.hexdigest()


def describe(path: Path) -> dict:
    with path.open("rb") as handle:
        return {"file": path.name, "sha256": sha256(handle)}


def image_archive(path: Path) -> dict:
    """Check the archived platform manifest, config and every compressed layer by descriptor."""
    with tarfile.open(path, mode="r:") as archive:
        members = archive.getnames()
        if len(set(members)) != len(members):
            raise ValueError(f"{path.name}: duplicate archive members")

        def open_member(name):
            member = archive.getmember(name)
            if not member.isfile():
                raise ValueError(f"{path.name}: expected regular archive member {name}")
            return archive.extractfile(member)

        def read_blob(descriptor, *, parse_json=False, diff_id=None):
            digest = descriptor["digest"]
            if not DIGEST.fullmatch(digest):
                raise ValueError(f"{path.name}: invalid digest {digest!r}")
            name = "blobs/sha256/" + digest.removeprefix("sha256:")
            if archive.getmember(name).size != descriptor["size"]:
                raise ValueError(f"{path.name}: invalid size for {digest}")
            with open_member(name) as handle:
                if "sha256:" + sha256(handle) != digest:
                    raise ValueError(f"{path.name}: invalid bytes for {digest}")
                if parse_json:
                    handle.seek(0)
                    return json.load(handle)
                if diff_id is not None:
                    handle.seek(0)
                    media_type = descriptor["mediaType"]
                    if media_type.endswith((".gzip", "+gzip")):
                        with gzip.GzipFile(fileobj=handle) as layer:
                            uncompressed = "sha256:" + sha256(layer)
                    elif media_type.endswith(".tar"):
                        uncompressed = digest
                    else:
                        raise ValueError(f"{path.name}: unsupported platform layer format {media_type}")
                    if uncompressed != diff_id:
                        raise ValueError(f"{path.name}: layer differs from the image config")

        with open_member("index.json") as handle:
            index = json.load(handle)
        manifests = index["manifests"]
        if index["schemaVersion"] != 2 or not manifests:
            raise ValueError(f"{path.name}: expected one platform image manifest")
        primary = [
            item for item in manifests
            if item.get("mediaType") in IMAGE_MEDIA_TYPES
            and not item.get("annotations", {}).get("io.containerd.manifest.subject")
        ]
        if len(primary) != 1:
            raise ValueError(f"{path.name}: expected one platform image manifest")
        primary_digest = primary[0]["digest"]
        for item in manifests:
            if item is primary[0]:
                continue
            subject = item.get("annotations", {}).get("io.containerd.manifest.subject")
            if item.get("mediaType") not in IMAGE_MEDIA_TYPES or subject != primary_digest:
                raise ValueError(f"{path.name}: expected one platform image manifest")
            # Verify optional Docker/OCI referrer/attestation manifest blob integrity as well.
            read_blob(item, parse_json=True)
        manifest = read_blob(primary[0], parse_json=True)
        config = read_blob(manifest["config"], parse_json=True)
        if (config["os"], config["architecture"]) != ("linux", "amd64"):
            raise ValueError(f"{path.name}: expected linux/amd64")
        diff_ids = config["rootfs"]["diff_ids"]
        layers = manifest["layers"]
        if not layers or len(layers) != len(diff_ids) or not all(DIGEST.fullmatch(value) for value in diff_ids):
            raise ValueError(f"{path.name}: incomplete image layer metadata")
        for layer, diff_id in zip(layers, diff_ids, strict=True):
            read_blob(layer, diff_id=diff_id)
        return {
            "archive": describe(path),
            "ociManifestDigest": primary_digest,
            "imageConfigDigest": manifest["config"]["digest"],
            "layerDiffIds": diff_ids,
        }


def read_reports(root: Path, service: str, identity: dict, scanner_version: str) -> dict:
    sbom_path = file_at(root, f"sbom-{service}.cdx.json")
    sbom = json.loads(sbom_path.read_bytes())
    metadata = sbom["metadata"]
    properties = metadata["component"]["properties"]
    components = sbom["components"]
    tools = metadata["tools"]["components"]
    sbom_ids = [item["value"] for item in properties if item["name"] == "aquasecurity:trivy:ImageID"]
    sbom_diff_ids = {item["value"] for item in properties if item["name"] == "aquasecurity:trivy:DiffID"}
    if (
        sbom["bomFormat"] != "CycloneDX" or not re.fullmatch(r"1\.[4-7]", sbom["specVersion"])
        or not isinstance(components, list) or not components
        or not any(tool["name"] == "trivy" and tool["version"] == scanner_version for tool in tools)
        or sbom_ids != [identity["imageConfigDigest"]] or sbom_diff_ids != set(identity["layerDiffIds"])
    ):
        raise ValueError(f"{sbom_path.name}: invalid CycloneDX SBOM or image identity")

    scan_path = file_at(root, f"trivy-{service}.json")
    report = json.loads(scan_path.read_bytes())
    results = report["Results"]
    if (
        report["ArtifactType"] != "container_image" or not isinstance(results, list) or not results
        or report["Metadata"]["ImageID"] != identity["imageConfigDigest"]
        or report["Metadata"]["DiffIDs"] != identity["layerDiffIds"]
    ):
        raise ValueError(f"{scan_path.name}: missing scan results or incorrect image identity")
    findings = [entry for result in results for entry in result.get("Vulnerabilities") or []]
    if any(entry["Severity"] not in {"HIGH", "CRITICAL"} or not entry["VulnerabilityID"] for entry in findings):
        raise ValueError(f"{scan_path.name}: invalid advisory data")
    return {
        "sbom": describe(sbom_path) | {"components": len(components)},
        "vulnerabilityReport": describe(scan_path) | {
            "high": sum(entry["Severity"] == "HIGH" for entry in findings),
            "critical": sum(entry["Severity"] == "CRITICAL" for entry in findings),
            "distinctAdvisories": len({entry["VulnerabilityID"] for entry in findings}),
            "fixableOccurrences": sum(bool(entry.get("FixedVersion")) for entry in findings),
        },
    }


def runtime_evidence(root: Path) -> tuple[dict, dict]:
    before = file_at(root, "runtime-before.txt")
    after = file_at(root, "runtime-after.txt")
    if before.read_bytes() != after.read_bytes():
        raise ValueError("The package containers or images changed during the tests")
    decoder = json.JSONDecoder()
    runtimes = {}
    for service, line in zip(PACKAGE_IMAGES, before.read_text().splitlines(), strict=True):
        container, offset = decoder.raw_decode(line)
        remaining = line[offset:].lstrip()
        image, offset = decoder.raw_decode(remaining)
        descriptor = json.loads(remaining[offset:])
        if not HEX_SHA256.fullmatch(container) or not DIGEST.fullmatch(image):
            raise ValueError(f"{service}: invalid runtime identity")
        runtimes[service] = {
            "containerId": container, "localImageId": image,
            "imageManifestDigest": descriptor["digest"] if descriptor is not None else None,
        }

    evidence = {}
    for spec, minimum in TESTS.items():
        report = file_at(root, f"real-{spec}.json")
        totals = read_playwright(report, (f"e2e/{spec}.spec.ts",))
        if totals["tests"] < minimum:
            raise ValueError(f"{report.name}: too few passing package tests")
        evidence[spec] = describe(report) | {"passed": totals["tests"]}
    return runtimes, {"before": describe(before), "after": describe(after), "playwright": evidence}


def generate_manifest(root: Path, git_sha: str, upstream_digest: str) -> dict:
    if not GIT_SHA.fullmatch(git_sha) or not DIGEST.fullmatch(upstream_digest):
        raise ValueError("Expected a full Git SHA and a pinned upstream PostgreSQL digest")
    runtimes, evidence = runtime_evidence(root)
    scanner_path = file_at(root, "scanner.json")
    scanner = json.loads(scanner_path.read_bytes())
    database_path = file_at(root, "scanner-database-sha256.txt")
    database_checksums = dict(line.split()[::-1] for line in database_path.read_text().splitlines())
    if (
        set(database_checksums) != {"db/trivy.db", "java-db/trivy-java.db"}
        or not all(HEX_SHA256.fullmatch(value) for value in database_checksums.values())
        or not all(scanner[db]["UpdatedAt"] for db in ("VulnerabilityDB", "JavaDB"))
        or not re.fullmatch(r"\d+\.\d+\.\d+", scanner["Version"])
    ):
        raise ValueError("Missing Trivy version or database evidence")

    upstream_index = file_at(root, "upstream-postgres-index.json")
    if "sha256:" + describe(upstream_index)["sha256"] != upstream_digest:
        raise ValueError("The upstream PostgreSQL index does not match the Dockerfile pin")
    upstream_manifests = json.loads(upstream_index.read_bytes())["manifests"]
    upstream_platform = [
        item["digest"] for item in upstream_manifests
        if (item.get("platform", {}).get("os"), item.get("platform", {}).get("architecture")) == ("linux", "amd64")
    ]

    artifacts = {}
    for service in (*PACKAGE_IMAGES, UPSTREAM_IMAGE):
        identity = image_archive(file_at(root, f"{service}.tar"))
        if service == UPSTREAM_IMAGE:
            if upstream_platform != [identity["ociManifestDigest"]]:
                raise ValueError("The PostgreSQL upstream archive does not match the pinned platform digest")
            source = {
                "kind": "external-base",
                "baseOf": "postgres",
                "upstreamIndexRef": f"docker.io/library/postgres@{upstream_digest}",
                "upstreamIndex": describe(upstream_index),
            }
        else:
            runtime = runtimes[service]
            tested_manifest = runtime["imageManifestDigest"]
            if tested_manifest is not None and tested_manifest != identity["ociManifestDigest"]:
                raise ValueError(f"{service}: OCI manifest differs from tested runtime")
            if tested_manifest is None and runtime["localImageId"] != identity["imageConfigDigest"]:
                raise ValueError(f"{service}: image config differs from tested runtime")
            source = {"kind": "project-built-demo" if service == "postgres" else "project-built", "testedRuntime": runtime}
        artifacts[service] = source | identity | read_reports(root, service, identity, scanner["Version"])

    return {
        "schemaVersion": 2,
        "mode": MODE,
        "gitSha": git_sha,
        "releaseId": f"candidate-{git_sha}",
        "platform": "linux/amd64",
        "scanner": {
            "version": scanner["Version"],
            "metadata": describe(scanner_path),
            "databaseChecksums": describe(database_path),
        },
        "tests": evidence,
        "artifacts": artifacts,
        "provenance": {"status": "unsigned"},
        "previousKnownGoodRelease": None,
    }


def verify_manifest(root: Path, git_sha: str, upstream_digest: str, *, require_published: bool = False) -> None:
    manifest = json.loads(file_at(root, MANIFEST).read_bytes())
    if require_published or manifest.get("mode") != MODE:
        raise ValueError("A local dry run can never verify a published release")
    if manifest.get("gitSha") != git_sha:
        raise ValueError("The manifest does not match the expected Git SHA")
    if manifest != generate_manifest(root, git_sha, upstream_digest):
        raise ValueError("Manifest mismatch: incorrect image inventory, digest, report checksum or test evidence")


def upstream_pin(revision: str) -> str:
    if not GIT_SHA.fullmatch(revision):
        raise ValueError("Expected a full lowercase Git SHA")
    dockerfile = subprocess.check_output(
        ["git", "show", f"{revision}:infra/demo/postgres.Dockerfile"], text=True,
    )
    match = re.fullmatch(r"FROM postgres:[0-9.]+@(sha256:[a-f0-9]{64})", dockerfile.splitlines()[0])
    if match is None:
        raise ValueError("The PostgreSQL Dockerfile must start with a digest-pinned PostgreSQL base")
    return match[1]


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=("generate", "verify"))
    parser.add_argument("directory", type=Path)
    parser.add_argument("--git-sha", required=True)
    parser.add_argument("--require-published", action="store_true")
    args = parser.parse_args(argv)
    try:
        digest = upstream_pin(args.git_sha)
        if args.operation == "generate":
            if args.require_published:
                raise ValueError("A local dry run can never generate a published release")
            manifest = generate_manifest(args.directory, args.git_sha, digest)
            with (args.directory / MANIFEST).open("x", encoding="utf-8") as handle:
                json.dump(manifest, handle, indent=2)
                handle.write("\n")
        else:
            verify_manifest(args.directory, args.git_sha, digest, require_published=args.require_published)
    except (ValueError, OSError, KeyError, TypeError, AttributeError, EOFError, tarfile.TarError, subprocess.CalledProcessError) as exc:
        print(f"FAIL release candidate: {exc}", file=sys.stderr)
        return 1
    print(f"PASS release candidate {args.operation}: {args.git_sha} (dry run)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
