# Implementation Plan: Phase 6 — Developer Experience and Standalone CLI Design

**Branch**: `006-developer-experience-cli` | **Date**: 2026-09-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/006-developer-experience-cli/spec.md`

## Summary

Deliver a polished, developer-focused command-line interface (CLI) and embeddable Java library API for FHIRLint. The CLI supports local files, directories, and standard input streams (`-`), target profile selection (`US_CORE`, `BASE_R4`), three output formats (`table`, `json`, `sarif`), configurable quality gate thresholds (`--min-score`, `--fail-on`), file redirection (`-o`), verbose issue expansion (`-v`), and standard POSIX exit codes (`0` on pass, `1` on quality gate breach, `2` on invocation/boundary error). Outputs embed actionable issue diagnostics, location context `(ResourceType/Id: path)`, remediation suggestions, and the mandatory non-clinical engineering indicator disclaimer (Constitution Principle IV).

---

## Technical Context

**Language/Version**: Java 21+

**Primary Dependencies**:
- Picocli 4.7.6 (POSIX CLI parsing, ANSI formatting, exit codes)
- HAPI FHIR R4 6.10.0 (`hapi-fhir-base`, `hapi-fhir-structures-r4`, `hapi-fhir-validation`)
- Jackson 2.18.2 (`jackson-databind`, `jackson-datatype-jsr310`)
- SLF4J 2.0.16 + Logback 1.5.16 (Logging)

**Storage**: None (Strictly stateless in-memory execution; zero database, zero disk cache, zero PHI retention)

**Testing**: JUnit 5 (5.11.4), AssertJ (3.27.3)

**Target Platform**: Cross-platform Linux / macOS / Windows on JVM 21+

**Project Type**: Standalone CLI tool and embeddable pure Java library (`fhir-lint-core`)

**Performance Goals**:
- Sub-500ms CLI argument parsing, input processing, and output rendering for standard bundles (< 1,000 resources)
- Total automated CLI and renderer test suite execution under 3.0 seconds

**Constraints**:
- Standard POSIX exit codes: `0` (pass), `1` (gate breach), `2` (invocation error)
- Strict stream separation: stdout receives only pure machine-readable formats (`json`, `sarif`), while diagnostics and breach descriptions write to stderr
- OASIS SARIF v2.1.0 schema validity with disclaimer placed in `runs[0].properties.disclaimer`
- Zero data retention: zero persistence of healthcare data (Constitution Principle V)

**Scale/Scope**:
- 3 input sources (single file, directory recursive, stdin `-`)
- 2 validation profiles (`US_CORE`, `BASE_R4`)
- 3 output renderers (ANSI `table`, `json`, OASIS `sarif` 2.1.0)
- Configurable quality gates (`--min-score 0-100`, `--fail-on error|warning|info|none`)

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Status | Evaluation |
| :--- | :---: | :--- |
| **I. Standards-First Healthcare Interoperability** | PASS | Uses standard FHIR R4 resources, HAPI FHIR validator, and official US Core profiles without proprietary extensions. |
| **II. Lean, Dependency-Minimized Architecture** | PASS | Framework-agnostic pure Java 21 core engine with Picocli CLI. Zero Spring Boot, zero web servers, zero relational databases. |
| **III. Testable and Reliable Software** | PASS | End-to-end CLI outer-loop tests verify exit code contracts (0, 1, 2), argument parsing, stdin pipes, and renderer outputs. Entire test suite executes in < 3s. |
| **IV. Actionable Data-Quality Analysis** | PASS | Findings report severity, rule ID, message, enriched resource location context `(ResourceType/Id: path)`, and remediation suggestions. Mandatory non-clinical disclaimer embedded across all outputs. |
| **V. Privacy-Conscious Healthcare Software** | PASS | Strictly in-memory transient processing with zero data retention, zero disk caching, zero remote network calls, and synthetic test data only. |
| **VI. Incremental Development and Simplicity** | PASS | Delivers the Phase 6 developer experience MVP. GraalVM native binaries and dataset diffing are cleanly deferred to Phase 7 and 8. |
| **VII. Developer-Focused CLI & Library Design** | PASS | Fully implements POSIX CLI standards, files/directories/stdin, exit codes (0/1/2), ANSI tables, JSON, SARIF 2.1.0, and fluent `FhirLinter` Java API. |

---

## Project Structure

### Documentation (this feature)

```text
specs/006-developer-experience-cli/
├── plan.md              # This implementation plan
├── research.md          # Phase 0: Technical decisions and research findings
├── data-model.md        # Phase 1: Entity definitions, fields, and relationships
├── quickstart.md        # Phase 1: Runnable end-to-end verification scenarios
├── contracts/           # Phase 1: Interface contracts
│   ├── cli-command-contract.md
│   ├── json-report-contract.md
│   ├── sarif-report-contract.md
│   └── java-library-api-contract.md
└── checklists/
    └── requirements.md  # Specification quality checklist
```

### Source Code (repository root)

```text
src/
├── main/java/org/fhirlint/
│   ├── cli/
│   │   ├── FhirLintApplication.java       # Main entry point & Picocli application
│   │   ├── command/
│   │   │   └── ValidateCommand.java       # CLI command: option validation, input resolution, exit codes
│   │   └── renderer/
│   │       ├── ConsoleTableRenderer.java  # ANSI table renderer: scorecards, progress bars, enriched locations
│   │       ├── JsonReportRenderer.java    # Formatted JSON report serialization
│   │       └── SarifReportRenderer.java   # OASIS SARIF 2.1.0 renderer: disclaimer in properties, dynamic rules
│   └── core/
│       ├── FhirLinter.java                # Fluent in-memory Java library entry point
│       ├── parser/
│       │   └── FhirBundleParser.java      # Parser handling files, strings, and stdin streams
│       └── model/
│           ├── LintReport.java            # Immutable report model with gate evaluation
│           ├── QualityGateConfig.java     # Policy configuration (minScore, failOn)
│           ├── QualityGateResult.java     # Policy result with breach diagnostics
│           └── QualityScore.java          # Composite score, grade tier, and non-clinical disclaimer
└── test/java/org/fhirlint/
    └── cli/
        ├── FhirLintCliTest.java           # [EXPANDED] Outer-loop CLI tests (stdin, directories, exit codes 0/1/2)
        ├── QualityGateCliTest.java        # [UPDATED] Gate exit code tests aligned with [0, 100] boundary
        └── renderer/
            └── ReportRenderersTest.java   # [EXPANDED] Unit tests for table, json, and SARIF 2.1.0 renderers
```

**Structure Decision**: Single Java project structure maintaining strict separation between `org.fhirlint.cli` and `org.fhirlint.core`.

---

## Complexity Tracking

> *No constitutional violations. Table left blank per specification.*

| Violation | Why Needed | Simpler Alternative Rejected Because |
| :--- | :--- | :--- |
| *None* | | |
