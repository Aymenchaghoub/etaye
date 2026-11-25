#!/usr/bin/env python3
"""Decide which verification jobs a change needs, failing closed.

Every path of a change is classified once, here, instead of in hand-kept lists inside each workflow:

* a path that a verification consumes names the scopes it affects;
* a path known not to be consumed by any verification is ignored (documentation, planning, agent tooling);
* every other path is unknown and triggers every scope, so a new input can only cause extra verification,
  never a skipped one.

A required check must still start on every pull request, so the workflows always run their job and use this
decision to skip only the heavy steps.
"""
import argparse
import re
import subprocess
import sys
from pathlib import PurePosixPath

SCOPES = ("api", "web", "package", "pnpm")
ALL = frozenset(SCOPES)
NONE = frozenset()

_WEB_TOKEN_INPUTS = (
    "design/tokens/adr-014.json",
    "design/received/2026-11-03/design-system/tokens.json",
    "design/received/2026-11-03/export/etaye-design-system/tokens/tokens.css",
    "design/received/2026-11-03/export/etaye-design-system/fonts/**",
    "design/received/2026-11-03/licenses/*-OFL.txt",
    "design/received/2026-11-03/licenses/Lucide-LICENSE.txt",
    "design/received/2026-11-03/icons.css",
    "design/offline-reference/site/logos/**",
    "docs/contracts/openapi.yaml",
    "docs/references/hey-api-license.md",
    "tools/generate_web_tokens.py",
    "tools/test_generate_web_tokens.py",
    "tools/fixtures/etaye-tokens.golden.css",
)

# Ordered, first match wins. The pattern syntax is Git-like: `*` stays inside a path segment, `**` crosses
# segments. Keep a more specific rule before the broader one it refines.
RULES = (
    # The classifier and its tests drive every decision.
    ("tools/ci_scope.py", ALL),
    ("tools/test_ci_scope.py", ALL),
    # Workflow files verify themselves.
    (".github/workflows/api.yml", frozenset({"api"})),
    (".github/workflows/web.yml", frozenset({"web"})),
    (".github/workflows/package.yml", frozenset({"package"})),
    (".github/workflows/dependency-review.yml", frozenset({"pnpm"})),
    # Evidence validators run by the verification jobs.
    ("tools/install_corepack.sh", frozenset({"web", "package", "pnpm"})),
    ("tools/run_package_smoke.sh", frozenset({"package"})),
    ("tools/run_image_scan.sh", frozenset({"package"})),
    ("tools/assert_test_reports.py", frozenset({"api", "web", "package"})),
    ("tools/test_assert_test_reports.py", frozenset({"api", "web", "package"})),
    ("tools/assert_coverage_reports.py", frozenset({"api", "web"})),
    ("tools/test_assert_coverage_reports.py", frozenset({"api", "web"})),
    ("tools/assert_changed_line_coverage.py", frozenset({"api", "web"})),
    ("tools/test_assert_changed_line_coverage.py", frozenset({"api", "web"})),
    ("tools/assert_isolation_matrix.py", frozenset({"api"})),
    ("tools/test_assert_isolation_matrix.py", frozenset({"api"})),
    # The manifest is read by the API matrix test and by a Web unit suite.
    ("apps/api/src/test/resources/isolation/matrix-cases.json", frozenset({"api", "web"})),
    # Applications. The delivery package builds both from source.
    # Tests are skipped when the delivery package builds the API (-Dmaven.test.skip=true).
    ("apps/api/src/test/**", frozenset({"api"})),
    ("apps/api/**", frozenset({"api", "package"})),
    ("apps/web/package.json", frozenset({"web", "package", "pnpm"})),
    ("apps/web/**", frozenset({"web", "package"})),
    ("apps/*/package.json", frozenset({"web", "package", "pnpm"})),
    ("packages/*/package.json", frozenset({"web", "package", "pnpm"})),
    # Workspace manifests and TypeScript base configuration.
    ("package.json", frozenset({"web", "package", "pnpm"})),
    ("pnpm-workspace.yaml", frozenset({"web", "package", "pnpm"})),
    ("pnpm-lock.yaml", frozenset({"web", "package", "pnpm"})),
    ("tsconfig.json", frozenset({"web", "package"})),
    ("tsconfig.base.json", frozenset({"web", "package"})),
    # Design and contract inputs that the Web build and its tests read.
    *((path, frozenset({"web", "package"})) for path in _WEB_TOKEN_INPUTS),
    # Delivery package.
    ("infra/**", frozenset({"package"})),
    (".dockerignore", frozenset({"package"})),
    # Line-ending and diff attributes change how every other file is checked out.
    (".gitattributes", ALL),
    # Known not to be consumed by any API, Web, package or pnpm verification.
    (".github/**", NONE),
    ("docs/**", NONE),
    ("design/**", NONE),
    ("tools/**", NONE),
    ("_bmad/**", NONE),
    (".claude/**", NONE),
    (".agents/**", NONE),
    (".codex/**", NONE),
    ("reports/**", NONE),
    (".coderabbit.yaml", NONE),
    (".pr_agent.toml", NONE),
    (".gitleaks.toml", NONE),
    (".gitignore", NONE),
    ("*.md", NONE),
    ("LICENSE*", NONE),
)


def compile_glob(pattern):
    parts = []
    index = 0
    while index < len(pattern):
        if pattern.startswith("**/", index):
            parts.append("(?:.*/)?")  # as in Git, `**/` also matches zero directories
            index += 3
        elif pattern.startswith("**", index):
            parts.append(".*")
            index += 2
        elif pattern[index] == "*":
            parts.append("[^/]*")
            index += 1
        elif pattern[index] == "?":
            parts.append("[^/]")
            index += 1
        else:
            parts.append(re.escape(pattern[index]))
            index += 1
    return re.compile("".join(parts))


_COMPILED = tuple((compile_glob(pattern), pattern, scopes) for pattern, scopes in RULES)


def classify_path(path):
    """Return (scopes, rule). A path no rule names is unknown and affects every scope."""
    normalized = PurePosixPath(path).as_posix()
    for regex, pattern, scopes in _COMPILED:
        if regex.fullmatch(normalized):
            return scopes, pattern
    return ALL, None


def decide(paths):
    """Map each scope to (relevant, triggering paths with their rule). An empty change is not trusted."""
    if not paths:
        return {scope: (True, [("(no changed path was listed)", None)]) for scope in SCOPES}
    decision = {scope: (False, []) for scope in SCOPES}
    for path in paths:
        scopes, rule = classify_path(path)
        for scope in scopes:
            decision[scope][1].append((path, rule))
    return {scope: (bool(triggers), triggers) for scope, (_, triggers) in decision.items()}


def changed_paths(base, head, runner=subprocess.run):
    """Names of added, modified and deleted files; a rename counts as a deletion plus an addition."""
    result = runner(
        ["git", "diff", "--name-only", "--no-renames", "-z", "--end-of-options", base, head],
        capture_output=True, check=False,
    )
    if result.returncode != 0:
        raise RuntimeError(f"git diff {base} {head} exited with {result.returncode}")
    return [name.decode("utf-8", "surrogateescape") for name in result.stdout.split(b"\0") if name]


def run(scope, event, base, head, runner=subprocess.run):
    """Return (relevant, reason) for one scope."""
    if scope not in SCOPES:
        raise ValueError(f"unknown scope {scope!r}; expected one of {', '.join(SCOPES)}")
    if event not in ("pull_request", "push"):
        return True, f"{event or 'unknown event'}: a manual or scheduled run verifies everything"
    try:
        paths = changed_paths(base, head, runner)
    except (RuntimeError, OSError) as exc:
        return True, f"change list unavailable ({exc}); verifying everything"
    relevant, triggers = decide(paths)[scope]
    if not relevant:
        return False, f"{len(paths)} changed path(s), none consumed by the {scope} verification"
    shown = ", ".join(path if rule else f"{path} (unclassified)" for path, rule in triggers[:5])
    more = f" and {len(triggers) - 5} more" if len(triggers) > 5 else ""
    return True, f"{shown}{more}"


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scope", required=True, choices=SCOPES)
    parser.add_argument("--event", default="")
    parser.add_argument("--base", default="HEAD^1")
    parser.add_argument("--head", default="HEAD")
    parser.add_argument("--github-output", help="append relevant=<bool> to this file")
    args = parser.parse_args(argv)
    relevant, reason = run(args.scope, args.event, args.base, args.head)
    print(f"{args.scope} verification needed: {str(relevant).lower()} ({reason})")
    if args.github_output:
        with open(args.github_output, "a", encoding="utf-8") as output:
            output.write(f"relevant={str(relevant).lower()}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
