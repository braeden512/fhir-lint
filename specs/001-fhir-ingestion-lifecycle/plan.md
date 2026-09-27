# Implementation Plan: Phase 1 — FHIR Dataset Ingestion, Parsing & Boundary Validation

**Branch**: `001-fhir-ingestion-lifecycle` | **Date**: 2026-09-26 | **Spec**: [specs/001-fhir-ingestion-lifecycle/spec.md](spec.md)

**Input**: Feature specification from `specs/001-fhir-ingestion-lifecycle/spec.md` (Amended for Constitution 2.0.0 and ADR-009)

## Summary

Build the foundational FHIR R4 dataset ingestion layer, boundary pre-flight verifier, and inventory extraction engine for FHIRLint. The tool operates as a zero-infrastructure, local-first standalone CLI application (powered by Picocli) and an embeddable, framework-agnostic Java 21 engine (`FhirLinter`). Datasets are accepted from local files, directory trees, or standard input (`stdin`). The parser performs fast syntactic pre-flight boundary verification using Jackson to ensure valid JSON and a declared `resourceType`, unrolls Bundles into indexed in-memory resource collections using HAPI FHIR R4, and extracts inventory metrics (total resource count, distribution per resource type). Results are rendered to stdout as colorized ANSI tables, formatted JSON, or SARIF 2.1.0 with standard POSIX exit codes (`0` for success, `1` for quality failure, `2` for syntax/boundary errors). Zero data is written to persistent disk storage or databases.

## Technical Context

**Language/Version**: Java 21 LTS

**Primary Dependencies**:
- HAPI FHIR R4 (`ca.uhn.hapi.fhir:hapi-fhir-base:6.10.0`, `ca.uhn.hapi.fhir:hapi-fhir-structures-r4:6.10.0`)
- Picocli CLI Framework (`info.picocli:picocli:4.7.6`, `info.picocli:picocli-codegen:4.7.6`)
- Jackson Databind (`com.fasterxml.jackson.core:jackson-databind:2.18.2`)
- Logging: SLF4J 2.x + Logback Classic 1.5.x

**Storage**: None (Strictly stateless, volatile in-memory processing only; zero persistent databases or disk caches).

**Testing**: JUnit 5, AssertJ.

**Target Platform**: Linux, macOS, Windows (JVM executable Fat JAR, GraalVM Native Binary).

**Project Type**: Standalone CLI Developer Tool & Embeddable Java Library.

**Performance Goals**:
- In-memory parsing and inventory extraction under 500 ms for datasets up to 10 MB.
- Sub-second unit and integration test suite execution.

**Constraints**:
- Zero external database or network dependencies.
- Zero data retention of clinical payloads (Constitution Principle V).

## Constitution Check (Constitution v2.0.0)

| Principle / Rule | Compliance Status | Analysis & Evidence |
| :--- | :---: | :--- |
| **I. Standards-First Healthcare Interoperability** | **PASS** | HAPI FHIR R4 (`hapi-fhir-structures-r4`) is used for parsing and object models. |
| **II. Lean, Dependency-Minimized Architecture** | **PASS** | Pure Java 21 core engine with zero framework lock-in. No Spring Boot, JPA, or PostgreSQL. |
| **III. Testable and Reliable Software** | **PASS** | Comprehensive automated JUnit 5 / AssertJ tests verify CLI commands, exit codes, and parser mechanics. |
| **IV. Actionable Data-Quality Analysis** | **PASS** | Clear diagnostic messages and inventory metrics formatted for developer readability. |
| **V. Privacy-Conscious Healthcare Software** | **PASS** | Strict zero-retention posture: data exists only in volatile RAM during the run and is discarded at process termination. |
| **VI. Incremental Development and Simplicity** | **PASS** | Monolithic, library-first architecture delivering minimal viable parsing before complex rules. |
| **VII. Developer-Focused CLI & Library Design** | **PASS** | Full POSIX CLI compliance: stdin/files, exit codes (0/1/2), colorized ANSI tables, JSON, and SARIF output. |

## Project Structure

```text
src/
├── main/
│   ├── java/org/fhirlint/
│   │   ├── cli/
│   │   │   ├── FhirLintApplication.java       # Picocli root command & main entry point
│   │   │   ├── command/
│   │   │   │   └── ValidateCommand.java       # 'validate' command implementation
│   │   │   └── renderer/
│   │   │       ├── ConsoleTableRenderer.java  # Colorized ANSI terminal tables
│   │   │       ├── JsonReportRenderer.java    # Indented JSON report output
│   │   │       └── SarifReportRenderer.java   # SARIF 2.1.0 output for GitHub PR scanning
│   │   └── core/
│   │       ├── FhirLinter.java                # Fluent programmatic Java entry point
│   │       ├── model/
│   │       │   ├── Severity.java              # ERROR, WARNING, INFO
│   │       │   ├── IssueCategory.java         # Quality categories & weights
│   │       │   ├── ValidationProfile.java     # US_CORE, BASE_R4
│   │       │   ├── QualityIssue.java          # Finding model with FHIRPath
│   │       │   ├── QualityScore.java          # Deterministic score calculation
│   │       │   ├── IngestionInventory.java    # Resource counts & distributions
│   │       │   ├── ParsedDataset.java         # Unrolled in-memory resources
│   │       │   └── LintReport.java            # Unified report model & gate evaluation
│   │       └── parser/
│   │           ├── FhirBundleParser.java      # HAPI FHIR parser & unroller
│   │           └── FhirParseException.java    # Pre-flight boundary exception
│   └── resources/
│       └── logback.xml                        # Quiet logging configuration
└── test/
    └── java/org/fhirlint/
        ├── cli/
        │   └── FhirLintCliTest.java           # CLI integration tests (table, json, sarif, exit codes, stdin)
        └── core/
            ├── FhirBundleParserTest.java      # Parser, unrolling & pre-flight boundary tests
            ├── QualityScoreTest.java          # Scoring formula & grade tier tests
            └── FhirLinterTest.java            # Fluent Java API tests
```
