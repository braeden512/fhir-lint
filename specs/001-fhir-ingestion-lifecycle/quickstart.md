# Quickstart Validation Guide: Phase 1 — FHIR Ingestion & Asynchronous Job Lifecycle

This guide describes how to validate the end-to-end functionality of Phase 1 using real HTTP requests against the application.

---

## Prerequisites

1. **Java 21 JDK** installed.
2. **Docker Compose** running PostgreSQL:
   ```bash
   docker compose up -d
   ```
3. **Application running**:
   ```bash
   ./gradlew bootRun
   ```
   The service will listen on `http://localhost:8080`.

---

## Scenario 1: Ingesting a FHIR R4 Bundle and Polling to Completion

### Step 1: Submit a Bundle Dataset
Submit a valid FHIR R4 Bundle with multiple resource entries (e.g., using `sample-data/clean/clean-bundle.json`):

```bash
curl -i -X POST http://localhost:8080/api/v1/quality-checks \
  -H "Content-Type: application/json" \
  -d @sample-data/clean/clean-bundle.json
```

**Expected Response**:
- **HTTP Status**: `202 Accepted`
- **Headers**:
  ```http
  Location: /api/v1/quality-checks/<JOB_UUID>
  Content-Type: application/json
  ```
- **Body**:
  ```json
  {
    "id": "<JOB_UUID>",
    "status": "QUEUED",
    "createdAt": "2026-09-26T...",
    "location": "/api/v1/quality-checks/<JOB_UUID>"
  }
  ```

---

### Step 2: Poll Job Status Until Completion
Query the job using the returned UUID or `Location` header:

```bash
curl -s http://localhost:8080/api/v1/quality-checks/<JOB_UUID> | jq .
```

**Expected Response (when processing completed)**:
- **HTTP Status**: `200 OK`
- **Body**:
  ```json
  {
    "id": "<JOB_UUID>",
    "status": "COMPLETED",
    "targetProfile": "BASE_R4",
    "createdAt": "2026-09-26T...",
    "startedAt": "2026-09-26T...",
    "completedAt": "2026-09-26T...",
    "resourcesAnalyzed": 10,
    "resourceTypeCounts": {
      "Patient": 1,
      "Observation": 6,
      "Encounter": 2,
      "Condition": 1
    },
    "failureReason": null
  }
  ```

---

## Scenario 2: Synchronous Boundary Rejection of Malformed JSON

Submit a request with invalid JSON:

```bash
curl -i -X POST http://localhost:8080/api/v1/quality-checks \
  -H "Content-Type: application/json" \
  -d '{"invalidJson": '
```

**Expected Response**:
- **HTTP Status**: `400 Bad Request`
- **Body**:
  ```json
  {
    "status": 400,
    "title": "Bad Request",
    "detail": "Malformed JSON payload in request body.",
    "timestamp": "2026-09-26T..."
  }
  ```

Verify that **no** job record was added to PostgreSQL:
```bash
docker compose exec postgres psql -U fhirlint -d fhirlint -c "SELECT COUNT(*) FROM quality_check_jobs;"
```

---

## Scenario 3: Synchronous Rejection of Non-FHIR Payload

Submit valid JSON lacking a `resourceType` attribute:

```bash
curl -i -X POST http://localhost:8080/api/v1/quality-checks \
  -H "Content-Type: application/json" \
  -d '{"name": "John Doe", "active": true}'
```

**Expected Response**:
- **HTTP Status**: `400 Bad Request`
- **Body**:
  ```json
  {
    "status": 400,
    "title": "Bad Request",
    "detail": "Payload must contain a valid FHIR 'resourceType' declaration.",
    "timestamp": "2026-09-26T..."
  }
  ```

---

## Scenario 4: Querying a Non-Existent Job ID

Query an unknown UUID:

```bash
curl -i http://localhost:8080/api/v1/quality-checks/00000000-0000-0000-0000-000000000000
```

**Expected Response**:
- **HTTP Status**: `404 Not Found`
- **Body**:
  ```json
  {
    "status": 404,
    "title": "Not Found",
    "detail": "Quality check job '00000000-0000-0000-0000-000000000000' was not found.",
    "timestamp": "2026-09-26T..."
  }
  ```

---

## Scenario 5: Automated Test Suite Execution

Run the complete test suite verifying ingestion, boundary checks, and asynchronous state transitions:

```bash
./gradlew check
```

All integration tests (leveraging Spring Boot Test and Testcontainers) must pass without errors.
