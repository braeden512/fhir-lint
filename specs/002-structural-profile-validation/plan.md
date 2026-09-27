# Implementation Plan: Phase 2 — Structural and Profile Validation

**Branch**: `002-structural-profile-validation` | **Date**: 2026-09-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/002-structural-profile-validation/spec.md`

## Summary

Implement industrial-grade structural and profile validation for FHIRLint using HAPI FHIR's validation engine configured with an in-memory `ValidationSupportChain`. The feature enables base FHIR R4 schema verification (datatypes, cardinalities, primitive regexes) and US Core profile conformance validation (mandatory fields, invariants, Must Support, slicing) across the 6 core clinical resources (`Patient`, `Encounter`, `Condition`, `Observation`, `MedicationRequest`, `DiagnosticReport`). Raw validator messages are normalized into standardized `QualityIssue` objects with clean categorization (`STRUCTURAL` vs `PROFILE_CONFORMANCE`), severity mapping, and actionable remediation suggestions, with complete zero-retention privacy and sub-second cached performance.

## Technical Context

**Language/Version**: Java 21+

**Primary Dependencies**: 
- HAPI FHIR R4 (`hapi-fhir-base:6.10.0`, `hapi-fhir-structures-r4:6.10.0`, `hapi-fhir-validation:6.10.0`, `hapi-fhir-validation-resources-r4:6.10.0`)
- Picocli 4.7.6 (CLI parsing and ANSI formatting)
- Jackson 2.18.2 (JSON processing)
- SLF4J 2.0.16 + Logback 1.5.16 (Logging)

**Storage**: None (Strictly stateless, in-memory processing only; zero database or persistent disk cache)

**Testing**: JUnit 5 (5.11.4), AssertJ (3.27.3)

**Target Platform**: Cross-platform Linux / macOS / Windows on JVM 21+

**Project Type**: Standalone CLI tool and embeddable pure Java library (`fhir-lint-core`)

**Performance Goals**: 
- < 2 seconds for a 100-resource bundle validation after in-memory warm-up
- Sub-second execution for individual resources
- Validation engine initialization cached across requests

**Constraints**: 
- 100% offline execution: zero remote network calls to external terminology servers or profile registries
- 100% zero-retention privacy: no clinical payloads written to disk or telemetry sinks
- Pure framework-agnostic Java: no Spring Boot or heavy web frameworks

**Scale/Scope**: 
- Core HL7 FHIR R4 structural schema validation
- 6 target US Core clinical profiles (`Patient`, `Encounter`, `Condition`, `Observation` [vital signs & lab], `MedicationRequest`, `DiagnosticReport`)
- 2 validation profiles: `BASE_R4` and `US_CORE` (default)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Evaluation |
| :--- | :---: | :--- |
| **I. Standards-First Healthcare Interoperability** | PASS | Employs official HAPI FHIR `FhirInstanceValidator` and HL7 StructureDefinitions. Does not invent custom parsers or conflicting validation semantics. |
| **II. Lean, Dependency-Minimized Architecture** | PASS | Pure Java 21 without Spring Boot or database engines. Pre-cached in-memory validation chain. |
| **III. Testable and Reliable Software** | PASS | Automated unit and integration tests verify outer-loop CLI behavior, exit codes, and library API results against synthetic fixtures. |
| **IV. Actionable Data-Quality Analysis** | PASS | Normalizes obscure compiler messages into human-friendly prose, distinct categories (`STRUCTURAL`, `PROFILE_CONFORMANCE`), exact FHIRPaths, and actionable suggestions. |
| **V. Privacy-Conscious Healthcare Software** | PASS | Uses exclusively synthetic test datasets. Operates entirely in volatile RAM with zero disk writes and zero external network calls. |
| **VI. Incremental Development and Simplicity** | PASS | Implements only Phase 2 scope: structural and US Core profile validation for the 6 core clinical resources. Deferring cross-resource graph indexing to Phase 3. |
| **VII. Developer-Focused CLI & Library Design** | PASS | Full integration with CLI `--profile` flag, standard POSIX exit codes (0 pass, 1 quality failure, 2 syntax/invocation error), ANSI tables, JSON, and SARIF. |

## Project Structure

### Documentation (this feature)

```text
specs/002-structural-profile-validation/
├── plan.md              # This implementation plan
├── research.md          # Phase 0: Technical decisions and research findings
├── data-model.md        # Phase 1: Entity definitions, fields, and relationships
├── quickstart.md        # Phase 1: Runnable end-to-end verification guide
├── contracts/           # Phase 1: Interface contracts (CLI, library API, issue schema)
│   ├── cli-contract.md
│   ├── library-api-contract.md
│   └── quality-issue.schema.json
└── checklists/
    └── requirements.md  # Specification quality checklist
```

### Source Code (repository root)

```text
src/
├── main/java/org/fhirlint/
│   ├── cli/
│   │   ├── FhirLintApplication.java
│   │   ├── command/
│   │   │   └── ValidateCommand.java
│   │   └── renderer/
│   │       ├── ConsoleTableRenderer.java
│   │       ├── JsonReportRenderer.java
│   │       └── SarifReportRenderer.java
│   └── core/
│       ├── FhirLinter.java
│       ├── model/
│       │   ├── IngestionInventory.java
│       │   ├── IssueCategory.java
│       │   ├── LintReport.java
│       │   ├── ParsedDataset.java
│       │   ├── QualityIssue.java
│       │   ├── QualityScore.java
│       │   ├── Severity.java
│       │   └── ValidationProfile.java
│       ├── parser/
│       │   ├── FhirBundleParser.java
│       │   └── FhirParseException.java
│       └── validation/
│           ├── FhirValidationEngine.java
│           ├── ValidationMessageNormalizer.java
│           └── ValidationSupportFactory.java
├── main/resources/
│   ├── logback.xml
│   └── profiles/
│       └── us-core/ (prepackaged StructureDefinitions & ValueSets)
└── test/java/org/fhirlint/
    ├── cli/
    │   └── FhirLintCliTest.java
    └── core/
        ├── FhirLinterTest.java
        └── validation/
            ├── FhirValidationEngineTest.java
            ├── ValidationMessageNormalizerTest.java
            └── UsCoreProfileValidationTest.java
```

**Structure Decision**: Builds directly upon the established single-project layout from Phase 1 (`src/main/java/org/fhirlint`), placing validation logic under `core/validation/` to keep the core engine modular and framework-agnostic.

## Complexity Tracking

> *No constitutional violations. Table kept empty.*

| Violation | Why Needed | Simpler Alternative Rejected Because |
| :--- | :--- | :--- |
| None | N/A | N/A |
