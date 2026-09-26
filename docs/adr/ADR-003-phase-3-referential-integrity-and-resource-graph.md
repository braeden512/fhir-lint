# ADR-003: Phase 3 — Referential Integrity and Resource Graph Indexing

## Status
Accepted

## Context & Problem Statement
A fundamental flaw in existing FHIR validators is their inability to evaluate dataset-level referential integrity within submitted Bundles. A FHIR resource like `Observation/obs-123` may contain `"subject": {"reference": "Patient/pat-999"}`. Standard schema validation only verifies that the reference string matches the regex for a reference; it does not check whether `Patient/pat-999` actually exists in the Bundle.

Downstream consumers (EHRs, data warehouses, analytics pipelines) fail when ingesting data with broken references. FHIRLint must construct an in-memory graph representation of the Bundle, resolve all references, and detect dangling references, type mismatches, and orphaned resources.

## Decision Drivers
1. **Multi-format Reference Resolution**: Must support relative references (`Patient/123`), UUID URNs (`urn:uuid:...`), and canonical absolute URLs.
2. **Speed & Memory Efficiency**: Graph construction and reference matching must execute in $O(N)$ time with minimal memory overhead.
3. **Actionable Diagnostics**: When a reference is broken, the report must clearly state which field points where, what target was expected, and suggested remediation.

## Considered Options
1. **Embedded Graph Database (e.g., Neo4j / embedded TinkerPop)**: High complexity, heavy dependencies, violates Constitution Principle VI.
2. **In-Memory Graph Index using Core Java Collections**: A lightweight index mapping resource identifiers (`fullUrl`, `type/id`) to AST nodes, accompanied by an edge list.
3. **Database-backed relational graph**: Inserting resources into temporary PostgreSQL tables and running SQL joins. (Slow, violates privacy requirement against storing raw FHIR in DB).

## Decision Outcome
Adopt **Option 2: In-Memory Graph Index using Core Java Collections (`ResourceGraphIndex`)**.

### Architecture & Algorithm
1. **Index Construction ($O(N)$ Pass 1)**:
   - Walk all entries in the parsed `Bundle`.
   - Populate `NodeIndex`:
     - Map `fullUrl` $\to$ `ResourceNode`
     - Map `${resourceType}/${id}` $\to$ `ResourceNode`
     - Map `${id}` $\to$ List of `ResourceNode` (for ambiguous lookups)
2. **Edge Extraction & Resolution ($O(E)$ Pass 2)**:
   - Using HAPI's `FhirTerser`, extract all `org.hl7.fhir.r4.model.Reference` instances across all resources.
   - For each reference:
     - Match `Reference.getReference()` against `NodeIndex`.
     - **Resolved**: Verify that target `ResourceType` matches expected type (e.g. `Observation.subject` must point to `Patient`, `Group`, `Device`, or `Location`). If mismatched, produce `REF-TYPE-MISMATCH`.
     - **Unresolved**:
       - If reference is local (`urn:uuid:` or relative `Type/id` within a self-contained bundle): produce `REF-BROKEN-LOCAL` (Severity: `ERROR`).
       - If reference is an absolute external HTTP URL: produce `REF-EXTERNAL-UNRESOLVED` (Severity: `INFO` / `WARNING` based on strictness mode).
3. **Orphan & Disconnection Analysis**:
   - Calculate incoming edge counts. Resources that require clinical context (e.g., `Observation`, `DiagnosticReport`, `Condition`) with zero incoming and zero outgoing links to a `Patient` or `Encounter` are flagged with `REF-ORPHANED-RESOURCE`.

## Consequences
### Positive
- Blazing-fast in-memory resolution ($O(N + E)$).
- Detects broken references that cause catastrophic downstream database integrity crashes.
- Zero external database or graph engine dependencies.

### Negative / Trade-offs
- Dataset size is bounded by JVM memory. Large bundles (>50MB) must be capped or processed in chunks.
