# ADR-007: Phase 7 — Production Engineering, Privacy-First Persistence, and Containerization

## Status
Accepted

## Context & Problem Statement
To demonstrate serious backend engineering, FHIRLint must transition from an in-memory prototype to a robust, reproducible production setup. This encompasses database schema evolution, structured observability, containerization, and rigorous automated testing.

Furthermore, Constitution Principle V mandates strict privacy safeguards: no PHI or submitted healthcare records may be persisted in the database.

## Decision Drivers
1. **Reproducible Infrastructure**: Deployable anywhere via a single `docker compose up` command.
2. **Versioned Database Migrations**: Relational schema changes must be automated and tracked in source control.
3. **Integration Testing**: Tests must execute against real PostgreSQL databases in CI using Testcontainers.
4. **Observability**: Metrics and health checks exposed for modern cloud environments.

## Considered Options
1. **Flyway for Database Migrations**: Mature, SQL-based schema versioning tool natively supported by Spring Boot.
2. **Hibernate `ddl-auto=update`**: Fragile, unversioned, dangerous for production.
3. **Docker Compose**: Standard orchestration for local dev running Spring Boot API and PostgreSQL.

## Decision Outcome
Adopt **Flyway + PostgreSQL + Docker Compose + Testcontainers + Spring Boot Actuator**.

### Database Schema Design (`db/migration/V1__init_schema.sql`)

```sql
CREATE TABLE quality_check_jobs (
    id UUID PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    profile_requested VARCHAR(64) NOT NULL,
    total_resources INTEGER NOT NULL DEFAULT 0,
    error_count INTEGER NOT NULL DEFAULT 0,
    warning_count INTEGER NOT NULL DEFAULT 0,
    info_count INTEGER NOT NULL DEFAULT 0,
    overall_score INTEGER,
    grade VARCHAR(32),
    category_scores JSONB,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_jobs_status_created ON quality_check_jobs(status, created_at DESC);

CREATE TABLE quality_issues (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES quality_check_jobs(id) ON DELETE CASCADE,
    rule_id VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    category VARCHAR(32) NOT NULL,
    resource_type VARCHAR(64),
    resource_id VARCHAR(64),
    fhir_path VARCHAR(255),
    message TEXT NOT NULL,
    suggestion TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_issues_job_severity ON quality_issues(job_id, severity);
CREATE INDEX idx_issues_job_category ON quality_issues(job_id, category);
```

### Docker Compose Architecture
- `db`: PostgreSQL 16 Alpine container with health check.
- `app`: Multi-stage build Dockerfile (Eclipse Temurin 21 JRE base), waiting for DB readiness before boot.

### Testcontainers
Integration tests will spin up an ephemeral PostgreSQL container with `org.testcontainers:postgresql` to test repositories, migrations, and end-to-end API workflows reliably without mocks.

## Consequences
### Positive
- Fully automated database schema evolution via Flyway.
- Zero local PostgreSQL installation required for new developers (`docker compose up`).
- High-fidelity integration tests using Testcontainers matching production environments.
- Strict non-PHI storage guarantees audited and enforced by schema constraints.

### Negative / Trade-offs
- Docker and Testcontainers require Docker daemon running during integration test execution.
