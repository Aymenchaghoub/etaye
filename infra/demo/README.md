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

## Read a record

Sign in as Dr Nora Lefebvre, open **Patients** in the practice context and choose **MARTIN Claire** or **BERNARD Louis** (story 3.2). Claire's chronology lists eight dated evidence items with their occurrence and recording dates, the corrected weight and the conflicting allergy reports; **Antécédents et allergies** shows the three sections and **Examiner les sources** the two sides of the conflict. **Voir le dossier à une date passée** shows the record as known at an instant (Paris time): at 20/10/2026 10:14 the weight is still 86,4 kg and nothing recorded later is shown. Louis's thin record reads *non renseigné* or *inconnu*, never normal. The **Mesures** tab (story 3.3) lists Claire's blood-pressure readings as pairs (128/80 then 134/82 mmHg) and her weight as 68,4 kg, marked *Corrigé · version 2*; **Voir l'historique** shows the original 86,4 kg with its reason, and a past instant shows the weight before (10:14) and after (10:16) its correction. A chart never replaces the table, and a line between two points is not a measurement. The screens are read-only; entering and correcting statements and measurements belongs to increment 2. A reset restores the evidence and the measurements with the rest of the fixture.

## Prove isolation

On the showcase, open **Prouvez l’isolation** after the scenario list. The guide walks through four real refusals against the running API: Nora reads an authorized record, Nora requests a record outside her practice (one generic *introuvable* page), Mila (administration, no clinical capability) is refused in the practice she can see, and Claire (patient) gets the unavailable page in her own layout. Start with **Commencer avec Nora**; the guide links **Mila** and **Claire** sign in as those accounts and return to the same read. Every result is the actual HTTP answer. The same cases are measured in CI (`api-isolation-matrix` artifact of `API verify`).

## Scripted journey

Use a fresh disposable stack for the walkthrough. With the demo running, run `corepack pnpm --filter etaye-web test:demo` from the repository root. It exercises the live journey and writes a video under `apps/web/test-results/demo/`. The test resets the demo fixture and its browser sessions. The package limits anti-forgery reads and sign-ins per address each minute. `test:demo` runs every spec of `apps/web/demo-e2e`, so run the 9.1 journey and the 1.4 isolation replay as two invocations, one minute apart, each naming its file: `corepack pnpm --filter etaye-web exec playwright test --config=playwright.demo.config.ts demo-e2e/journey.spec.ts`, then the same command with `demo-e2e/isolation.spec.ts`. Restarting the API container between them also clears the limiter.


## Managed PostgreSQL rehearsal

The Compose package keeps its existing Flyway history and demo location. A separate PostgreSQL 18 path supports a database-owning administrator with `CREATEROLE` and `ADMIN OPTION` on any pre-existing Étaye roles. The administrator temporarily gets `SET` on `etaye_owner` to transfer `public` and apply its grants, then loses `SET` in the same transaction. PostgreSQL's administrator grant still allows role administration. Keep the provisioning credential separate from the migration, runtime and identity credentials. Flyway connects as the non-superuser schema owner. The HTTP service uses the non-owner runtime and restricted identity roles.

Use a fresh, isolated database named `etaye_demo_<suffix>` for synthetic data. Use a direct connection for provisioning and migrations. For a future managed TLS connection use certificate and hostname verification. Supply libpq connection settings and generated passwords through the environment or a private configuration file.

1. With `POSTGRES_DB` and `ETAYE_MANAGED_DEMO_DB` set to the same dedicated name, run `infra/local/init-roles.sh` and then `infra/demo/mark-demo-database.sh` as the database administrator before applying Flyway.
2. Apply and validate Flyway as `etaye_owner` using only the schema location (`db/migration`, or `filesystem:/flyway/schema` in the migration image). The supported version set is 1, 2, 3, 4, 7, 8, 9 and 11. Keep the local demo location out of this history.
3. Run `infra/demo/bootstrap-managed-demo.sh` with the exact database name and owner credential. It requires an administrator-owned marker, refuses existing application data and initialized RLS heaps, and installs the historical fixture definitions before loading the final fixture in a single transaction. A successful retry keeps the current fixture generation. Recreate a disposable empty database if a non-pristine rehearsal database is refused.
4. Only when explicitly authorized for a dedicated, disposable synthetic rehearsal database, run one API replica with `ETAYE_DEMO_ENABLED=true` and `ETAYE_DEMO_MANAGED_DATABASE_NAME` equal to the exact marked database name. Keep `ETAYE_DEMO_ENABLED` disabled (`false`) on ordinary and production-like databases (consistent with UD-56 limiting operator reset to dedicated local/disposable synthetic demos).

Backup and recovery rehearsals capture the application schemas (`public` and `etaye_demo`) and Flyway history with a separately privileged local backup credential. Provision and mark a new disposable database with the same application roles (which already creates `public` and `etaye_demo`), filter out the pre-existing `SCHEMA` entries before restoring objects as `etaye_owner` (`pg_restore --list <dump> | sed -E '/^[0-9]+; .* SCHEMA - (public|etaye_demo) /d' > restore.list`, then `pg_restore --no-owner --single-transaction --exit-on-error --use-list=restore.list <dump>`), and validate schema-only Flyway history before starting the API. A schema owner subject to forced RLS cannot capture a complete logical backup merely by using `pg_dump`. The supported export privilege, provider restore procedure, TLS path and compatibility with Neon remain to be verified in an authorized remote rehearsal.
