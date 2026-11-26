#!/usr/bin/env python3
"""Check engineering invariants on every GitHub Actions workflow.

The files are discovered, not listed, so a new workflow is covered the day it is added. The rules protect
properties the project relies on (a required check always starts and cannot be silenced, tokens stay
least-privilege, third-party code is pinned, untrusted input never reaches a shell). They are not a snapshot of
the current YAML: a workflow may change freely while it keeps the invariants.

`zizmor` and `actionlint` run beside this check for the deeper security and syntax analysis.
"""
import argparse
import re
import sys
from pathlib import Path

import yaml

import ci_scope

ROOT = Path(__file__).resolve().parents[1]

# Job names that the ruleset on main requires. The ruleset also requires `CodeQL`, which GitHub code scanning
# reports itself. Keep this list in step with the ruleset: a renamed job would leave a required check that
# never reports, which blocks every merge.
REQUIRED_JOB_NAMES = (
    "Planning package checks",
    "Analyze tooling",
    "Analyze API (Java)",
    "Analyze Web (TypeScript)",
    "API verify",
    "Web verify",
)
# Token scopes a job may request above read. `contents: write` is deliberately absent.
ALLOWED_JOB_WRITE_SCOPES = {"security-events", "pages", "id-token", "attestations"}
MAX_TIMEOUT_MINUTES = 30
MAX_ARTIFACT_RETENTION_DAYS = 30
FORBIDDEN_TRIGGERS = {"pull_request_target", "workflow_run"}
# Only the dedicated non-required SonarQube workflow may read SONAR_TOKEN on pull_request (guarded by preflight).
PULL_REQUEST_SECRET_WORKFLOWS = {"sonarqube.yml": frozenset({"SONAR_TOKEN"})}
PINNED_USE = re.compile(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_./-]+@[0-9a-f]{40}")
USE_LINE = re.compile(r"^\s*(?:-\s*)?uses:\s*(?P<ref>\S+)\s*(?:#\s*(?P<comment>.*))?$")
VERSION_COMMENT = re.compile(r"v?\d+(?:\.\d+){1,2}\b")
SCOPE_CALL = re.compile(r"ci_scope\.py\s+(?:.*\s)?--scope\s+(?P<scope>\S+)")


def _load(text):
    # BaseLoader keeps the GitHub key `on` from YAML 1.1 boolean coercion; every scalar stays a string.
    return yaml.load(text, Loader=yaml.BaseLoader)


def _triggers(workflow):
    trigger = workflow.get("on")
    if isinstance(trigger, str):
        return {trigger: None}
    if isinstance(trigger, list):
        return {name: None for name in trigger}
    return trigger if isinstance(trigger, dict) else {}


def _elevated(permissions):
    """Token scopes above read. `write-all` and unknown string forms are elevated as a whole."""
    if permissions is None:
        return {}
    if isinstance(permissions, str):
        return {} if permissions == "read-all" else {"*": permissions}
    return {scope: level for scope, level in permissions.items() if level not in ("read", "none")}


def check_workflow(name, text):
    """Return the invariant violations of one workflow, each as a readable line."""
    problems = []

    def fail(message):
        problems.append(f"{name}: {message}")

    try:
        workflow = _load(text)
    except yaml.YAMLError as exc:
        return [f"{name}: not valid YAML ({exc.__class__.__name__})"]
    if not isinstance(workflow, dict) or not isinstance(workflow.get("jobs"), dict) or not workflow["jobs"]:
        return [f"{name}: no jobs"]

    triggers = _triggers(workflow)
    for trigger in sorted(FORBIDDEN_TRIGGERS & set(triggers)):
        fail(f"trigger {trigger} runs with elevated context on untrusted input")
    pull_request = triggers.get("pull_request")
    runs_on_pull_request = "pull_request" in triggers

    permissions = workflow.get("permissions")
    if permissions is None:
        fail("missing top-level permissions; the default token scope must be stated")
    else:
        elevated = _elevated(permissions)
        if elevated:
            fail(f"top-level permissions grant more than read: {elevated}")

    if runs_on_pull_request or "push" in triggers:
        if "concurrency" not in workflow:
            fail("missing concurrency group for a workflow triggered by pull_request or push")

    owns_required_check = any(job.get("name") in REQUIRED_JOB_NAMES for job in workflow["jobs"].values()
                              if isinstance(job, dict))
    if owns_required_check and not runs_on_pull_request:
        fail("a workflow that owns a required check must run on pull_request, or the check never reports on one")
    if owns_required_check and isinstance(pull_request, dict):
        # A required check that never starts blocks the merge; one that is skipped reports success.
        for filter_name in ("paths", "paths-ignore", "branches-ignore", "types"):
            if filter_name in pull_request:
                fail(f"a workflow that owns a required check must not filter pull requests with {filter_name}: "
                     "the check would not start, or would not cover every pull request")
        if pull_request.get("branches", ["main"]) != ["main"]:
            fail("a workflow that owns a required check may only restrict pull requests to the main branch")

    concurrency = workflow.get("concurrency")
    if "push" in triggers and isinstance(concurrency, dict) and concurrency.get("cancel-in-progress") == "true":
        fail("concurrency must not cancel a run unconditionally in a workflow that also runs on push: a merge to "
             "main would cancel the verification of the previous one")

    for job_id, job in workflow["jobs"].items():
        where = f"job {job_id}"
        if not isinstance(job, dict):
            fail(f"{where} is not a mapping")
            continue
        timeout = job.get("timeout-minutes", "")
        if not re.fullmatch(r"[0-9]+", str(timeout)) or not 0 < int(timeout) <= MAX_TIMEOUT_MINUTES:
            fail(f"{where} needs timeout-minutes between 1 and {MAX_TIMEOUT_MINUTES}")
        if "continue-on-error" in job and job["continue-on-error"] != "false":
            fail(f"{where} must not use continue-on-error")
        if job.get("name") in REQUIRED_JOB_NAMES:
            for key in ("if", "needs"):
                if key in job:
                    fail(f"{where} owns a required check and must not use {key}: a skipped job reports success")
        runner = job.get("runs-on")
        if not isinstance(runner, str) or runner.endswith("-latest") or "${{" in runner:
            fail(f"{where} must pin its runner image, not use a floating label or an expression")
        extra = _elevated(job.get("permissions"))
        if extra and (set(extra) - ALLOWED_JOB_WRITE_SCOPES or any(level != "write" for level in extra.values())):
            fail(f"{where} requests permissions outside the allowed write scopes: {extra}")
        steps = job.get("steps", [])
        for index, step in enumerate(steps if isinstance(steps, list) else []):
            if not isinstance(step, dict):
                fail(f"{where} step {index + 1} is not a mapping")
                continue
            label = f"{where} step {index + 1} ({step.get('name') or step.get('uses') or 'unnamed'})"
            options = step.get("with") if isinstance(step.get("with"), dict) else {}
            script = step.get("run") if isinstance(step.get("run"), str) else ""
            if "continue-on-error" in step and step["continue-on-error"] != "false":
                fail(f"{label} must not use continue-on-error")
            if "${{" in script:
                fail(f"{label} interpolates an expression into a shell script; pass it through env instead")
            uses = step.get("uses") if isinstance(step.get("uses"), str) else ""
            if uses.startswith("actions/checkout@") and options.get("persist-credentials") != "false":
                fail(f"{label} must set persist-credentials: false")
            if uses.startswith("actions/upload-artifact@"):
                retention = str(options.get("retention-days", ""))
                if not re.fullmatch(r"[0-9]+", retention) or not 0 < int(retention) <= MAX_ARTIFACT_RETENTION_DAYS:
                    fail(f"{label} needs retention-days between 1 and {MAX_ARTIFACT_RETENTION_DAYS}")
            for scope in SCOPE_CALL.findall(script):
                if scope not in ci_scope.SCOPES:
                    fail(f"{label} asks tools/ci_scope.py for an unknown scope {scope!r}")

    uses_seen = 0
    for number, line in enumerate(text.splitlines(), 1):
        match = USE_LINE.match(line)
        if not match:
            continue
        uses_seen += 1
        reference = match["ref"].strip("'\"")
        if reference.startswith("./"):
            continue
        if not PINNED_USE.fullmatch(reference):
            fail(f"line {number}: {reference} is not pinned to a full commit SHA")
        elif not VERSION_COMMENT.match(match["comment"] or ""):
            fail(f"line {number}: {reference} has no version comment such as '# v1.2.3'")

    referenced_secrets = set(re.findall(r"secrets\.([A-Za-z0-9_]+)", text)) - {"GITHUB_TOKEN"}
    if referenced_secrets and runs_on_pull_request:
        allowed_secrets = PULL_REQUEST_SECRET_WORKFLOWS.get(name)
        if allowed_secrets is None:
            fail("a workflow triggered by pull_request must not read repository secrets")
        else:
            extra_secrets = referenced_secrets - allowed_secrets
            if extra_secrets:
                fail(f"a pull_request workflow may only read {sorted(allowed_secrets)}, found {sorted(extra_secrets)}")
            if not isinstance(pull_request, dict) or pull_request.get("branches") != ["main"]:
                fail("a pull_request workflow reading repository secrets must restrict pull_request branches to [main]")
            if "secrets." in str(workflow.get("env") or ""):
                fail("workflow-level env must not expose repository secrets")
            for job_id, job in workflow["jobs"].items():
                if not isinstance(job, dict):
                    continue
                if "secrets." in str(job.get("env") or ""):
                    fail(f"job {job_id} env must not expose repository secrets")
                job_steps = job.get("steps", [])
                if any("secrets." in str(step) for step in (job_steps if isinstance(job_steps, list) else [])):
                    job_if = str(job.get("if") or "")
                    if "trusted == 'true'" not in job_if:
                        fail(f"job {job_id} reads a secret on pull_request without gating on trusted == 'true'")
    return problems


def check_repository(workflows_dir, required_names=REQUIRED_JOB_NAMES):
    paths = sorted(list(workflows_dir.glob("*.yml")) + list(workflows_dir.glob("*.yaml")))
    if not paths:
        return [f"{workflows_dir}: no workflow files found"]
    problems = []
    job_names = {}
    for path in paths:
        text = path.read_text(encoding="utf-8")
        problems.extend(check_workflow(path.name, text))
        try:
            jobs = _load(text).get("jobs", {})
        except (yaml.YAMLError, AttributeError):
            continue  # reported by check_workflow
        for job in jobs.values() if isinstance(jobs, dict) else ():
            if isinstance(job, dict) and "name" in job:
                job_names.setdefault(job["name"], []).append(path.name)
    for required in required_names:
        owners = job_names.get(required, [])
        if len(owners) != 1:
            problems.append(f"required check {required!r} must be produced by exactly one job, found {owners or 'none'}")
    return problems


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workflows", type=Path, default=ROOT / ".github/workflows")
    args = parser.parse_args(argv)
    problems = check_repository(args.workflows)
    for problem in problems:
        print(f"FAIL {problem}")
    if problems:
        return 1
    count = len(list(args.workflows.glob("*.y*ml")))
    print(f"PASS workflow policy: {count} workflows, {len(REQUIRED_JOB_NAMES)} required check names produced once")
    return 0


if __name__ == "__main__":
    sys.exit(main())
