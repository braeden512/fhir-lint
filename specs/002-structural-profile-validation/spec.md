# Feature Specification: Phase 2 — Structural and Profile Validation

**Feature Branch**: `002-structural-profile-validation`

**Created**: 2026-09-26

**Status**: Draft

**Input**: User description: "Create the spec for phase 2. Use the documentation in docs , specifically docs/adr/ADR-002-phase-2-structural-and-profile-validation.md as well as the PRODUCT_SPEC.md to guide your thinking."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Base FHIR R4 Structural Conformance Validation (Priority: P1)

As a healthcare software engineer or data integrator, I want FHIRLint to validate incoming FHIR resources against core HL7 FHIR R4 structural schema definitions (datatypes, cardinalities, field existence, primitive format regexes, and value ranges), so that structural syntax defects and malformed elements are caught immediately before data flows into downstream applications.

**Why this priority**: Structural conformance is the foundational baseline for healthcare interoperability. If a resource violates the core HL7 FHIR R4 structure (e.g., invalid data types, missing mandatory base fields, or invalid primitive formatting), downstream healthcare databases and parsers will reject or corrupt the data.

**Independent Test**: Can be tested by running the validator against resources with intentional schema violations (e.g., an `Observation` with a string in an integer field, missing mandatory fields, or an invalid date format) and verifying that the system generates structural error issues pinpointing the exact field and issue.

**Acceptance Scenarios**:

1. **Given** a FHIR R4 resource with an invalid primitive format (e.g., an unparseable `Patient.birthDate` string "1980-99-99"), **When** structural validation is performed, **Then** the system detects the defect and generates a structural error issue citing the invalid date format.
2. **Given** a FHIR R4 resource missing a mandatory core schema element (e.g., a `Patient` without any valid structure, or an `Encounter` missing required `status`), **When** structural validation is performed, **Then** the system reports a structural error identifying the missing mandatory field.
3. **Given** a FHIR R4 resource containing unrecognized or extraneous fields not defined in the specification, **When** structural validation is performed, **Then** the system flags the unrecognized elements as structural validation issues.
4. **Given** a structurally valid FHIR R4 resource, **When** base structural validation is performed, **Then** zero structural errors are reported for that resource.

---

### User Story 2 - US Core Implementation Guide Profile Validation (Priority: P2)

As a healthcare data engineer working within the U.S. healthcare ecosystem, I want FHIRLint to validate resources against official US Core profile constraints (including mandatory elements, Must Support elements, sliced arrays, and profile-specific invariants), so that I know whether our dataset complies with ONC/USCDI interoperability requirements.

**Why this priority**: Structural validity alone is insufficient for real-world healthcare exchange. US Core imposes additional clinical and operational constraints on top of base FHIR R4. Ensuring compliance with US Core profiles is essential for regulatory readiness and reliable electronic health record integration.

**Independent Test**: Can be tested by providing resources conforming to base FHIR R4 but failing US Core requirements (e.g., a `Patient` lacking mandatory identifiers or US Core race/ethnicity extensions, or an `Observation` missing a required category slice) and verifying that profile conformance issues are generated.

**Acceptance Scenarios**:

1. **Given** a `Patient` resource that is valid base FHIR R4 but lacks a required US Core identifier, name, or gender, **When** validation is run under the US Core profile, **Then** the system reports profile conformance errors citing the missing US Core mandatory elements.
2. **Given** a Vital Signs `Observation` resource missing a required category slice code (`vital-signs`) or missing UCUM units in `valueQuantity`, **When** validation is run under the US Core profile, **Then** the system reports a profile conformance error identifying the slicing or invariant violation.
3. **Given** a dataset containing clinical resources (Patient, Encounter, Condition, Observation, MedicationRequest, DiagnosticReport) that satisfy all US Core constraints, **When** validation is executed under the US Core profile, **Then** the system reports zero profile conformance errors.

---

### User Story 3 - Configurable Validation Profile Target (Priority: P3)

As a developer or CI/CD engineer, I want to select the target validation profile (such as base FHIR R4 only or US Core) via a CLI option or library configuration, with US Core enabled by default, so that I can validate datasets against the specific regulatory and architectural level required for my workflow.

**Why this priority**: Different workflows require different conformance strictness. Some internal pipelines only need base FHIR R4 validity, while external-facing interoperability gateways require strict US Core conformance. Giving users control over the active profile avoids false positives for non-US workflows while maintaining strict defaults.

**Independent Test**: Can be tested by running the validator on the same dataset with `--profile BASE_R4` and with `--profile US_CORE` (or defaulting to US Core), verifying that US Core constraints are only enforced when US Core is the selected profile.

**Acceptance Scenarios**:

1. **Given** a resource that is valid base FHIR R4 but does not meet US Core constraints, **When** the user validates with `--profile BASE_R4`, **Then** the validation passes with zero errors.
2. **Given** the same resource, **When** the user validates without specifying a profile flag (default) or with `--profile US_CORE`, **Then** profile conformance errors for US Core violations are reported.
3. **Given** an invalid or unsupported profile argument (e.g., `--profile INVALID_PROFILE`), **When** the command is executed, **Then** the system terminates gracefully with an actionable diagnostic message and exit code `2`.

---

### User Story 4 - Normalized and Actionable Issue Reporting (Priority: P4)

As a developer inspecting linting results, I want low-level validation engine messages to be normalized into clear, actionable issue records—with standardized severity levels, categorized distinctions between structural and profile defects, exact field locations, and concrete remediation advice—so that I can quickly fix problems without deciphering dense compiler diagnostics.

**Why this priority**: Raw conformance messages from underlying validation engines are often hundreds of characters long, highly technical, and obscure the actual fix. Developer velocity requires clear, human-readable explanations paired with actionable remediation suggestions and accurate location paths.

**Independent Test**: Can be tested by asserting the structure and content of generated issues across multiple validation failures, ensuring each issue contains severity, category, location path, understandable message, and remediation guidance.

**Acceptance Scenarios**:

1. **Given** a validation failure, **When** the issue is produced, **Then** its severity is mapped to one of `ERROR`, `WARNING`, or `INFO`.
2. **Given** a base schema violation vs. a US Core constraint failure, **When** issues are categorized, **Then** the schema violation is labeled `STRUCTURAL` and the US Core failure is labeled `PROFILE_CONFORMANCE`.
3. **Given** an issue on a specific resource field, **When** the location is reported, **Then** it provides an unambiguous path (e.g., `Patient.birthDate` or `Observation.code.coding[0].system`) indicating exactly where the error occurred.
4. **Given** a validation issue, **When** the issue is displayed or serialized, **Then** it includes a concrete suggestion explaining how to remediate the defect.

---

### User Story 5 - In-Memory Fast Execution and Zero Data Retention (Priority: P5)

As a security, privacy, and DevOps stakeholder, I want validation definitions and profile models to be preloaded and cached in memory, executing with zero network calls and zero persistent disk retention, so that validation runs in sub-second time without exposing protected health data.

**Why this priority**: Conformance validation definitions are large and compute-intensive. Re-parsing schemas on every resource causes unacceptable performance lag. Furthermore, strict healthcare privacy mandates that validation occurs entirely in volatile memory without caching user payloads or making outbound network calls to external terminology servers.

**Independent Test**: Can be verified by running validation repeatedly across multi-resource datasets in an isolated environment without network access, measuring sub-second validation throughput and confirming zero disk writes.

**Acceptance Scenarios**:

1. **Given** an execution run on a 100-resource dataset, **When** validation executes, **Then** validation schemas and profiles are cached in memory so subsequent resource checks execute without re-parsing overhead.
2. **Given** an environment with no internet access, **When** validation executes, **Then** all base schema and US Core profile validations succeed entirely offline using packaged in-memory resources.
3. **Given** sensitive synthetic patient data in the input, **When** validation completes, **Then** no resource data or validation artifacts are written to disk.

---

### Edge Cases

- **Mixed Resources in Bundles**: A Bundle contains both supported clinical resources (e.g., `Patient`, `Observation`) and unsupported or generic resource types (e.g., `Basic` or custom extensions). The system validates known types against their respective profiles and handles unspecified types gracefully against base R4 schemas without crashing.
- **Resources with Explicit `meta.profile`**: A resource explicitly declares a `meta.profile` canonical URL that differs from or specializes the active profile target. The validator validates against the declared profile if known, or defaults to the active suite without failure.
- **Resources Missing `meta.profile`**: Resources in a dataset may lack `meta.profile` tags. When the target profile is US Core, the system should associate the appropriate US Core profile based on the resource type (e.g., mapping `Patient` to the US Core Patient profile).
- **Missing or Remote ValueSets / Terminology**: When an element binds to an external terminology ValueSet not fully packaged in-memory, the validator treats it as a non-fatal warning or informational notice rather than halting validation, ensuring offline operability.
- **Deeply Sliced Arrays**: When an element has multiple complex slices (e.g., `Observation.category` or `Observation.component`), errors must accurately pinpoint which slice failed and why.
- **Empty or Zero-Resource Datasets**: Validating an empty bundle returns a clean report with zero structural or profile errors without failing.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST validate FHIR R4 resources against HL7 FHIR R4 core structural schemas, including data types, cardinality boundaries, required base fields, and primitive format regexes.
- **FR-002**: The system MUST validate FHIR R4 resources against official US Core profile definitions for core clinical resources: `Patient`, `Encounter`, `Condition`, `Observation` (vital signs and laboratory), `MedicationRequest`, and `DiagnosticReport`.
- **FR-003**: The system MUST support configurable profile selection via a profile option (`--profile` in CLI, or configuration parameter in library API), supporting at least `BASE_R4` and `US_CORE`.
- **FR-004**: The system MUST default to the `US_CORE` validation profile when no profile is explicitly specified.
- **FR-005**: When `US_CORE` is active, the system MUST validate resources against US Core profiles even if the resource payload does not explicitly declare a `meta.profile` tag.
- **FR-006**: The system MUST normalize raw validation diagnostics into standard issue records containing:
  - Severity: `ERROR`, `WARNING`, or `INFO`.
  - Category: `STRUCTURAL` (for core schema violations) or `PROFILE_CONFORMANCE` (for Implementation Guide/US Core constraint violations).
  - Resource type and resource ID (where available).
  - Location path indicating the specific element or slice in the resource.
  - Actionable human-readable description of the defect.
  - Concrete remediation guidance explaining how to resolve the issue.
- **FR-007**: The system MUST map fatal and error conditions from validation to `ERROR` severity, warnings to `WARNING`, and informational messages to `INFO`.
- **FR-008**: The system MUST cache validation definitions, snapshot generators, and profile schemas in memory to avoid repeated parsing overhead across resources and subsequent validation runs.
- **FR-009**: The system MUST operate completely offline and in-memory, without making remote network calls or persisting data to disk.
- **FR-010**: The system MUST support individual resource validation as well as unrolled multi-resource Bundle validation.
- **FR-011**: If an invalid profile name is provided, the system MUST terminate gracefully with an actionable error message and exit code `2`.

---

### Key Entities *(include if feature involves data)*

- **Validation Profile (`ValidationProfile`)**: Represents the target conformance specification applied during validation (e.g., `BASE_R4` for core HL7 FHIR R4 schema, `US_CORE` for US Core Implementation Guide profiles).
- **Validation Issue (`QualityIssue`)**: A standardized, developer-oriented finding representing a validation defect or warning. Key attributes:
  - `id`: Unique identifier for the issue instance.
  - `severity`: Conformance severity (`ERROR`, `WARNING`, `INFO`).
  - `category`: Classification of the defect (`STRUCTURAL`, `PROFILE_CONFORMANCE`).
  - `resourceType`: The FHIR resource type where the issue occurred (e.g., `Patient`, `Observation`).
  - `resourceId`: The local identifier of the affected resource (if available).
  - `path`: The FHIRPath or field path indicating the offending element.
  - `message`: Clear, human-readable description of what is wrong.
  - `ruleId` / `code`: A stable rule or error identifier for programmatic filtering.
  - `suggestion`: Practical, concrete guidance on how to fix the issue.
- **Validation Report (`LintReport` / Validation Summary)**: The aggregated result of validating an ingested dataset, providing a total count of structural and profile issues, grouped by severity and category, alongside the list of individual issues.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: **100% Core Schema Coverage**: All standard HL7 FHIR R4 structural schema violations (datatype mismatches, missing required base fields, invalid date/time formats, unrecognized elements) are detected and flagged as `STRUCTURAL` errors.
- **SC-002**: **US Core Profile Conformance**: 100% of missing US Core mandatory elements, invalid slices, and profile invariants on the target clinical resources (`Patient`, `Encounter`, `Condition`, `Observation`, `MedicationRequest`, `DiagnosticReport`) are detected and flagged as `PROFILE_CONFORMANCE` errors when `US_CORE` is selected.
- **SC-003**: **Actionable Remediation**: 100% of reported validation issues provide both an accurate element location path and a non-empty, actionable suggestion for remediation.
- **SC-004**: **Validation Performance**: Once validation profiles are initialized and cached in memory, validating a 100-resource bundle completes in under 2 seconds.
- **SC-005**: **Zero Data Retention & Offline Operation**: Validation executes 100% offline with zero outbound network calls, writing zero temporary files or cache records to disk.
- **SC-006**: **Profile Switchability**: When validating a dataset that is valid base FHIR R4 but non-compliant with US Core, selecting `--profile BASE_R4` results in 0 errors, while selecting `--profile US_CORE` results in >0 profile conformance errors.

---

## Assumptions

- **Pre-Flight Parsing**: Resources have passed the initial pre-flight syntactic boundary check (Phase 1) and can be parsed into valid FHIR resource objects.
- **FHIR Version**: The target FHIR version is strictly FHIR R4 (4.0.1). Other FHIR versions (STU3, R4B, R5) are out of scope for this phase.
- **Target Implementation Guide**: The primary profile target is US Core (v3.1.1 / v6.1.0). Additional national or specialty Implementation Guides (e.g., mCODE, CARIN BB) are deferred to future phases.
- **Terminology Scope**: Full remote terminology server validation (e.g., verifying every SNOMED CT or LOINC concept against a remote terminology server) is out of scope; validation uses in-memory base value sets, regexes, and code system checks.
- **Single-Resource Scope for Structural Validation**: Structural and profile validation evaluates resources individually or within bundle entries; cross-resource referential integrity (e.g., checking if referenced patient exists) is explicitly separated and handled in Phase 3.
