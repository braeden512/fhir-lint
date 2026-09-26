# Technical Research & Architectural Decisions: Phase 1 — FHIR Ingestion & Asynchronous Job Lifecycle

This document consolidates research, design choices, and technical trade-offs for Phase 1 of FHIRLint in alignment with [ADR-001](../../docs/adr/ADR-001-phase-1-fhir-ingestion-and-job-lifecycle.md) and the [Project Constitution](../../.specify/memory/constitution.md).

---

## 1. Asynchronous Task Execution Engine

### Decision
Adopt Spring Boot's native `@Async` mechanism backed by a dedicated, custom-configured `ThreadPoolTaskExecutor` and a PostgreSQL-backed job state machine.

### Rationale
- **Simplicity and Maintainability (Constitution Principle II & VI)**: Avoids introducing external message brokers (RabbitMQ, Kafka) or distributed caching systems (Redis) during the initial MVP.
- **Resource Protection**: A bounded queue with a controlled pool size (e.g., core pool of 4, max pool of 8, queue capacity of 500 with `CallerRunsPolicy` or explicit backpressure rejection) prevents OutOfMemoryErrors and thread starvation when concurrent multi-megabyte payloads arrive.
- **Transactional State Persistence**: The job metadata is stored in PostgreSQL before the task is dispatched to the executor, ensuring the job status is queryable immediately upon returning `202 Accepted`.

### Alternatives Considered
- **Synchronous Execution**: Running parsing and analysis inside the HTTP request-response cycle. Rejected because payloads up to 10 MB with thousands of resources will cause HTTP gateway timeouts (504), client drops, and thread starvation.
- **External Message Brokers (RabbitMQ / Kafka)**: Highly scalable for distributed worker nodes, but introduces external infrastructure overhead and operational complexity that violates Constitution Principle VI (MVP simplicity). Can be introduced in later phases if horizontally distributed nodes become necessary.
- **Java 21 Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor`)**: Excellent for I/O-bound operations, but FHIR parsing and validation are CPU- and memory-intensive operations. Unbounded virtual threads running heavy HAPI AST parsing simultaneously could lead to sudden heap exhaustion under load. A bounded platform thread pool provides predictable memory boundaries.

---

## 2. Ingestion Parsing & Privacy-Preserving Memory Management

### Decision
Parse FHIR payloads using HAPI FHIR's `FhirContext` (R4) and `IParser` in a transient in-memory pipeline. Raw JSON and HAPI resource object models are never written to disk or the database. Once summary metrics (resource counts and types) are extracted, references to the raw payload and AST are cleared to allow prompt garbage collection.

### Rationale
- **Privacy by Design (Constitution Principle V)**: The platform explicitly operates under a zero-retention posture for clinical data. Only operational metadata (job UUID, status, execution timestamps, resource counts, diagnostic failure messages) is persisted to PostgreSQL.
- **Performance & Standards Compliance (Constitution Principle I)**: HAPI FHIR's `JsonParser` is the industry-standard parser for HL7 FHIR R4 in Java. It safely handles complex FHIR datatypes, extensions, and resource structures. Caching a singleton `FhirContext` ensures thread-safe, high-speed parsing without repeated schema initialization overhead.

### Alternatives Considered
- **Persisting Raw FHIR Payloads in PostgreSQL (`JSONB` column)**: Allows offline replay and debugging, but directly violates Constitution Principle V ("FHIRLint MUST NOT store or expose healthcare data beyond the transient duration required to execute the requested analysis"). Rejected.
- **Custom Streaming JSON Parser (Jackson-only without HAPI)**: While faster for mere counting, it would require writing custom parsing logic for polymorphic FHIR types and Bundles, violating Constitution Principle I ("MUST NOT implement custom parsers...").

---

## 3. Boundary Syntactic Pre-flight Validation

### Decision
Execute a fast, synchronous syntactic pre-flight check at the REST Controller layer before creating a job. The check verifies:
1. The request body is valid, well-formed JSON.
2. The root JSON contains a non-blank `resourceType` property.
3. If `resourceType == "Bundle"`, the root contains an `entry` array (if present).

If pre-flight fails, return `400 Bad Request` with an RFC 7807 `ProblemDetail` or standard JSON error response immediately, without generating a job in the database.

### Rationale
- **Fast Failure & Resource Conservation**: Prevents allocating database records, UUIDs, and asynchronous worker queue slots to malformed garbage payloads or non-FHIR JSON.
- **Developer Experience (Constitution Principle VII)**: Immediate synchronous feedback for syntax mistakes enables instant developer iteration.

### Alternatives Considered
- **Queuing all payloads and failing asynchronously**: Moves all validation into the background worker. Rejected because it wastes queue slots and forces developers to poll a job just to discover a simple syntax error.

---

## 4. Job State Lifecycle & Watchdog Mechanism

### Decision
Model the job lifecycle using four distinct states:
`QUEUED` $\to$ `PROCESSING` $\to$ `COMPLETED` | `FAILED`

Transitions:
- `QUEUED`: Recorded in PostgreSQL with `createdAt` before HTTP `202 Accepted` response.
- `PROCESSING`: Transitioned by background worker with `startedAt` upon picking the task up from the queue.
- `COMPLETED`: Transitioned upon successful HAPI FHIR parsing and metric extraction with `completedAt` and populated `IngestionMetrics`.
- `FAILED`: Transitioned if an unhandled error, invalid FHIR semantics, or timeout occurs, recording `completedAt` and `failureReason`.

A scheduled background watchdog (`@Scheduled(fixedDelay = 60000)`) queries for jobs stuck in `PROCESSING` longer than a configurable timeout (default: 5 minutes) and marks them `FAILED` with failure reason: `"Processing timed out or worker terminated unexpectedly"`.

### Rationale
- **Predictability & Self-Healing**: Ensures clients never poll a job that hangs indefinitely due to JVM crashes, unhandled errors, or worker thread interruption.

### Alternatives Considered
- **Database polling without a watchdog**: Simple, but crashes would leave jobs permanently in `PROCESSING`.

---

## 5. REST API Contract & Versioning

### Decision
- Endpoint paths:
  - `POST /api/v1/quality-checks`: Ingests dataset. Returns HTTP `202 Accepted` with `Location: /api/v1/quality-checks/{id}` header and body `{"id": "...", "status": "QUEUED", "createdAt": "..."}`.
  - `GET /api/v1/quality-checks/{id}`: Returns HTTP `200 OK` with full job details, timestamps, and metrics (if completed), or `404 Not Found` if the job ID is unknown.
- API documentation: OpenAPI 3.1 specification generated and exposed via SpringDoc Swagger UI (`/swagger-ui.html` and `/v3/api-docs`).

### Rationale
- **Developer-Focused API Design (Constitution Principle VII)**: Uses HTTP 202 with `Location` header, following asynchronous REST best practices (RFC 7231).
- **Predictable Error Schemas**: All error responses return standard JSON error structures with clear error codes, timestamps, and actionable messages.

---

## 6. Dependency Selection & Justification

In compliance with **Constitution Technology Stack & Architectural Constraints**:
- **HAPI FHIR**: `ca.uhn.hapi.fhir:hapi-fhir-base:6.10.0` and `ca.uhn.hapi.fhir:hapi-fhir-structures-r4:6.10.0`
  - *Justification*: Mandated by Principle I for standards-compliant FHIR R4 AST representation and parsing.
- **SpringDoc OpenAPI**: `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.5`
  - *Justification*: Mandated by Principle VII for automated, live OpenAPI documentation.
- **PostgreSQL JDBC & Flyway**: `org.postgresql:postgresql` and `org.flywaydb:flyway-database-postgresql`
  - *Justification*: Reliable, production-grade schema migration and persistent job state tracking.
- **Testcontainers**: `org.testcontainers:postgresql:1.20.4` and `org.testcontainers:junit-jupiter:1.20.4`
  - *Justification*: Mandated by Constitution Principle III for authentic, automated integration testing against a real PostgreSQL instance.
