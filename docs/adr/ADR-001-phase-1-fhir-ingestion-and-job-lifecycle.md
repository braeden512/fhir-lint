# ADR-001: Phase 1 — FHIR Ingestion and Asynchronous Job Lifecycle

## Status
Accepted

## Context & Problem Statement
FHIRLint must accept FHIR R4 resources and Bundles from clients via REST HTTP endpoints. Payloads can range from single resources (<5 KB) to large multi-resource Bundles (1 MB to 10+ MB containing thousands of clinical entries). Running comprehensive linting synchronously in a single HTTP request-response cycle risks request timeouts (HTTP 504), client disconnection, thread starvation, and uncontrolled heap exhaustion.

Additionally, under **Constitution Principle V (Privacy-Conscious Healthcare Software)**, raw FHIR payloads containing potential PHI must NOT be stored permanently in the database.

## Decision Drivers
1. **Responsiveness**: The API must immediately acknowledge client submissions with predictable latency.
2. **Resource Protection**: Prevent OutOfMemoryErrors and thread pool exhaustion from large payloads.
3. **Stateless Privacy**: Do not store submitted FHIR payloads in PostgreSQL; only store job lifecycle state, metrics, and findings.
4. **Simplicity (Constitution Principle II & VI)**: Avoid introducing external message brokers (Kafka/RabbitMQ) prematurely for the MVP.

## Considered Options
1. **Synchronous-only processing**: Simple to implement, but fails on larger bundles and violates asynchronous requirement.
2. **Spring `@Async` + `ThreadPoolTaskExecutor` + PostgreSQL Job State**: Uses standard Spring Boot capabilities without external message brokers. Payloads are processed in-memory or streamed through a transient buffer.
3. **External Queue (RabbitMQ / Kafka / Redis streams)**: Highly scalable, but introduces heavy operational dependencies violating Constitution Principle VI.

## Decision Outcome
Adopt **Option 2: Spring `@Async` + `ThreadPoolTaskExecutor` with PostgreSQL Job State Machine**.

### Processing Flow
1. Client sends `POST /api/v1/quality-checks` with a FHIR resource or Bundle in the request body.
2. The controller performs fast syntactic pre-flight validation (verifying valid JSON and resourceType presence).
3. The service generates a unique `jobId` (UUID), persists a `QualityCheckJob` in PostgreSQL with status `QUEUED`, and dispatches the payload to an internal asynchronous task executor.
4. The controller immediately responds with `202 Accepted`, returning:
   - Header: `Location: /api/v1/quality-checks/{jobId}`
   - Body: `{"id": "{jobId}", "status": "QUEUED", "createdAt": "..."}`
5. The background worker updates job status to `PROCESSING`, streams/parses the payload with HAPI FHIR, runs analysis, updates status to `COMPLETED` (or `FAILED`), records issues/scores, and deletes all payload references from memory.

### Job State Machine
`QUEUED` $\to$ `PROCESSING` $\to$ `COMPLETED` | `FAILED`

## Consequences
### Positive
- Client receives an immediate `202 Accepted` response.
- No heavy infrastructure dependencies required beyond PostgreSQL.
- Complying with Constitution Principle V: no patient payloads stored in the database.

### Negative / Trade-offs
- If the application crashes midway through processing, the in-memory payload is lost and the job remains in `PROCESSING` until a watchdog sweeps it to `FAILED`. This is an acceptable trade-off for the MVP.
