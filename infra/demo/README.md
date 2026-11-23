# Étaye · Local demonstration

Explore access to a fictional practice through three accounts: practitioner, patient and administration. The package includes a second isolated practice and a reset to the published fixture version.

## Run

From the repository root, run `./infra/demo/start.sh`, then open http://localhost:4179. Docker with Compose v2 and OpenSSL are required. The first run downloads and builds the images. The static entry page waits for the real database readiness signal.

The script creates a private `infra/demo/.env` with four randomly generated database credentials and starts the Compose stack. Subsequent runs can use `docker compose -f infra/demo/compose.yml up -d`. Only the web port is published on loopback. Keep the single API replica shipped in the Compose configuration. Stop the stack with `docker compose -f infra/demo/compose.yml down`.

## Fictional sign-in

Shared synthetic password for the following local accounts: `Etaye-Jeu-Fictif-91!`.

| Scenario | Identifier |
|---|---|
| Dr Nora Lefebvre, practitioner | `nora.lefebvre@example.test` |
| Claire Martin, patient | `claire.martin@example.test` |
| Mila Dubois, administration | `mila.dubois@example.test` |
| Demo operator | `operateur@example.test` |

Choose a scenario, review its limits and sign in. The scenario stops at the current account or practice destination. The demo offers no clinical data entry, provider calls, external email, medical advice or real care.

To reset, sign in as the demo operator at `/connexion`, return to `/demonstration` and confirm **Réinitialiser la démonstration**. It reloads `etaye-design-v1`, increments its generation and closes existing sessions. Wait 30 seconds between resets. The operator has no practice or patient grant.

The seeded invitation tokens use `etaye-fictional-invitation-` followed by the invitation alias in the [synthetic dataset](../../docs/design/synthetic-data.md). To try team activation use `INV-TEAM-01` and the fictional name Alex Rivière. For the pending portal activation (`INV-PORTAL-03`), use Marie Collet's fictional date of birth from the dataset.

## Prove isolation

On the showcase, open **Prouvez l’isolation** after the scenario list. The guide walks through four real refusals against the running API: Nora reads an authorized record, Nora requests a record outside her practice (one generic *introuvable* page), Mila (administration, no clinical capability) is refused in the practice she can see, and Claire (patient) gets the unavailable page in her own layout. Start with **Commencer avec Nora**; the guide links **Mila** and **Claire** sign in as those accounts and return to the same read. Every result is the actual HTTP answer. The same cases are measured in CI (`api-isolation-matrix` artifact of `API verify`).

## Scripted journey

Use a fresh disposable stack for the walkthrough. With the demo running, run `corepack pnpm --filter etaye-web test:demo` from the repository root. It exercises the live journey and writes a video under `apps/web/test-results/demo/`. The test resets the demo fixture and its browser sessions. The package limits anti-forgery reads and sign-ins per address each minute, so run `test:demo` (the 9.1 journey and the 1.4 isolation replay) as two invocations one minute apart, or restart the API container between them.
