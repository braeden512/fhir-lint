# Implementation Tasks: Phase 1 — FHIR Dataset Ingestion, Parsing & Boundary Validation

This document provides the actionable task record for implementing Phase 1 in alignment with [spec.md](spec.md), [plan.md](plan.md), [data-model.md](data-model.md), and [research.md](research.md).

---

## Phase 1: Setup & Build Configuration

**Purpose**: Project build configuration and dependencies for zero-infrastructure standalone CLI.

- [X] T001 Overhaul `build.gradle` to standard Java application (`application` and `java` plugins), add HAPI FHIR R4 (`ca.uhn.hapi.fhir:hapi-fhir-base`, `ca.uhn.hapi.fhir:hapi-fhir-structures-r4`), Picocli (`info.picocli:picocli:4.7.6`), Jackson (`com.fasterxml.jackson.core:jackson-databind`), SLF4J, Logback, and JUnit 5 / AssertJ.
- [X] T002 Configure logging in `src/main/resources/logback.xml` to quiet HAPI FHIR `ModelScanner` internal debug logs during CLI execution.
- [X] T003 Remove obsolete server configuration files: `docker-compose.yml`, `src/main/resources/application.yml`, and Flyway migrations.

---

## Phase 2: Core Domain & Ingestion Models

**Purpose**: Stateless, in-memory domain models and deterministic scoring algorithm.

- [X] T004 Create `src/main/java/com/braeden/fhirlint/core/model/Severity.java` enum (`ERROR`, `WARNING`, `INFO`).
- [X] T005 Create `src/main/java/com/braeden/fhirlint/core/model/IssueCategory.java` enum with display names and scoring weights.
- [X] T006 Create `src/main/java/com/braeden/fhirlint/core/model/ValidationProfile.java` enum (`BASE_R4`, `US_CORE`).
- [X] T007 Create `src/main/java/com/braeden/fhirlint/core/model/QualityIssue.java` record with FHIRPath location, rule ID, and suggestion.
- [X] T008 Create `src/main/java/com/braeden/fhirlint/core/model/QualityScore.java` implementing the deterministic category scoring formula and grade tiers (`EXCELLENT`, `ACCEPTABLE`, `DEGRADED`, `CRITICAL`).
- [X] T009 Create `src/main/java/com/braeden/fhirlint/core/model/IngestionInventory.java` record for resource counts and distribution.
- [X] T010 Create `src/main/java/com/braeden/fhirlint/core/model/ParsedDataset.java` record for unrolled in-memory resources.
- [X] T011 Create `src/main/java/com/braeden/fhirlint/core/model/LintReport.java` record with quality gate evaluation method (`passes(minScore, failOn)`).

---

## Phase 3: Core Parser & Fluent Java API (User Story 1 & 3)

**Purpose**: High-performance HAPI FHIR parsing, syntactic pre-flight boundary verification, and programmatic entry point.

- [X] T012 Create `src/main/java/com/braeden/fhirlint/core/parser/FhirParseException.java` for boundary syntax and file errors.
- [X] T013 Implement `src/main/java/com/braeden/fhirlint/core/parser/FhirBundleParser.java` using Jackson for fast JSON syntax and `resourceType` pre-flight checks, and HAPI FHIR `IParser` for resource parsing, Bundle unrolling, and inventory extraction.
- [X] T014 Implement `src/main/java/com/braeden/fhirlint/core/FhirLinter.java` fluent programmatic entry point (`FhirLinter.create().withProfile(...).lint(...)`) supporting files, directories, JSON strings, and streams.

---

## Phase 4: CLI Application & Output Renderers (User Story 1, 2, 4)

**Purpose**: Standalone Picocli command-line interface with ANSI table, JSON, and SARIF output renderers.

- [X] T015 Implement `src/main/java/com/braeden/fhirlint/cli/renderer/ConsoleTableRenderer.java` for colorized ANSI terminal scorecards, category bars, and defect listings.
- [X] T016 Implement `src/main/java/com/braeden/fhirlint/cli/renderer/JsonReportRenderer.java` for structured JSON output.
- [X] T017 Implement `src/main/java/com/braeden/fhirlint/cli/renderer/SarifReportRenderer.java` for OASIS SARIF 2.1.0 output for GitHub PR annotations.
- [X] T018 Implement `src/main/java/com/braeden/fhirlint/cli/command/ValidateCommand.java` handling flags (`-p`, `-f`, `--min-score`, `--fail-on`, `-v`, `-o`), stdin piping (`-`), directory scanning, and exit code logic (`0`, `1`, `2`).
- [X] T019 Implement `src/main/java/com/braeden/fhirlint/cli/FhirLintApplication.java` root Picocli command entry point.

---

## Phase 5: Automated Testing & Verification

**Purpose**: Comprehensive unit and integration test coverage for sub-second verification.

- [X] T020 Implement `src/test/java/com/braeden/fhirlint/core/FhirBundleParserTest.java` verifying clean bundle parsing, messy bundle parsing, single resource parsing, empty bundle handling, malformed JSON rejection, missing resourceType rejection, and missing file rejection.
- [X] T021 Implement `src/test/java/com/braeden/fhirlint/core/QualityScoreTest.java` verifying deterministic scoring formula, penalty weights, and grade tiers.
- [X] T022 Implement `src/test/java/com/braeden/fhirlint/core/FhirLinterTest.java` verifying fluent Java API execution.
- [X] T023 Implement `src/test/java/com/braeden/fhirlint/cli/FhirLintCliTest.java` verifying CLI execution on files, stdin (`-`), JSON formatting, SARIF formatting, quality gate exit code `1`, and boundary exit code `2`.
- [X] T024 Create sample boundary test fixtures: `sample-data/invalid-syntax.json` and `sample-data/non-fhir.json`.
- [X] T025 Execute `./gradlew check` and `./gradlew test` ensuring 100% pass rate.
