# Phase 0: Technical Research & Architecture Decisions

This document captures the technical decisions, architecture evaluations, and component designs for **Phase 2: Structural and Profile Validation** of FHIRLint.

---

## Decision 1: Validation Engine Architecture & HAPI FHIR Integration

### Decision
Integrate HAPI FHIR's `FhirInstanceValidator` managed via `FhirValidator` and composed using a thread-safe `ValidationSupportChain` in `FhirValidationEngine`, using `FhirContext.forR4Cached()`.

### Rationale
- **HL7 Conformance Accuracy**: HL7 FHIR R4 schema and Implementation Guide (IG) conformance requires evaluating FHIRPath invariants, complex choice types (`value[x]`), slicing on lists (e.g. `Observation.category`), and cardinality boundaries. Official HAPI FHIR validation tooling is the gold standard in the Java ecosystem (Constitution Principle I).
- **In-Memory Operation**: `FhirInstanceValidator` can operate completely in volatile memory without connecting to an external database or network endpoint.
- **Support Chain Composition**: HAPI's `ValidationSupportChain` enables modular assembly of base R4 definitions (`DefaultProfileValidationSupport`), pre-packaged US Core profiles (`NpmPackageValidationSupport` / `PrePopulatedValidationSupport`), dynamic snapshot generation (`SnapshotGeneratingValidationSupport`), and in-memory ValueSet evaluation (`InMemoryTerminologyServerValidationSupport`).

### Alternatives Considered
1. **External HL7 Validator CLI (`validator_cli.jar`)**: Invoking the official validator via `ProcessBuilder` or CLI wrapper.
   - *Rejected*: Extremely heavy startup latency (multiple seconds per invocation), fragile IPC serialization, complex dependency packaging, and poor developer ergonomics.
2. **Custom JSON-Schema Engine**: Translating FHIR StructureDefinitions into JSON Schema drafts.
   - *Rejected*: Cannot evaluate FHIRPath invariants (e.g. `us-core-8`), cannot resolve complex choice element typing, and violates Constitution Principle I (do not reinvent standards).

---

## Decision 2: US Core Profile Packaging & Offline Conformance (Adhering to ADR-002)

### Decision
Package the official HL7 US Core v3.1.1 package (`hl7.fhir.us.core-3.1.1.tgz`) or all complete transitive StructureDefinitions, extensions (`us-core-race`, `us-core-ethnicity`, `us-core-birthsex`, `us-core-genderIdentity`), and ValueSets directly on the classpath under `src/main/resources/profiles/us-core/`. Load this locally using `NpmPackageValidationSupport` (or `PrePopulatedValidationSupport`) within the validation support chain.

### Rationale
- **Direct Conformance with ADR-002**: ADR-002 specifies using `NpmPackageValidationSupport` preloaded with the official US Core package. Loading the package locally from the classpath provides complete transitive definition support (profiles, extensions, snapshots, ValueSets) without needing manual file-by-file extraction.
- **100% Offline Capability**: Healthcare data linters must function in secure, air-gapped developer workstations and private CI/CD runners without external network egress (Constitution Principle V). Loading from classpath is 100% offline.
- **Targeted Scope**: Baseline target is US Core v3.1.1 covering the core clinical backbone resources (`Patient`, `Encounter`, `Condition`, `Observation` [vital signs and lab], `MedicationRequest`, `DiagnosticReport`), which account for >95% of clinical exchange.

### Alternatives Considered
1. **Dynamic Remote NPM Package Download**: Fetching the `hl7.fhir.us.core` package from the HL7 package registry at runtime.
   - *Rejected*: Violates offline requirements, fails in isolated CI environments, and poses security/supply-chain risks during build and execution.
2. **Remote Terminology Server / FHIR Server Endpoint**: Pointing the validator to an external FHIR server (e.g. Tx.fhir.org).
   - *Rejected*: Incurs massive network round-trip delays, exposes potential PHI or resource metadata in queries, and prevents offline CLI execution.

---

## Decision 3: Profile Assignment for Resources Lacking `meta.profile`

### Decision
When the validation profile is set to `ValidationProfile.US_CORE`, the validation engine automatically associates the corresponding US Core StructureDefinition URL with the resource if the resource does not explicitly declare a `meta.profile` matching US Core:

- `Patient` $\to$ `http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient`
- `Encounter` $\to$ `http://hl7.org/fhir/us/core/StructureDefinition/us-core-encounter`
- `Condition` $\to$ `http://hl7.org/fhir/us/core/StructureDefinition/us-core-condition`
- `Observation`:
  - If `category` contains code `vital-signs` (system `http://terminology.hl7.org/CodeSystem/observation-category`) $\to$ `http://hl7.org/fhir/us/core/StructureDefinition/us-core-vital-signs`
  - If `category` contains code `laboratory` $\to$ `http://hl7.org/fhir/us/core/StructureDefinition/us-core-observation-lab`
  - Otherwise $\to$ validate against base FHIR R4 `Observation` and emit an informational notice (`INFO`) stating that no specialized US Core observation category was matched.
- `MedicationRequest` $\to$ `http://hl7.org/fhir/us/core/StructureDefinition/us-core-medicationrequest`
- `DiagnosticReport`:
  - If `category` contains `laboratory` or `LAB` $\to$ `http://hl7.org/fhir/us/core/StructureDefinition/us-core-diagnosticreport-lab`
  - Otherwise $\to$ `http://hl7.org/fhir/us/core/StructureDefinition/us-core-diagnosticreport-note`

### Rationale
- In real-world healthcare datasets (e.g., Synthea synthetic bundles, hospital legacy exports), resources often conform to base schemas and omit explicit `meta.profile` tags.
- Categorization-based dispatch for `Observation` and `DiagnosticReport` avoids false positive slicing errors on general observations while ensuring vital signs and lab results are rigorously validated against their respective US Core profiles.

---

## Decision 4: Validation Message Normalization & Deterministic `ruleId` Scheme

### Decision
Implement `ValidationMessageNormalizer` to transform HAPI FHIR's `SingleValidationMessage` objects into clean, developer-focused `QualityIssue` records.

### Normalization Logic:
1. **Severity Mapping**:
   - `ResultSeverityEnum.FATAL` $\to$ `Severity.ERROR`
   - `ResultSeverityEnum.ERROR` $\to$ `Severity.ERROR`
   - `ResultSeverityEnum.WARNING` $\to$ `Severity.WARNING`
   - `ResultSeverityEnum.INFORMATION` $\to$ `Severity.INFO`
2. **Category Classification**:
   - Base FHIR R4 schema constraints (datatype parsing, unknown elements, base field cardinality, primitive regex) $\to$ `IssueCategory.STRUCTURAL`.
   - Implementation Guide constraints (US Core profile URL, slicing constraint, Must Support element, or US Core invariants like `us-core-8`) $\to$ `IssueCategory.PROFILE_CONFORMANCE`.
3. **Deterministic `ruleId` Scheme**:
   - `STRUCT_PRIMITIVE_FORMAT`: Invalid primitive regex (e.g., birthDate syntax).
   - `STRUCT_DATATYPE`: Datatype mismatch (e.g., string in integer field).
   - `STRUCT_CARDINALITY`: Missing mandatory base element or array bounds violation.
   - `STRUCT_UNKNOWN_ELEMENT`: Unrecognized property in resource payload.
   - `USCORE_MANDATORY_FIELD`: Missing mandatory US Core element (min cardinality > 0 in profile).
   - `USCORE_INVARIANT`: Failure of US Core invariant expression (e.g., `us-core-8`).
   - `USCORE_SLICING`: Missing required slice entry or invalid slicing discriminator.
   - `USCORE_MUST_SUPPORT`: Missing Must Support element.
4. **Prose Cleaning & Actionable Suggestions**:
   - Strip internal HAPI parser artifact prefixes (e.g., `HAPI-xxxx:`, raw Java package names).
   - Generate concrete, actionable `suggestion` strings (e.g., `"Ensure Patient.identifier includes an official system (e.g., MRN or SSN)"` or `"Provide a valid UCUM unit for vital sign observations"`).

---

## Decision 5: Performance Optimization & In-Memory LRU Caching

### Decision
Wrap the validation support chain in HAPI's `CachingValidationSupport` and maintain a cached, thread-safe instance in `FhirValidationEngine` reusing `FhirContext.forR4Cached()`.

### Rationale
- Schema structure definitions, snapshot generators, and profile trees take 2–3 seconds to initialize on cold start.
- Wrapping the chain with `CachingValidationSupport` caches resolved StructureDefinitions and validation results in an in-memory LRU cache, dropping subsequent resource validation to < 5ms per resource.
- Validating a 100-resource bundle completes in well under 2 seconds once initialized.
- Thread-safe design allows safe reuse in concurrent CI/CD runs or embeddable library pipelines without Spring overhead.

---

## Decision 6: Pre-flight Syntax vs. Structural Validation Boundary in Parser

### Decision
Configure `FhirBundleParser`'s HAPI `JsonParser` with a lenient error handler (`LenientErrorHandler`) during dataset intake so that syntax/pre-flight check (exit code 2) is strictly confined to:
1. Malformed JSON syntax (unparseable tokens, truncated JSON), and
2. Missing root `resourceType` declaration.

FHIR primitive format errors (e.g., invalid date format `"1985-99-99"`), datatype mismatches, and unrecognized fields are preserved in the parsed DOM/resource and evaluated by `FhirValidationEngine`, properly generating `STRUCTURAL` error issues and resulting in quality gate failure (exit code 1).

### Rationale
- Resolves the architectural conflict where `DataFormatException` on primitive syntax caused premature exit code 2 aborts, bypassing the validation engine and violating Scenario 1 of `quickstart.md`.
