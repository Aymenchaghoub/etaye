# Future GitHub delivery rules

Status: Policy for future implementation; no feature work authorized. Repository owner Aymen Chaghoub. Target `main`, no permanent integration branch. Use `gh` or the GitHub connector, never organizational GitLab tooling.

Use short-lived `feat/<story-id>-<slug>`, `fix/<story-id>-<slug>` and `docs/<slug>` branches. Initial tooling bootstrap is on main; planning package uses docs/planning-package. Do not develop features directly on main. Before story creation/edit/review read current relevant PRD/rules/epic/UX/architecture/data/API/AI/security/test/deferred documents and dependency stories; record sources/date/version, assumptions and effect on AC. Planning backlog becomes implementation-ready only after complete design review (or explicit specific exception), Aymen's development authorization and installed BMAD readiness gate.

Group coherent English Conventional Commits. No emoji, added Co-Authored-By trailer or automatic attribution text. Retain third-party licenses/credits in files. English PR title/body lead with trigger/problem and resulting behavior, validation evidence and material limits; no fake completed features. Keep story-related docs/tracking/report changes together where practical.

Before delivery: checks appropriate to change, critical real-stack isolated tests, three-lens review (silent failures, edge cases, acceptance criteria), running-UI comparison against exact Figma references/viewport, PDF/email review as applicable. Open PR to main, require actual supported checks/review, verify exact head before merge. Do not claim branch protection/CI as enabled until configured and observed. Documentation-only PRs use documentary checks, not fabricated application results.

After a safe merge, verify the branch's work is integrated before deleting local/remote merged branches. Preserve unmerged commits/worktrees; never routine-delete unmerged work. Do not force-push or rewrite unrelated history. Keep actual progress in workflow tracking + stories/code/history/evidence and design coverage separately. Continuation note records phase, completed files, decisions/access/verification and next action.
