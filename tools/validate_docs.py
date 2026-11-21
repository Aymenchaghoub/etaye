#!/usr/bin/env python3
"""Validate planning artifacts, without building or testing an application."""
import csv
import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import tomllib
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import unquote


STORY_10_1_TRACKED_PATH = re.compile(
    r"""
    apps/api/(?:
        pom\.xml | mvnw(?:\.cmd)? | \.mvn/wrapper/maven-wrapper\.properties
        | src/(?:main|test)/java/dev/aymen/etaye/(?:[^/]+/)*[^/]+\.java
        | (?P<resource>src/(?:main|test)/resources/(?:[^/]+/)*[^/]+)
    )
    | apps/web/(?:
        package\.json | tsconfig\.json | vite\.config\.ts | playwright(?:\.real)?\.config\.ts | index\.html
        | src/(?:[^/]+/)*[^/]+\.(?:ts|tsx|css)
        | (?:tests|e2e)/(?:[^/]+/)*[^/]+\.ts
    )
    | infra/local/(?:docker-compose\.yml | init-roles\.sh | verify-init-roles\.sh | \.env\.example)
    """,
    re.VERBOSE,
)
STORY_10_2_TRACKED_PATH = re.compile(
    r"apps/web/(?:scripts/[^/]+\.(?:mjs|ts)|(?:openapi|codegen)[^/]*\.config\.(?:ts|mjs))"
)
STORY_1_3_WEB_FIXTURES = {"apps/web/tests/fixtures/practice-switch.html",
                          "apps/web/tests/fixtures/practice-switch.tsx"}
RESOURCE_ARTIFACT_SUFFIXES = {
    ".sh", ".bash", ".cmd", ".bat", ".ps1",
    ".class", ".jar", ".war", ".ear", ".exe", ".dll", ".so", ".bin",
    ".zip", ".tar", ".gz", ".tgz", ".bz2", ".xz", ".7z", ".rar",
}


def is_story_resource(path):
    name = path.name.lower()
    if path.suffix.lower() in RESOURCE_ARTIFACT_SUFFIXES or name == ".env" or name.startswith(".env."):
        return False
    if path.is_symlink() or not path.is_file():
        return False
    try:
        # Allow nested text resources without accepting a binary under a text extension.
        return not re.search(r"[\x00-\x08\x0b\x0c\x0e-\x1f]", path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError):
        return False


def check_application_scope(root, errors):
    modes = {
        (): "planning-only",
        ("10.1",): "story-10.1",
        ("10.1", "10.2"): "stories-10.1-10.2",
        ("10.1", "10.2", "1.2"): "stories-10.1-10.2-1.2",
        ("10.1", "10.2", "1.2", "1.1"): "stories-10.1-10.2-1.2-1.1",
        ("10.1", "10.2", "1.2", "1.1", "1.3"): "stories-10.1-10.2-1.2-1.1-1.3",
    }
    decision_ids = {"10.1": "UD-41", "10.2": "UD-45", "1.2": "UD-48", "1.1": "UD-50", "1.3": "UD-53"}
    scope = {"mode": "planning-only", "authorizedStories": []}
    try:
        config = tomllib.loads((root / "_bmad/custom/config.toml").read_text(encoding="utf-8"))
        etaye = config.get("etaye")
        if not isinstance(etaye, dict) or etaye.get("development_authorized") is not False:
            raise ValueError("[etaye] development_authorized must be false")
        if etaye.keys() - {"development_authorized", "authorized_stories", "phase", "test_artifacts"}:
            raise ValueError("Unknown keys in [etaye] authorization config")
        if any(not isinstance(etaye.get(key, ""), str) for key in ("phase", "test_artifacts")):
            raise ValueError("[etaye] phase and test_artifacts must be strings")
        stories = etaye.get("authorized_stories", [])
        if not isinstance(stories, list) or any(not isinstance(story, str) for story in stories):
            raise ValueError("[etaye] authorized_stories must be a list of story IDs")
        story_ids = set(stories)
        if story_ids - decision_ids.keys():
            raise ValueError("unknown authorized story in [etaye] authorized_stories")
        if len(story_ids) != len(stories):
            raise ValueError("[etaye] authorized_stories contains duplicate story IDs")
        mode = modes.get(tuple(stories))
        if mode is None:
            raise ValueError('[etaye] authorized_stories must be [], ["10.1"], ["10.1", "10.2"], '
                             '["10.1", "10.2", "1.2"], ["10.1", "10.2", "1.2", "1.1"] or '
                             '["10.1", "10.2", "1.2", "1.1", "1.3"]')
        scope = {"mode": mode, "authorizedStories": stories}
    except (OSError, ValueError) as exc:
        errors.append(f"Invalid authorization in _bmad/custom/config.toml: {exc}")
        scope["mode"] = "invalid-authorization"

    authorized_stories = scope["authorizedStories"]
    authorized = bool(authorized_stories)
    scope_label = "story 10.1"
    if len(authorized_stories) > 1:
        scope_label = f"stories {', '.join(authorized_stories[:-1])} and {authorized_stories[-1]}"
    workflows = {"documentation.yml", "codeql.yml", "architecture-pages.yml"}
    forbidden = ("pom.xml", "src", "compose.yaml", "docker-compose.yml")
    if authorized:
        workflows.update({"api.yml", "web.yml"})
        forbidden += ("packages",)
    else:
        forbidden += ("package.json", "apps", "infra")
    workflow_dir = root / ".github/workflows"
    if authorized or workflow_dir.exists():
        if ((authorized and workflow_dir.is_symlink()) or not workflow_dir.is_dir()
                or {path.name for path in workflow_dir.iterdir()} != workflows):
            errors.append(f"Unexpected workflows for {scope_label}" if authorized else
                          "Unexpected application workflow before development authorization")
    for name in forbidden:
        artifact = root / name
        if artifact.exists() or (authorized and artifact.is_symlink()):
            errors.append("Application implementation artifact created: " + name)

    if not authorized:
        return scope

    register = "docs/planning-artifacts/decisions-assumptions.md"
    try:
        decisions = (root / register).read_text(encoding="utf-8")
    except (OSError, UnicodeError):
        decisions = ""
    # Keep line boundaries while ignoring fenced examples and hidden comments.
    decisions = re.sub(
        r"^ {0,3}(?P<fence>`{3,})[^`\n]*(?:\n|\Z).*?(?:^ {0,3}(?P=fence)`*[ \t]*$|\Z)"
        r"|<!--.*?(?:-->|\Z)",
        lambda hidden: "\n" * hidden[0].count("\n"),
        decisions,
        flags=re.MULTILINE | re.DOTALL,
    )
    for story in authorized_stories:
        decision_id = decision_ids[story]
        rows = re.findall(rf"^[ \t]*\|[ \t]*{decision_id}[ \t]*\|(?:([^|\n]*)\|)?", decisions, re.MULTILINE)
        decision = rows[0].casefold() if len(rows) == 1 else ""
        if (not re.search(rf"\bauthorized for \*{{0,2}}story {re.escape(story)} only\b\*{{0,2}}", decision)
                or set(re.findall(r"\b\d{1,2}\.\d{1,2}\b", decision)) != {story}
                or re.search(r"\b(?:not|never|no\s+longer)(?:\s+\w+){0,3}\s+authorized\b|refus", decision)):
            errors.append(f"Story {story} authorization requires {decision_id} in {register} to authorize only story {story}")

    apps = root / "apps"
    if apps.is_symlink() or not apps.is_dir() or {path.name for path in apps.iterdir()} != {"api", "web"}:
        errors.append(f"{scope_label.capitalize()} requires exactly apps/api and apps/web under apps/")
    for name in ("apps/api", "apps/web"):
        path = root / name
        if not path.is_dir() or path.is_symlink():
            errors.append(f"Missing or invalid {scope_label} directory: {name}")
    for name in ("apps/api/pom.xml", "apps/api/mvnw", "apps/web/package.json"):
        path = root / name
        if not path.is_file() or path.is_symlink():
            errors.append(f"Missing or invalid {scope_label} file: {name}")

    infra = root / "infra"
    if infra.exists() or infra.is_symlink():
        local = infra / "local"
        if (infra.is_symlink() or not infra.is_dir() or {path.name for path in infra.iterdir()} != {"local"}
                or local.is_symlink() or not local.is_dir()):
            errors.append(f"{scope_label.capitalize()} only allows infra/local under infra/")

    build_directories = {"node_modules", "target", "test-results", "playwright-report", "dist"}
    try:
        ignore_rules = {line.rstrip("/") for line in (root / ".gitignore").read_text(encoding="utf-8").splitlines()}
    except (OSError, UnicodeError):
        ignore_rules = set()
    for name in sorted(build_directories - ignore_rules):
        errors.append("Missing build output rule in .gitignore: " + name)

    if not (root / ".git").exists():
        scope["trackedBuildOutputCheck"] = "skipped: no Git index at validation root"
        return scope
    try:
        index = subprocess.run(["git", "ls-files", "-s", "-z"], cwd=root, capture_output=True, check=True)
    except (OSError, subprocess.CalledProcessError):
        scope["trackedBuildOutputCheck"] = "skipped: git ls-files unavailable"
    else:
        scope["trackedBuildOutputCheck"] = "checked with git ls-files"
        for entry in os.fsdecode(index.stdout).split("\0"):
            if not entry:
                continue
            metadata, name = entry.split("\t", 1)
            mode = metadata.split(" ", 1)[0]
            if mode == "120000" and name.startswith(("apps/api/", "apps/web/", "infra/local/")):
                errors.append(f"Tracked symlink for {scope_label}: {name}")
                continue
            if build_directories.intersection(name.split("/")):
                errors.append("Tracked build output: " + name)
                continue
            application_path = name.startswith(("apps/", "infra/"))
            if application_path and mode not in {"100644", "100755"}:
                errors.append(f"Tracked non-regular entry for {scope_label}: {name}")
                continue
            if not application_path:
                continue
            if "10.2" in authorized_stories and STORY_10_2_TRACKED_PATH.fullmatch(name):
                continue
            if "1.3" in authorized_stories and name in STORY_1_3_WEB_FIXTURES:
                continue
            match = STORY_10_1_TRACKED_PATH.fullmatch(name)
            if not match or (match["resource"] and not is_story_resource(root / name)):
                errors.append(f"Unexpected tracked path for {scope_label}: {name}")
    return scope


def main():
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
        return json.loads((ROOT / relative).read_text(encoding="utf-8"))


    def slug(text):
        return re.sub(r"[^\w\- ]", "", text.lower()).replace(" ", "-")


    docs = list((ROOT / "docs").rglob("*.md")) + [ROOT / p for p in
            ("README.md", "AGENTS.md", "CLAUDE.md", "THIRD_PARTY_NOTICES.md")]
    link_count = 0
    for path in docs:
        text = path.read_text(encoding="utf-8")
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
                content = resolved.read_text(encoding="utf-8")
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
    # Error responses and ETag follow the policy written in docs/contracts/README.md.
    for path, methods in api["paths"].items():
        for method, op in methods.items():
            ident = op["operationId"]
            declared = {code for code in op["responses"] if not code.startswith("2")}
            names = {p["$ref"].rsplit("/", 1)[-1] for p in op.get("parameters", []) if "$ref" in p}
            require(("412" in declared) == ("IfMatch" in names), "412 must be declared exactly with If-Match: " + ident)
            require(("428" in declared) == ("IfMatch" in names), "428 must be declared exactly with If-Match: " + ident)
            if method == "get":
                require(not declared & {"409", "412", "422", "428"}, "GET declares a write-only error: " + ident)
            for code, response in op["responses"].items():
                if code.startswith("2"):
                    schema = ((response.get("content") or {}).get("application/json") or {}).get("schema") or {}
                    name = schema.get("$ref", "").rsplit("/", 1)[-1]
                    versioned = bool(name) and "version" in api["components"]["schemas"].get(name, {}).get("properties", {})
                    require(versioned == ("ETag" in (response.get("headers") or {})),
                            "ETag must mark a single versioned aggregate only: " + ident)

    stories = read_json("docs/planning-artifacts/epics/story-index.json")
    required = {f"FR-{i:03d}" for i in range(1, 21)}
    covered = {r for story in stories for r in story["requirements"]}
    require(covered == required, "Missing/orphan requirement in stories: " + str(required ^ covered))
    require(len({s["id"] for s in stories}) == len(stories), "Duplicate story IDs")
    for s in stories:
        require(s["status"] == "backlog", "Premature implementation status: " + s["id"])
        require((ROOT / s["path"]).exists(), "Story source missing: " + s["id"])
        require(s["test"] in (ROOT / s["path"]).read_text(encoding="utf-8"), "Unlinked AT design: " + s["id"])
    checks["planningStories"] = len(stories)
    by_story = {s["id"]: s for s in stories}
    roadmap = read_json("docs/planning-artifacts/roadmap-assignments.json")
    increments = set(roadmap["increments"])
    require(set(roadmap["stories"]) == set(by_story), "Roadmap stories differ from the story index")
    for sid, story in by_story.items():
        require(isinstance(story.get("dependsOn"), list), "Story lacks dependsOn: " + sid)
        item = roadmap["stories"].get(sid)
        if item is None:
            continue
        require(str(item["increment"]) in increments, "Unknown increment for story " + sid)
        for part in item["deferredParts"]:
            require(str(part["increment"]) in increments and part["increment"] > item["increment"],
                    "Deferred part must come in a later increment: " + sid)
        for dep in story.get("dependsOn", []):
            require(dep in by_story, "Unknown story dependency: " + sid + "->" + dep)
            if dep in roadmap["stories"]:
                require(roadmap["stories"][dep]["increment"] <= item["increment"],
                        "Story depends on a later increment: " + sid + "->" + dep)
    state = {}


    def visit_story(sid, trail=()):
        if state.get(sid) == "done":
            return
        require(sid not in trail, "Story dependency cycle: " + " -> ".join(trail + (sid,)))
        if sid in trail:
            return
        for dep in by_story[sid].get("dependsOn", []):
            if dep in by_story:
                visit_story(dep, trail + (sid,))
        state[sid] = "done"


    for sid in by_story:
        visit_story(sid)
    checks["roadmapStories"] = len(roadmap["stories"])

    with (ROOT / "docs/design/coverage.csv").open(encoding="utf-8", newline="") as stream:
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
        require(row["id"] in spec.read_text(encoding="utf-8"), "Unspecified surface: " + row["id"])
    checks["requiredDesignFamilies"] = len(rows)
    require(set(roadmap["surfaces"]) == expected, "Roadmap design families differ from the design inventory")
    for sid, item in roadmap["surfaces"].items():
        require(str(item["increment"]) in increments, "Unknown increment for family " + sid)
        for part in item["parts"]:
            require(part["increment"] > item["increment"], "Surface part must come later: " + sid)
    core = roadmap["coreDesignFamilies"]
    require(set(core["screens"]) | set(core["interactions"]) <= expected, "Unknown core design family")
    contract_ops = {name for name, item in roadmap["operations"].items() if item["status"] == "contract"}
    require(contract_ops == set(operations), "Roadmap operations differ from the contract")
    for name, item in roadmap["operations"].items():
        require(str(item["increment"]) in increments, "Unknown increment for operation " + name)
        require(item["status"] != "planned" or name not in operations, "Planned operation already in contract: " + name)
    checks["roadmapSurfaces"] = len(roadmap["surfaces"])
    checks["roadmapOperations"] = len(roadmap["operations"])

    screen_text = (ROOT / "docs/design/screen-catalog.md").read_text(encoding="utf-8")
    interaction_text = (ROOT / "docs/design/interaction-catalog.md").read_text(encoding="utf-8")
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
    capabilities = set(api["components"]["schemas"]["Capability"]["enum"])
    for holder in ("Membership", "MembershipEdit", "InvitationCreate"):
        require(api["components"]["schemas"][holder]["properties"]["capabilities"]["items"]
                == {"$ref": "#/components/schemas/Capability"}, "Capability enum must be referenced, not copied: " + holder)
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
    offline_reference = ROOT / "design/offline-reference"
    offline_snapshot = read_json("design/offline-reference/snapshot.json")
    offline_files = [
        {"path": path.relative_to(offline_reference).as_posix(), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
        for path in sorted((offline_reference / "site").rglob("*"), key=lambda path: path.parts)
        if path.is_file()
    ]
    require(offline_snapshot["files"] == offline_files, "Changed or missing offline mockup files")
    offline_digest = hashlib.sha256(json.dumps(offline_files, sort_keys=True).encode()).hexdigest()
    require(offline_snapshot["snapshotSha256"] == offline_digest, "Offline mockup snapshot hash mismatch")
    checks["offlineMockupFiles"] = len(offline_files)
    require((ROOT / ".agents/skills").resolve() == (ROOT / ".claude/skills").resolve(), "Shared skills discovery link broken")
    require((ROOT / "CLAUDE.md").read_text(encoding="utf-8").startswith("@AGENTS.md"), "Claude shared-policy import missing")
    require(not (ROOT / "docs/design/claude-chat-kit").exists(), "Redundant chat export should remain outside repository")
    checks["applicationScope"] = check_application_scope(ROOT, errors)

    # Do not print any matched secret text. Scan project-owned files, not third-party examples.
    secret = re.compile(r"(?:figd_[A-Za-z0-9_-]{25,}|gh[pousr]_[A-Za-z0-9]{25,}|sk-[A-Za-z0-9]{30,}|-----BEGIN (?:RSA |OPENSSH )?PRIVATE KEY-----)")
    scanned = 0
    for path in list((ROOT / "docs").rglob("*")) + list((ROOT / "tools").glob("*.py")):
        if path.is_file() and path.suffix in {".md", ".json", ".yaml", ".csv", ".py", ".svg"}:
            scanned += 1
            require(not secret.search(path.read_text(encoding="utf-8")), "Potential credential found in: " + str(path.relative_to(ROOT)))
    checks["projectFilesSecretScanned"] = scanned
    result = {"kind": "documentary-validation-only", "date": datetime.now(timezone.utc).date().isoformat(), "ok": not errors,
              "checks": checks, "errors": errors, "applicationTestsExecuted": False,
              "independentReview": "Automated documentary checks; not independent design approval"}
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
