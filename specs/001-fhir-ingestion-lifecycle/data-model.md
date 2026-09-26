# Data Model Specification: Phase 1 — FHIR Ingestion & Asynchronous Job Lifecycle

This document defines the persistent and transient data models, database schemas, validation invariants, and state transition rules for Phase 1.

---

## 1. Entities & Value Objects

### 1.1 `QualityCheckJob` (JPA Entity / Table: `quality_check_jobs`)

Represents an individual asynchronous quality check execution request.

| Field | Type | Nullable | Description | Validation / Constraints |
| :--- | :--- | :---: | :--- | :--- |
| `id` | `UUID` | No | Primary key, globally unique job identifier. | Generated via UUID v4. |
| `status` | `JobStatus` (VARCHAR 32) | No | Current lifecycle status of the job. | Must be one of `QUEUED`, `PROCESSING`, `COMPLETED`, `FAILED`. |
| `target_profile` | `VARCHAR(64)` | Yes | Optional requested profile constraint (e.g. `US_CORE`, `BASE_R4`). | Defaults to `BASE_R4` if unspecified. |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | No | Exact timestamp when the request was accepted at the HTTP boundary. | Immutable once created. Set to current UTC time. |
| `started_at` | `TIMESTAMP WITH TIME ZONE` | Yes | Timestamp when the asynchronous worker began processing. | Must be $\ge$ `created_at`. Null if still `QUEUED`. |
| `completed_at` | `TIMESTAMP WITH TIME ZONE` | Yes | Timestamp when processing concluded (success or failure). | Must be $\ge$ `started_at`. Null until terminal state reached. |
| `failure_reason` | `TEXT` | Yes | Human-readable explanation if job reached `FAILED`. | Null if `status != FAILED`. Max 4000 characters. |
| `resources_analyzed` | `INTEGER` | Yes | Total count of FHIR resources discovered in the payload. | Must be $\ge 0$. Null until `COMPLETED`. |
| `resource_type_counts` | `JSONB` | Yes | Key-value mapping of FHIR resource types to their occurrence count (e.g. `{"Patient": 1, "Observation": 10}`). | Valid JSON object mapping string to integer. Null until `COMPLETED`. |

> **Privacy Invariant (Constitution Principle V)**: The entity and table MUST NOT contain columns for raw FHIR payload JSON, raw clinical content, or patient-identifiable data.

---

### 1.2 `JobStatus` (Enumeration)

Defines the lifecycle state machine of an ingestion job.

```
          [Client Submit]
                 │
                 ▼
             ┌────────┐
             │ QUEUED │
             └────────┘
                 │ (Worker picks up task)
                 ▼
          ┌────────────┐
          │ PROCESSING │
          └────────────┘
            │        │
 (Success)  │        │ (Parsing error or Watchdog timeout)
            ▼        ▼
     ┌───────────┐ ┌────────┐
     │ COMPLETED │ │ FAILED │
     └───────────┘ └────────┘
```

- **`QUEUED`**: The submission passed pre-flight boundary validation and was persisted in PostgreSQL. A worker task has been dispatched.
- **`PROCESSING`**: A worker thread has begun reading and parsing the transient payload with HAPI FHIR.
- **`COMPLETED`**: Parsing finished successfully. Resource counts and type distribution are recorded. Transient payload memory is released.
- **`FAILED`**: Parsing encountered an unrecoverable syntax error or the watchdog swept a stalled job. Error details recorded.

---

### 1.3 `IngestionMetrics` (Embeddable / Response DTO)

Structured representation of the results extracted during ingestion:

| Field | Type | Description |
| :--- | :--- | :--- |
| `resourcesAnalyzed` | `int` | Total count of valid resources discovered in the payload. |
| `resourceTypeCounts` | `Map<String, Integer>` | Distribution of counts indexed by FHIR resource type name. |
| `durationMs` | `long` | Time elapsed in milliseconds between `startedAt` and `completedAt`. |

---

## 2. Relational Database Schema (PostgreSQL DDL)

```sql
CREATE TABLE IF NOT EXISTS quality_check_jobs (
    id UUID PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    target_profile VARCHAR(64) DEFAULT 'BASE_R4',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    failure_reason TEXT,
    resources_analyzed INTEGER,
    resource_type_counts JSONB,
    CONSTRAINT chk_job_status CHECK (status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_resources_analyzed CHECK (resources_analyzed IS NULL OR resources_analyzed >= 0)
);

CREATE INDEX IF NOT EXISTS idx_quality_check_jobs_status_created 
ON quality_check_jobs (status, created_at);

CREATE INDEX IF NOT EXISTS idx_quality_check_jobs_started_at 
ON quality_check_jobs (started_at) 
WHERE status = 'PROCESSING';
```

---

## 3. Data Flow & Memory Lifecycle

```
[Client HTTP POST]
       │
       ▼ (Raw JSON InputStream)
┌──────────────────────────────────────┐
│ QualityCheckController               │
│ - Pre-flight JSON check              │
│ - Extracts `resourceType`            │
└──────────────────────────────────────┘
       │ Valid
       ▼
┌──────────────────────────────────────┐
│ QualityCheckService                  │
│ - Generates UUID                     │
│ - Saves Job(QUEUED) to PostgreSQL    │
│ - Hands payload to Async Executor    │
└──────────────────────────────────────┘
       │ Returns 202 Accepted immediately
       ▼
┌──────────────────────────────────────┐
│ Async IngestionWorker (Thread Pool)  │
│ 1. Updates Job to `PROCESSING`       │
│ 2. Parses via HAPI FHIR IParser      │
│ 3. Counts resources & types          │
│ 4. Updates Job to `COMPLETED`        │
│ 5. Clears all payload references     │
└──────────────────────────────────────┘
       │
       ▼
[JVM Garbage Collection reclaims payload memory]
```
