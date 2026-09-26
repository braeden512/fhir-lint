# Implementation Tasks: Phase 1 — FHIR Ingestion and Asynchronous Job Lifecycle

This document provides the actionable, dependency-ordered task breakdown for implementing Phase 1 in alignment with [spec.md](spec.md), [plan.md](plan.md), [data-model.md](data-model.md), [research.md](research.md), and [contracts/quality-checks-api.yaml](contracts/quality-checks-api.yaml).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project dependency configuration, Spring Boot initialization, and core configuration beans.

- [ ] T001 Update dependencies in `build.gradle` to include HAPI FHIR R4 (`ca.uhn.hapi.fhir:hapi-fhir-base:6.10.0`, `ca.uhn.hapi.fhir:hapi-fhir-structures-r4:6.10.0`), SpringDoc OpenAPI (`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.5`), Flyway PostgreSQL (`org.flywaydb:flyway-database-postgresql`), and Testcontainers (`org.testcontainers:postgresql:1.20.4`)
- [ ] T002 [P] Configure async task execution thread pool in `src/main/java/com/braeden/fhirlint/config/AsyncConfig.java` with core pool size 4, max pool size 8, queue capacity 500, and thread prefix `fhir-ingest-`
- [ ] T003 [P] Configure singleton FHIR R4 context bean in `src/main/java/com/braeden/fhirlint/config/FhirConfig.java` wrapping `FhirContext.forR4()`
- [ ] T004 [P] Configure SpringDoc OpenAPI documentation metadata in `src/main/java/com/braeden/fhirlint/config/OpenApiConfig.java` matching OpenAPI 3.1 contract
- [ ] T005 [P] Update application properties in `src/main/resources/application.yml` for multipart limits (10MB max), PostgreSQL datasource, Flyway enabled, and watchdog timeout parameters

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Database schema migration, core entity models, repository, and error response infrastructure.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [ ] T006 Create Flyway migration script `src/main/resources/db/migration/V1__create_quality_check_jobs.sql` defining `quality_check_jobs` table with constraints `chk_job_status CHECK (status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED'))`, `chk_resources_analyzed CHECK (resources_analyzed IS NULL OR resources_analyzed >= 0)`, and composite index on `(status, created_at)`
- [ ] T007 [P] Create job lifecycle enum in `src/main/java/com/braeden/fhirlint/model/JobStatus.java` with values `QUEUED`, `PROCESSING`, `COMPLETED`, `FAILED`
- [ ] T008 Create JPA entity `src/main/java/com/braeden/fhirlint/model/QualityCheckJob.java` with fields: `id` (UUID, primary key), `status` (JobStatus, not null), `target_profile` (VARCHAR(64), default 'BASE_R4'), `created_at` (Instant, not null), `started_at` (Instant, nullable), `completed_at` (Instant, nullable), `failure_reason` (TEXT max 4000 chars, nullable), `resources_analyzed` (Integer >= 0, nullable), `resource_type_counts` (JSONB Map<String, Integer> using Hibernate `@JdbcTypeCode(SqlTypes.JSON)`, nullable)
- [ ] T009 [P] Create Spring Data JPA repository in `src/main/java/com/braeden/fhirlint/repository/QualityCheckJobRepository.java` with query method `findByStatusAndStartedAtBefore(JobStatus status, Instant threshold)`
- [ ] T010 [P] Create standardized error response DTO in `src/main/java/com/braeden/fhirlint/dto/ErrorResponse.java` with fields: `status` (int), `title` (String), `detail` (String), `timestamp` (Instant) per RFC 7807
- [ ] T011 Create global exception handler in `src/main/java/com/braeden/fhirlint/controller/RestExceptionHandler.java` translating JSON parsing exceptions, missing fields, and custom exceptions into `ErrorResponse`

**Checkpoint**: Foundation ready - database schema, JPA entity, repository, and error handler complete.

---

## Phase 3: User Story 1 - Asynchronous Ingestion of FHIR Datasets (Priority: P1) 🎯 MVP

**Goal**: Accept a FHIR R4 dataset (single resource or multi-resource Bundle), immediately return HTTP 202 Accepted with a Location header and tracking UUID, and asynchronously parse the dataset in the background to calculate resource counts and update the job to `COMPLETED`.

**Independent Test**: Submit `sample-data/clean/clean-bundle.json` to `POST /api/v1/quality-checks`. Verify immediate `202 Accepted` response with `Location: /api/v1/quality-checks/{id}` header, and verify background worker transitions job state in PostgreSQL from `QUEUED` to `PROCESSING` and terminates at `COMPLETED` with accurate resource counts.

### Tests for User Story 1 ⚠️

- [ ] T012 [P] [US1] Create controller web slice test in `src/test/java/com/braeden/fhirlint/controller/QualityCheckControllerTest.java` verifying `POST /api/v1/quality-checks` returns `202 Accepted`, `Location` header matching `/api/v1/quality-checks/{id}`, and `JobCreatedResponse` body
- [ ] T013 [P] [US1] Create unit test in `src/test/java/com/braeden/fhirlint/service/AsyncIngestionWorkerTest.java` verifying HAPI FHIR parsing extracts accurate resource counts and resource type distributions for both single resources (Patient) and collection Bundles
- [ ] T014 [US1] Create end-to-end integration test in `src/test/java/com/braeden/fhirlint/integration/QualityCheckLifecycleIT.java` using Testcontainers PostgreSQL verifying submission, asynchronous state transitions (`QUEUED` $\to$ `PROCESSING` $\to$ `COMPLETED`), and metric calculation

### Implementation for User Story 1

- [ ] T015 [P] [US1] Create response DTO `src/main/java/com/braeden/fhirlint/dto/JobCreatedResponse.java` with fields `id` (UUID), `status` (JobStatus), `createdAt` (Instant), `location` (String)
- [ ] T016 [US1] Implement asynchronous ingestion worker in `src/main/java/com/braeden/fhirlint/service/AsyncIngestionWorker.java` annotated with `@Async("qualityCheckExecutor")` to transition job to `PROCESSING` with `started_at`, parse transient payload using HAPI `IParser`, count total resources and group by resource type, update job to `COMPLETED` with `completed_at` and metrics, and release payload memory
- [ ] T017 [US1] Implement intake service logic in `src/main/java/com/braeden/fhirlint/service/QualityCheckService.java` to generate UUID, persist `QualityCheckJob` in `QUEUED` status, dispatch async execution to `AsyncIngestionWorker`, and return `JobCreatedResponse`
- [ ] T018 [US1] Implement `POST /api/v1/quality-checks` endpoint in `src/main/java/com/braeden/fhirlint/controller/QualityCheckController.java` returning HTTP `202 Accepted` with `Location` header

**Checkpoint**: At this point, User Story 1 is fully functional and delivers a complete, independently testable MVP.

---

## Phase 4: User Story 2 - Track Job Status and Inspect Ingestion Results (Priority: P2)

**Goal**: Query job status and inspection metrics via `GET /api/v1/quality-checks/{id}`, returning progress for in-flight jobs, summary metrics for completed jobs, failure reasons for failed jobs, and 404 for unknown IDs.

**Independent Test**: Query the endpoint for jobs in `QUEUED`, `PROCESSING`, `COMPLETED`, and `FAILED` states, verifying that the returned payload contains accurate timestamps, status, and resource metrics. Query an unknown UUID to verify `404 Not Found`.

### Tests for User Story 2 ⚠️

- [ ] T019 [P] [US2] Create controller web slice test in `src/test/java/com/braeden/fhirlint/controller/QualityCheckStatusControllerTest.java` verifying `GET /api/v1/quality-checks/{id}` returns `200 OK` with `JobDetailResponse` for existing jobs and `404 Not Found` with `ErrorResponse` for nonexistent UUIDs
- [ ] T020 [P] [US2] Add integration test scenario in `src/test/java/com/braeden/fhirlint/integration/QualityCheckLifecycleIT.java` polling `GET /api/v1/quality-checks/{id}` until status reaches `COMPLETED` and asserting that `resourcesAnalyzed` and `resourceTypeCounts` match the submitted bundle

### Implementation for User Story 2

- [ ] T021 [P] [US2] Create detailed job response DTO in `src/main/java/com/braeden/fhirlint/dto/JobDetailResponse.java` with fields: `id` (UUID), `status` (JobStatus), `targetProfile` (String), `createdAt` (Instant), `startedAt` (Instant), `completedAt` (Instant), `resourcesAnalyzed` (Integer), `resourceTypeCounts` (Map<String, Integer>), and `failureReason` (String)
- [ ] T022 [US2] Implement `getJobStatus(UUID id)` in `src/main/java/com/braeden/fhirlint/service/QualityCheckService.java` querying repository and mapping entity to `JobDetailResponse`
- [ ] T023 [US2] Implement `GET /api/v1/quality-checks/{id}` endpoint in `src/main/java/com/braeden/fhirlint/controller/QualityCheckController.java` returning `200 OK` or throwing `ResourceNotFoundException`

**Checkpoint**: User Stories 1 and 2 work seamlessly together, allowing end-to-end ingestion and status polling.

---

## Phase 5: User Story 3 - Reject Malformed and Unsupported Payloads at Ingestion Boundary (Priority: P3)

**Goal**: Synchronously reject malformed JSON and payloads lacking a `resourceType` with HTTP `400 Bad Request` at the boundary without allocating database jobs, and transition jobs to `FAILED` with descriptive error messages when background parsing fails.

**Independent Test**: Submit invalid JSON, empty body, and non-FHIR JSON to `POST /api/v1/quality-checks` and verify immediate `400 Bad Request` with zero rows added to PostgreSQL. Submit valid JSON with corrupted FHIR semantics and verify the job transitions to `FAILED` with an actionable `failureReason`.

### Tests for User Story 3 ⚠️

- [ ] T024 [P] [US3] Create boundary validation tests in `src/test/java/com/braeden/fhirlint/controller/QualityCheckBoundaryValidationTest.java` testing malformed JSON, empty payload, and missing `resourceType`, asserting HTTP `400 Bad Request` and zero database records created
- [ ] T025 [P] [US3] Create parser failure unit test in `src/test/java/com/braeden/fhirlint/service/AsyncIngestionWorkerFailureTest.java` verifying that invalid FHIR R4 syntax caught by HAPI FHIR transitions the job status to `FAILED` and populates `failureReason`

### Implementation for User Story 3

- [ ] T026 [US3] Implement synchronous pre-flight syntactic validation in `src/main/java/com/braeden/fhirlint/service/QualityCheckService.java` using Jackson `JsonNode` tree checking for valid JSON structure, non-blank `resourceType`, and throwing `MalformedPayloadException` on failure
- [ ] T027 [US3] Enhance exception handling in `src/main/java/com/braeden/fhirlint/controller/RestExceptionHandler.java` to map `MalformedPayloadException` and `HttpMessageNotReadableException` to `400 Bad Request` with descriptive `ErrorResponse`
- [ ] T028 [US3] Add error handling and rollback logic in `src/main/java/com/braeden/fhirlint/service/AsyncIngestionWorker.java` catching `DataFormatException` and HAPI parser exceptions, updating job status to `FAILED`, setting `completed_at`, and recording diagnostic message in `failure_reason`

**Checkpoint**: Ingestion boundary and asynchronous pipeline are hardened against corrupted, malformed, and invalid inputs.

---

## Phase 6: User Story 4 - Privacy-Conscious Ingestion and Stateless Payload Handling (Priority: P4)

**Goal**: Enforce zero-retention posture by ensuring raw payloads are never persisted and memory references are promptly released, while a scheduled watchdog service recovers orphaned jobs that remain stalled in `PROCESSING`.

**Independent Test**: Query PostgreSQL table via direct SQL in integration test to prove no raw payload or PHI columns exist. Test watchdog by simulating a job stalled in `PROCESSING` past timeout and verifying it transitions to `FAILED`.

### Tests for User Story 4 ⚠️

- [ ] T029 [P] [US4] Create watchdog test in `src/test/java/com/braeden/fhirlint/service/JobWatchdogServiceTest.java` verifying that jobs stuck in `PROCESSING` past the timeout threshold are identified and transitioned to `FAILED` with message `"Processing timed out or worker terminated unexpectedly"`
- [ ] T030 [P] [US4] Create privacy assertion integration test in `src/test/java/com/braeden/fhirlint/integration/ZeroRetentionPrivacyIT.java` inspecting PostgreSQL information schema and job table contents to verify 0% payload retention

### Implementation for User Story 4

- [ ] T031 [US4] Implement scheduled watchdog in `src/main/java/com/braeden/fhirlint/service/JobWatchdogService.java` annotated with `@Scheduled(fixedDelayString = "${fhirlint.watchdog.interval:60000}")` calling `QualityCheckJobRepository.findByStatusAndStartedAtBefore`, transitioning stalled jobs to `FAILED`, and recording failure reason
- [ ] T032 [US4] Explicitly verify memory dereferencing in `src/main/java/com/braeden/fhirlint/service/AsyncIngestionWorker.java` by nullifying payload and HAPI AST references in a `finally` block to allow immediate garbage collection

**Checkpoint**: All 4 user stories implemented with full zero-retention compliance and automated self-healing.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Verification of documentation, contract compliance, quickstart validation, and build verification.

- [ ] T033 [P] Verify live Swagger UI and OpenAPI documentation at `/swagger-ui.html` and `/v3/api-docs` matches `contracts/quality-checks-api.yaml`
- [ ] T034 Execute end-to-end scenarios from `quickstart.md` using `sample-data/clean/clean-bundle.json` and `sample-data/messy/messy-bundle.json` against local running application
- [ ] T035 Execute full test suite via `./gradlew check` ensuring 100% test pass rate with zero compiler warnings or lint errors

---

## Dependencies & Execution Order

### Phase Dependencies

```
Phase 1: Setup (T001-T005)
       │
       ▼
Phase 2: Foundational (T006-T011) ── [BLOCKS ALL USER STORIES]
       │
       ├────────────────────────────────────────┬────────────────────────────────────────┐
       ▼                                        ▼                                        ▼
Phase 3: US1 Ingestion [MVP] (T012-T018)  Phase 4: US2 Status (T019-T023)  Phase 5: US3 Boundary (T024-T028)
       │                                        │                                        │
       └────────────────────────────────────────┴────────────────────────────────────────┘
                                                │
                                                ▼
                                  Phase 6: US4 Privacy & Watchdog (T029-T032)
                                                │
                                                ▼
                                  Phase 7: Polish & Verification (T033-T035)
```

- **Setup (Phase 1)**: Independent, can start immediately.
- **Foundational (Phase 2)**: Depends on Phase 1. Blocks all user stories.
- **User Story 1 (Phase 3)**: Core MVP slice. Depends on Phase 2.
- **User Story 2 (Phase 4)**: Depends on Phase 2 and integrates with US1 models.
- **User Story 3 (Phase 5)**: Depends on Phase 2; hardens US1 boundary and worker.
- **User Story 4 (Phase 6)**: Depends on Phase 2; adds watchdog and privacy assertions.
- **Polish (Phase 7)**: Depends on all user story phases complete.

### Parallel Opportunities

- **Setup Phase**: T002, T003, T004, T005 can all be implemented in parallel after T001.
- **Foundational Phase**: T007, T009, T010 can be developed in parallel after T006 and T008.
- **User Story 1**: Tests T012 and T013 can be written in parallel. DTO T015 can be written in parallel with worker T016.
- **User Story 2**: Test T019 and DTO T021 can be implemented in parallel.
- **User Story 3**: Tests T024 and T025 can be written in parallel.
- **User Story 4**: Test T029 and T030 can be written in parallel.

---

## Parallel Example: User Story 1

```bash
# Launch test creation tasks together:
Task: "T012 [P] [US1] Create controller web slice test in src/test/java/com/braeden/fhirlint/controller/QualityCheckControllerTest.java"
Task: "T013 [P] [US1] Create unit test in src/test/java/com/braeden/fhirlint/service/AsyncIngestionWorkerTest.java"

# Launch DTO and Worker in parallel:
Task: "T015 [P] [US1] Create response DTO src/main/java/com/braeden/fhirlint/dto/JobCreatedResponse.java"
Task: "T016 [US1] Implement asynchronous ingestion worker in src/main/java/com/braeden/fhirlint/service/AsyncIngestionWorker.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (`build.gradle`, `AsyncConfig`, `FhirConfig`, `OpenApiConfig`, `application.yml`).
2. Complete Phase 2: Foundational (Flyway migration, `QualityCheckJob`, `JobStatus`, repository, error handling).
3. Complete Phase 3: User Story 1 (Ingestion worker, service, controller, and tests).
4. **STOP and VALIDATE**: Verify submitting a Bundle returns `202 Accepted` and transitions to `COMPLETED` in PostgreSQL.

### Incremental Delivery

1. Setup + Foundational complete $\to$ Solid core infrastructure.
2. User Story 1 $\to$ Working asynchronous ingestion (MVP).
3. User Story 2 $\to$ Status querying and metric inspection.
4. User Story 3 $\to$ Resilient boundary validation and error diagnostics.
5. User Story 4 $\to$ Zero-retention privacy verification and self-healing watchdog.
6. Polish $\to$ Full OpenAPI verification and test suite execution (`./gradlew check`).
