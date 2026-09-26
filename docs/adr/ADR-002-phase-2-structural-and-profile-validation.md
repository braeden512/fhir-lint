# ADR-002: Phase 2 — Structural and Profile Validation with HAPI FHIR

## Status
Accepted

## Context & Problem Statement
FHIRLint requires an industrial-grade baseline validation layer that verifies whether incoming FHIR R4 resources conform to core HL7 schema structures, datatypes, cardinalities, primitive regexes, and official Implementation Guide profiles (specifically US Core v3.1.1 / v6.1.0). 

Building a custom parser or schema engine from scratch would violate **Constitution Principle I (Standards-First Healthcare Interoperability)**. We must establish how HAPI FHIR's validation engine is integrated, configured, and adapted to produce standardized, actionable issue records.

## Decision Drivers
1. **Conformance Accuracy**: HL7 FHIR conformance rules are intricate; official HL7/HAPI tooling must be used.
2. **Profile Extensibility**: Support for US Core profiles without baking rigid profile code into the service.
3. **Execution Performance**: Validator initialization is CPU- and memory-intensive; validation artifacts must be cached.
4. **Issue Normalization**: HAPI's `SingleValidationMessage` objects must be translated into FHIRLint's clean, developer-focused `QualityIssue` model.

## Considered Options
1. **Raw HAPI `FhirValidator` with `ValidationSupportChain`**: Standard HAPI approach utilizing `DefaultProfileValidationSupport`, `NpmPackageValidationSupport`, and `CachingValidationSupport`.
2. **HL7 Java Validator CLI wrapper**: Invoking the external `validator_cli.jar` via process execution. (High process overhead, fragile inter-process communication).
3. **Custom JSON-Schema Validator**: Mapping FHIR to JSON-Schema. (Fails to validate FHIRPath invariants, slicing, or complex US Core profiles).

## Decision Outcome
Adopt **Option 1: HAPI `FhirValidator` configured with a cached `ValidationSupportChain`**.

### Configuration Details
1. **Singleton `FhirContext`**: Instantiated once as a Spring `@Bean` (`FhirContext.forR4()`) to avoid expensive schema re-initialization.
2. **Validation Support Chain**:
   - `DefaultProfileValidationSupport`: Base FHIR R4 StructureDefinitions and ValueSets.
   - `NpmPackageValidationSupport`: Preloaded with the official US Core NPM package (`hl7.fhir.us.core`).
   - `SnapshotGeneratingValidationSupport`: Generates snapshot profiles dynamically.
   - `InMemoryTerminologyServerValidationSupport`: Basic in-memory code validation for base types.
   - `CachingValidationSupport`: LRU cache wrapper around the chain.
3. **Message Normalization**:
   - Map HAPI severity `FATAL` / `ERROR` $\to$ `QualityIssue.Severity.ERROR`
   - Map HAPI severity `WARNING` $\to$ `QualityIssue.Severity.WARNING`
   - Map HAPI severity `INFORMATION` $\to$ `QualityIssue.Severity.INFO`
   - Clean up dense compiler messages into clear, actionable prose.
   - Categorize issues as `STRUCTURAL` (base schema failures) or `PROFILE_CONFORMANCE` (US Core constraint violations).

## Consequences
### Positive
- Strict compliance with official HL7 FHIR R4 and US Core specifications.
- Caching prevents repeated parsing of large profile definitions.
- Normalizes disparate HL7 error formats into a consistent API response.

### Negative / Trade-offs
- The HAPI validator initialization incurs an initial startup latency (typically 2–4 seconds on first run) while profiles are loaded into memory.
