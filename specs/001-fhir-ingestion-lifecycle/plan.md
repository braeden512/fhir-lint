# Implementation Plan: Phase 1 — FHIR Ingestion and Asynchronous Job Lifecycle

**Branch**: `001-fhir-ingestion-lifecycle` | **Date**: 2026-09-26 | **Spec**: [specs/001-fhir-ingestion-lifecycle/spec.md](spec.md)

**Input**: Feature specification from `specs/001-fhir-ingestion-lifecycle/spec.md`

## Summary

Build the foundational FHIR R4 ingestion layer and asynchronous job state machine for FHIRLint. The service exposes a versioned REST API (`POST /api/v1/quality-checks`) that performs synchronous pre-flight syntactic checks on incoming JSON payloads, records a new job entity in PostgreSQL with status `QUEUED`, and immediately returns `202 Accepted` with a `Location` header. A dedicated, bounded Spring `ThreadPoolTaskExecutor` picks up the job, marks it `PROCESSING`, parses the transient payload using HAPI FHIR R4 parsers to calculate inventory metrics (total resource count, distribution by type), marks the job `COMPLETED` (or `FAILED` on syntax errors), and purges payload memory references. Clients query job progress and metrics via `GET /api/v1/quality-checks/{id}`.

## Technical Context

**Language/Version**: Java 21 LTS

**Primary Dependencies**:
- Spring Boot 4.1.x (`spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`)
- HAPI FHIR R4 (`ca.uhn.hapi.fhir:hapi-fhir-base:6.10.0`, `ca.uhn.hapi.fhir:hapi-fhir-structures-r4:6.10.0`)
- SpringDoc OpenAPI (`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.5`)
- PostgreSQL JDBC driver & Flyway migration (`org.postgresql:postgresql`, `org.flywaydb:flyway-database-postgresql`)

**Storage**: PostgreSQL 16+ (job lifecycle metadata and metrics only; zero persistent PHI/FHIR payload storage)

**Testing**: JUnit 5, Spring Boot Test (`MockMvc`), Testcontainers PostgreSQL (`org.testcontainers:postgresql`)

**Target Platform**: Linux / Containerized JVM environment (Docker)

**Project Type**: RESTful Web Service / Backend Developer Tool

**Performance Goals**:
- Intake acknowledgment and job identifier returned in $<500$ ms for 99% of submissions under 10 MB.
- Status retrieval response time $<100$ ms for 99% of queries.

**Constraints**:
- Maximum payload size up to 10 MB per submission.
- Zero raw clinical payload data stored in PostgreSQL (transient in-memory processing only, per Constitution Principle V).
- Bounded thread pool executor to safeguard memory and avoid OOM during concurrent bundle submissions.

**Scale/Scope**:
- Phase 1 scope covers ingestion, fast pre-flight validation, asynchronous execution lifecycle, baseline parsing, and metadata metrics. Quality rule evaluation and scoring are deferred to subsequent phases.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle / Rule | Compliance Status | Analysis & Evidence |
| :--- | :---: | :--- |
| **I. Standards-First Healthcare Interoperability** | **PASS** | HAPI FHIR R4 (`hapi-fhir-structures-r4`) is used for parsing and object models. No custom FHIR parser or specification inventiveness. |
| **II. Maintainable Spring Boot Architecture** | **PASS** | Idiomatic 3-tier architecture (Controller, Service, Repository) with Spring `@Async` and `ThreadPoolTaskExecutor`. No premature message queues or distributed caches. |
| **III. Testable and Reliable Software** | **PASS** | Automated integration tests with Testcontainers and `MockMvc` verify full end-to-end acceptance scenarios, state transitions, and error handling. |
| **IV. Actionable Data-Quality Analysis** | **PASS** | Phase 1 focuses on ingestion and inventory metrics. Diagnostic error messages for malformed inputs are clearly formatted and actionable. |
| **V. Privacy-Conscious Healthcare Software** | **PASS** | Strict zero-retention posture: raw FHIR payloads exist exclusively in transient worker memory and are never persisted to PostgreSQL. |
| **VI. Incremental Development and Simplicity** | **PASS** | Delivers the minimal viable ingestion engine using native Spring capabilities before adding external brokers. |
| **VII. Developer-Focused API Design** | **PASS** | Versioned `/api/v1` REST paths, RFC 7231 `202 Accepted` with `Location` header, standard JSON error envelopes, and automated OpenAPI documentation. |

**Gate Result**: All gates passed without violations. No complexity waivers required.

## Project Structure

### Documentation (this feature)

```text
specs/001-fhir-ingestion-lifecycle/
├── spec.md              # Feature specification
├── plan.md              # This implementation plan
├── research.md          # Technical research & architectural decisions (Phase 0)
├── data-model.md        # Data models, DDL, and lifecycle state machine (Phase 1)
├── quickstart.md        # Runnable verification guide (Phase 1)
├── contracts/           # OpenAPI 3.1 specification (Phase 1)
│   └── quality-checks-api.yaml
└── checklists/
    └── requirements.md  # Specification quality checklist
```

### Source Code (repository root)

```text
src/
├── main/
│   ├── java/com/braeden/fhirlint/
│   │   ├── FHIRLintApplication.java
│   │   ├── config/
│   │   │   ├── AsyncConfig.java                 # ThreadPoolTaskExecutor configuration
│   │   │   ├── FhirConfig.java                  # Singleton FhirContext bean
│   │   │   └── OpenApiConfig.java               # SpringDoc OpenAPI configuration
│   │   ├── controller/
│   │   │   ├── QualityCheckController.java      # POST & GET /api/v1/quality-checks
│   │   │   └── RestExceptionHandler.java        # Centralized RFC 7807/JSON error handler
│   │   ├── dto/
│   │   │   ├── JobCreatedResponse.java          # 202 Accepted payload
│   │   │   ├── JobDetailResponse.java           # GET job status & metrics payload
│   │   │   ├── IngestionMetricsDto.java         # Summary metrics DTO
│   │   │   └── ErrorResponse.java               # Standard error DTO
│   │   ├── model/
│   │   │   ├── QualityCheckJob.java             # JPA Entity
│   │   │   └── JobStatus.java                   # Enum: QUEUED, PROCESSING, COMPLETED, FAILED
│   │   ├── repository/
│   │   │   └── QualityCheckJobRepository.java   # Spring Data JPA repository
│   │   └── service/
│   │       ├── QualityCheckService.java         # Boundary validation & job persistence
│   │       ├── AsyncIngestionWorker.java        # Background HAPI FHIR parser & executor
│   │       └── JobWatchdogService.java          # Scheduled cleanup for stalled jobs
│   └── resources/
│       ├── application.yml
│       └── db/migration/
│           └── V1__create_quality_check_jobs.sql # Flyway baseline schema
└── test/
    ├── java/com/braeden/fhirlint/
    │   ├── FHIRLintApplicationTests.java
    │   ├── controller/
    │   │   └── QualityCheckControllerTest.java  # WebMvc pre-flight & validation tests
    │   ├── service/
    │   │   ├── AsyncIngestionWorkerTest.java    # Parser & metrics extraction unit tests
    │   │   └── JobWatchdogServiceTest.java      # Watchdog timeout test
    │   └── integration/
    │       └── QualityCheckLifecycleIT.java     # Full Testcontainers async lifecycle IT
    └── resources/
        ├── application-test.yml
        └── sample-data/                         # Synthetic test fixtures
```

**Structure Decision**: Idiomatic Spring Boot multi-package structure with clear separation across web (`controller`), business logic (`service`), persistence (`model`, `repository`), and cross-cutting infrastructure (`config`).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

*No violations. All components adhere to the project constitution.*
