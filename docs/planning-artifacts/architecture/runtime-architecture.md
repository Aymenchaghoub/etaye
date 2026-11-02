# Runtime and module design

Status: Proposed. Read [ARCHITECTURE-SPINE.md](ARCHITECTURE-SPINE.md) and ADR-001–007 first. No runtime exists.

| Domain module | Owns | Allowed integration |
|---|---|---|
| identity | Account, credentials/session/recovery | Authenticated actor/context contracts; no direct clinical browsing |
| practices | Practice, Membership, Practitioner, Invitation grants | Capability evaluation; no clinical content in admin projections |
| records | Patient, PatientRecord, assertions, source revisions, measurements/labs, preparation | Explicit versioned read views for consultations/AI; owns corrections |
| scheduling | Availability/appointments/conflicts | Verifies local record/practitioner references; attendance separate from encounter |
| consultations | Encounter and structured notes | Requests source snapshots and document creation through contracts |
| documents | ClinicalDocument/Version/citations/review/publication | Enforces exact-version audience lifecycle; emits outbox events |
| files | Upload/quarantine, guarded byte store and exports | Guarded metadata/view-model ports, no raw storage keys to browser |
| ai | Processing leases, provider port, evaluation metadata | Reads authorized snapshots; creates draft candidate only |
| communications | Minimal email templates/outbox delivery | Consumes committed safe event references; rechecks recipient/allowlisted origin |
| audit/demo operations | Event projection, health/readiness, bounded fixture reset | Minimal operational capability; no automatic clinical superuser |

Organize future Java code by these business domains, with API/application/domain/infrastructure boundaries where useful. One deployable process, one PostgreSQL database, scoped private object store and external adapter ports. No broker, microservices or vector database. Enforce module dependencies and never share JPA entities as transport objects. English identifiers/comments explaining constraints. React routes/features use typed API clients, query cache scoped to account/practice and local form state; no persistence of clinical cache across logout/switch.

Request path: resolve session → membership/portal grant → capability/resource authorization → open scoped transaction and SET LOCAL context → repository integrity/version checks → commit domain/audit/outbox/receipt → return typed projection. Set RLS context inside the same transaction as queries, including read-only transactions; no global thread/client setting across unrelated pooled calls. Reset/check context on worker and error paths. Migrations run as a distinct privileged role; runtime role neither owns tables nor bypasses RLS.

Worker path: atomically claim task and lease → load exact authorized snapshot in scoped short transaction → release transaction → call bounded external adapter → validate typed result/citations → in a new scoped transaction recheck lease, grant, record freshness and target version → create new draft/export or safe failure. Notifications likewise use committed outbox rows, dedupe and backoff. Persistent rows allow recovery after sleeping service/restart; sleeping hosts do not guarantee timely reminders.

Readiness distinguishes API/database/storage availability from a liveness process check; health output reveals no credentials or clinical data. Metrics include API latency/error, DB pool, queue age/attempts, PDF memory/time, generation cost and outbox failures. Configuration separates dev/CI/preview/demo stores and credentials; tests must reject public-demo endpoints. Future seed/reset is versioned synthetic data, atomic boundary with active-session invalidation, never a real-data import.
