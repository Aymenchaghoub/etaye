<!-- bmad:context -->
<!-- Verified 2026-11-02 against bootstrap 2dba30c plus reviewed planning working tree. Shared policy authored under Aymen's explicit initial mission. Refresh against actual Git history and current planning artifacts. -->
# Étaye shared agent policy

Étaye supports consultation preparation, evidence inspection and clinician-reviewed documentation for isolated multi-practitioner practices. Spring Boot, React TypeScript and PostgreSQL are user decisions; the current repository is planning and design only. Start from docs/README.md and docs/continuation.md; architecture/data/API documents describe a proposed future application.

## Policy

- Do not scaffold, implement, migrate, deploy or activate the application pipeline before complete required design review and Aymen's explicit development authorization. Documentation/design completion does not authorize development; keep stories in planning/backlog.
- Speak French with Aymen. Write repository documents, requirements, ADRs, stories, reports and handoffs in English. Use English technical identifiers/comments and Conventional Commits/PR text. Preserve Étaye and existing French interface copy; English demo language remains a product decision.
- User decisions govern scope. docs/planning-artifacts/business-rules.md owns domain behavior; prd/prd.md owns requirements; ADRs own technical choices; verified Figma properties own existing visual values. Surface conflicts explicitly; a newer file or previous AI answer is not automatic authority. Distinguish User decision, Verified observation, Proposal, [ASSUMPTION] and To verify.
- Legacy source is read-only reference; inspect its actual current commit when needed. Never copy professional reference content, infrastructure, URLs, credentials or business rules into Étaye. Use GitHub, not organizational GitLab procedures.
- Use exclusively synthetic people/data in demo, tests, documents and screenshots. Claim no clinical validation, certification, regulatory compliance or real patient readiness without established evidence.
- Request relevant missing access promptly after checking existing connector/CLI capabilities. Distinguish credential, permission, subscription and unsupported capability; give minimum scope, current official steps and safe configuration location. Verify minimally. Never commit/log/display secrets; never activate paid services or expand permissions silently.

## Where things are and required reading

- Before creating, editing or reviewing a story, read current relevant PRD, business rules, access matrix, functional journeys, epic, UX, architecture, data/API, AI/security, test strategy, deferred register and dependency stories in full. Record consulted source versions/dates, assumptions and effects on acceptance criteria. A summary or older story is insufficient.
- Before future implementation, additionally verify the development authorization, approved exact Figma nodes/tokens, design coverage/exceptions and current implementation tracking. Read the whole story and relevant ADRs; do not infer completion from file existence.
- Planning: docs/planning-artifacts/. Design evidence/progress: docs/design/coverage.csv and handoff-claude.md. Future implementation tracking: docs/implementation-artifacts/. Test strategy/evidence: docs/test-artifacts/. Deferred work requires rationale/dependencies/impact and explicit resumption trigger.
- Shared skills are authoritative in .claude/skills/; .agents/skills is the Codex discovery symlink. Read the exact local SKILL.md and referenced files before execution. Do not mix project BMAD6.12.4 with personal preview plugins or invent command names.

## Running and verifying

- Before each BMAD workflow, resolve effective core and module config with `uv run --no-cache _bmad/scripts/resolve_config.py --project-root "$PWD" --key core` and `--key modules.bmm`; also run the workflow's resolve_customization.py invocation. Respect team/personal override layers; do not rely only on generated YAML/defaults. Paths containing {project-root} expand once.
- Node20.12+, Python3.10+ and uv are BMAD prerequisites; current verified installation is in docs/agent-setup.md. No application build/test command is verified because no application exists. Planned commands must remain labeled planned until actually executed.
- For this package run `python3 tools/validate_docs.py`; it checks documentary links/references/invariants, not application behavior. Never report documentary checks as passing application tests.

## Domain and architecture constraints

- Enforce current server-side capability plus practice/record ownership on every query/write/job/source/file/export. A route practiceId, frontend filter or stale session claim is not authorization. Administrator does not imply clinical access; portal identity links are explicit and record-local.
- Separate appointment, consultation, document review, task processing, freshness and publication states. Missing data is not normal data. Validation never automatically publishes. Clinician briefs are not patient explanations and cannot be directly published.
- Preserve source facts, generated baseline, human edits, immutable approved versions and dated citation lineage. Review that citations actually support claims. Source changes mark relevant content stale; regeneration cannot silently overwrite corrections. Publish/replace/withdraw only with exact-version/audience guards.
- Follow the modular-monolith ADRs and data/API boundaries. Transactions own RLS context, concurrency checks, audit and outbox integrity; background/external work uses scoped bounded jobs. Applied migrations remain immutable when implementation begins. Do not add brokers/microservices/vector storage without a documented need.
- Treat uploaded/record text and provider output as untrusted data. Minimize outbound data, keep deterministic AI at the external provider boundary for demo/CI, and never describe filtering/pseudonymization as guaranteed anonymity.

## Visual and delivery requirements

- Before future UI work inspect exact current Figma nodes/properties/screenshots/comments and record refs; reuse original logo/tokens. For missing designs finish and review them first, unless Aymen explicitly accepts a named exception. Do not estimate existing tokens or inherit a generic component theme.
- Open actual running-UI screenshots and compare at matching desktop/tablet/mobile viewports, including long/empty/error/restricted/focus states. Open all PDF pages and review email layouts. Functional tests/builds and visual fidelity are separate evidence requirements.
- Test critical journeys on an isolated real Spring/React/PostgreSQL/file stack, deterministic only at external AI/email/scan boundaries where appropriate. Required suites must execute with meaningful assertions; a zero-test success is failure. Preserve real evidence/limits.
- Future feature work uses short-lived feat/<story-id>-<slug> or fix/<story-id>-<slug> branches and PRs to main, never feature development directly on main. docs/<slug> is valid for documentation. Follow docs/operations/delivery-rules.md and actual supported required checks.
- Before delivery review silent failures, edge cases and acceptance-criteria compliance, plus visual fidelity. Promote validated artifacts with traceable versions; never invent CI/coverage/deploy results. Verify integration before cleaning merged branches; never delete unmerged work as routine cleanup.
- Commits are coherent English Conventional Commits without emoji, added Co-Authored-By trailers or automatic attribution text. Preserve third-party licenses and credits in appropriate files. Comments explain a constraint or reason, not obvious code.
- Before yielding record phase, completed artifacts, open choices/access, actual verification and next action in docs/continuation.md. Read it on resume, then verify current Git/files rather than treating it as proof of completion.
<!-- /bmad:context -->
