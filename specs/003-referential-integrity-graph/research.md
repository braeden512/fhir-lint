# Technical Research: Phase 3 — Referential Integrity and Resource Graph

## Overview

Referential integrity analysis evaluates dataset-level resource relationships to catch defects that single-resource schema validators cannot detect: broken local references, target type mismatches, orphaned clinical records, and unverified external links.

This document consolidates architectural decisions, algorithmic choices, and technical trade-offs adhering to [ADR-003](file:///home/braeden/projects/fhir-lint/docs/adr/ADR-003-phase-3-referential-integrity-and-resource-graph.md), [PRODUCT_SPEC.md](file:///home/braeden/projects/fhir-lint/PRODUCT_SPEC.md), and [Constitution 2.0.0](file:///home/braeden/projects/fhir-lint/.specify/memory/constitution.md).

---

## Technical Decisions

### Decision 1: Reference Extraction & Element Path Tracking

- **Context**: Every `Reference` in every FHIR resource must be identified. To validate expected target types (`REF-002`) and report actionable FHIRPaths (`Observation.subject.reference`), the extraction mechanism must preserve the element property name and index as it traverses.
- **Decision**: Implement a lightweight recursive AST visitor over HAPI's `org.hl7.fhir.r4.model.Base` children using `Base.children()`, combined with HAPI's `FhirTerser` for reference resolution.
- **Rationale**: 
  - `Base.children()` exposes each `Property` along with its property name and child values.
  - As the visitor recurses through complex types (e.g. backbone elements, extensions, components), it maintains a breadcrumb stack producing standard FHIRPaths (e.g. `DiagnosticReport.result[0].reference` or `Observation.performer[1].reference`).
  - Completely framework-agnostic, pure Java 21, and operates directly on HAPI R4 objects without re-parsing raw JSON.
- **Alternatives Considered**:
  - *HAPI `FhirTerser.getAllEmbeddedResources(resource, false)`*: Only returns embedded sub-resources, not `Reference` primitive elements.
  - *HAPI `FhirTerser.getValues(resource, path)`*: Requires pre-defining every potential reference path across all 145+ FHIR R4 resource types, which is fragile and incomplete.
  - *Custom JSON AST / Jackson tree traversal*: Duplicates HAPI's model and violates Constitution Principle I (Standards-First Healthcare Interoperability).

---

### Decision 2: In-Memory Dual-Map Graph Index (`ResourceGraphIndex`)

- **Context**: References in FHIR datasets appear in multiple formats:
  1. Transaction/Bundle UUIDs: `urn:uuid:12345678-1234-1234-1234-123456789abc`
  2. Relative references: `Patient/pat-1` or `Condition/cond-99`
  3. Absolute URLs matching bundle fullUrl: `https://example.org/fhir/Patient/pat-1`
  4. Contained resource fragments: `#contained-id`
  5. Standalone/bare IDs: `pat-1` (unqualified ID lookups)
- **Decision**: Adopt a pure Java, thread-safe, in-memory dual-map indexing architecture using core Java collections (`HashMap`, `ArrayList`):
  - `fullUrlIndex`: `Map<String, ResourceNode>` matching `fullUrl` and absolute URLs.
  - `typeAndIdIndex`: `Map<String, ResourceNode>` with key `${resourceType}/${id}` (e.g. `Patient/123`).
  - `bareIdIndex`: `Map<String, List<ResourceNode>>` with key `${id}` to detect disambiguation collisions.
  - `containedIndex`: Scoped per `ResourceNode`, indexing fragments against the resource's `contained` list.
- **Rationale**:
  - Guarantees $O(1)$ lookup time for any reference format during the edge resolution pass.
  - Ensures total indexing and traversal complexity remains $O(N + E)$, where $N$ is total resources and $E$ is total reference edges.
  - Memory footprint is negligible (< 15 MB for 10,000 resources), well within the 64 MB budget.
  - Strictly stateless and zero-retention, satisfying Constitution Principles II and V.
- **Alternatives Considered**:
  - *Embedded Graph Database (Neo4j, Apache TinkerPop, OrientDB)*: Heavy external dependencies, JVM startup overhead, and violates Constitution Principle II (Lean, Dependency-Minimized Architecture) and Principle VI.
  - *In-Memory SQLite / H2 with SQL Joins*: Relational tables add ORM/JDBC complexity, slow query overhead, and risk persistent temporary files.

---

### Decision 3: Schema-Permitted Target Type Lookup for `REF-002`

- **Context**: When a reference resolves to an existing target node, the system must verify whether the target's `ResourceType` is legal for that specific reference element (e.g., `Observation.subject` allows `Patient`, `Group`, `Device`, `Location`, but rejects `MedicationRequest`).
- **Decision**: Query HAPI FHIR's cached R4 `RuntimeResourceDefinition` and `RuntimeChildResourceDefinition` using the source resource type and the extracted property path.
- **Rationale**:
  - HAPI's `FhirContext` already preloads the complete HL7 FHIR R4 metamodel into memory.
  - The runtime child definitions expose `getTargetResourceTypes()`, providing the official set of valid target types without manual configuration or external downloads.
  - Completely offline and 100% compliant with standard HL7 R4 semantics.
- **Alternatives Considered**:
  - *Hardcoded Map of Common Reference Fields*: Inflexible, error-prone, and misses hundreds of valid reference fields across the 145+ FHIR R4 resource definitions.
  - *Querying StructureDefinition XML/JSON Files at Runtime*: Unnecessary file I/O overhead; the HAPI `FhirContext` already contains this parsed metamodel in memory.

---

### Decision 4: Topological Patient Reachability & Orphan Detection (`REF-003`)

- **Context**: Clinical resources (`Observation`, `Condition`, `DiagnosticReport`) must not exist in clinical isolation; they require attribution to a patient context. The link may be direct (`Observation.subject` $\to$ `Patient`) or indirect (`Observation` $\to$ `Encounter` $\to$ `Patient`, or `DiagnosticReport.result` $\to$ `Observation`).
- **Decision**: Implement a Breadth-First Search (BFS) graph reachability algorithm on the reference index:
  1. For each candidate resource in `{Observation, Condition, DiagnosticReport}`:
  2. Check outgoing reference edges: if any edge points directly to a `Patient`, mark as reachable.
  3. If an edge points to an `Encounter`, recursively/BFS evaluate whether that `Encounter` reaches a `Patient`.
  4. Check incoming reference edges: if a parent `DiagnosticReport` or `Composition` links to this resource and reaches a `Patient`, mark as reachable.
  5. Maintain a `visited` set to prevent infinite loops in cyclic references (e.g. self-referential encounters or circular observation links).
  6. If no path to a `Patient` exists, emit `REF-003` (`WARNING`).
- **Rationale**:
  - Captures genuine clinical workflows without false positives.
  - $O(V + E)$ worst-case traversal bounded by the clinical subgraph; with caching of resolved reachability status, execution is instantaneous.
- **Alternatives Considered**:
  - *Direct-Reference-Only Check*: Inspecting only `subject` or `patient` elements on the resource. Rejected because it produces false-positive warnings on observations linked through encounters or parent diagnostic reports.
  - *Tarjan's Strongly Connected Components*: Over-engineered for simple path reachability to patient sinks.

---

### Decision 5: Multi-File Directory Batch Aggregation

- **Context**: When a user runs `fhir-lint validate ./data-dir/`, resources from the same patient or clinical encounter may be split across files (`patient.json`, `encounters.json`, `observations.json`).
- **Decision**: In `FhirLinter.lint(List<File>)`, parse all files in the batch, combine all extracted resources into a unified collection, build a single aggregated `ResourceGraphIndex`, and execute referential analysis across the entire dataset.
- **Rationale**:
  - Eliminates false-positive `REF-001` missing reference defects caused by file boundary partitioning.
  - Aligns with FR-001 and POSIX CLI expectations.
- **Alternatives Considered**:
  - *Per-file isolated validation*: Causes widespread false positives for multi-file exports.
  - *Pre-merging JSON files on disk*: Violates zero-persistence principle and creates temporary file management overhead.
