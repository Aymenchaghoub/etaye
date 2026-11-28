# Étaye · Local demonstration

Explore the merged Web feature set through fictional accounts in two isolated practices. The showcase has three account entry cards. The guided routes below also use the seeded multi-practice account and the two pending invitations.

## Run

From the repository root, run `./infra/demo/start.sh`, then open http://localhost:4179. Docker with Compose v2 and OpenSSL are required. The first run downloads and builds the images. The static entry page waits for the real database readiness signal. The French guide is available at `/demo-guide.txt` and through the existing scenario details.

The script creates a private `infra/demo/.env` with four randomly generated database credentials and starts the Compose stack. Subsequent runs can use `docker compose -f infra/demo/compose.yml up -d`. Only the web port is published on loopback. Keep the single API replica shipped in the Compose configuration. Stop the stack with `docker compose -f infra/demo/compose.yml down`.

## Fictional sign-in

Shared synthetic password for the following local accounts: `Etaye-Jeu-Fictif-91!`.

| Scenario | Identifier |
|---|---|
| Dr Nora Lefebvre, practitioner | `nora.lefebvre@example.test` |
| Claire Martin, patient | `claire.martin@example.test` |
| Mila Dubois, administration | `mila.dubois@example.test` |
| Dr Sami Rahmani, practitioner in both practices | `sami.rahmani@example.test` |
| Demo operator | `operateur@example.test` |

Choose a scenario and review its limits. The demo handles synthetic account and identity changes and read-only clinical evidence. External email, live providers and clinical data entry are outside this walkthrough.

To reset, sign in as the demo operator at `/connexion`, return to `/demonstration` and confirm **Réinitialiser la démonstration**. It reloads `etaye-design-v1`, increments its generation and closes existing sessions. Wait 30 seconds between resets. The operator has no practice or patient grant.

Use a reset fixture before repeating an activation, a password change or a registration. The fixture restores the seeded password and makes the demo invitations pending again.

## Guided routes

| Route | Time | Starting point and accounts | Observable result | Playwright file |
| --- | --- | --- | --- | --- |
| Welcome and identity | 8 min | `/connexion`, Nora in two separate browser contexts, then the Alex and Marie invitations in signed-out contexts | Password recovery revokes Nora's old session and reconnects to the same workspace. Alex receives a team membership. Marie opens her own patient space. | `identity.spec.ts` |
| Two practices | 3 min | `/connexion`, Sami | **Choisir un contexte de travail**, **Patients**, then **Changer de contexte**. The Tilleuls list contains Claire and the Canal list contains Hugo. Each record is hidden through the other practice's API path. | `practices.spec.ts` |
| Patient reception | 6 min | `/connexion`, Nora as practitioner and Mila for scheduling API evidence | Patient search, creation, duplicate warning, and a concurrent correction refused before reapplying the retained draft. Mila's two scheduling API operations return only administrative identity data. | `reception.spec.ts` |
| Record and evidence | 6 min | `/connexion`, Nora, then Claire's and Marie's records under **Patients** | Dated chronology, conflicting sources, historical weight, sourced laboratory intervals and correction history. | `record-evidence.spec.ts` |
| Platform and isolation | 4 min | `/`, `/demonstration`, then the **Prouvez l’isolation** guide with Nora, Mila and Claire | Readiness, version metadata, both generated themes, the actual scope and CSRF refusals and their applicable minimized audit evidence. | `platform.spec.ts`, `isolation.spec.ts` |
| Reset | under 30 s from confirmation on the verified stack | `/demonstration`, demo operator | A new generation restores the demo fixture, including modified access and active sessions. | `journey.spec.ts`, `identity.spec.ts` |

### Welcome and identity

1. Sign in as Nora. Keep the workspace open. In a separate signed-out browser context open `/connexion`, select **Mot de passe oublié ?** and request a link for `nora.lefebvre@example.test`.
2. For this step, start the local package with `ETAYE_DEMO_RECOVERY_INBOX=true ./infra/demo/start.sh` before requesting the link. From the repository root run `python3 infra/demo/recovery-inbox.py` as the local Docker operator. Open the printed file URL in the second context, select Nora's address and choose a disposable demonstration password. For a custom local port pass `--origin http://localhost:<port>`. The inbox stays on the API container's loopback. The helper creates a private temporary HTML snapshot. Delete it after use and keep it outside shared folders, screenshots and logs. The underlying link expires after 15 minutes and is single-use.
3. Return to Nora's original window. **Votre session a expiré** clears the previous workspace. **Se reconnecter** returns to it with the new password.
4. In a signed-out context open `/invitation#etaye-fictional-invitation-INV-TEAM-01`. Activate for Alex Rivière using a synthetic password. Confirm **Cabinet des Tilleuls** in his workspace. Sign out and open `/invitation#etaye-fictional-invitation-INV-PORTAL-03`. Enter Marie's fictional birth date, **12/03/1990**, and a synthetic password. Confirm **Votre espace patient** for Marie. The two invitations and the fictional identities come from the [synthetic dataset](../../docs/design/synthetic-data.md).

### Patient reception

As Nora, open **Patients**, search for `Mar Claire`, and select **Créer un dossier patient**. Use **Jeanne Dupont**, born **02/06/1985**. Try **Juliette Dupont** with the same fictional date to see **Un dossier semblable existe dans ce cabinet**. The warning creates nothing. Use **Créer quand même** only for this distinct fictional identity. Open **Modifier l’identité** for Jeanne in two tabs before saving either. Save **Jeanne Anne** in the second tab, then submit **Jeanne Louise** from the first. The stale edit gets HTTP 412 and the form retains the submitted value. Review the newer version and save again.

The scheduling picker is outside the merged Web screens. The scheduling part of the Playwright route uses Mila's actual session to exercise the two existing scheduling endpoints, registers a second fictional identity, **Camille Fabre**, born **13/06/1988**, and confirms that the new clinical record is still refused. [Scheduling contract](../../docs/contracts/README.md#scheduling-patient-projection)

## Read a record

Sign in as Dr Nora Lefebvre, open **Patients** in the practice context and choose **MARTIN Claire** or **BERNARD Louis** (story 3.2). Claire's chronology lists eight dated evidence items with their occurrence and recording dates, the corrected weight and the conflicting allergy reports; **Antécédents et allergies** shows the three sections and **Examiner les sources** the two sides of the conflict. **Voir le dossier à une date passée** shows the record as known at an instant (Paris time): at 20/10/2026 10:14 the weight is still 86,4 kg and nothing recorded later is shown. Louis's thin record reads *non renseigné* or *inconnu*, never normal. The **Mesures** tab (story 3.3) lists Claire's blood-pressure readings as pairs (128/80 then 134/82 mmHg) and her weight as 68,4 kg, marked *Corrigé · version 2*; **Voir l'historique** shows the original 86,4 kg with its reason, and a past instant shows the weight before (10:14) and after (10:16) its correction. A chart never replaces the table, and a line between two points is not a measurement. The screens are read-only; entering and correcting statements and measurements belongs to increment 2. A reset restores the evidence and the measurements with the rest of the fixture.

In **Biologie** (story 3.4), Claire's glucose result retains its supplied **4,1–6,1 mmol/L** interval and exact source **SRC-A-003**. In Marie's record, **Analyse B** shows a comparator and *Intervalle non fourni*. **Historique du résultat · Analyse A** shows **7,40** and **7,04**, with separate dated evidence. The laboratory entry and correction actions belong to increment 2.

### Platform evidence

`GET /api/v1/probes/readiness` returns `UP` before the entry redirects to the showcase. `GET /api/v1/meta` returns API and client versions and local fixture metadata. Use `/demonstration?theme=clair` and `/demonstration?theme=sombre` to compare the generated themes. The build runs the same token generator used for the shipped CSS. The Playwright route verifies a refused CSRF-free sign-out without closing the session, and checks the minimized audit receipt of Mila's separate clinical denial through read-only PostgreSQL inspection by the local Docker operator.

## Prove isolation

On the showcase, open **Prouvez l’isolation** after the scenario list. The guide starts with Nora reading an authorized record. It then shows the real refusals for Nora requesting a record outside her practice, Mila requesting a clinical record in her practice and Claire requesting a staff-only route. The scripted replay compares an absent record with Nora's cross-practice refusal and checks that the foreign record exists for its authorized account. Start with **Commencer avec Nora**. The guide links **Mila** and **Claire** sign in as those accounts and return to the same read. The same cases are measured in CI (`api-isolation-matrix` artifact of `API verify`).

## Scripted journey

Use a fresh disposable stack and enable the recovery inbox for the identity route. Use the same Compose project in the test shell as in the startup shell. When using a custom web port, export `ETAYE_E2E_DEMO_URL` with its HTTP loopback origin.

Run each file in a separate invocation from the repository root. Wait one minute or restart only the API container and wait for readiness between invocations. This leaves the normal rate limits in force. Keep the individual output directories to preserve each video. A reproducible order is `journey`, `isolation`, `platform`, `practices`, `reception`, `record-evidence`, `identity`. Allow at least 30 seconds between operator resets.

```sh
corepack pnpm --filter etaye-web exec playwright test --config=playwright.demo.config.ts \
  demo-e2e/identity.spec.ts --output=test-results/demo/identity
```

Replace both occurrences of `identity` with the selected file stem. The reception route creates three fictional records. The identity suite changes and restores the fixture through the operator. Reset manually before repeating a failed or interrupted write walkthrough. [Baseline audit and scope limits](../../docs/implementation-artifacts/local-demo-coverage.md)


## Managed PostgreSQL rehearsal

The Compose package keeps its existing Flyway history and demo location. A separate PostgreSQL 18 path supports a database-owning administrator with `CREATEROLE` and `ADMIN OPTION` on any pre-existing Étaye roles. The administrator temporarily gets `SET` on `etaye_owner` to transfer `public` and apply its grants, then loses `SET` in the same transaction. PostgreSQL's administrator grant still allows role administration. Keep the provisioning credential separate from the migration, runtime and identity credentials. Flyway connects as the non-superuser schema owner. The HTTP service uses the non-owner runtime and restricted identity roles.

Use a fresh, isolated database named `etaye_demo_<suffix>` for synthetic data. Use a direct connection for provisioning and migrations. For a future managed TLS connection use certificate and hostname verification. Supply libpq connection settings and generated passwords through the environment or a private configuration file.

1. With `POSTGRES_DB` and `ETAYE_MANAGED_DEMO_DB` set to the same dedicated name, run `infra/local/init-roles.sh` and then `infra/demo/mark-demo-database.sh` as the database administrator before applying Flyway.
2. Apply and validate Flyway as `etaye_owner` using only the schema location (`db/migration`, or `filesystem:/flyway/schema` in the migration image). The supported version set is 1, 2, 3, 4, 7, 8, 9 and 11. Keep the local demo location out of this history.
3. Run `infra/demo/bootstrap-managed-demo.sh` with the exact database name and owner credential. It requires an administrator-owned marker, refuses existing application data and initialized RLS heaps, and installs the historical fixture definitions before loading the final fixture in a single transaction. A successful retry keeps the current fixture generation. Recreate a disposable empty database if a non-pristine rehearsal database is refused.
4. Only when explicitly authorized for a dedicated, disposable synthetic rehearsal database, run one API replica with `ETAYE_DEMO_ENABLED=true` and `ETAYE_DEMO_MANAGED_DATABASE_NAME` equal to the exact marked database name. Keep `ETAYE_DEMO_ENABLED` disabled (`false`) on ordinary and production-like databases (consistent with UD-56 limiting operator reset to dedicated local/disposable synthetic demos).

Backup and recovery rehearsals capture the application schemas (`public` and `etaye_demo`) and Flyway history with a separately privileged local backup credential. Provision and mark a new disposable database with the same application roles (which already creates `public` and `etaye_demo`), filter out the pre-existing `SCHEMA` entries before restoring objects as `etaye_owner` (`pg_restore --list <dump> | sed -E '/^[0-9]+; .* SCHEMA - (public|etaye_demo) /d' > restore.list`, then `pg_restore --no-owner --single-transaction --exit-on-error --use-list=restore.list <dump>`), and validate schema-only Flyway history before starting the API. A schema owner subject to forced RLS cannot capture a complete logical backup merely by using `pg_dump`. The supported export privilege, provider restore procedure, TLS path and compatibility with Neon remain to be verified in an authorized remote rehearsal.
