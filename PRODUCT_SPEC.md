# Healthcare Data Quality API

## 1. Project Overview

### Working name

**FHIRLint API**

> A developer-focused API that analyzes FHIR healthcare data and identifies interoperability, integrity, consistency, completeness, and terminology issues before the data reaches downstream applications.

### Problem

FHIR provides a standardized representation for healthcare data, but "valid FHIR" does not necessarily mean "good healthcare data."

Healthcare data can contain:

- Broken resource references
- Missing important information
- Invalid or inconsistent terminology
- Duplicate resources
- Conflicting information
- Inconsistent dates
- Resources that do not conform to an expected profile
- Missing relationships between resources
- Data that technically passes schema validation but is difficult or unsafe for downstream applications to use

FHIR Implementation Guides and profiles can impose additional constraints beyond the base FHIR specification. US Core, for example, defines additional constraints for U.S. healthcare interoperability. FHIR also supports `Must Support` requirements and implementation-specific profiles.

The project will build an API that goes beyond basic structural validation and provides a **developer-oriented quality analysis** of a FHIR dataset.

### Core value proposition

Instead of:

> "Is this valid FHIR?"

the API answers:

> **"Can my application safely and reliably use this healthcare data, and what problems should I fix first?"**

---

# 2. Project Goals

## Primary goals

1. Accept FHIR resources and FHIR Bundles through a REST API.
2. Validate incoming data against the applicable FHIR specification/profile.
3. Analyze relationships between resources.
4. Detect data-quality problems that basic schema validation does not catch.
5. Produce actionable issues with severity, location, explanation, and suggested remediation.
6. Produce an overall quality report and category-level metrics.
7. Support asynchronous processing for larger datasets.
8. Provide a clean REST API and OpenAPI specification.
9. Build a small demonstration interface/CLI showing the system in action.
10. Use the project to demonstrate serious Java/Spring Boot backend engineering.

## Secondary goals (Out of scope for now)

Eventually support:

- Multiple FHIR versions
- Additional Implementation Guides/profiles
- Custom quality rules
- Quality checks in CI/CD
- Webhooks
- Historical quality tracking
- Dataset comparison
- Automated remediation suggestions
- Healthcare data ingestion/normalization

---

# 3. Non-Goals

The initial version will NOT attempt to:

- Replace a full EHR
- Store real patient data
- Provide clinical decision support
- Determine whether a medical treatment is clinically appropriate
- Make diagnoses
- Guarantee that healthcare data is medically correct
- Become a complete FHIR server
- Support every FHIR resource immediately
- Build a general-purpose AI medical assistant
- Replace established FHIR conformance validators

The project is a **developer infrastructure/data-quality tool**, not a clinical product.

---

# 4. Target User

The primary user is a software developer or engineering team working with healthcare data.

Example:

> A healthcare application receives a 20,000-resource FHIR Bundle from an external system. Before loading it into the application's database, the engineering team sends it to FHIRLint.

The API responds:

```text
Quality Score: 84/100

Resources analyzed: 20,143

Errors:       7
Warnings:    43
Informational: 91

Critical findings:
- 3 broken Patient references
- 2 invalid terminology codes
- 1 duplicate Patient
- 1 Observation missing required relationship

Completeness: 91%
Referential Integrity: 98%
Terminology: 94%
Consistency: 96%
```

The developer can then inspect each issue.

---

# 5. Core Concept: Quality Is More Than Validation

The project will explicitly separate different categories of quality.

## 5.1 Structural validity

Does the resource conform to the FHIR structure?

Examples:

- Invalid resource type
- Invalid datatype
- Invalid cardinality
- Invalid field format
- Invalid Bundle structure

This layer should leverage established FHIR validation tooling rather than reinventing the FHIR specification.

---

## 5.2 Profile/conformance validation

Does the resource conform to a specified profile or Implementation Guide?

Initial target:

**FHIR R4 + US Core**

US Core is based on FHIR R4 and defines additional constraints and interactions for U.S. healthcare interoperability.

The system should eventually allow the caller to specify a profile:

```json
{
  "profile": "US_CORE"
}
```

Future possibilities:

```text
US_CORE
CUSTOM_PROFILE
CUSTOM_IMPLEMENTATION_GUIDE
```

---

## 5.3 Referential integrity

Analyze relationships between resources.

Example:

```text
Observation/123
      |
      └── subject → Patient/999
                         X
                    not found
```

Issue:

```json
{
  "severity": "ERROR",
  "category": "REFERENTIAL_INTEGRITY",
  "resource": "Observation/123",
  "path": "Observation.subject",
  "message": "Referenced Patient/999 does not exist in the dataset."
}
```

This is one of the most important custom components of the project.

---

## 5.4 Data completeness

Identify missing information that could affect downstream processing.

Examples:

```text
Patient missing birthDate
Observation missing subject
MedicationRequest missing dosage information
Encounter missing participant information
```

Important distinction:

**Missing data is not automatically an error.**

The system must distinguish between:

- Required
- Recommended
- Optional
- Contextually expected

FHIR's profiling system allows implementation guides to define constraints and `Must Support` expectations, so completeness should be evaluated relative to the selected profile rather than using arbitrary universal rules.

---

## 5.5 Terminology quality

Analyze coded healthcare data.

Potential initial terminology targets:

- LOINC
- SNOMED CT
- RxNorm
- ICD-10-CM

Potential checks:

- Invalid code
- Unknown code system
- Deprecated code
- Incorrect value set
- Code/value mismatch
- Missing coding system

Terminology validation should initially focus on a manageable subset using a pluggable `TerminologyService` abstraction. For MVP, an in-memory, static file-backed provider validates against a core subset of essential codes (such as US Core value sets, administrative gender, and core LOINC/RxNorm codes) without requiring an external licensed terminology server or database.

---

## 5.6 Cross-resource consistency

Look for problems that cannot be detected by analyzing resources individually.

Examples:

```text
Encounter:
  start = 2026-09-20
  end   = 2026-09-18
```

or:

```text
MedicationRequest:
  authoredOn = 2026-09-20

Encounter:
  start = 2026-10-05
```

or:

```text
DiagnosticReport
      |
      └── result → Observation/123

Observation/123
      |
      └── status = entered-in-error
```

The goal is to identify suspicious relationships and inconsistencies, not to make clinical judgments.

---

## 5.7 Duplicate detection

Identify resources that appear to represent the same underlying record.

Initial candidates:

- Patients
- Medications
- Conditions
- Observations

Example:

```text
Patient/123
Patient/984

Same:
  name
  date of birth
  identifier

Potential duplicate: 94% similarity
```

This can initially use deterministic rules and later evolve into more sophisticated matching.

---

# 6. Quality Model

The API should not initially pretend that there is a scientifically universal "healthcare data quality score."

Instead, the score will be explicitly defined by the project.

Example:

```text
Overall Quality
├── Structural Integrity
├── Profile Conformance
├── Referential Integrity
├── Completeness
├── Terminology
└── Cross-Resource Consistency
```

Each category receives a score.

Example:

```json
{
  "overallScore": 84,
  "categories": {
    "structural": 100,
    "profileConformance": 92,
    "referentialIntegrity": 97,
    "completeness": 81,
    "terminology": 88,
    "consistency": 76
  }
}
```

The scoring algorithm will be documented and deterministic.

The score is intended as an **engineering quality indicator**, not a clinical or regulatory measurement.

---

# 7. Issue Model

Every detected issue should have a consistent structure.

Example:

```json
{
  "id": "issue_8f32",
  "severity": "ERROR",
  "category": "REFERENTIAL_INTEGRITY",
  "resourceType": "Observation",
  "resourceId": "obs-123",
  "path": "subject.reference",
  "message": "Referenced Patient/p-999 does not exist.",
  "ruleId": "REF-001",
  "suggestion": "Verify the Patient reference or include Patient/p-999 in the dataset."
}
```

## Severity levels

### ERROR

Data is invalid or likely to cause downstream failure.

### WARNING

Data may be usable but presents a meaningful quality concern.

### INFO

Useful observation that does not necessarily indicate a problem.

---

# 8. API Design

## Submit a quality check

```http
POST /api/v1/quality-checks
```

Request:

```json
{
  "profile": "US_CORE",
  "bundle": {
    "resourceType": "Bundle",
    "type": "collection",
    "entry": []
  }
}
```

Response:

```json
{
  "id": "qc_12345",
  "status": "PROCESSING"
}
```

---

## Retrieve quality check

```http
GET /api/v1/quality-checks/{id}
```

Response:

```json
{
  "id": "qc_12345",
  "status": "COMPLETED",
  "qualityScore": 84,
  "resourcesAnalyzed": 1842,
  "errors": 7,
  "warnings": 43,
  "info": 91
}
```

---

## Retrieve issues

```http
GET /api/v1/quality-checks/{id}/issues
```

Support filtering:

```text
?severity=ERROR
?category=REFERENTIAL_INTEGRITY
?resourceType=Observation
```

---

## Retrieve category summary

```http
GET /api/v1/quality-checks/{id}/summary
```

---

## Retrieve individual issue

```http
GET /api/v1/quality-checks/{id}/issues/{issueId}
```

---

# 9. Processing Architecture

Initial architecture:

```text
                    REST API
                       |
                       v
              Quality Check Service
                       |
                       v
              ┌─────────────────┐
              │ FHIR Parser     │
              └────────┬────────┘
                       |
                       v
              ┌─────────────────┐
              │ FHIR Validator  │
              └────────┬────────┘
                       |
                       v
              ┌─────────────────┐
              │ Resource Index  │
              └────────┬────────┘
                       |
          ┌────────────┼────────────┐
          v            v            v
      Reference    Terminology   Consistency
       Rules         Rules         Rules
          |            |            |
          └────────────┼────────────┘
                       v
                Issue Aggregator
                       |
                       v
                 Quality Scorer
                       |
                       v
                 Report Storage
```

The architecture should be modular enough that new rule types can be added without modifying the core processing pipeline.

---

# 10. Rule Engine Architecture

A major design decision:

**Quality rules should be pluggable.**

Instead of writing:

```java
if (something) {
    createIssue();
}
```

throughout the codebase, define a rule abstraction.

Conceptually:

```java
public interface QualityRule {

    RuleResult evaluate(QualityContext context);

}
```

Potential implementations:

```text
BrokenReferenceRule
MissingRequiredFieldRule
InvalidTerminologyRule
DuplicateResourceRule
DateConsistencyRule
ProfileConformanceRule
```

Each rule should declare:

```text
Rule ID
Category
Severity
Applicable resource types
Evaluation logic
Description
```

This will make the system much easier to extend.

---

# 11. Technology Stack

## Backend

**Java 21+**

Reason:

- Modern Java
- Strong typing
- Excellent ecosystem
- Directly relevant to user's professional development

## Framework

**Spring Boot**

Primary technologies:

- Spring Web
- Spring Validation
- Spring Data JPA
- Spring Boot Actuator
- Spring Security later

Spring Boot will be the primary application framework rather than building a minimal Java HTTP server.

---

## FHIR libraries

Use an established Java FHIR implementation rather than manually parsing the entire FHIR specification.

Primary candidate:

**HAPI FHIR**

Responsibilities:

- FHIR resource parsing
- FHIR model representation
- FHIR validation
- Profile support

The project should build custom quality-analysis functionality around established FHIR infrastructure rather than attempting to recreate the FHIR specification.

---

## Database

**PostgreSQL**

Use PostgreSQL for:

- Quality-check jobs
- Results
- Issues
- Rule metadata
- API users/API keys later
- Processing metadata
- Audit information

Do not initially attempt to make PostgreSQL the primary FHIR repository.

To comply with privacy requirements, raw submitted FHIR payloads MUST NOT be stored in the database. PostgreSQL stores only anonymized job execution metadata, scores, and issue reports (which reference resource IDs and FHIRPath locations, without persisting raw PHI/payload body).

---

## Caching

Potential future:

**Redis**

Use only when an actual performance requirement emerges.

Potential use cases:

- terminology lookups
- repeated validation
- job status
- rate limiting

Redis is not required for MVP.

---

## Async processing

MVP:

**Spring's asynchronous/job capabilities**

Future:

**RabbitMQ or Kafka**

Large FHIR datasets should eventually be processed asynchronously.

Do not introduce Kafka merely to make the architecture look impressive.

---

## API documentation

**OpenAPI / Swagger**

The API should be fully documented and interactable through Swagger UI.

---

## Testing

- JUnit 5
- Mockito where appropriate
- Spring Boot integration tests
- Testcontainers
- PostgreSQL integration tests
- API-level tests

Quality rules should have extensive unit tests using intentionally broken FHIR examples.

---

## Build

**Gradle**

Use Gradle for dependency management and builds.

---

## Containers

**Docker**

The application should be runnable with:

```bash
docker compose up
```

Initial Compose environment:

```text
FHIRLint API
PostgreSQL
```

Add other infrastructure only when needed.

---

# 12. Repository Structure

Initial structure:

```text
fhir-lint/
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com.fhir-lint/
│   │   │       ├── api/
│   │   │       ├── quality/
│   │   │       ├── validation/
│   │   │       ├── terminology/
│   │   │       ├── rules/
│   │   │       ├── scoring/
│   │   │       ├── persistence/
│   │   │       └── config/
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       └── ...
│   │
│   └── test/
│
├── sample-data/
│   ├── valid/
│   ├── invalid/
│   └── edge-cases/
│
├── docs/
│
├── docker-compose.yml
├── Dockerfile
├── build.gradle
└── README.md
```

The exact package structure can evolve as implementation begins.

---

# 13. Development Phases

## Phase 0 — Research & Architecture

Goal:

Understand the FHIR ecosystem well enough to avoid building something redundant or incorrectly.

Tasks:

- Study FHIR R4 resource model
- Study Bundles
- Study profiles
- Study US Core
- Study `Must Support`
- Study HAPI FHIR
- Identify existing validation capabilities
- Define what FHIRLint adds beyond existing validators

Deliverables:

- Architecture document
- Initial API specification
- Rule catalog
- Sample datasets

---

# Phase 1 — FHIR Ingestion

Goal:

Build a working Spring Boot API that accepts FHIR.

Implement:

```http
POST /api/v1/quality-checks
```

Support:

- Single resources
- Bundles
- JSON
- FHIR R4

Store:

- Job
- Status
- Input metadata
- Results

Do not build custom quality rules yet.

---

# Phase 2 — Structural Validation

Integrate HAPI FHIR validation.

Return:

- Errors
- Warnings
- Resource/path information
- Validation messages

Goal:

Establish a baseline:

> "This is what existing FHIR validation says."

This is important because the project's differentiation should be built **on top of** established validation.

---

# Phase 3 — Referential Integrity

Build the resource graph.

Example:

```text
Patient/1
   ↑
   |
Observation/5
   |
   └── Encounter/3
```

Detect:

- Missing references
- Broken references
- Orphaned resources
- Invalid reference types
- Duplicate identifiers

This should be one of the first major custom features.

---

# Phase 4 — Data Quality Rules

Implement the rule engine.

Initial rules:

### Completeness

- Missing important fields
- Missing profile-required information
- Missing recommended relationships

### Consistency

- Invalid date relationships
- Conflicting values
- Invalid resource relationships

### Duplicates

- Exact duplicate resources
- Duplicate identifiers
- Basic Patient similarity

### Terminology

Start with a limited number of terminology systems.

Do not attempt to implement the entire medical terminology ecosystem.

---

# Phase 5 — Quality Scoring

Create:

```text
Overall Score
Category Scores
Issue Counts
Resource Counts
```

Build a deterministic scoring algorithm.

Document exactly how scores are calculated.

Example:

```text
Structural:       100
Conformance:       92
References:        98
Completeness:      81
Terminology:       88
Consistency:       76

Overall:           84
```

---

# Phase 6 — Developer Experience

Build the polished API experience.

Implement:

- Swagger/OpenAPI
- Good error responses
- Pagination
- Filtering
- Job status
- API documentation
- Example requests
- Example datasets

Add a CLI or lightweight frontend.

The frontend is **not the product**.

It exists to make the backend easy to demonstrate.

---

# Phase 7 — Production Engineering

Once the core system works:

- Docker
- Testcontainers
- integration testing
- structured logging
- metrics
- health checks
- request IDs
- rate limiting
- authentication/API keys
- database migrations
- CI/CD

Potential architecture:

```text
Client
  |
  v
API
  |
  v
Job Queue
  |
  +---- Validator
  |
  +---- Quality Rules
  |
  +---- Terminology
  |
  v
Results DB
```

---

# Phase 8 — Advanced Features

Only after the core project is solid.

Potential additions:

### Custom Rules

Allow developers to define organization-specific quality rules.

### CI/CD Integration

```bash
fhir-lint validate bundle.json --fail-on error
```

Example:

```text
Quality score: 91

Errors: 0
Warnings: 12

BUILD PASSED
```

Or:

```text
Quality score: 63

Errors: 4

BUILD FAILED
```

### Dataset Comparison

```http
POST /api/v1/compare
```

Identify what changed between two healthcare datasets.

### Webhooks

Notify clients when asynchronous validation completes.

### Historical Quality

Track:

```text
Dataset A
  ↓
Quality: 72

Dataset B
  ↓
Quality: 81

Dataset C
  ↓
Quality: 94
```

This could eventually make the system useful as a **healthcare data-quality monitoring platform**.

---

# 14. Demo Strategy

The final demo should tell a simple story.

## Step 1

Upload a deliberately messy FHIR Bundle.

## Step 2

FHIRLint analyzes it.

## Step 3

Show:

```text
84 / 100

7 Errors
43 Warnings
91 Info
```

## Step 4

Click into the errors.

Example:

```text
ERROR REF-001

Observation/123

subject.reference = Patient/999

Patient/999 does not exist.

Suggested action:
Verify the patient reference or include the
referenced Patient resource.
```

## Step 5

Show the resource relationship graph.

## Step 6

Fix the data.

## Step 7

Run it again.

```text
84 → 97
```

That before/after demonstration is the key visual payoff.

---

# 15. Sample Dataset Strategy

Do not use real patient data.

Create synthetic datasets specifically designed to exercise the rules.

Examples:

### Dataset A — Clean

Valid FHIR Bundle with good relationships.

Expected:

```text
95–100 quality
```

### Dataset B — Broken References

Missing Patient/Observation references.

Expected:

```text
multiple referential integrity errors
```

### Dataset C — Missing Data

Resources with missing important fields.

Expected:

```text
completeness warnings
```

### Dataset D — Duplicate Patients

Two highly similar patient records.

Expected:

```text
duplicate warning
```

### Dataset E — Terminology Problems

Invalid or inconsistent codes.

Expected:

```text
terminology warnings/errors
```

### Dataset F — "Technically Valid, Practically Bad"

This should be the flagship dataset.

It should pass basic FHIR validation while containing multiple cross-resource quality problems.

This dataset demonstrates **why FHIRLint exists beyond a normal FHIR validator**.

---

# 16. Important Architectural Principle

The project should be designed around:

> **Existing standards first, custom intelligence second.**

Do not rebuild:

- FHIR parsing
- FHIR schemas
- FHIR validation
- terminology databases

Instead:

```text
Existing FHIR infrastructure
          +
FHIRLint's quality-analysis layer
          =
Product
```

This makes the project both more realistic and more technically defensible.

---

# 17. Future Product Direction

The long-term vision can evolve from:

**FHIR Quality Checker**

into:

**Healthcare Data Quality Infrastructure**

Potential architecture:

```text
                Healthcare Data
                       |
          ┌────────────┴────────────┐
          |                         |
       FHIR                      HL7/CSV
          |                         |
          └────────────┬────────────┘
                       ↓
                FHIRLint
                       |
              ┌────────┴────────┐
              ↓                 ↓
         Normalization       Validation
              ↓                 ↓
              └────────┬────────┘
                       ↓
                 Quality Engine
                       |
        ┌──────────────┼──────────────┐
        ↓              ↓              ↓
     Reports        Webhooks       Monitoring
        ↓              ↓              ↓
      API          Developer      Dashboard
                     Systems
```

The initial implementation should **not** attempt to build all of this.

The first milestone is simply:

> **"Give me a FHIR Bundle and tell me exactly what's wrong with it, why it matters, and where I should fix it."**

Everything else can grow from that foundation.

---

# 18. Definition of MVP

The project is considered MVP-complete when a developer can:

1. Start the application with Docker.
2. Submit a FHIR R4 Bundle.
3. Select a validation profile.
4. Receive an asynchronous quality-check job.
5. Have the system perform standard FHIR validation.
6. Detect broken resource references.
7. Detect selected completeness problems.
8. Detect selected terminology problems.
9. Detect selected consistency problems.
10. Detect basic duplicates.
11. Receive categorized issues.
12. Receive an overall quality score.
13. Retrieve results through documented REST endpoints.
14. View the results through Swagger or a small demo UI.
15. Re-run the same dataset after fixing it and observe the quality improvement.
16. Run a comprehensive automated test suite.

---

# 19. Resume-Level Technical Story

The eventual resume description should emphasize the engineering problem rather than simply saying "built a healthcare API."

Possible direction:

**Healthcare Data Quality Infrastructure — Java, Spring Boot, PostgreSQL, HAPI FHIR, Docker**

> Built a developer-focused FHIR data-quality platform that analyzes healthcare datasets for profile conformance, referential integrity, terminology issues, duplicates, completeness, and cross-resource inconsistencies; designed a pluggable rule engine and asynchronous processing pipeline for scalable quality analysis.

As the project develops, the bullet should be updated to reflect the actual engineering accomplishments rather than prematurely claiming features.

---

# 20. Guiding Principles

### 1. Build a real developer tool

The API should feel like something another developer could actually use.

### 2. Avoid fake complexity

Do not add Kafka, Redis, Kubernetes, microservices, or AI simply because they look impressive.

### 3. Favor depth over feature count

Five excellent quality checks are better than fifty superficial ones.

### 4. Make every issue explainable

Developers should understand:

```text
What happened?
Where?
Why does it matter?
How can I fix it?
```

### 5. Separate standard validation from custom quality analysis

The project should clearly demonstrate what established FHIR tooling provides and what FHIRLint adds.

### 6. Make the demo obvious

A viewer should be able to understand the value within approximately 60 seconds.

### 7. Keep the architecture extensible

The system should eventually support additional profiles, rules, input formats, and integrations without requiring a rewrite.

### 8. Treat healthcare as the domain, not an excuse for unnecessary complexity

The project should remain fundamentally a strong backend/infrastructure project.

---

# Initial Technology Decision Summary

| Area                | Decision                                    |
| ------------------- | ------------------------------------------- |
| Language            | Java                                        |
| Framework           | Spring Boot                                 |
| Java version        | 21+                                         |
| Healthcare standard | FHIR R4                                     |
| Initial profile     | US Core                                     |
| FHIR library        | HAPI FHIR                                   |
| Database            | PostgreSQL                                  |
| ORM                 | Spring Data JPA                             |
| Build               | Gradle                                      |
| API                 | REST                                        |
| Documentation       | OpenAPI / Swagger                           |
| Testing             | JUnit 5 + Spring Boot Test + Testcontainers |
| Containerization    | Docker                                      |
| Async processing    | Spring-based initially                      |
| Messaging           | Deferred until justified                    |
| Cache               | Deferred until justified                    |
| Frontend            | Minimal demo UI, not core product           |
| Authentication      | Later phase                                 |
| Deployment          | Later phase                                 |
| AI/ML               | Not part of MVP                             |

## First implementation target

**Do not start by building the UI.**

The first coding milestone should be:

```text
Spring Boot application
        ↓
POST /api/v1/quality-checks
        ↓
Accept FHIR R4 Bundle
        ↓
Parse with HAPI FHIR
        ↓
Run baseline FHIR validation
        ↓
Return structured validation results
```

Once that works, begin adding **FHIRLint-specific quality rules**.

The most important early architectural decision is making the rule engine extensible. That is the part most likely to turn this from "FHIR validator wrapper" into an actual software engineering project.
