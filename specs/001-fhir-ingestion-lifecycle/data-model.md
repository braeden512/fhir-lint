# Data Model Specification: Phase 1 — In-Memory Dataset & Ingestion Models

This document defines the stateless in-memory data models, inventory metrics, and parsing contracts for Phase 1.

---

## 1. Domain Models & Value Objects

### 1.1 `ParsedDataset`
Represents the result of parsing a FHIR R4 JSON input into memory.

| Field | Type | Description |
| :--- | :--- | :--- |
| `rawResource` | `IBaseResource` | Root parsed HAPI FHIR R4 resource (typically a `Bundle` or single resource like `Patient`). |
| `resources` | `List<IBaseResource>` | Flattened list of all individual resources (unrolled from Bundle entries). |
| `inventory` | `IngestionInventory` | Extracted inventory metrics (counts and distribution). |
| `isBundle` | `boolean` | True if the root resource is a Bundle. |

---

### 1.2 `IngestionInventory`
Structured summary of the resources discovered during ingestion:

| Field | Type | Description |
| :--- | :--- | :--- |
| `totalResources` | `int` | Total count of individual FHIR resources in the dataset. |
| `resourceTypeCounts` | `Map<String, Integer>` | Count per FHIR resource type (e.g., `Patient: 1`, `Observation: 6`). |
| `parseDurationMs` | `long` | Elapsed time in milliseconds spent parsing the dataset. |

---

### 1.3 `LintReport`
The root report object produced by FHIRLint:

| Field | Type | Description |
| :--- | :--- | :--- |
| `inventory` | `IngestionInventory` | Resource count breakdown. |
| `issues` | `List<QualityIssue>` | List of detected quality issues (empty in Phase 1 baseline). |
| `qualityScore` | `QualityScore` | Deterministic score (100 for clean baseline ingestion). |
| `status` | `LintStatus` | `PASSED`, `FAILED`, `SYNTAX_ERROR`. |
| `executionTimeMs` | `long` | Total execution time. |

---

## 2. Ingestion & Memory Lifecycle

```
[Input: File / Directory / Stdin]
       │
       ▼ (Raw JSON String / Stream)
┌──────────────────────────────────────┐
│ FhirBundleParser (HAPI FHIR)         │
│ 1. Pre-flight syntax verification    │
│ 2. Deserializes into IBaseResource   │
│ 3. Unrolls Bundle.entry resources    │
│ 4. Extracts IngestionInventory       │
└──────────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────┐
│ ParsedDataset (In-Memory Collection) │
└──────────────────────────────────────┘
       │ (Passed to CLI Renderers: Table / JSON / SARIF)
       ▼
[Process Exits -> Memory Automatically Reclaimed by OS]
```

**Privacy Guarantee**: Zero disk caches, zero database persistence. Clinical data exists only in volatile memory during the command run.
