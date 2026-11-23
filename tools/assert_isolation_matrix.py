#!/usr/bin/env python3
"""Fail CI for an incomplete, failing or unsafe adversarial isolation matrix report (story 1.4, UD-33, UD-59)."""
import argparse
import json
import re
from pathlib import Path

UUID = re.compile(r"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
COMMIT = re.compile(r"[0-9a-f]{40}")
CASE_FIELDS = ("id", "group", "label", "expected", "observed", "result", "test")
REPORT_KEYS = {"schemaVersion", "story", "commit", "fixtureVersion", "parity", "cases", "pending"}
CASE_KEYS = set(CASE_FIELDS) | {"fingerprint"}
PENDING_KEYS = {"id", "resource", "operations", "owners", "status", "trigger"}
FINGERPRINT = re.compile(r"[0-9a-f]{16}")
FORBIDDEN_KEYS = {"body", "headers", "header", "cookie", "token", "request", "response", "url", "path"}


def load_json(path, description):
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError) as exc:
        raise ValueError(f"Cannot read the {description} {path.name}") from exc
    except json.JSONDecodeError as exc:
        raise ValueError(f"Malformed JSON in the {description} {path.name}") from exc
    if not isinstance(data, dict):
        raise ValueError(f"The {description} must be a JSON object")
    return data


def forbidden_keys(value):
    if isinstance(value, dict):
        for key, child in value.items():
            if key.lower() in FORBIDDEN_KEYS:
                yield key
            yield from forbidden_keys(child)
    elif isinstance(value, list):
        for child in value:
            yield from forbidden_keys(child)


def check(report, manifest, require_commit, expect_commit=None):
    if report.get("schemaVersion") != 1 or report.get("story") != "1.4":
        raise ValueError("The report must declare schemaVersion 1 and story 1.4")
    if set(report) != REPORT_KEYS:
        raise ValueError("The report does not match its schema")
    cases = report.get("cases")
    if not isinstance(cases, list) or not cases:
        raise ValueError("The report holds no measured case")

    commit = report.get("commit")
    if not isinstance(commit, str) or commit == "unknown" or (require_commit and not COMMIT.fullmatch(commit)):
        raise ValueError("The report must name the commit it measured")
    if expect_commit and commit != expect_commit:
        raise ValueError("The report measured another commit than the one under verification")
    fixture = report.get("fixtureVersion")
    if not isinstance(fixture, str) or not fixture.strip() or fixture == "unknown":
        raise ValueError("The report must name the fixture version it measured")
    if fixture != manifest.get("fixtureVersion"):
        raise ValueError("The report measured another fixture version than the manifest names")

    expected_cases = {case["id"]: case for case in manifest.get("cases", [])}
    if not expected_cases:
        raise ValueError("The case manifest lists no mandatory case")
    seen = {}
    for case in cases:
        if not isinstance(case, dict) or any(not isinstance(case.get(field), str) or not case[field].strip()
                                             for field in CASE_FIELDS):
            raise ValueError(f"A report case lacks one of {', '.join(CASE_FIELDS)}")
        if not set(case) <= CASE_KEYS:
            raise ValueError("A report case holds fields outside its schema")
        if case["id"] in seen:
            raise ValueError(f"Duplicate case {case['id']}")
        seen[case["id"]] = case
    unknown = sorted(set(seen) - set(expected_cases))
    if unknown:
        raise ValueError(f"Cases absent from the manifest: {', '.join(unknown)}")
    missing = sorted(set(expected_cases) - set(seen))
    if missing:
        raise ValueError(f"Missing mandatory cases: {', '.join(missing)}")
    for case_id, case in seen.items():
        wanted = expected_cases[case_id]
        if case["expected"] != wanted["expected"] or case["label"] != wanted["label"] or case["group"] != wanted["group"]:
            raise ValueError(f"Case {case_id} no longer matches the manifest")
        if case["result"] != "PASS" or case["observed"] != case["expected"]:
            # The observation is not echoed: it is unvalidated text until the checks below have run.
            raise ValueError(f"Case {case_id} did not pass: its result or outcome differs from the expectation")

    if report.get("parity") != manifest.get("parity"):
        raise ValueError("The parity groups of the report differ from the manifest")
    for group, members in manifest.get("parity", {}).items():
        fingerprints = {seen[member].get("fingerprint") for member in members}
        if any(not isinstance(value, str) or not FINGERPRINT.fullmatch(value) for value in fingerprints):
            raise ValueError(f"Parity group {group} has a case without a response fingerprint")
        if len(fingerprints) != 1:
            raise ValueError(f"Parity group {group} discloses a difference between refusals that must be identical")

    pending = report.get("pending")
    wanted_pending = [{**row, "status": "PENDING"} for row in manifest.get("pending", [])]
    if not isinstance(pending, list) or any(not isinstance(row, dict) or set(row) != PENDING_KEYS for row in pending):
        raise ValueError("The pending rows of the report differ from the manifest")
    if any(row.get("status") != "PENDING" for row in pending):
        raise ValueError("A resource without a real route must stay PENDING")
    if sorted(pending, key=lambda row: str(row.get("id"))) != sorted(wanted_pending, key=lambda row: row["id"]):
        raise ValueError("The pending rows of the report differ from the manifest")

    text = json.dumps(report)
    if UUID.search(text):
        raise ValueError("The report must not contain an identifier")
    leaked = sorted(set(forbidden_keys(report)))
    if leaked:
        raise ValueError(f"The report must not carry request or response material: {', '.join(leaked)}")
    return {"cases": len(cases), "pending": len(pending), "groups": len(manifest.get("parity", {}))}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path, help="isolation-matrix.json written by the API tests")
    parser.add_argument("--manifest", type=Path, required=True, help="matrix-cases.json listing the mandatory cases")
    parser.add_argument("--require-commit", action="store_true", help="require a full Git commit hash (CI)")
    parser.add_argument("--expect-commit", help="the commit under verification (GITHUB_SHA in CI)")
    args = parser.parse_args(argv)
    try:
        totals = check(load_json(args.report, "isolation matrix report"),
                       load_json(args.manifest, "isolation case manifest"), args.require_commit, args.expect_commit)
    except ValueError as exc:
        print(f"FAIL isolation matrix: {' '.join(str(exc).splitlines())}")
        return 1
    print("PASS isolation matrix: " + ", ".join(f"{name}={count}" for name, count in totals.items()))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
