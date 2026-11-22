# Étaye

[![Documentation](https://github.com/Aymenchaghoub/etaye/actions/workflows/documentation.yml/badge.svg)](https://github.com/Aymenchaghoub/etaye/actions/workflows/documentation.yml)
[![CodeQL](https://github.com/Aymenchaghoub/etaye/actions/workflows/codeql.yml/badge.svg)](https://github.com/Aymenchaghoub/etaye/actions/workflows/codeql.yml)
[![API](https://github.com/Aymenchaghoub/etaye/actions/workflows/api.yml/badge.svg)](https://github.com/Aymenchaghoub/etaye/actions/workflows/api.yml)
[![Web](https://github.com/Aymenchaghoub/etaye/actions/workflows/web.yml/badge.svg)](https://github.com/Aymenchaghoub/etaye/actions/workflows/web.yml)

Étaye is a portfolio project for evidence-supported consultation preparation in multi-practitioner general practices. The product design lets clinicians inspect dated sources, correct AI drafts, approve an exact version and explicitly publish selected information to a patient. All examples and test data are synthetic.

## Current scope

- **Story 10.1** delivers a runnable Spring Boot, React and PostgreSQL foundation. It includes a technical readiness page, practice-scoped patient and record API operations, session and CSRF foundations, and an audit trail for access denials. [Implementation record](docs/implementation-artifacts/story-10-1-implementation-record.md)
- **Story 10.2** adds the OpenAPI-generated TypeScript client, version discovery, and the token generator for the light and dark web themes. [PR #44](https://github.com/Aymenchaghoub/etaye/pull/44) · [Implementation record](docs/implementation-artifacts/story-10-2-implementation-record.md)
- **Story 1.2** adds Web sign-in, access recovery with an optional local test inbox, and session expiry and return. [Implementation record](docs/implementation-artifacts/story-1-2-implementation-record.md)
- The consultation screens, patient portal and Expo client are planned. [Story delivery order](docs/planning-artifacts/story-delivery-order.md) · [Roadmap](docs/planning-artifacts/roadmap.md) · [Design coverage](docs/design/coverage.csv)

## Run the local demonstration

Start the Compose package with `./infra/demo/start.sh` and open `http://localhost:4179`. Choose a fictional practitioner, patient or administration scenario. The guide lists the fictional accounts, the reset procedure and the recorded Playwright walkthrough. [Local demonstration guide](infra/demo/README.md)

## Develop locally

Use Java 25, Node 26, Corepack, Python 3 and Docker. The repository pins pnpm 12.21.0 and includes a Maven wrapper. For the full stack and its generated local database credentials, follow [Running it locally](docs/implementation-artifacts/story-10-1-implementation-record.md#9-running-it-locally). The web server at `http://localhost:5173` proxies API calls to `http://127.0.0.1:8080`.

Open `/connexion` for professional access, `/connexion/patient` for patient sign-in, or `/recuperation` to request a reset link with a synthetic account. [Try local recovery](docs/implementation-artifacts/story-1-2-implementation-record.md#try-local-recovery)

Run these from the repository root to verify the current checkout:

```sh
(cd apps/api && ./mvnw -B -ntp verify)
corepack pnpm install --frozen-lockfile
corepack pnpm --filter etaye-web build
(cd apps/web && corepack pnpm exec playwright install chromium && corepack pnpm test:smoke)
python3 tools/validate_docs.py
```

The API suite uses PostgreSQL through Testcontainers. Browser smoke tests exercise readiness and authentication against controlled responses. The separate real-stack tests exercise recovery with a disposable synthetic account and stop and pause an isolated database container. The Python validator checks the documentation package. [Test strategy](docs/test-artifacts/test-strategy.md) · [GitHub controls](docs/operations/github-security.md)

## Project references

- [Documentation index](docs/README.md) and [session continuation](docs/continuation.md)
- [Product requirements](docs/planning-artifacts/prd/prd.md), [business rules](docs/planning-artifacts/business-rules.md) and [API contract](docs/contracts/README.md)
- [Figma identity](https://www.figma.com/design/vfh4BSnbRAbaDE1q0CyssL/Etaye?node-id=0-1) and [offline design reference](design/offline-reference/README.md)
- [Agent policy](AGENTS.md), [verified tooling](docs/agent-setup.md) and [third-party notices](THIRD_PARTY_NOTICES.md)

## Architecture

The overview shows the planned web and mobile clients, Spring Boot modules, PostgreSQL and external services.

[![Planned Étaye system architecture](docs/architecture-views/overview.svg)](https://aymenchaghoub.github.io/etaye/)

[Explore the five interactive views](https://aymenchaghoub.github.io/etaye/) · [Diagram sources and local viewing](docs/architecture-views/README.md)
