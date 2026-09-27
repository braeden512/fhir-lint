# Technical Research & Architectural Decisions: Phase 1 — FHIR Dataset Ingestion, Parsing & Boundary Validation

This document consolidates research, design choices, and technical trade-offs for Phase 1 of FHIRLint in alignment with [ADR-009](../../docs/adr/ADR-009-pivot-to-standalone-cli-and-core-engine.md) and the [Project Constitution v2.0.0](../../.specify/memory/constitution.md).

---

## 1. Local-First Standalone Execution Engine

### Decision
Implement FHIRLint as a standalone command-line application (powered by Picocli) and a reusable, framework-agnostic Java 21 engine (`FhirLinter`), eliminating external web servers, background task queues, and persistent databases.

### Rationale
- **Zero Cloud Infrastructure Cost**: Eliminates hosting bills for databases, web servers, and async queues.
- **HIPAA Compliance & Zero Data Leakage**: In healthcare, transmitting clinical records across the internet to a third-party API triggers severe compliance hurdles (BAAs, SOC 2, HIPAA audits). Running locally guarantees data never leaves the user's boundary.
- **Developer Ergonomics**: Linters are developer and CI/CD tools. They belong in terminal workflows, pre-commit hooks, and CI/CD runners (like GitHub Actions) with standard POSIX exit codes (`0`, `1`, `2`).

---

## 2. In-Memory Parsing & Privacy-Preserving Memory Management

### Decision
Parse FHIR payloads using HAPI FHIR's `FhirContext` (R4) and `IParser` in a transient in-memory pipeline. Raw JSON and HAPI resource object models are never written to disk or database. Once summary metrics and quality findings are extracted, all references are released and reclaimed by the JVM.

### Rationale
- **Privacy by Design (Constitution Principle V)**: The platform operates under a strict zero-retention posture for clinical data. No records are written to persistent databases or disk caches.
- **Standards Compliance (Constitution Principle I)**: HAPI FHIR's `JsonParser` is the industry reference standard for HL7 FHIR R4. Caching a thread-safe singleton `FhirContext.forR4Cached()` provides high-speed parsing without repeated schema initialization overhead.

---

## 3. Boundary Syntactic Pre-flight Validation

### Decision
Execute a fast, synchronous syntactic pre-flight check using Jackson `ObjectMapper` before invoking HAPI FHIR:
1. Ensure payload is not empty or whitespace.
2. Verify the input is valid, well-formed JSON.
3. Verify the root JSON contains a non-blank `resourceType` property.

If pre-flight fails, raise `FhirParseException`, output an actionable diagnostic to `stderr`, and terminate the CLI with exit code `2`.

### Rationale
- **Fast Failure & Clear Diagnostics**: Catches simple syntax or file mistakes in milliseconds before initializing deep FHIR model parsers.

---

## 4. CLI Framework & Output Formats

### Decision
Adopt **Picocli (`info.picocli:picocli:4.7.6`)** for the CLI framework. Support three distinct output formats:
1. `table`: Colorized ANSI terminal tables for interactive developer use.
2. `json`: Formatted JSON representation of `LintReport` for piping into `jq` or recording in CI artifacts.
3. `sarif`: OASIS SARIF 2.1.0 JSON format for native GitHub Code Scanning pull request annotations.

### Standard Exit Codes
- `0`: Quality check passed (meets `--min-score` and `--fail-on` criteria).
- `1`: Quality check failed quality gate thresholds.
- `2`: Syntax, input, or file boundary error.

---

## 5. Dependency Selection & Justification

In compliance with **Constitution v2.0.0 Technology Stack & Architectural Constraints**:
- **HAPI FHIR**: `ca.uhn.hapi.fhir:hapi-fhir-base:6.10.0` and `ca.uhn.hapi.fhir:hapi-fhir-structures-r4:6.10.0`
  - *Justification*: Mandated by Principle I for official HL7 FHIR R4 object models and parsing.
- **Picocli**: `info.picocli:picocli:4.7.6`
  - *Justification*: Mandated by Principle VII for POSIX-compliant CLI argument parsing, ANSI styling, and GraalVM native binary support.
- **Jackson Databind**: `com.fasterxml.jackson.core:jackson-databind:2.18.2`
  - *Justification*: Fast syntactic pre-flight checks and JSON/SARIF rendering.
- **JUnit 5 & AssertJ**: `org.junit.jupiter:junit-jupiter` and `org.assertj:assertj-core`
  - *Justification*: Robust automated testing with sub-second execution.
