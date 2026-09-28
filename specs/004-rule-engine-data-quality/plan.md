# Implementation Plan: Phase 4 — Pluggable Rule Engine and Data-Quality Checks

**Branch**: `004-rule-engine-data-quality` | **Date**: 2026-09-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/004-rule-engine-data-quality/spec.md`

## Summary

Implement a modular, pluggable data-quality rule engine for FHIRLint that detects clinical inconsistencies, chronological contradictions, duplicate records, missing clinical context, and terminology defects. The architecture defines a framework-agnostic `QualityRule` interface and `RuleRegistry` with clear `RuleScope` delineation (`RESOURCE` vs `DATASET`). The catalog provides 11 targeted rules across 4 categories: Consistency (`CONS-001..004`), Deduplication (`DUP-001..002`), Terminology (`TERM-001..003`), and Completeness (`COMP-001..002`). The engine integrates into `FhirLinter` by sharing a single-pass `ResourceGraphIndex` with Phase 3, running completely in-memory with zero external database or network dependencies.

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
- Complete 11-rule catalog evaluation in < 1.5 seconds for a dataset containing 5,000 resources on standard developer hardware
- Working memory overhead strictly under 64 MB
- $O(N)$ demographic and identifier deduplication via hash map indexing

**Constraints**: 
- 100% offline execution: zero remote network calls to external terminology servers
- 100% zero-retention privacy: no clinical payloads written to disk or telemetry sinks
- Pure framework-agnostic Java 21: core collections without external rule engines (Drools/EasyRules rejected)

**Scale/Scope**: 
- 11 core quality rules across 4 categories
- Evaluates individual FHIR R4 resource models and cross-resource graphs
- Single-pass `ResourceGraphIndex` reuse across Phase 3 and Phase 4

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Evaluation |
| :--- | :---: | :--- |
| **I. Standards-First Healthcare Interoperability** | PASS | Operates directly on HAPI FHIR R4 domain models, validates canonical URIs (`http://loinc.org`, `http://snomed.info/sct`), enforces UCUM unit representations (`http://unitsofmeasure.org`), and adheres to standard FHIR R4 value sets. |
| **II. Lean, Dependency-Minimized Architecture** | PASS | Pure Java 21 interface (`QualityRule`) and registry with zero heavy rule frameworks (Drools, EasyRules, Spring Boot rejected). Completely stateless and in-memory. |
| **III. Testable and Reliable Software** | PASS | Every rule is an isolated class with dedicated unit tests and synthetic fixtures. End-to-end integration tests verify scoring, category breakdown, and CLI exit codes. |
| **IV. Actionable Data-Quality Analysis** | PASS | Emits exact FHIRPath element paths, explanations, and concrete remediation suggestions for every issue under distinct categories (`CONSISTENCY`, `DUPLICATE`, `TERMINOLOGY`, `COMPLETENESS`). |
| **V. Privacy-Conscious Healthcare Software** | PASS | Strictly in-memory evaluation with synthetic test fixtures. Zero disk persistence, zero remote telemetry, zero PHI retention. |
| **VI. Incremental Development and Simplicity** | PASS | Focuses strictly on Phase 4 rule catalog and engine dispatching. Custom DSL scripting and external rule loading deferred to Phase 8. |
| **VII. Developer-Focused CLI & Library Design** | PASS | Output integrates seamlessly into existing ANSI tables, JSON reports, and SARIF GitHub annotations, honoring standard POSIX exit codes. |

## Project Structure

### Documentation (this feature)

```text
specs/004-rule-engine-data-quality/
├── plan.md              # This implementation plan
├── research.md          # Phase 0: Technical decisions and research findings
├── data-model.md        # Phase 1: Entity definitions, fields, and relationships
├── quickstart.md        # Phase 1: Runnable end-to-end verification guide
├── contracts/           # Phase 1: Interface contracts
│   ├── quality-rule-api.md
│   ├── rule-catalog.md
│   └── cli-contract.md
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
│       │   ├── ResolutionStatus.java
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
│       │   ├── FhirParseException.java
│       │   └── RecordingParserErrorHandler.java
│       ├── rules/
│       │   ├── DefaultQualityRuleEngine.java
│       │   ├── QualityRule.java
│       │   ├── QualityRuleEngine.java
│       │   ├── RuleContext.java
│       │   ├── RuleRegistry.java
│       │   ├── RuleScope.java
│       │   ├── completeness/
│       │   │   ├── MissingObservationValueRule.java
│       │   │   └── MissingSubjectContextRule.java
│       │   ├── consistency/
│       │   │   ├── BirthToEventChronologyRule.java
│       │   │   ├── ChronologyHelper.java
│       │   │   ├── DeceasedStatusRule.java
│       │   │   ├── DiagnosticReportObservationStateRule.java
│       │   │   └── PeriodChronologyRule.java
│       │   ├── duplicate/
│       │   │   ├── PatientDemographicDuplicateRule.java
│       │   │   └── PatientIdentifierDuplicateRule.java
│       │   └── terminology/
│       │       ├── CanonicalSystemUriRule.java
│       │       ├── CoreValueSetBindingRule.java
│       │       ├── DefaultTerminologyService.java
│       │       ├── TerminologyService.java
│       │       └── VitalSignsUcumUnitRule.java
│       └── validation/
│           ├── FhirValidationEngine.java
│           ├── ValidationMessageNormalizer.java
│           └── ValidationSupportFactory.java
└── test/java/org/fhirlint/
    ├── cli/
    │   └── FhirLintCliTest.java
    └── core/
        ├── FhirLinterTest.java
        ├── graph/
        │   ├── DefaultReferentialIntegrityEngineTest.java
        │   ├── ReferenceExtractorTest.java
        │   ├── ResourceGraphIndexTest.java
        │   └── TopologicalContextAnalysisTest.java
        └── rules/
            ├── DefaultQualityRuleEngineTest.java
            ├── RuleRegistryTest.java
            ├── completeness/
            │   ├── MissingObservationValueRuleTest.java
            │   └── MissingSubjectContextRuleTest.java
            ├── consistency/
            │   ├── BirthToEventChronologyRuleTest.java
            │   ├── ChronologyHelperTest.java
            │   ├── DeceasedStatusRuleTest.java
            │   ├── DiagnosticReportObservationStateRuleTest.java
            │   └── PeriodChronologyRuleTest.java
            ├── duplicate/
            │   ├── PatientDemographicDuplicateRuleTest.java
            │   └── PatientIdentifierDuplicateRuleTest.java
            └── terminology/
                ├── CanonicalSystemUriRuleTest.java
                ├── CoreValueSetBindingRuleTest.java
                ├── DefaultTerminologyServiceTest.java
                └── VitalSignsUcumUnitRuleTest.java
```

**Structure Decision**: Houses all rule engine abstractions and rule catalog implementations under `org.fhirlint.core.rules` organized by category sub-packages (`consistency`, `duplicate`, `terminology`, `completeness`). This keeps individual rules modular, decoupled, and easily testable without polluting the single-resource validation engine or graph index packages.

## Complexity Tracking

> *No constitutional violations. Table kept empty.*

| Violation | Why Needed | Simpler Alternative Rejected Because |
| :--- | :--- | :--- |
| None | N/A | N/A |
