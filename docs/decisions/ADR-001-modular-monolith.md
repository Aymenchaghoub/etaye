# ADR-001 — Modular monolith and durable jobs

Status: Proposed. Date: 2026-11-02. Related: FR-001–020, AD-2/7/8.

Étaye has interacting records, appointments, document review and publication. Atomic integrity matters more than independent scaling for a portfolio demonstration.

**Choice:** one Spring Boot runtime, domain modules with explicit interfaces and inward dependencies, one PostgreSQL database. Durable job and outbox tables handle asynchronous AI, exports, scanning and notifications. A scheduled executor claims jobs using leases and `FOR UPDATE SKIP LOCKED`; domain updates and notification intents commit together. Workers persist attempt counts, next attempt and lease expiry. No Redis, broker, vector store or microservice is required.

Alternatives: a single unstructured service reduces initial files but hides ownership; microservices create deployment/consistency burdens; an in-memory async executor loses work on restart. A durable broker can be reconsidered if measured throughput or delivery requirements exceed the database queue.

Consequences: module boundary tests are required. Provider calls occur outside database transactions; outcome commits must check lease and draft versions. Deliveries are at least once, with idempotent handlers and deduplication. A sleeping host does not provide timely background work: pending jobs remain visible and resume when the service wakes. Do not promise reminder timing on free sleeping hosting.
