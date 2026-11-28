# Étaye

[![Documentation](https://github.com/Aymenchaghoub/etaye/actions/workflows/documentation.yml/badge.svg)](https://github.com/Aymenchaghoub/etaye/actions/workflows/documentation.yml)
[![CodeQL](https://github.com/Aymenchaghoub/etaye/actions/workflows/codeql.yml/badge.svg)](https://github.com/Aymenchaghoub/etaye/actions/workflows/codeql.yml)
[![API](https://github.com/Aymenchaghoub/etaye/actions/workflows/api.yml/badge.svg)](https://github.com/Aymenchaghoub/etaye/actions/workflows/api.yml)
[![Web](https://github.com/Aymenchaghoub/etaye/actions/workflows/web.yml/badge.svg)](https://github.com/Aymenchaghoub/etaye/actions/workflows/web.yml)

Étaye is a portfolio project for evidence-supported consultation preparation in multi-practitioner general practices. The product design lets clinicians inspect dated sources, correct AI drafts, approve an exact version and explicitly publish selected information to a patient. All examples and test data are synthetic.

## Current scope

Eleven stories of increment 1 are merged, listed in merge order. The [API guide](docs/contracts/README.md#runtime-coverage) lists the operations served so far.

- **Story 10.1** delivers a runnable Spring Boot, React and PostgreSQL foundation. It includes a technical readiness page, practice-scoped patient and record API operations, session and CSRF foundations, and an audit trail for access denials. [PR #33](https://github.com/Aymenchaghoub/etaye/pull/33) · [Implementation record](docs/implementation-artifacts/story-10-1-implementation-record.md)
- **Story 10.2** adds the OpenAPI-generated TypeScript client, version discovery, and the token generator for the light and dark web themes. [PR #44](https://github.com/Aymenchaghoub/etaye/pull/44) · [Implementation record](docs/implementation-artifacts/story-10-2-implementation-record.md)
- **Story 1.2** adds Web sign-in, access recovery with an optional local test inbox, and session expiry and return. [PR #45](https://github.com/Aymenchaghoub/etaye/pull/45) · [Implementation record](docs/implementation-artifacts/story-1-2-implementation-record.md)
- **Story 1.1** adds activation of a team or patient-portal invitation, which creates the intended membership or record-local portal link. [PR #49](https://github.com/Aymenchaghoub/etaye/pull/49) · [Implementation record](docs/implementation-artifacts/story-1-1-implementation-record.md)
- **Story 1.3** adds the practice context selector and its switch, with a fresh authorization check on every selection. [PR #50](https://github.com/Aymenchaghoub/etaye/pull/50) · [Implementation record](docs/implementation-artifacts/story-1-3-implementation-record.md)
- **Story 9.1** adds the local demonstration package with three fictional journeys and a reset to a published fixture. [PR #52](https://github.com/Aymenchaghoub/etaye/pull/52) · [Implementation record](docs/implementation-artifacts/story-9-1-implementation-record.md)
- **Story 1.4** adds the guided isolation proof and an adversarial isolation matrix measured in the `API verify` job. [PR #58](https://github.com/Aymenchaghoub/etaye/pull/58) · [Implementation record](docs/implementation-artifacts/story-1-4-implementation-record.md)
- **Story 3.1** adds the practitioner shell with patient search, registration with the duplicate rule, and correction under `If-Match`. [PR #59](https://github.com/Aymenchaghoub/etaye/pull/59) · [Implementation record](docs/implementation-artifacts/story-3-1-implementation-record.md)
- **Story 3.2** adds read-only exploration of a record: the dated chronology, explicit assertions, sources and a view at a past instant. [PR #68](https://github.com/Aymenchaghoub/etaye/pull/68) · [Implementation record](docs/implementation-artifacts/story-3-2-implementation-record.md)
- **Story 3.3** adds measurement readings, eligible trends and the correction history. [PR #71](https://github.com/Aymenchaghoub/etaye/pull/71) · [Implementation record](docs/implementation-artifacts/story-3-3-implementation-record.md)
- **Story 3.4** adds laboratory results with supplied intervals, dated sources and the correction history. [PR #105](https://github.com/Aymenchaghoub/etaye/pull/105) · [Implementation record](docs/implementation-artifacts/story-3-4-implementation-record.md)
- The consultation workspace, documents and publication, the patient portal pages and the Expo client are planned. [Story delivery order](docs/planning-artifacts/story-delivery-order.md) · [Roadmap](docs/planning-artifacts/roadmap.md) · [Design coverage](docs/design/coverage.csv)

## Run the local demonstration

Start the Compose package with `./infra/demo/start.sh` and open `http://localhost:4179`. Follow the guided routes for account activation and recovery, two isolated practices, patient registration and identity correction, dated record evidence, and the platform checks. The record route includes measurements and laboratory results. **Prouvez l’isolation** replays the existing access checks against the running API. All people and records are fictional. [Local demonstration guide](infra/demo/README.md) · [Baseline coverage audit](docs/implementation-artifacts/local-demo-coverage.md)

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

The overview shows the web and mobile clients, Spring Boot modules, PostgreSQL and external services. A node marked **Built** or **Partly** has code on `main`; every other node is planned.

[![Étaye system architecture, built and planned](docs/architecture-views/overview.svg)](https://aymenchaghoub.github.io/etaye/)

[Explore the five interactive views](https://aymenchaghoub.github.io/etaye/) · [Diagram sources and local viewing](docs/architecture-views/README.md)
