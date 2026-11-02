# GitHub security and initial CI

Updated: 2026-11-02. Aymen explicitly authorized making etaye public, initial documentation CI/first PR, and useful free security/quality features. Application implementation remains forbidden. Never enable a paid trial, billing plan or unbounded paid AI usage under this authorization.

## Planned and observed controls

The exact final remote settings and first run/PR evidence are recorded below after verification. A local file is configuration, not proof that GitHub executed it.

- Documentation CI: typed contract validation, links/anchors/fixture/traceability/asset checks, BMAD spine lint, Ruff fatal/undefined-name checks and full-history Gitleaks CLI. Python dependencies and scanner checksums pinned; no app build or fake coverage.
- CodeQL advanced setup: Python analysis limited to first-party tools; security-extended query suite. Full SHA-pinned actions; contents:read by default and security-events:write only for the analyzer.
- Dependabot alerts/security updates and weekly GitHub Actions/Python-tool update configuration; dependency graph public-repository support.
- Main branch rules target PRs, actual required checks, conversation resolution, no force push/deletion; solo workflow does not demand an impossible self-approval. Existing branch work must be preserved.
- GitHub secret scanning/push protection and private vulnerability reporting enabled and verified after public transition. These are separate from Gitleaks; neither proves all credentials impossible to leak.
- PR template and SECURITY/CONTRIBUTING policies; actual third-party licenses retained. No app-wide license is invented merely because the repository is public.

## AI review access

[CodeRabbit's current public-repository offer](https://www.coderabbit.ai/oss) provides free reviews; [official setup](https://docs.coderabbit.ai/getting-started/quickstart) uses a GitHub App installation. No CodeRabbit connector was available and `gh` OAuth cannot list/grant GitHub App installations (403); that is an app-authorization capability, not a missing deployment/API key. Aymen was asked to install [coderabbitai](https://github.com/apps/coderabbitai) for **etaye only** and confirmed installation. A manual full-review request received a [real bot acknowledgement](https://github.com/Aymenchaghoub/etaye/pull/1#issuecomment-6585677680) and a completed [review with nine inline findings](https://github.com/Aymenchaghoub/etaye/pull/1#pullrequestreview-5741890622). This verifies repository review access; verified dispositions are recorded in docs/review-report.md. The run reported plan label Advanced; that label alone is not a billing record. The official public-repository offer explicitly requires neither application nor card. The public app metadata reports fixed permissions including contents/workflows/PR/issues/checks/status write and actions/metadata/discussion/member read. This was disclosed; do not silently grant access to other personal or professional repositories. No card/paid plan needed for this public-review offer; paid agents/usage-based scans remain off.

The committed .coderabbit.yaml was validated against the current official JSON schema. It points to AGENTS.md, uses English review text, preserves French UI copy, disables automatic approvals/code-generation finishing actions/post-merge actions and treats AI advice separately from required CI checks. No paid agent or deep-scan command was invoked. Copilot Free does not include GitHub PR code review; no paid Copilot plan was activated by this setup. An existing automatic Copilot request on PR1 returned an insufficient-quota message, not a completed review. [Current Copilot plans](https://docs.github.com/en/copilot/get-started/plans). The old free Gemini consumer GitHub review service is shut down; it is not a valid alternative now. [Official sunset](https://developers.google.com/gemini-code-assist/docs/deprecations/consumer-code-review).

An existing Qodo integration also reacted automatically to PR1, producing a summary. This is not proof of a completed independent review or a permanently free entitlement. [Current Qodo pricing](https://www.qodo.ai/pricing/) has a trial/credit model; qualified open-source projects can apply for free access. Aymen was asked to verify the existing account entitlement; no subscription, overage setting or paid review request was enabled here. Do not add Qodo as a required check or promise unlimited free usage. CodeRabbit is the selected free reviewer. The repository-level .pr_agent.toml sets pr_commands/push_commands empty and handle_push_trigger=false to suppress redundant Qodo automation for subsequent PRs after merge, following the [official command reference](https://docs.qodo.ai/configuration/configuration-and-command-reference). This does not uninstall or alter the account subscription and is not claimed to stop the already-running PR1 request. Resume automatic Qodo only after explicit selection and verified free entitlement.

## Official capability evidence

Consulted: 2026-11-02: [public CodeQL availability](https://docs.github.com/en/code-security/concepts/code-scanning/code-scanning), [security products/free features](https://docs.github.com/en/get-started/learning-about-github/about-github-advanced-security), [ruleset availability](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets), [secure workflows](https://docs.github.com/en/actions/reference/security/secure-use). Standard hosted runners for public repositories are currently free under [GitHub Actions billing](https://docs.github.com/en/billing/concepts/product-billing/github-actions); do not infer unlimited storage or paid AI entitlement.

## Remote execution record

Verified through authenticated GitHub API read-back on 2026-11-02:

- Repository visibility is public, under Aymenchaghoub/etaye.
- Secret scanning, push protection and Dependabot security updates enabled; Dependabot alerts enable request and subsequent read-back both returned 204. Automated fixes read-back: enabled=true, paused=false.
- Private vulnerability reporting: enabled=true.
- Actions: enabled; selected actions only (GitHub-owned actions and astral-sh/setup-uv); verified marketplace actions are not broadly allowed. Full commit SHA pinning is required.
- Default GITHUB_TOKEN permissions: read; workflow pull-request approval permission disabled.
- Squash merging only; automatic deletion of merged head branches enabled.
- Local full-history Gitleaks 8.30.1 scan: no leaks. 38 generic-pattern candidates were individually verified as public Figma identifiers/publishing keys and an upstream BMAD document SHA256. .gitleaks.toml keeps defaults and restricts exceptions to exact verified values AND paths; it does not exclude whole asset/vendor directories.
- Active [main ruleset 26390376](https://github.com/Aymenchaghoub/etaye/rules/26390376): PR required, resolved review threads, linear history, no force push/deletion and no bypass actors. Zero mandatory approvals accommodates a solo maintainer; this does not waive documented review evidence.
- Required checks are bound to their actual GitHub apps: Planning package checks and Analyze tooling (GitHub Actions 15368), plus CodeQL results (GitHub Advanced Security 57789). Strict up-to-date checks enabled. The results check blocks errors/high/critical security findings under GitHub's default threshold; the analyzer job alone is not treated as a quality gate.
- [First documentation run 41505033436](https://github.com/Aymenchaghoub/etaye/actions/runs/41505033436) and [CodeQL run 41505033501](https://github.com/Aymenchaghoub/etaye/actions/runs/41505033501) succeeded on ed96cabb065ce3b5c32daac1cbe2c0eca6f34b2c. CodeQL results check succeeded; open code-scanning alerts for refs/pull/1/merge returned an empty array. Retained documentary evidence is attached to the run, with seven-day retention. See [PR1](https://github.com/Aymenchaghoub/etaye/pull/1) for current-head check/merge status.

Generic-pattern and partner-validity options were not enabled: their current individual setup documentation requires organization-owned repositories with Secret Protection. Full-history Gitleaks provides the separate generic-pattern scan without a paid entitlement. [Generic patterns](https://docs.github.com/en/code-security/how-tos/secure-your-secrets/detect-secret-leaks/enabling-secret-scanning-for-generic-patterns), [partner validity checks](https://docs.github.com/en/code-security/how-tos/secure-your-secrets/customize-leak-detection/enable-validity-checks).

No runtime application tests, deployments, real-model evaluations or design approvals are claimed.
