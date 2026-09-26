# Feature Specification: Phase 1 — FHIR Ingestion and Asynchronous Job Lifecycle

**Feature Branch**: `001-fhir-ingestion-lifecycle`

**Created**: 2026-09-26

**Status**: Draft

**Input**: User description: "Create the spec for phase 1 - fhir ingestion and asynchronous job lifecycle. Use the documentation in docs, specifically docs/adr/ADR-001-phase-1-fhir-ingestion-and-job-lifecycle.md as well as the PRODUCT_SPEC.md to guide your thinking."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Asynchronous Ingestion of FHIR Datasets (Priority: P1)

As a healthcare software engineer or system integrator, I want to submit a FHIR R4 dataset (single resource or multi-resource Bundle) to the quality check service and immediately receive an acknowledgment with a unique tracking identifier, so that my client application is not blocked or subjected to HTTP request timeouts while large payloads are analyzed.

**Why this priority**: Immediate acknowledgment and asynchronous execution form the core foundation of the entire FHIRLint platform. Without the ability to reliably accept datasets and track them through an asynchronous lifecycle, subsequent validation and quality checks cannot function.

**Independent Test**: Can be tested independently by submitting a valid FHIR R4 single resource or Bundle. The system immediately returns an acceptance status with a tracking identifier and location reference, and transitions the background job from submission to completion.

**Acceptance Scenarios**:

1. **Given** a valid FHIR R4 JSON single resource (such as a Patient), **When** a client submits the resource to the quality checks intake, **Then** the system accepts the request immediately with an acceptance acknowledgment, assigns a unique tracking identifier, sets the initial status to `QUEUED`, and provides a status check location link.
2. **Given** a valid FHIR R4 JSON Bundle containing multiple entries, **When** a client submits the Bundle to the quality checks intake, **Then** the system accepts the submission, schedules background processing, and returns a unique tracking identifier without waiting for parsing or processing to finish.
3. **Given** a queued quality check job, **When** the background processing commences, **Then** the job state transitions to `PROCESSING` and parses the submitted resources.

---

### User Story 2 - Track Job Status and Inspect Ingestion Results (Priority: P2)

As a healthcare software engineer, I want to query the status of a previously submitted quality check job using its tracking identifier, so that I can monitor progress and inspect the resulting dataset summary once ingestion and parsing are complete.

**Why this priority**: Once a job is accepted asynchronously, clients require a deterministic mechanism to inspect job progress and retrieve execution results.

**Independent Test**: Can be tested independently by querying a job at each phase of its lifecycle (`QUEUED`, `PROCESSING`, `COMPLETED`, `FAILED`) using its tracking identifier and verifying that the returned status, timestamps, and resource summaries accurately reflect system state.

**Acceptance Scenarios**:

1. **Given** a job currently in progress, **When** the client queries the job status using its identifier, **Then** the system returns the current state (`QUEUED` or `PROCESSING`) and creation timestamp.
2. **Given** a successfully processed job, **When** the client queries the job status, **Then** the system returns `COMPLETED`, along with completion timestamps and summary metrics (total count of resources analyzed and resource type distribution).
3. **Given** a job that encountered a fatal parsing or execution failure, **When** the client queries the job status, **Then** the system returns `FAILED` with an actionable explanation of why processing failed.

---

### User Story 3 - Reject Malformed and Unsupported Payloads at Ingestion Boundary (Priority: P3)

As a client developer, I want the system to validate payload syntax at the intake boundary and reject non-JSON or structurally invalid submissions immediately with clear diagnostic feedback, so that I do not wait on an asynchronous job for simple formatting mistakes.

**Why this priority**: Fast failure at the boundary preserves system resources, avoids allocating background worker threads to invalid payloads, and provides instantaneous feedback to the caller.

**Independent Test**: Can be tested by submitting malformed JSON, empty bodies, non-JSON formats, or payloads lacking FHIR resource markers, and confirming that the intake boundary rejects them synchronously with diagnostic error messages.

**Acceptance Scenarios**:

1. **Given** an invalid or unparseable JSON payload, **When** the client submits it to the quality check intake, **Then** the system immediately rejects the submission with a descriptive error message without creating a background job.
2. **Given** a valid JSON object that lacks a recognized FHIR `resourceType` field, **When** the client submits it to the quality check intake, **Then** the system rejects the submission with an explanation that a FHIR resource or Bundle is required.
3. **Given** a client querying a non-existent or invalid job identifier, **When** the status is requested, **Then** the system returns a not found response with an explanatory message.

---

### User Story 4 - Privacy-Conscious Ingestion and Stateless Payload Handling (Priority: P4)

As a healthcare data privacy officer and compliance stakeholder, I want to ensure that submitted FHIR payloads containing clinical data or potential Protected Health Information (PHI) are never persisted to system databases or long-term disk storage, so that the service maintains a zero-retention posture for raw clinical data.

**Why this priority**: Healthcare data tools must respect patient privacy by default. Strictly limiting persistent data to operational job metadata and non-PHI metrics aligns with the core architectural constitution and prevents accidental disclosure of sensitive health data.

**Independent Test**: Can be tested by submitting a dataset with simulated sensitive clinical entries, completing the job lifecycle, and verifying that database records contain only job status, resource counts, timestamps, and diagnostic summaries, with zero raw payload data retained.

**Acceptance Scenarios**:

1. **Given** a submitted FHIR dataset with clinical data, **When** the job is accepted and processed, **Then** only job metadata (identifier, status, counts, timestamps) is stored in the persistent database.
2. **Given** a completed or failed job, **When** background execution concludes, **Then** all transient in-memory references to the raw FHIR payload are discarded and released for garbage collection.

---

### Edge Cases

- **Empty Bundle**: A valid FHIR Bundle containing zero entries (`entry: []`). The system must accept the submission, process it to completion, and report `0` resources analyzed without error.
- **Large Multi-Megabyte Bundles**: Payloads up to 10 MB containing thousands of resources. The intake boundary must ingest the stream without request timeouts or memory exhaustion, acknowledging receipt promptly.
- **Stalled / Interrupted Jobs**: If an active worker process terminates abruptly or a job stays in `PROCESSING` beyond a pre-configured timeout threshold, an automated watchdog or recovery sweep must mark the job as `FAILED` with an explanatory message rather than leaving it in an indefinite state.
- **Duplicate Submissions**: Each submission receives a unique job identifier regardless of whether the payload is identical to a prior submission.
- **Syntactically Valid JSON but Invalid FHIR Semantics**: When a payload passes JSON pre-flight checks but cannot be parsed by the FHIR parser during background execution, the job must gracefully transition to `FAILED` with a diagnostic message detailing the parser error.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide an intake interface that accepts FHIR R4 datasets (individual resources or multi-resource Bundles) formatted as JSON.
- **FR-002**: The system MUST perform synchronous pre-flight syntactic verification on incoming payloads to ensure the content is valid JSON and contains a declared FHIR `resourceType`.
- **FR-003**: The system MUST immediately acknowledge accepted submissions with an acceptance indicator, a unique tracking identifier (UUID), and a reference locator for checking job status.
- **FR-004**: The system MUST manage an asynchronous job lifecycle with explicit states: `QUEUED`, `PROCESSING`, `COMPLETED`, and `FAILED`.
- **FR-005**: The system MUST dispatch accepted payloads to an asynchronous background worker pool, transitioning the job state to `PROCESSING` when execution begins.
- **FR-006**: The system MUST parse the submitted dataset using standard FHIR R4 models to inspect resource structures and extract inventory counts without modifying the underlying resource definitions.
- **FR-007**: Upon successful parsing of the dataset, the system MUST transition the job state to `COMPLETED` and record ingestion metrics, including total resource count, counts per resource type, and processing timestamps.
- **FR-008**: If background parsing encounters unrecoverable structural or schema syntax errors, the system MUST transition the job state to `FAILED` and record an actionable diagnostic message detailing the reason for failure.
- **FR-009**: The system MUST provide a status retrieval interface allowing clients to query job progress, current state, timestamps, and completed metrics using the unique tracking identifier.
- **FR-010**: The system MUST immediately reject malformed payloads (invalid JSON syntax or missing top-level `resourceType`) at the boundary with an informative client error and without allocating background job resources.
- **FR-011**: The system MUST NOT persist raw clinical data or FHIR payload bodies to persistent storage; only operational job metadata (job ID, status, timestamps, resource counts, and diagnostic messages) may be stored.
- **FR-012**: The system MUST purge and release all in-memory references to the raw FHIR payload immediately upon job completion or termination.
- **FR-013**: The system MUST detect jobs that have remained in the `PROCESSING` state past a configurable execution threshold and transition them to `FAILED` to prevent perpetually hanging jobs.
- **FR-014**: The system MUST return a not found response when a client requests status for an unknown or nonexistent job identifier.

### Key Entities

- **Quality Check Job**: Represents the lifecycle of an asynchronous quality analysis request. Attributes include:
  - `id`: Unique identifier (UUID).
  - `status`: Current lifecycle phase (`QUEUED`, `PROCESSING`, `COMPLETED`, `FAILED`).
  - `createdAt`: Timestamp when the submission was accepted.
  - `startedAt`: Timestamp when background processing commenced.
  - `completedAt`: Timestamp when execution concluded (completed or failed).
  - `failureReason`: Descriptive explanation of error if status is `FAILED`.
- **Ingestion Metrics**: Operational summary data calculated during dataset ingestion. Attributes include:
  - `resourcesAnalyzed`: Total count of individual FHIR resources discovered in the payload.
  - `resourceTypeCounts`: Breakdown of resource count by specific FHIR resource type (e.g., Patient: 1, Observation: 12, Encounter: 2).
- **Submission Request**: The input structure provided by the client containing:
  - Target validation profile indicator (optional, defaults to base/US Core).
  - FHIR dataset content (single resource or collection Bundle).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The system returns an acceptance acknowledgment and unique tracking identifier within 500 milliseconds for 99% of submissions under 10 MB.
- **SC-002**: 100% of accepted jobs successfully transition to a terminal state (`COMPLETED` or `FAILED`) without dropping from the queue or remaining in an indefinite state.
- **SC-003**: For completed jobs, 100% of valid FHIR resources contained within the submission are accurately counted and categorized in the job summary metrics.
- **SC-004**: 100% of structurally malformed submissions (invalid JSON or missing `resourceType`) are rejected synchronously at the boundary within 200 milliseconds.
- **SC-005**: 0% of raw clinical resource contents or patient-identifiable data are written to persistent storage, maintaining a 100% compliance rate with zero-retention privacy constraints.
- **SC-006**: Status retrieval queries for existing jobs return within 100 milliseconds for 99% of requests.

## Assumptions

- **Format and Standard**: FHIR R4 in JSON format is the primary supported standard for Phase 1. XML representation and other FHIR releases (DSTU2, R5) are out of scope for this phase.
- **Scope Boundary**: Phase 1 focuses exclusively on payload ingestion, asynchronous execution management, baseline resource parsing, inventory metrics, and lifecycle querying. Deeper profile conformance validation, referential integrity graph generation, custom quality rules, and quality scoring will be added in subsequent phases.
- **Security & Network**: The initial API operates within a protected internal development environment; enterprise authorization, API key authentication, and role-based access control are deferred to later phases.
- **Client Integration Pattern**: Integrating clients monitor asynchronous task completion via polling the status retrieval interface.
- **Storage Policy**: Only job metadata, execution metrics, and diagnostic messages are retained in persistent storage; all clinical payloads remain transient in memory during processing.
