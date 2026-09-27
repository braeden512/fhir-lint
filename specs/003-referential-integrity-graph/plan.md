# Implementation Plan: Phase 3 — Referential Integrity and Resource Graph

**Branch**: `003-referential-integrity-graph` | **Date**: 2026-09-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/003-referential-integrity-graph/spec.md`

## Summary

Implement an in-memory dataset relationship graph and referential integrity analysis engine for FHIRLint. The engine extracts all inter-resource references using an AST property visitor over HAPI FHIR R4 models, populates a dual-map index (`fullUrl`, `${type}/${id}`, and bare ID collisions), and resolves references in $O(N + E)$ time. It evaluates 4 distinct rules: `REF-001` (broken local/UUID/contained references and empty/malformed IDs), `REF-002` (target resource type mismatches verified against HAPI R4 runtime schema definitions), `REF-003` (orphaned clinical resources lacking direct or indirect Patient context reachability), and `REF-004` (unverified external absolute HTTP/HTTPS links). Multi-file directory inputs are aggregated into a single unified index to eliminate cross-file partitioning false positives, with complete zero-retention privacy and sub-second execution.

## Technical Context

**Language/Version**: Java 21+

**Primary Dependencies**: 
- HAPI FHIR R4 (`hapi-fhir-base:6.10.0`, `hapi-fhir-structures-r4:6.10.0`, `hapi-fhir-validation:6.10.0`)
- Picocli 4.7.6 (CLI commands, flags, and ANSI tables)
- Jackson 2.18.2 (JSON report serialization)
- SLF4J 2.0.16 + Logback 1.5.16 (Logging)

**Storage**: None (Strictly stateless, in-memory processing only; zero database or persistent disk cache)

**Testing**: JUnit 5 (5.11.4), AssertJ (3.27.3)

**Target Platform**: Cross-platform Linux / macOS / Windows on JVM 21+

**Project Type**: Standalone CLI tool and embeddable pure Java library (`fhir-lint-core`)

**Performance Goals**: 
- Complete graph indexing and referential integrity analysis in < 1.0 second for a dataset containing 5,000 resources and 20,000 references
- Working memory overhead strictly under 64 MB for 10,000 resources
- Overall $O(N + E)$ computational complexity

**Constraints**: 
- 100% offline execution: zero remote network calls to resolve external HTTP(S) references
- 100% zero-retention privacy: no clinical payloads written to disk or telemetry sinks
- Pure framework-agnostic Java 21: core collections (`HashMap`, `ArrayList`) without external graph databases

**Scale/Scope**: 
- All FHIR R4 `Reference` elements across all standard resource types
- 4 rule evaluations: `REF-001`, `REF-002`, `REF-003`, `REF-004`
- Multi-file directory batch aggregation into a single dataset graph index

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Evaluation |
| :--- | :---: | :--- |
| **I. Standards-First Healthcare Interoperability** | PASS | Operates directly on HAPI FHIR R4 resource models and uses HAPI's preloaded `RuntimeResourceDefinition` to look up permitted reference target types. |
| **II. Lean, Dependency-Minimized Architecture** | PASS | Pure Java 21 collections (`HashMap`, `ArrayList`) without Neo4j, TinkerPop, SQLite, or web frameworks. In-process and completely stateless. |
| **III. Testable and Reliable Software** | PASS | Comprehensive test suite covering graph indexing, resolution formats (relative, UUID URN, contained fragment, external URI), cyclic references, and CLI end-to-end execution. |
| **IV. Actionable Data-Quality Analysis** | PASS | Reports exact FHIRPaths (`Observation.subject.reference`), clear diagnostic explanations, and concrete remediation advice. Categorized under `REFERENTIAL_INTEGRITY`. |
| **V. Privacy-Conscious Healthcare Software** | PASS | Uses exclusively synthetic de-identified test datasets. Operates with strict zero retention in volatile RAM with zero disk writes. |
| **VI. Incremental Development and Simplicity** | PASS | Delivers targeted Phase 3 scope: dataset-level referential integrity without premature distributed systems or persistence layers. |
| **VII. Developer-Focused CLI & Library Design** | PASS | Fully integrated into POSIX CLI (`fhir-lint validate`), returns standard exit codes (0 pass, 1 quality failure, 2 invocation error), renders ANSI tables, JSON, and SARIF. |

## Project Structure

### Documentation (this feature)

```text
specs/003-referential-integrity-graph/
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
│       ├── graph/
│       │   ├── DefaultReferentialIntegrityEngine.java
│       │   ├── ReferenceExtractor.java
│       │   ├── ReferenceResolution.java
│       │   ├── ReferenceType.java
│       │   ├── ReferentialIntegrityEngine.java
│       │   ├── ResourceGraphIndex.java
│       │   ├── ResourceNode.java
│       │   └── ResourceReference.java
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
│       │   └── RecordingParserErrorHandler.java
│       └── validation/
│           ├── FhirValidationEngine.java
│           ├── ValidationMessageNormalizer.java
│           └── ValidationSupportFactory.java
└── test/java/org/fhirlint/
    ├── cli/
    │   └── FhirLintCliTest.java
    └── core/
        ├── FhirLinterTest.java
        └── graph/
            ├── DefaultReferentialIntegrityEngineTest.java
            ├── ReferenceExtractorTest.java
            ├── ResourceGraphIndexTest.java
            └── TopologicalContextAnalysisTest.java
```

**Structure Decision**: Houses all graph indexing and referential integrity components under `org.fhirlint.core.graph` to maintain clean domain separation between single-resource schema/profile validation (`core.validation`) and cross-resource dataset relationship analysis (`core.graph`).

## Complexity Tracking

> *No constitutional violations. Table kept empty.*

| Violation | Why Needed | Simpler Alternative Rejected Because |
| :--- | :--- | :--- |
| None | N/A | N/A |
