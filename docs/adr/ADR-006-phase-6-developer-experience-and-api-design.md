# ADR-006: Phase 6 — Developer Experience, OpenAPI Specification, and CLI Client

## Status
Accepted

## Context & Problem Statement
FHIRLint is an infrastructure developer tool. Its primary consumers are software engineers building health-tech integrations. The developer experience (DX) must be frictionless, predictable, and self-documenting.

Constitution Principle VII mandates: "All REST APIs MUST be predictable, versioned... fully documented, and return JSON-formatted actionable error payloads. The OpenAPI/Swagger documentation MUST be kept strictly in sync with the actual API implementation using automated validation checks."

Furthermore, demonstrating the system quickly requires an intuitive CLI tool or demonstration script so developers can test datasets locally without writing boilerplate HTTP requests.

## Decision Drivers
1. **Interactive Documentation**: Instant discovery and testing via Swagger UI.
2. **Filtering & Pagination**: Large bundles can generate hundreds of issues; clients need to filter by severity, category, or resource type.
3. **Ergonomic CLI / Script**: Single-command execution against local JSON files (`./gradlew runCli --args="bundle.json"` or a lightweight script).

## Considered Options
1. **Springdoc OpenAPI (`org.springdoc:springdoc-openapi-starter-webmvc-ui`)**: Standard modern Spring Boot OpenAPI 3 library with automated Swagger UI generation and annotation support.
2. **Manual static OpenAPI YAML**: High maintenance overhead; prone to drifting out of sync with code (violating Principle VII).
3. **Rich SPA Frontend (React/Vue)**: Violates project non-goals ("The frontend is not the product... It exists to make the backend easy to demonstrate").

## Decision Outcome
Adopt **Option 1: Springdoc OpenAPI with Swagger UI + Lightweight Python/Shell CLI demonstration client**.

### API Specification Design

```http
POST /api/v1/quality-checks
Content-Type: application/json
{
  "profile": "US_CORE",
  "bundle": { ... }
}
--> 202 Accepted
Location: /api/v1/quality-checks/{id}
```

```http
GET /api/v1/quality-checks/{id}
--> 200 OK
{
  "id": "qc-123",
  "status": "COMPLETED",
  "overallScore": 84,
  "grade": "ACCEPTABLE",
  "totalResources": 12,
  "summary": { "errors": 2, "warnings": 4, "info": 1 }
}
```

```http
GET /api/v1/quality-checks/{id}/issues?severity=ERROR&category=REFERENTIAL_INTEGRITY&page=0&size=20
--> 200 OK (Paginated Spring Data response)
```

```http
GET /api/v1/quality-checks/{id}/summary
--> 200 OK (Category scores and breakdown)
```

### Demonstration CLI
A lightweight standalone CLI script (`scripts/fhirlint-cli.sh` or Python helper) will:
1. Accept a target FHIR file path (`./fhirlint-cli.sh validate sample-data/messy/messy-bundle.json`).
2. Post to the local API.
3. Poll job status with terminal progress animation.
4. Render a formatted ANSI summary table of the score, grade, and critical issues.

## Consequences
### Positive
- Zero manual OpenAPI maintenance; docs generated automatically from Spring controllers.
- Developers can immediately inspect datasets via Swagger UI (`http://localhost:8080/swagger-ui.html`) or the CLI.
- No heavy frontend build tools (Webpack/Node/NPM) required.

### Negative / Trade-offs
- Adding Springdoc adds a small compile dependency to `build.gradle`, justified by Principle VII.
