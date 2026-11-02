#!/usr/bin/env python3
"""Validate planning artifacts, without building or testing an application."""
import csv
import argparse
import hashlib
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--report", type=Path, default=ROOT / "docs/test-artifacts/document-validation.json")
report_path = parser.parse_args().report
errors = []
checks = {}


def require(condition, message):
    if not condition:
        errors.append(message)


def read_json(relative):
    return json.loads((ROOT / relative).read_text())


def slug(text):
    return re.sub(r"[^\w\- ]", "", text.lower()).replace(" ", "-")


docs = list((ROOT / "docs").rglob("*.md")) + [ROOT / p for p in
        ("README.md", "AGENTS.md", "CLAUDE.md", "THIRD_PARTY_NOTICES.md")]
link_count = 0
for path in docs:
    text = path.read_text()
    require(not re.search(r"\{(?:i:03d|title|br|j|aid|sid)\}", text),
            "Unresolved authoring placeholder in: " + str(path.relative_to(ROOT)))
    # Code examples are not navigable prose links.
    prose = re.sub(r"```.*?```", "", text, flags=re.S)
    if path.name == "ARCHITECTURE-SPINE.md":
        for refs in re.findall(r"^(?:sources|companions): \[(.+)\]$", text, re.M):
            for ref in refs.split(","):
                require((path.parent / ref.strip()).is_file(),
                        "Missing architecture metadata reference: " + ref.strip())
    for target in re.findall(r"\]\(([^)]+)\)", prose):
        target = target.strip().strip("<>")
        if re.match(r"[a-zA-Z][\w+.-]*:", target):
            continue
        file_part, _, anchor = target.partition("#")
        resolved = (path.parent / unquote(file_part)).resolve() if file_part else path
        link_count += 1
        require(resolved.exists(), f"Broken link in {path.relative_to(ROOT)}: {target}")
        if resolved.is_file() and resolved.suffix == ".md" and anchor:
            content = resolved.read_text()
            anchors = {slug(h) for h in re.findall(r"^#{1,6} (.+)$", content, re.M)}
            anchors.update(re.findall(r'<a id="([^"]+)"', content))
            require(unquote(anchor) in anchors,
                    f"Broken anchor in {path.relative_to(ROOT)}: {target}")
checks["markdownFiles"] = len(docs)
checks["localLinks"] = link_count

api = read_json("docs/contracts/openapi.yaml")
require(api.get("openapi", "").startswith("3.1."), "OpenAPI version missing")


def resolve_ref(ref):
    current = api
    for key in ref[2:].split("/"):
        current = current[key.replace("~1", "/").replace("~0", "~")]
    return current


def visit(value):
    if isinstance(value, dict):
        if "$ref" in value:
            ref = value["$ref"]
            try:
                require(ref.startswith("#/"), "Unexpected external API ref: " + ref)
                resolve_ref(ref)
            except (KeyError, TypeError):
                errors.append("Unresolved API reference: " + ref)
        for child in value.values():
            visit(child)
    elif isinstance(value, list):
        for child in value:
            visit(child)


visit(api)
operations = []
for path, methods in api["paths"].items():
    for method, op in methods.items():
        operations.append(op["operationId"])
        require(bool(op.get("x-required-permissions")), "Missing permission: " + op["operationId"])
        parameters = [resolve_ref(p["$ref"]) if "$ref" in p else p
                      for p in op.get("parameters", [])]
        actual = {p["name"] for p in parameters if p["in"] == "path" and p.get("required")}
        require(actual == set(re.findall(r"{([^}]+)}", path)), "Path parameter mismatch: " + path)
        require(any(code.startswith("2") for code in op["responses"]), "Missing success response: " + path)
require(len(operations) == len(set(operations)), "Duplicate operation IDs")
expected_operation_index = [
    {"operationId": op["operationId"], "method": method, "path": path,
     "permission": " AND ".join(op["x-required-permissions"])}
    for path, methods in api["paths"].items() for method, op in methods.items()
]
require(read_json("docs/contracts/operation-index.json") == expected_operation_index,
        "Operation index differs from the authoritative API contract")
checks["apiOperations"] = len(operations)
checks["apiSchemas"] = len(api["components"]["schemas"])

stories = read_json("docs/planning-artifacts/epics/story-index.json")
required = {f"FR-{i:03d}" for i in range(1, 21)}
covered = {r for story in stories for r in story["requirements"]}
require(covered == required, "Missing/orphan requirement in stories: " + str(required ^ covered))
require(len({s["id"] for s in stories}) == len(stories), "Duplicate story IDs")
for s in stories:
    require(s["status"] == "backlog", "Premature implementation status: " + s["id"])
    require((ROOT / s["path"]).exists(), "Story source missing: " + s["id"])
    require(s["test"] in (ROOT / s["path"]).read_text(), "Unlinked AT design: " + s["id"])
checks["planningStories"] = len(stories)

with (ROOT / "docs/design/coverage.csv").open() as stream:
    rows = list(csv.DictReader(stream))
expected = {f"S-{i:02d}" for i in range(1, 41)} | {f"I-{i:02d}" for i in range(1, 29)} | {
    f"P-{i:02d}" for i in range(1, 4)} | {f"M-{i:02d}" for i in range(1, 6)}
require({r["id"] for r in rows} == expected, "Incomplete design inventory")
require(len(rows) == len(expected), "Duplicate design coverage IDs")
for row in rows:
    require(row["design_status"] in {"Draft", "Ready for design", "Designed", "Reviewed", "Approved by Aymen"},
            "Unknown design status: " + row["id"])
    if row["design_status"] in {"Designed", "Reviewed", "Approved by Aymen"}:
        require(bool(row["figma_node"]), "Design status without actual Figma reference: " + row["id"])
    if row["design_status"] in {"Reviewed", "Approved by Aymen"}:
        require(all(row[field] for field in ["visual_evidence", "reviewer", "reviewed_at"]),
                "Reviewed design without evidence: " + row["id"])
    if row["design_status"] == "Approved by Aymen":
        require(row["aymen_approval"] not in {"", "Not approved"},
                "Approved design lacks explicit user approval reference: " + row["id"])
    spec = ROOT / "docs/design" / row["specification"].split("#")[0]
    require(spec.exists(), "Missing surface specification: " + row["id"])
    require(row["id"] in spec.read_text(), "Unspecified surface: " + row["id"])
checks["requiredDesignFamilies"] = len(rows)

screen_text = (ROOT / "docs/design/screen-catalog.md").read_text()
interaction_text = (ROOT / "docs/design/interaction-catalog.md").read_text()
interaction_surfaces = {}
for iid, surfaces in re.findall(r"^\| \[(I-\d+)\].*?\| ([^|]+) \|$", interaction_text, re.M):
    interaction_surfaces[iid] = set(re.findall(r"S-\d+", surfaces))
for sid, section in re.findall(r"## (S-\d+) — (.*?)(?=\n<a id=|\Z)", screen_text, re.S):
    for iid in set(re.findall(r"I-\d+", section)):
        require(iid in interaction_surfaces, "Unknown screen interaction: " + sid + "/" + iid)
        supported = interaction_surfaces.get(iid, set())
        require(not supported or sid in supported,
                "Interaction surface mismatch: " + sid + "/" + iid)
checks["screenInteractionMappings"] = len(interaction_surfaces)

data = read_json("docs/design/synthetic-data.json")
require(data["synthetic"] is True and len(data["practices"]) >= 2, "Synthetic/isolation fixture missing")
capabilities = set(api["components"]["schemas"]["MembershipEdit"]["properties"]["capabilities"]["items"]["enum"])
for membership in data["memberships"]:
    require(set(membership["capabilities"]) <= capabilities,
            "Unknown fixture capability in " + membership["id"])
all_ids = {item["id"] for value in data.values() if isinstance(value, list)
           for item in value if isinstance(item, dict) and "id" in item}
require(len(all_ids) == sum(1 for v in data.values() if isinstance(v, list)
                          for item in v if isinstance(item, dict) and "id" in item), "Duplicate fixture IDs")
by_id = {item["id"]: item for value in data.values() if isinstance(value, list)
         for item in value if isinstance(item, dict) and "id" in item}


def validate_fixture(value, parent=None):
    if isinstance(value, dict):
        for key, val in value.items():
            if key.endswith("Id") and isinstance(val, str) and key not in {"datasetId"}:
                require(val in all_ids, "Unknown fixture reference: " + key + "=" + val)
                if val in by_id and value.get("practiceId") and by_id[val].get("practiceId"):
                    # Scenario attempts deliberately cross boundaries and are the negative fixture.
                    if key not in {"attemptedRecordId"}:
                        require(value["practiceId"] == by_id[val]["practiceId"], "Cross-practice fixture relation: " + val)
            if key.endswith("Ids") and isinstance(val, list):
                for item in val:
                    require(item in all_ids, "Unknown fixture list reference: " + str(item))
                    if item in by_id and value.get("practiceId") and by_id[item].get("practiceId"):
                        require(value["practiceId"] == by_id[item]["practiceId"],
                                "Cross-practice fixture relation: " + item)
            if key.endswith("At") and isinstance(val, str):
                try:
                    datetime.fromisoformat(val.replace("Z", "+00:00"))
                except ValueError:
                    errors.append("Invalid fixture timestamp: " + key)
            validate_fixture(val, value)
    elif isinstance(value, list):
        for item in value:
            validate_fixture(item, parent)


validate_fixture(data)
for m in data["measurements"]:
    require(bool(m.get("sourceId")), "Measurement lacks source lineage: " + m["id"])
for publication in data["publications"]:
    doc = by_id[publication["documentVersionId"]]
    require(doc["businessStatus"] == "VALIDATED", "Unvalidated fixture publication")
    require(doc["kind"] != "CLINICIAN_BRIEF", "Published clinician brief in fixture")
for task in data["aiTasks"]:
    require(task["status"] != "FAILED" or task.get("outputVersionId") is None, "Failed task has fake output")
checks["syntheticObjects"] = len(all_ids)

manifest = read_json("docs/design/figma/manifest.json")
for asset in manifest["assets"]:
    path = ROOT / "docs/design/figma" / asset["file"]
    require(path.exists() and hashlib.sha256(path.read_bytes()).hexdigest() == asset["sha256"],
            "Changed/missing original Figma export: " + asset["file"])
checks["verifiedFigmaExports"] = len(manifest["assets"])
require((ROOT / ".agents/skills").resolve() == (ROOT / ".claude/skills").resolve(), "Shared skills discovery link broken")
require((ROOT / "CLAUDE.md").read_text().startswith("@AGENTS.md"), "Claude shared-policy import missing")
require(not (ROOT / "docs/design/claude-chat-kit").exists(), "Redundant chat export should remain outside repository")
workflow_dir = ROOT / ".github/workflows"
if workflow_dir.exists():
    require({p.name for p in workflow_dir.iterdir()} == {"documentation.yml", "codeql.yml"},
            "Unexpected application workflow before development authorization")
for forbidden in ("pom.xml", "package.json", "apps", "src", "compose.yaml", "docker-compose.yml"):
    require(not (ROOT / forbidden).exists(), "Application implementation artifact created: " + forbidden)

# Do not print any matched secret text. Scan project-owned files, not third-party examples.
secret = re.compile(r"(?:figd_[A-Za-z0-9_-]{25,}|gh[pousr]_[A-Za-z0-9]{25,}|sk-[A-Za-z0-9]{30,}|-----BEGIN (?:RSA |OPENSSH )?PRIVATE KEY-----)")
scanned = 0
for path in list((ROOT / "docs").rglob("*")) + list((ROOT / "tools").glob("*.py")):
    if path.is_file() and path.suffix in {".md", ".json", ".yaml", ".csv", ".py", ".svg"}:
        scanned += 1
        require(not secret.search(path.read_text()), "Potential credential found in: " + str(path.relative_to(ROOT)))
checks["projectFilesSecretScanned"] = scanned
result = {"kind": "documentary-validation-only", "date": datetime.now(timezone.utc).date().isoformat(), "ok": not errors,
          "checks": checks, "errors": errors, "applicationTestsExecuted": False,
          "independentReview": "Authoring subagents stopped at usage limit; final checks performed by coordinator"}
report_path.parent.mkdir(parents=True, exist_ok=True)
report_path.write_text(json.dumps(result, indent=2) + "\n")
print(json.dumps(result, indent=2))
sys.exit(1 if errors else 0)
