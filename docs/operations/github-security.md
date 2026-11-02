# GitHub security and initial CI

Updated2026-11-02. Aymen explicitly authorized making etaye public, initial documentation CI/first PR, and useful free security/quality features. Application implementation remains forbidden. Never enable a paid trial, billing plan or unbounded paid AI usage under this authorization.

## Planned and observed controls

The exact final remote settings and first run/PR evidence are recorded below after verification. A local file is configuration, not proof that GitHub executed it.

- Documentation CI: typed contract validation, links/anchors/fixture/traceability/asset checks, BMAD spine lint, Ruff fatal/undefined-name checks and full-history Gitleaks CLI. Python dependencies and scanner checksums pinned; no app build or fake coverage.
- CodeQL advanced setup: Python analysis limited to first-party tools; security-extended query suite. Full SHA-pinned actions; contents:read by default and security-events:write only for the analyzer.
- Dependabot alerts/security updates and weekly GitHub Actions/Python-tool update configuration; dependency graph public-repository support.
- Main branch rules target PRs, actual required checks, conversation resolution, no force push/deletion; solo workflow does not demand an impossible self-approval. Existing branch work must be preserved.
- GitHub secret scanning/push protection and private vulnerability reporting to be enabled and read back after public transition. These are separate from Gitleaks; neither proves all credentials impossible to leak.
- PR template and SECURITY/CONTRIBUTING policies; actual third-party licenses retained. No app-wide license is invented merely because the repository is public.

## AI review access

[CodeRabbit's current public-repository offer](https://www.coderabbit.ai/oss) provides free reviews; [official setup](https://docs.coderabbit.ai/getting-started/quickstart) uses a GitHub App installation. No CodeRabbit connector was available and `gh` OAuth cannot list/grant GitHub App installations (403); that is an app-authorization capability, not a missing deployment/API key. Aymen was asked to install [coderabbitai](https://github.com/apps/coderabbitai) for **etaye only**. The public app metadata reports fixed permissions including contents/workflows/PR/issues/checks/status write and actions/metadata/discussion/member read. This was disclosed; do not silently grant access to other personal or professional repositories. No card/paid plan needed for this public-review offer; paid agents/usage-based scans remain off.

Installation and a real review must be verified before claiming AI review active. If not installed, maintain explicit pending access and complete independent CI work. Copilot Free does not include GitHub PR code review; no paid Copilot plan or credit-consuming automatic review was activated. [Current Copilot plans](https://docs.github.com/en/copilot/get-started/plans). The old free Gemini consumer GitHub review service is shut down; it is not a valid alternative now. [Official sunset](https://developers.google.com/gemini-code-assist/docs/deprecations/consumer-code-review).

## Official capability evidence

Consulted2026-11-02: [public CodeQL availability](https://docs.github.com/en/code-security/concepts/code-scanning/code-scanning), [security products/free features](https://docs.github.com/en/get-started/learning-about-github/about-github-advanced-security), [ruleset availability](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets), [secure workflows](https://docs.github.com/en/actions/reference/security/secure-use). Standard hosted runners for public repositories are currently free under [GitHub Actions billing](https://docs.github.com/en/billing/concepts/product-billing/github-actions); do not infer unlimited storage or paid AI entitlement.

## Remote execution record

Pending actual first-PR run and settings read-back; update with API results before final delivery. No runtime application tests, deployments, real-model evaluations or design approvals are claimed.
