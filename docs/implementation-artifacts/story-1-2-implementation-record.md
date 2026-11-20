# Story 1.2 implementation record

Authorization UD-48. The Web implements both sign-in entries, access recovery, session expiry and safe return under one account authority. It uses the approved French forms and amber reset error summary. The neutral expiry screen removes the protected view and offers explicit reconnection or exit. A paused operation never runs automatically. Practice selection belongs to 1.3 and branded mail to 8.3. [Scope and acceptance](spec-1-2-sign-in-recovery-expiry.md) · [Approved design](../design/reviews/2026-11-20-story-1-2-review.md)

## Runtime behavior

- The API rotates the JDBC session ID and CSRF value at sign-in, resolves current grants and limits return paths to exact authorized routes. Routine status polling leaves the 30-minute idle deadline intact, and activity never exceeds the 8-hour absolute deadline. Recovery expires after 15 minutes.
- V2 grants the identity role access through narrow functions. Reset atomically redeems a digest, changes the password, increments its revision, revokes sessions and records a safe audit event. Request checks reject late sessions at older credential revisions. Recovery requests and sign-in failures use generic responses and bounded admission.
- A local, opt-in inbox holds captured synthetic links in memory. It listens on `127.0.0.1:8025` and issues links for `@example.test` only. A documented credential version identifies PBKDF2-HMAC-SHA256 at 600,000 iterations for new passwords. V1 demo fixtures using the Spring v5.8 encoding can still sign in and receive the new encoding when they reset their passwords. [Spring Security password storage](https://docs.spring.io/spring-security/reference/7.0/features/authentication/password-storage.html) · [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)

## Verified evidence on 2026-11-20

| Check | Executed result |
|---|---|
| Java 25 Maven verify | 150 tests passed against PostgreSQL 18 Testcontainers. HTTP tests exercise generic credential failures, cookie and CSRF rotation, bounds, concurrent recovery, single-use and expired tokens, session revocation and current grants. |
| V1-to-V2 upgrade | Flyway applied V2 to an isolated V1 database and preserved a synthetic account and its membership. Verification also used a fresh database with both migrations. |
| Web verification | Node 26 build, SDK drift and TypeScript checks passed. All 19 Chromium smoke tests passed, including keyboard and axe checks. The CI evidence gates require the named API and browser suites. |
| Isolated stack | The real browser signed in, submitted recovery, opened the local link, reset the password, observed revocation in the other session and signed in again. The outage test stopped, paused and restarted PostgreSQL. Both passed. |
| Rendered UI | 51 captures at desktop, tablet, mobile and 320 CSS px widths with both themes. Primary comparisons include professional and patient sign-in, the amber alert inside the reset form and the neutral expiry panel with mobile actions at the bottom. Opened samples use the corrected offline reference under the approved Figma access exception. |
| Password work factor | On Temurin 25 and the development laptop, 15 warmed verifications at 600,000 iterations measured p50 117.2 ms and p95 136.8 ms for that encoding. The Spring v5.8 preset measured 66.0 and 68.0 ms. These are encoder timings on one machine. |

The real-stack captures and test run used the implementation candidate `57bb52dfd261f6722b257039c5ab2ead23615b2e`. The password encoding and bundle-notice checks ran separately after the candidate.

## Try local recovery

Set `ETAYE_DEMO_RECOVERY_INBOX=true` and `SERVER_ADDRESS=127.0.0.1` before starting the API with the [local stack instructions](story-10-1-implementation-record.md#9-running-it-locally). Use `http://localhost:5173` for the Web so the fixed reset link opens in the same local app.

On a disposable local database, seed the synthetic account for a recovery-first trial:

```sh
docker exec -i etaye-local-postgres psql -U etaye_owner -d etaye -v ON_ERROR_STOP=1 <<'SQL'
INSERT INTO account (normalized_email, display_name, password_hash)
VALUES (
    'nora.lefebvre@example.test',
    'Nora Lefebvre',
    replace(gen_random_uuid()::text || gen_random_uuid()::text || gen_random_uuid()::text, '-', '')
)
ON CONFLICT (normalized_email) DO NOTHING;
SQL
```

Request recovery for `nora.lefebvre@example.test` at `http://localhost:5173/recuperation`. Open `http://127.0.0.1:8025/`, follow the captured link and choose a demonstration password. Sign in at `http://localhost:5173/connexion`. Existing practice and portal grants appear through the session contract and are rechecked by the server.
