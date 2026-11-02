#!/usr/bin/env python3
"""Check the planning-only CI boundary and pinned action permissions."""
from pathlib import Path
import json
import re
import tomllib
import yaml

root = Path(__file__).resolve().parents[1]
path = root / ".github/workflows/documentation.yml"
# BaseLoader keeps the GitHub key `on` from YAML 1.1 boolean coercion.
workflow = yaml.load(path.read_text(), Loader=yaml.BaseLoader)
assert set(workflow["on"]) == {"pull_request", "push", "workflow_dispatch"}
assert workflow["permissions"] == {"contents": "read"}
jobs = workflow["jobs"]
assert set(jobs) == {"documentation"}
job = jobs["documentation"]
assert "continue-on-error" not in job
assert int(job["timeout-minutes"]) <= 10
steps = job["steps"]
for step in steps:
    if "uses" in step:
        assert re.fullmatch(r"[A-Za-z0-9_./-]+@[a-f0-9]{40}", step["uses"]), "Unpinned action"
    assert "continue-on-error" not in step, "Required checks must not suppress failure"
assert "${{ secrets." not in path.read_text(), "Planning CI must not consume secrets"
assert "persist-credentials: false" in path.read_text()
assert all(command in path.read_text() for command in [
    "tools/validate_docs.py", "tools/check_doc_workflow.py", "openapi_spec_validator",
    "lint_spine.py", "git diff --check HEAD^1 HEAD", "--require-hashes",
    "unittest discover -s tools -p 'test_*.py'",
])
assert "pull_request_target" not in workflow["on"]
codeql = yaml.load((root / ".github/workflows/codeql.yml").read_text(), Loader=yaml.BaseLoader)
assert codeql["permissions"] == {"contents": "read"}
assert codeql["jobs"]["analyze"]["permissions"] == {"contents": "read", "security-events": "write"}
assert "continue-on-error" not in codeql["jobs"]["analyze"]
for step in codeql["jobs"]["analyze"]["steps"]:
    if "uses" in step:
        assert re.fullmatch(r"[A-Za-z0-9_./-]+@[a-f0-9]{40}", step["uses"])
    assert "continue-on-error" not in step, "Required checks must not suppress failure"
config = yaml.load((root / ".github/codeql-config.yml").read_text(), Loader=yaml.BaseLoader)
assert config["paths"] == ["tools"], "Current CodeQL scope is first-party planning tooling"
rabbit = yaml.safe_load((root / ".coderabbit.yaml").read_text())
assert rabbit["language"] == "en-US" and rabbit["early_access"] is False
assert rabbit["reviews"]["auto_approvals"]["enabled"] is False
assert rabbit["reviews"]["post_merge_actions"] == []
assert all(item["enabled"] is False for item in rabbit["reviews"]["finishing_touches"].values())
qodo = tomllib.loads((root / ".pr_agent.toml").read_text())["github_app"]
assert qodo == {"pr_commands": [], "handle_push_trigger": False, "push_commands": []}
print(json.dumps({"ok": True, "kind": "documentation-workflow-policy", "jobs": len(jobs),
                  "applicationBuild": False, "deployment": False}))
