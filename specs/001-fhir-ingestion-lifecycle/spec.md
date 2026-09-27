# Feature Specification: Phase 1 — FHIR Dataset Ingestion, Parsing & Boundary Validation

**Feature Branch**: `001-fhir-ingestion-lifecycle`

**Created**: 2026-09-26 | **Amended**: 2026-09-26 (Constitution 2.0.0 & ADR-009 Realignment)

**Status**: Ready for Implementation

**Input**: Realignment to standalone CLI and pure Java core engine (ADR-009), preserving Phase 1's HAPI FHIR R4 parsing, Bundle unrolling, boundary validation, and zero-retention privacy principles.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Local Ingestion of FHIR Datasets via CLI & Core Engine (Priority: P1)

As a healthcare software engineer or data integrator, I want to submit a FHIR R4 dataset (single resource or multi-resource Bundle) from a local file, directory, or standard input (`stdin`) to FHIRLint, so that my dataset is parsed and ingested immediately without requiring background database services or cloud network connections.

**Why this priority**: Reliable parsing and dataset intake form the core foundation of FHIRLint. Without the ability to ingest datasets into memory via HAPI FHIR, subsequent validation and quality rules cannot function.

**Independent Test**: Can be tested by invoking the CLI `fhir-lint validate <path>` or `cat bundle.json | fhir-lint validate -` against a valid FHIR R4 resource or Bundle. The system immediately parses the dataset and outputs the inventory metrics and status to stdout.

**Acceptance Scenarios**:

1. **Given** a valid FHIR R4 JSON file (such as a Bundle), **When** a user runs `fhir-lint validate bundle.json`, **Then** the system parses the file in-memory using HAPI FHIR, extracts all resources, and outputs a summary of the ingested resources.
2. **Given** a stream of JSON piped into `stdin`, **When** a user runs `cat bundle.json | fhir-lint validate -`, **Then** the system reads from standard input, unrolls the Bundle entries, and outputs the ingestion summary.
3. **Given** a directory containing multiple FHIR JSON files, **When** a user runs `fhir-lint validate ./data-dir/`, **Then** the system scans and ingests all JSON files in the directory.

---

### User Story 2 - Immediate Ingestion Inventory & Distribution Metrics (Priority: P2)

As a healthcare data engineer, I want the ingestion pass to compute inventory metrics (total resource count and counts grouped by FHIR resource type), so that I have immediate visibility into the composition of the dataset.

**Why this priority**: Understanding dataset volume and resource distribution (e.g. 1 Patient, 15 Observations, 2 Encounters) is the primary baseline for computing defect density and quality scores.

**Independent Test**: Can be tested by submitting a Bundle with known resource counts (e.g. `clean-bundle.json`) and verifying that the output contains accurate total resource counts and per-type breakdowns.

**Acceptance Scenarios**:

1. **Given** a Bundle containing 5 resources (1 Patient, 1 Encounter, 1 Observation, 1 Condition, 1 MedicationRequest), **When** the ingestion completes, **Then** the report records `totalResources: 5` and an exact map of resource type counts.
2. **Given** an empty Bundle (`entry: []`), **When** the ingestion completes, **Then** the report records `totalResources: 0` without error.

---

### User Story 3 - Pre-Flight Boundary Verification & Graceful Failure (Priority: P3)

As a developer, I want FHIRLint to perform fast syntactic pre-flight verification on incoming files and streams, rejecting malformed JSON or non-FHIR payloads immediately with actionable diagnostics and standard exit codes (`2`), so that syntax mistakes are caught before expensive validation begins.

**Why this priority**: Fast failure preserves developer time and prevents uncaught parser exceptions from crashing the CLI.

**Independent Test**: Can be tested by running the CLI against non-existent files, unparseable JSON files, or JSON lacking a `resourceType` declaration, and confirming that the CLI exits with code `2` and displays a descriptive error to stderr.

**Acceptance Scenarios**:

1. **Given** an unparseable or truncated JSON file, **When** the user runs `fhir-lint validate malformed.json`, **Then** the CLI displays a clear JSON syntax error and terminates with exit code `2`.
2. **Given** a JSON file lacking a FHIR `resourceType` field, **When** the user runs `fhir-lint validate non-fhir.json`, **Then** the CLI terminates with exit code `2` and explains that a valid FHIR resource or Bundle is required.
3. **Given** a file path that does not exist, **When** the user executes the command, **Then** the CLI reports file not found and exits with code `2`.

---

### User Story 4 - Strict Zero-Retention In-Memory Privacy (Priority: P4)

As a healthcare data privacy officer and compliance stakeholder, I want to ensure that clinical data is never written to persistent disk storage, databases, or external telemetry, maintaining a 100% zero-retention posture.

**Why this priority**: Healthcare privacy requires minimizing data proliferation. Operating entirely in volatile RAM during the CLI execution guarantees no residual PHI remains on disk.

**Independent Test**: Can be verified by running the linter against a dataset with synthetic clinical records and auditing file system and network calls to confirm zero persistence or remote calls.

**Acceptance Scenarios**:

1. **Given** a dataset containing clinical records, **When** the CLI executes, **Then** data is processed in-memory and discarded upon process termination without creating disk cache files.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST accept FHIR R4 datasets from local files, directory paths, or standard input (`stdin`).
- **FR-002**: The system MUST perform fast pre-flight syntactic verification, ensuring the input is valid JSON and contains a declared FHIR `resourceType`.
- **FR-003**: The system MUST parse JSON payloads into strongly-typed FHIR R4 resources using HAPI FHIR (`JsonParser`).
- **FR-004**: If the payload is a FHIR `Bundle`, the system MUST iterate through and extract all individual entries into an in-memory indexed resource collection.
- **FR-005**: If the payload is an individual FHIR resource (e.g. `Patient`), the system MUST treat it as a single-element dataset collection.
- **FR-006**: The system MUST calculate inventory metrics: total resources analyzed and resource count per FHIR resource type (`Patient`, `Observation`, `Encounter`, etc.).
- **FR-007**: The system MUST return exit code `2` and an actionable diagnostic error to `stderr` when input is unparseable, missing `resourceType`, or file not found.
- **FR-008**: The system MUST operate entirely in-memory with zero persistent database or disk cache storage.

### Success Criteria

- **SC-001**: Sub-second execution: in-memory parsing and inventory extraction of a 1,000-resource Bundle completes in under 500 milliseconds.
- **SC-002**: 100% of structurally malformed inputs are caught at the pre-flight boundary with exit code `2`.
- **SC-003**: 100% zero data retention: no temporary files or database records written to disk.
