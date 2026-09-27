# Feature Specification: Phase 3 — Referential Integrity and Resource Graph

**Feature Branch**: `003-referential-integrity-graph`

**Created**: 2026-09-27

**Status**: Draft

**Input**: User description: "Create the spec for phase 2. Use the documentation in docs , specifically docs/adr/ADR-003-phase-3-referential-integrity-and-resource-graph.md as well as the PRODUCT_SPEC.md to guide your thinking."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Detection of Broken and Dangling Local References (Priority: P1)

As a healthcare data engineer or application developer, I want FHIRLint to analyze all inter-resource references within a dataset and detect broken or dangling links (e.g., an `Observation` referencing a `Patient/pat-999` or `urn:uuid:missing-id` that does not exist in the dataset), so that I can prevent referential integrity failures and database foreign key crashes before ingesting the data into downstream systems.

**Why this priority**: Broken references are one of the most critical defects in healthcare data exchange. Standard FHIR schema validators only check that reference strings match regex patterns; they fail to verify whether the target resource actually exists. Ingesting broken references corrupts clinical records and causes downstream ETL pipelines and EHR integrations to fail catastrophically.

**Independent Test**: Can be tested independently by submitting a Bundle containing resources with known broken relative references (`Patient/nonexistent`) or broken UUID URNs (`urn:uuid:00000000-0000-0000-0000-000000000000`) and verifying that the system reports diagnostic issues with rule `REF-001`, severity `ERROR`, exact source element paths, and remediation guidance.

**Acceptance Scenarios**:

1. **Given** a Bundle containing an `Observation` whose `subject.reference` points to `Patient/pat-missing`, and no `Patient` with ID `pat-missing` exists in the dataset, **When** referential integrity validation is performed, **Then** the system detects the broken reference and reports a `REF-001` error issue specifying the exact location `Observation.subject.reference`, the missing target `Patient/pat-missing`, and concrete remediation advice.
2. **Given** a transaction or collection Bundle where an `Encounter` references `urn:uuid:7b420ee9-4c8d-4f15-99d8-111111111111`, and no resource entry has that `fullUrl`, **When** referential integrity validation is performed, **Then** the system reports a `REF-001` error identifying the unresolved UUID URN reference.
3. **Given** a dataset where all relative references (`Type/id`) and URNs (`urn:uuid:...`) match corresponding resources within the dataset, **When** referential integrity validation is performed, **Then** zero `REF-001` broken reference errors are reported.
4. **Given** a resource containing a contained fragment reference (e.g. `#inline-org`) pointing to an entry in its own `contained` list, **When** referential integrity validation is performed, **Then** the fragment reference resolves successfully to the contained resource without triggering a missing reference defect.

---

### User Story 2 - Detection of Reference Target Type Mismatches (Priority: P2)

As a healthcare software engineer, I want FHIRLint to verify that resolved references point to resources of the expected resource type (e.g., ensuring `Observation.subject` points to a `Patient`, `Group`, `Device`, or `Location` rather than a `MedicationRequest` or `Organization`), so that type incompatibilities do not trigger runtime deserialization and schema cast errors in downstream applications.

**Why this priority**: Even if a referenced resource ID exists within a dataset, pointing to the wrong resource type violates FHIR semantic constraints and causes subtle data corruption or unexpected exceptions in clinical processing logic.

**Independent Test**: Can be tested independently by supplying a dataset where an `Observation` has `subject.reference` pointing to an existing `Condition` or `MedicationRequest`, and verifying that the system produces a `REF-002` diagnostic issue with `ERROR` severity, stating the actual versus expected resource type.

**Acceptance Scenarios**:

1. **Given** an `Observation` whose `subject.reference` points to an existing `Condition/cond-001`, **When** referential integrity validation is performed, **Then** the system reports a `REF-002` error issue indicating that the target resource type `Condition` is invalid for `Observation.subject` (which expects `Patient`, `Group`, `Device`, or `Location`).
2. **Given** a `Condition` whose `encounter.reference` points to an existing `Encounter/enc-001`, **When** referential integrity validation is performed, **Then** the system verifies the target is indeed an `Encounter` and reports no type mismatch issues.
3. **Given** any resource reference where the target exists and matches one of the valid target types defined for that reference field in the FHIR R4 specification, **When** referential integrity validation is performed, **Then** zero `REF-002` type mismatch errors are reported.

---

### User Story 3 - Detection of Orphaned and Context-Isolated Clinical Resources (Priority: P3)

As a clinical data quality analyst, I want FHIRLint to identify orphaned clinical resources (such as `Observation`, `DiagnosticReport`, or `Condition`) that lack essential clinical context linkages to any `Patient` or `Encounter` in the dataset, so that disconnected records without clinical ownership or visit context are flagged for remediation.

**Why this priority**: Healthcare records are fundamentally patient-centric and encounter-centric. Observations or Conditions existing in isolation without an associated subject or clinical encounter represent incomplete or unlinked clinical data that cannot be attributed or safely displayed in patient charts.

**Independent Test**: Can be tested independently by validating a dataset containing an `Observation` that has no `subject` or `encounter` reference (or zero incoming/outgoing links connecting it to a `Patient`), and verifying that the system generates a `REF-003` diagnostic issue with `WARNING` severity.

**Acceptance Scenarios**:

1. **Given** an `Observation`, `Condition`, or `DiagnosticReport` resource in a dataset with no reference to a `Patient` or `Encounter`, and no other resource in the dataset linking to it as a patient-associated component, **When** referential integrity validation is performed, **Then** the system reports a `REF-003` warning issue highlighting the orphaned clinical resource.
2. **Given** a clinical resource (`Observation`, `Condition`, `DiagnosticReport`) linked to an `Encounter` that itself has no direct or indirect linkage to any `Patient` in the dataset, **When** referential integrity validation is performed, **Then** the system reports a `REF-003` warning issue identifying the missing root patient context.
3. **Given** clinical resources that are linked directly or transitively to a `Patient` (e.g. via `subject`, `patient`, or through an `Encounter` that resolves to a `Patient`), **When** referential integrity validation is performed, **Then** zero `REF-003` orphaned resource warnings are emitted for those resources.

---

### User Story 4 - Differentiated Handling of External and Absolute URI References (Priority: P4)

As an integration engineer validating datasets with federated or external references (such as canonical URLs or external absolute HTTP/HTTPS endpoints), I want FHIRLint to distinguish between local internal references and external references, so that external links do not generate false-positive missing-local-resource errors while still being transparently summarized.

**Why this priority**: Bundles in distributed healthcare environments frequently reference external resources (e.g. `https://external-ehr.org/fhir/Patient/123` or canonical profile URLs). Treating these external URLs as missing local bundle entries would produce noisy false alarms, undermining developer trust in the linter.

**Independent Test**: Can be tested independently by submitting a dataset containing valid absolute HTTP/HTTPS references and verifying that the system does not produce `REF-001` missing local reference errors for those external links.

**Acceptance Scenarios**:

1. **Given** a resource containing an absolute external URI reference (e.g. `https://terminology.hl7.org/...` or `https://hospital.org/fhir/Patient/ext-1`), **When** referential integrity validation is performed, **Then** the system does not flag the reference as a `REF-001` broken local reference error.
2. **Given** an absolute reference whose base matches the Bundle's own declared base URL or `fullUrl` entries, **When** referential integrity validation is performed, **Then** the system resolves the reference internally and verifies target existence.
3. **Given** an unresolvable external absolute HTTP/HTTPS reference outside the dataset perimeter, **When** referential validation is executed in standard offline mode, **Then** the system reports the external reference as informational under rule `REF-004` (`INFO`) without blocking the dataset quality gate.

---

### Edge Cases

- **Self-Referential Links**: A resource that references itself directly (e.g. an `Encounter` referencing itself in `partOf`) or forms a circular reference loop (Resource A $\to$ Resource B $\to$ Resource A) must not cause infinite recursion, memory exhaustion, or graph traversal lockups.
- **Identical IDs Across Different Resource Types**: A dataset containing `Patient/1` and `Observation/1` must resolve references with type-scoped specificity (`Patient/1` resolves strictly to the Patient node, not the Observation node).
- **Ambiguous Relative References**: A reference specifying only an ID without a resource type prefix when multiple resources share that ID must be flagged as an unresolvable reference under `REF-001` (`ERROR`).
- **Empty or Whitespace-Only References**: Reference elements containing empty strings (`"reference": ""`), whitespace, or null must be reported as malformed reference defects under `REF-001` (`ERROR`) rather than crashing the resolver.
- **Contained Resource Scope**: References to contained resources (`#sub-resource`) must be scoped strictly to the enclosing parent resource and must not resolve across different root resources; an unresolvable `#contained` reference must be reported under `REF-001` (`ERROR`).
- **Datasets with Zero References**: Datasets containing only standalone resources (e.g. a list of independent Patient records with no outgoing references) must process cleanly and produce zero referential defects.
- **Deeply Nested References**: References located deep within nested components, backbone elements, or extensions must be discovered and validated with full element path tracking.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST extract and index all resource identifiers across the submitted dataset—including aggregating all resources across all input files when a multi-file directory or collection is analyzed—capturing full URLs (`fullUrl`), type-qualified identifiers (`${resourceType}/${id}`), and standalone resource IDs (`${id}`).
- **FR-002**: System MUST identify all outgoing references (`Reference` elements) across every resource in the dataset, including relative references, URN UUIDs/OIDs, contained fragment references, and absolute URLs.
- **FR-003**: System MUST resolve relative references (`${resourceType}/${id}`) against indexed resources in the dataset.
- **FR-004**: System MUST resolve URN references (`urn:uuid:...` and `urn:oid:...`) against indexed `fullUrl` entries in the dataset.
- **FR-005**: System MUST resolve contained fragment references (`#<id>`) against the containing parent resource's `contained` element list.
- **FR-006**: System MUST report a `REF-001` error issue with exact element path and target identifier when a local relative reference (`Type/id`), UUID/OID URN, contained fragment (`#<id>`), unresolved bare ID, or empty/whitespace reference cannot be resolved in the dataset or containing resource.
- **FR-007**: System MUST track source property paths during reference extraction to look up expected target resource types defined by the FHIR specification and validate that resolved reference targets conform to those permitted types.
- **FR-008**: System MUST report a `REF-002` error issue with exact element path, actual target type, and expected target types when a resolved reference target type violates schema expectations.
- **FR-009**: System MUST perform topological relationship analysis to evaluate context reachability for clinical resources strictly bounded to `Observation`, `Condition`, and `DiagnosticReport`, verifying that each such resource has a direct or indirect path in the reference graph reaching a `Patient` (or an `Encounter` that itself resolves to a `Patient`).
- **FR-010**: System MUST report a `REF-003` warning issue when an orphaned or context-isolated clinical resource (`Observation`, `Condition`, `DiagnosticReport`) lacking patient context reachability is detected.
- **FR-011**: System MUST distinguish external absolute HTTP/HTTPS references outside the dataset's base URL or `fullUrl` perimeter from local references, reporting unverified external references under rule `REF-004` (`REF-EXTERNAL-UNRESOLVED`) with `INFO` severity rather than `REF-001` `ERROR` severity, ensuring external links do not block the dataset quality gate during standard offline linting.
- **FR-012**: System MUST include actionable remediation guidance and the exact FHIRPath location for every detected referential integrity issue.
- **FR-013**: System MUST provide the referential integrity findings to the scoring and reporting layers, categorizing all referential findings under the `REFERENTIAL_INTEGRITY` category.
- **FR-014**: System MUST perform graph indexing and reference verification entirely in memory without persistent databases, external network requests, or disk caching.

### Key Entities

- **Resource Node**: An in-memory representation of an individual FHIR resource in the dataset, capturing its primary identity (`fullUrl`, `resourceType`, `id`), incoming references, and outgoing references.
- **Resource Reference**: A directed relationship link originating from a specific field path in a source resource and pointing to a target resource via relative path, URN, canonical URL, or internal fragment.
- **Resource Graph Index**: The in-memory dataset-level relationship graph maintaining index lookups across all resource nodes and directed edges between them.
- **Referential Integrity Issue**: A specialized data quality issue belonging to the `REFERENTIAL_INTEGRITY` category, detailing the violated rule (`REF-001`, `REF-002`, `REF-003`, `REF-004`), severity (`ERROR`, `WARNING`, `INFO`), source location path, target reference string, and remediation instructions.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of missing local relative references (`Type/id`), unresolvable UUID URNs (`urn:uuid:...`), broken contained fragments (`#<id>`), and empty references in test datasets are detected and reported under rule `REF-001` with `ERROR` severity.
- **SC-002**: 100% of reference target type mismatches are detected and reported under rule `REF-002` with `ERROR` severity, correctly stating expected vs actual types.
- **SC-003**: 100% of orphaned clinical resources (`Observation`, `Condition`, `DiagnosticReport` without direct or indirect Patient context) are identified and reported under rule `REF-003` with `WARNING` severity.
- **SC-004**: 0% false-positive `REF-001` broken reference errors for valid relative references, matching `urn:uuid` references, contained fragment references, or external absolute URLs.
- **SC-005**: Referential integrity graph construction and analysis for a dataset containing 5,000 resources and 20,000 references executes in under 1.0 second on standard developer hardware.
- **SC-006**: Graph construction and reference traversal operate with $O(N + E)$ computational complexity and require no more than 64 MB of additional working memory for 10,000 resources.
- **SC-007**: 100% of reported referential integrity issues provide actionable remediation advice and exact FHIRPath element paths.

## Assumptions

- Datasets submitted for referential integrity analysis have already been parsed and ingested as FHIR R4 resources through the ingestion lifecycle (Phase 1).
- When multi-file directory inputs or batch collections are processed, all resources across all files in the batch are aggregated into a single unified dataset index prior to reference resolution.
- Analysis is executed strictly in-memory with zero data retention and zero external network access, adhering to Constitution Principles II and V.
- External absolute HTTP/HTTPS references point to external systems outside the dataset perimeter and are not fetched over the network during offline zero-retention analysis.
- Permitted target resource types for reference validation adhere to the standard HL7 FHIR R4 resource definitions, resolved via source property path context.
- Referential integrity analysis executes as a distinct inspection phase, with findings feeding directly into the category scoring formula for `referentialIntegrity` (25% overall score weight).
