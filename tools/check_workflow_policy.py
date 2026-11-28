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

# Job names that the ruleset on main requires (plus `Package verify`, prepared for promotion alongside the
# eight active checks). The ruleset also requires `CodeQL`, which GitHub code scanning reports itself. Keep this
# list in step with the ruleset: a renamed job would leave a required check that never reports, which blocks every merge.
REQUIRED_JOB_NAMES = (
    "Planning package checks",
    "Analyze tooling",
    "Analyze API (Java)",
    "Analyze Web (TypeScript)",
    "API verify",
    "Web verify",
    "Dependency review",
    "Package verify",
)
# Token scopes a job may request above read. `contents: write` is deliberately absent.
ALLOWED_JOB_WRITE_SCOPES = {"security-events", "pages", "id-token", "attestations"}
RELEASE_REF_GUARD = "github.ref == 'refs/heads/main' && github.ref_protected"
RELEASE_PUBLISH_PERMISSIONS = {
    "contents": "read", "packages": "write", "attestations": "write", "id-token": "write",
}
RELEASE_INPUTS = {
    "source_sha": {"type": "string", "required": "true"},
    "publish": {"type": "boolean", "default": "false"},
}

MAX_TIMEOUT_MINUTES = 30
MAX_ARTIFACT_RETENTION_DAYS = 30
FORBIDDEN_TRIGGERS = {"pull_request_target", "workflow_run"}
# Only the dedicated non-required SonarQube workflow may read SONAR_TOKEN on pull_request (guarded by preflight).
PULL_REQUEST_ALLOWED_CONTEXTS = {"sonarqube.yml": frozenset({"SONAR_TOKEN"})}
RESTRICTED_CONTEXT_REF = re.compile(
    r"\bsecrets(?:\.([A-Za-z0-9_]+)|\[['\"]([A-Za-z0-9_]+)['\"]\])"
)
PINNED_USE = re.compile(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_./-]+@[0-9a-f]{40}")
VERSION_COMMENT = re.compile(r"v?\d+(?:\.\d+){1,2}\b")


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
    if not isinstance(permissions, dict):
        return {} if permissions == "read-all" else {"*": permissions}
    return {scope: level for scope, level in permissions.items() if level not in ("read", "none")}


def _yaml_values(mapping, name):
    if isinstance(mapping, yaml.MappingNode):
        for key, value in mapping.value:
            if key.value == name:
                yield value


def _action_refs(syntax):
    # Read the scalar and its source location from YAML. A textual search misses values in flow mappings.
    for jobs in _yaml_values(syntax, "jobs"):
        if not isinstance(jobs, yaml.MappingNode):
            continue
        for _, job in jobs.value:
            yield from _yaml_values(job, "uses")
            for steps in _yaml_values(job, "steps"):
                if isinstance(steps, yaml.SequenceNode):
                    for step in steps.value:
                        yield from _yaml_values(step, "uses")


def _static_scope_calls(script):
    for line in script.replace("\\\n", "").splitlines():
        arguments = iter(line.partition("ci_scope.py")[2].split())
        for argument in arguments:
            if argument == "--scope":
                yield next(arguments, "")


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
    release_rehearsal = name == "release.yml"
    if release_rehearsal:
        dispatch = triggers.get("workflow_dispatch") or {}
        inputs = dispatch.get("inputs", {}) if isinstance(dispatch, dict) else {}
        input_contract = {
            name: {key: value for key, value in spec.items() if key != "description"}
            for name, spec in inputs.items() if isinstance(spec, dict)
        }
        if set(triggers) != {"workflow_dispatch"} or input_contract != RELEASE_INPUTS:
            fail("release must use only manual workflow_dispatch with an exact SHA and opt-in publication")
        if set(workflow["jobs"]) != {"candidate", "publish"}:
            fail("release must have separate candidate and publication jobs")
    demo_delivery = name == "deploy-demo.yml"
    if demo_delivery:
        dispatch = triggers.get("workflow_dispatch") or {}
        inputs = dispatch.get("inputs", {}) if isinstance(dispatch, dict) else {}
        expected_inputs = {
            "release_run": {"type": "string", "required": "true"},
            "source_sha": {"type": "string", "required": "true"},
            "stage": {"type": "boolean", "default": "false"},
            "migration_receipt": {"type": "string", "required": "false"},
        }
        input_contract = {
            key: {field: value for field, value in spec.items() if field != "description"}
            for key, spec in inputs.items() if isinstance(spec, dict)
        }
        if set(triggers) != {"workflow_dispatch"} or input_contract != expected_inputs:
            fail("demo delivery must be manual with a selected release and opt-in staging")
        if workflow.get("concurrency") != {"group": "etaye-demo-delivery", "cancel-in-progress": "false"}:
            fail("demo delivery must be serialized without cancelling an active deployment")
        if set(workflow["jobs"]) != {"verify", "azure"}:
            fail("demo delivery must isolate release verification and Azure federation")

    permissions = workflow.get("permissions")
    if demo_delivery and permissions != {"contents": "read"}:
        fail("demo delivery requires top-level contents read")
    if release_rehearsal and permissions != {"contents": "read"}:
        fail("release must grant only contents: read at workflow level")
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
        publication = release_rehearsal and job_id == "publish"
        allowed_write = ALLOWED_JOB_WRITE_SCOPES | ({"packages"} if publication else set())
        if extra and (set(extra) - allowed_write or any(level != "write" for level in extra.values())):
            fail(f"{where} requests permissions outside the allowed write scopes: {extra}")
        if release_rehearsal:
            guard = RELEASE_REF_GUARD + (" && inputs.publish" if publication else "")
            if job.get("if") != guard:
                fail(f"{where} must only run on protected main with the expected publication gate")
            if publication:
                if job.get("needs") != "candidate" or job.get("environment") != "ghcr-release":
                    fail(f"{where} requires the verified candidate and protected publishing environment")
                if job.get("permissions") != RELEASE_PUBLISH_PERMISSIONS:
                    fail(f"{where} must request exactly the narrowly scoped publishing permissions")
            elif job.get("permissions") != {"contents": "read", "actions": "read"} or "environment" in job:
                fail(f"{where} must be read-only without an environment")
        if demo_delivery:
            if job.get("if") != RELEASE_REF_GUARD:
                fail(f"{where} must only run on protected main")
            if job_id == "azure":
                if job.get("environment") != "demo" or job.get("needs") != "verify":
                    fail(f"{where} requires verified release evidence and the demo environment")
                if job.get("permissions") != {"contents": "read", "id-token": "write"}:
                    fail(f"{where} must scope OIDC to the verified demo job")
            elif job.get("permissions") != {"contents": "read", "actions": "read", "attestations": "read"} or "environment" in job:
                fail(f"{where} must be read-only without an environment")
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
            for scope in _static_scope_calls(script):
                if scope not in ci_scope.SCOPES:
                    fail(f"{label} asks tools/ci_scope.py for an unknown scope {scope!r}")

    lines = text.split("\n")
    syntax = yaml.compose(text, Loader=yaml.BaseLoader)
    for action in _action_refs(syntax):
        number = action.start_mark.line + 1
        if not isinstance(action, yaml.ScalarNode):
            fail(f"line {number}: uses must be a scalar action reference")
            continue
        reference = action.value
        if reference.startswith("./"):
            continue
        if not PINNED_USE.fullmatch(reference):
            fail(f"line {number}: {reference} is not pinned to a full commit SHA")
        else:
            end = action.end_mark
            suffix = lines[end.line][end.column:] if end.line < len(lines) else ""
            _, _, comment = suffix.partition("#")
            if not VERSION_COMMENT.match(comment.lstrip()):
                fail(f"line {number}: {reference} has no version comment such as '# v1.2.3'")

    declared_refs = _restricted_context_refs(text)
    if declared_refs and demo_delivery:
        fail("demo delivery uses OIDC and must not read repository secrets")
    if declared_refs and release_rehearsal:
        fail("release must not read repository secrets")
    if declared_refs and runs_on_pull_request:
        permitted_refs = PULL_REQUEST_ALLOWED_CONTEXTS.get(name)
        if permitted_refs is None:
            fail("a workflow triggered by pull_request must not read repository secrets")
        else:
            unexpected_refs = declared_refs - permitted_refs
            if unexpected_refs:
                fail(f"a pull_request workflow may only read {sorted(permitted_refs)}, found {sorted(unexpected_refs)}")
            if not isinstance(pull_request, dict) or pull_request.get("branches") != ["main"]:
                fail("a pull_request workflow reading repository secrets must restrict pull_request branches to [main]")
            if _restricted_context_refs(workflow.get("env")):
                fail("workflow-level env must not expose repository secrets")
            for job_id, job in workflow["jobs"].items():
                if not isinstance(job, dict):
                    continue
                if _restricted_context_refs(job.get("env")):
                    fail(f"job {job_id} env must not expose repository secrets")
                if job_id == "preflight" and _restricted_context_refs(job):
                    fail("job preflight must not reference repository secrets")
                job_steps = job.get("steps", [])
                if _restricted_context_refs(job_steps if isinstance(job_steps, list) else []):
                    job_if = str(job.get("if") or "")
                    clauses = [part.strip() for part in job_if.split("&&")]
                    if (
                        "||" in job_if
                        or "always()" in job_if
                        or "needs.preflight.outputs.trusted == 'true'" not in clauses
                    ):
                        fail(f"job {job_id} reads a secret on pull_request without gating on trusted == 'true'")
    return problems


def _restricted_context_refs(value):
    text = str(value or "")
    refs = {
        match.group(1) or match.group(2)
        for match in RESTRICTED_CONTEXT_REF.finditer(text)
    }
    refs = {ref for ref in refs if ref.upper() != "GITHUB_TOKEN"}
    if "tojson(secrets)" in text.lower():
        refs.add("toJSON(secrets)")
    return refs


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
    print(f"PASS workflow policy: {count} workflows, {len(REQUIRED_JOB_NAMES)} required workflow job names produced once; CodeQL is reported by GitHub code scanning")
    return 0


if __name__ == "__main__":
    sys.exit(main())
