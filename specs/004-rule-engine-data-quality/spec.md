# Feature Specification: Phase 4 — Pluggable Rule Engine and Data-Quality Checks

**Feature Branch**: `004-rule-engine-data-quality`

**Created**: 2026-09-27

**Status**: Draft

**Input**: User description: "Create the spec for phase 4. Use the documentation in docs , specifically the ADR for docs/adr/ADR-004-phase-4-pluggable-rule-engine-and-data-quality.md , as well as the PRODUCT_SPEC.md to guide your thinking."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Chronological and Cross-Resource Consistency Validation (Priority: P1)

As a healthcare data engineer or application developer, I want FHIRLint to analyze clinical timestamps across related resources and detect chronological contradictions (such as encounter end dates occurring before start dates, procedures or medications dated prior to a patient's birth date, events recorded after a patient's death, or final diagnostic reports referencing entered-in-error observations), so that chronologically impossible or corrupt clinical records are intercepted before downstream analytics, clinical decision support, or EHR systems process them.

**Why this priority**: Chronological contradictions and cross-resource state conflicts represent severe data integrity failures. Standard schema validation does not inspect relational dates or cross-resource clinical statuses. Ingesting records where encounters end before they begin or treatments predate patient birth corrupts patient timelines, breaks longitudinal health records, and causes billing and clinical calculations to fail.

**Independent Test**: Can be tested independently by submitting datasets containing specific chronological errors (e.g. an `Encounter` with `period.end` prior to `period.start`, an `Observation` dated prior to a linked `Patient.birthDate`, or a `DiagnosticReport` referencing an `Observation` with status `entered-in-error`) and verifying that the system reports diagnostic issues with the appropriate rule IDs (`CONS-001`, `CONS-002`, `CONS-003`, `CONS-004`), exact element paths, severities, and remediation guidance.

**Acceptance Scenarios**:

1. **Given** an `Encounter` or `Coverage` resource where `period.end` occurs before `period.start`, **When** consistency evaluation is performed, **Then** the system detects the chronological inversion and reports a `CONS-001` error issue with the exact element path `period.end` and actionable remediation advice.
2. **Given** a `MedicationRequest`, `Observation`, or `Condition` linked to a `Patient` whose declared event date (`authoredOn`, `effectiveDateTime`, or `onsetDateTime`) occurs before the patient's `birthDate`, **When** consistency evaluation is performed, **Then** the system reports a `CONS-002` error issue identifying the pre-birth event timestamp, the patient's birth date, and the element location.
3. **Given** a clinical event (`Observation`, `Procedure`, `MedicationRequest`, or `Encounter`) linked to a deceased patient where the event date occurs after the patient's `deceasedDateTime`, **When** consistency evaluation is performed, **Then** the system reports a `CONS-003` warning issue identifying the post-mortem event and timestamp.
4. **Given** a `DiagnosticReport` with status `final` referencing an `Observation` whose status is `entered-in-error` or `cancelled`, **When** consistency evaluation is performed, **Then** the system reports a `CONS-004` error issue specifying the report ID and the invalid referenced observation status.
5. **Given** a dataset where all timestamps are chronologically valid and all diagnostic reports reference active observations, **When** consistency evaluation is performed, **Then** zero `CONS-` category issues are emitted.

---

### User Story 2 - Duplicate Entity and Identity Reconciliation (Priority: P2)

As an integration engineer ingesting aggregated patient data from external facilities, I want FHIRLint to identify duplicate patient records that represent the same individual within a dataset (either by matching formal identifiers such as SSN/MRN or by matching comprehensive demographic profiles), so that duplicate records can be flagged for merging or deduplication prior to master patient index (MPI) storage.

**Why this priority**: Duplicate patient records lead to fragmented medical charts, duplicate orders, and compromised patient safety. Catching duplicate records during ingestion protects downstream data repositories from identity fragmentation.

**Independent Test**: Can be tested independently by providing a dataset containing two distinct `Patient` resources that share the same identifier system and value, or match across core demographic fields (family name, given name, birth date, and postal code), and verifying that the system produces `DUP-001` (`ERROR`) or `DUP-002` (`WARNING`) findings identifying both duplicate resources.

**Acceptance Scenarios**:

1. **Given** two distinct `Patient` resources in the dataset sharing the same identifier `system` and `value` (e.g. identical SSN or MRN), **When** duplicate detection evaluation is performed, **Then** the system reports a `DUP-001` error issue identifying both conflicting patient resource IDs and the matching identifier.
2. **Given** two distinct `Patient` resources with different IDs and no shared formal identifiers, but matching case-insensitive `name.family`, `name.given`, `birthDate`, and `address.postalCode`, **When** duplicate detection evaluation is performed, **Then** the system reports a `DUP-002` warning issue indicating probable demographic duplicate records.
3. **Given** multiple distinct `Patient` resources with unique identifiers and different demographics, **When** duplicate detection evaluation is performed, **Then** zero `DUP-` category issues are reported.

---

### User Story 3 - Healthcare Terminology and Coding Standards Validation (Priority: P3)

As a health informatics developer, I want FHIRLint to inspect coded elements across resources and detect terminology defects (such as invalid or non-canonical code system URIs, missing UCUM units on vital signs, or invalid codes in core fixed value sets), so that terminology drift and coding errors are caught before clinical interoperability interfaces fail to map or interpret the concepts.

**Why this priority**: Healthcare interoperability relies fundamentally on standardized clinical vocabularies (LOINC, SNOMED CT, RxNorm, UCUM). Minor typos in system URIs (e.g., non-canonical `http://loinc.org/` with trailing slash vs canonical `http://loinc.org`) or missing units cause data to become unindexable and uncomputable in downstream clinical repositories.

**Independent Test**: Can be tested independently by submitting resources with misspelled system URIs, vital signs lacking valid UCUM units, or invalid administrative gender codes, and verifying that the system outputs `TERM-001` (`WARNING`), `TERM-002` (`ERROR`), or `TERM-003` (`ERROR`) issues with exact FHIRPath element paths.

**Acceptance Scenarios**:

1. **Given** a resource containing a `Coding` with an invalid, non-canonical, or misspelled system URI (such as `http://loinc.org/` containing an extraneous trailing slash, or an informal SNOMED URI), **When** terminology evaluation is performed, **Then** the system reports a `TERM-001` warning issue providing the non-canonical URI and suggesting the canonical URI.
2. **Given** an `Observation` categorized as a vital sign (`vital-signs`) with a `valueQuantity` lacking a `unit`, missing a `code`, or having a system other than `http://unitsofmeasure.org`, **When** terminology evaluation is performed, **Then** the system reports a `TERM-002` error issue highlighting the non-conforming UCUM unit representation.
3. **Given** a resource using a code outside the required fixed value set for `Patient.gender`, `Encounter.status`, or `Condition.clinicalStatus`, **When** terminology evaluation is performed, **Then** the system reports a `TERM-003` error issue listing the invalid code and acceptable permitted values.
4. **Given** resources containing valid canonical URIs, properly specified UCUM vital sign measurements, and valid value set codes, **When** terminology evaluation is performed, **Then** zero `TERM-` category issues are reported.

---

### User Story 4 - Clinical Data Completeness and Context Validation (Priority: P4)

As a data quality analyst, I want FHIRLint to verify that critical clinical resources contain essential context and clinical findings (such as observations and conditions having explicit subject linkages, and observations declaring either a measurable value or a documented clinical reason for absent data), so that vacuous or clinically unusable records are flagged.

**Why this priority**: An observation without a subject is un-attributable, and an observation without either a measurement or an explicit reason for absence represents a null finding that downstream clinical decision algorithms cannot safely interpret.

**Independent Test**: Can be tested independently by submitting an `Observation` or `Condition` without a `subject` reference, or an `Observation` with empty values and no `dataAbsentReason`, and verifying that the system reports `COMP-001` (`ERROR`) or `COMP-002` (`WARNING`) issues with exact FHIRPath element locations.

**Acceptance Scenarios**:

1. **Given** an `Observation` or `Condition` resource lacking a `subject` reference, **When** completeness evaluation is performed, **Then** the system reports a `COMP-001` error issue identifying the missing mandatory subject context.
2. **Given** an `Observation` that contains no `value[x]`, no child `component.value[x]`, and no `dataAbsentReason`, **When** completeness evaluation is performed, **Then** the system reports a `COMP-002` warning issue indicating the observation lacks both result data and an absence explanation.
3. **Given** an `Observation` that lacks a top-level `value[x]` but provides valid `component` measurements (e.g. a blood pressure panel containing systolic and diastolic components) or provides an explicit `dataAbsentReason` (e.g. `unknown` or `not-performed`), **When** completeness evaluation is performed, **Then** no `COMP-002` issue is reported.
4. **Given** clinical resources with complete subject links and either recorded values or explicit absent reasons, **When** completeness evaluation is performed, **Then** zero `COMP-` category issues are reported.

---

### User Story 5 - Pluggable and Targeted Rule Execution (Priority: P5)

As an engineer or integration pipeline developer, I want the data quality rule engine to evaluate rules based on declared applicability (such as specific resource types or dataset-wide scopes) and return uniform, structured quality issues across all categories, so that new quality rules can be introduced independently without altering pipeline execution logic.

**Why this priority**: A monolithic validation loop that intertwines rule logic with pipeline execution quickly becomes brittle, unmaintainable, and difficult to extend. A pluggable architecture ensures every quality rule remains an isolated, declarative, and independently verifiable component.

**Independent Test**: Can be tested independently by registering rules with specific resource type filters, evaluating a multi-resource bundle, and verifying that rules only execute against relevant resource types while dataset-level deduplication rules evaluate across the aggregate collection.

**Acceptance Scenarios**:

1. **Given** a registered rule that targets only `Encounter` resources, **When** evaluation executes across a mixed dataset of Patients, Encounters, and Observations, **Then** the rule is only evaluated against `Encounter` resources.
2. **Given** a dataset-scoped rule (such as patient deduplication), **When** evaluation executes, **Then** the rule receives the aggregate dataset and relationship graph context to evaluate cross-resource patterns.
3. **Given** rule evaluations across multiple rules and categories, **When** issues are generated, **Then** every issue provides a uniform structure including rule ID, severity, category, resource type, resource ID, element path, explanatory message, and actionable remediation advice.

---

### Edge Cases

- **Date and Timestamp Precision Discrepancies**: Timestamps with different precisions (e.g. comparing a date-only `birthDate` `1980-05-15` with a full ISO-8601 timestamp `1980-05-15T08:30:00Z`) must be evaluated with timezone-aware calendar comparison rather than raw string comparison, avoiding false-positive inversions when an event occurred on the same day as birth.
- **Open-Ended / Ongoing Intervals**: Resources with open-ended periods (e.g. an active `Encounter` or `Condition` with `period.start` or `onsetDateTime` but no `period.end` or `abatementDateTime`) must be treated as valid ongoing events without triggering chronological inversion errors.
- **Compound and Panel Observations**: Multi-component observations (such as blood pressure panels with separate systolic and diastolic components) that lack a root `valueQuantity` but contain valid values inside `component` elements must not be falsely flagged as missing values.
- **Case-Insensitive Demographic Matching**: Demographic deduplication must normalize whitespace, case, and punctuation when comparing patient names and postal codes (e.g., "John Doe" vs "JOHN  DOE", "90210" vs "90210-1234" prefix).
- **Multiple Identifiers Per Patient**: When patients have multiple identifiers (e.g., driver's license, MRN, national ID), collisions on any shared system and value pair between distinct patient records must trigger duplicate identification without crashing or duplicating reports.
- **Missing or Partial Reference Resolution**: If a clinical resource references a patient ID that does not exist in the dataset (a broken reference handled by Phase 3), consistency rules that depend on patient demographics (such as birth date or deceased status) must skip execution gracefully for that unresolvable link without throwing null-pointer exceptions.
- **Deceased Status As Boolean**: A patient may declare `deceasedBoolean: true` without specifying a `deceasedDateTime`. In this case, post-mortem chronology checks cannot compute an exact cutoff timestamp and must skip chronological comparison without error.
- **Empty or Whitespace-Only Code Systems**: Codings with blank or whitespace-only `system` or `code` attributes must be flagged appropriately under terminology without causing parser crashes.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST validate date intervals on resources containing period elements (including `Encounter.period` and `Coverage.period`), reporting a `CONS-001` error issue whenever `period.end` occurs chronologically earlier than `period.start`.
- **FR-002**: System MUST cross-reference patient birth dates with associated clinical events (including `MedicationRequest.authoredOn`, `Observation.effective[x]`, and `Condition.onset[x]`), reporting a `CONS-002` error issue whenever a clinical event is timestamped prior to the linked patient's `birthDate`.
- **FR-003**: System MUST cross-reference patient death records with associated clinical events, reporting a `CONS-003` warning issue whenever a clinical event (`Observation`, `Procedure`, `MedicationRequest`, or `Encounter`) is timestamped after the linked patient's `deceasedDateTime`.
- **FR-004**: System MUST verify that `DiagnosticReport` resources with status `final` do not reference `Observation` resources whose status is `entered-in-error` or `cancelled`, reporting a `CONS-004` error issue upon detecting invalid observation states in final reports.
- **FR-005**: System MUST detect distinct `Patient` resources within the dataset sharing an identical identifier `system` and identifier `value`, reporting a `DUP-001` error issue identifying all colliding patient records.
- **FR-006**: System MUST compare demographic attributes across distinct `Patient` resources, reporting a `DUP-002` warning issue whenever distinct patients share matching family name, given name, birth date, and postal code (where all four demographic attributes are present and non-blank).
- **FR-007**: System MUST validate coding system URIs against canonical healthcare terminology URIs (including LOINC `http://loinc.org`, SNOMED CT `http://snomed.info/sct`, RxNorm `http://hl7.org/fhir/sid/rxnorm`, and ICD-10-CM `http://hl7.org/fhir/sid/icd-10-cm`), reporting a `TERM-001` warning issue when a coding system URI is non-canonical, misspelled, or uses an outdated scheme.
- **FR-008**: System MUST verify that `Observation` resources categorized as vital signs (`vital-signs`) with numeric values declare a valid UCUM unit code and specify the canonical UCUM system URI (`http://unitsofmeasure.org`), reporting a `TERM-002` error issue if the unit or system URI is missing, invalid, or non-canonical.
- **FR-009**: System MUST validate core coded elements against required fixed value sets (specifically `Patient.gender` adhering to AdministrativeGender, `Encounter.status` adhering to EncounterStatus, and `Condition.clinicalStatus` adhering to ConditionClinicalStatusCodes), reporting a `TERM-003` error issue when an invalid code is supplied.
- **FR-010**: System MUST check clinical resources (specifically `Observation` and `Condition`) for the presence of a subject reference linking them to a `Patient`, reporting a `COMP-001` error issue when the subject reference is missing.
- **FR-011**: System MUST inspect active clinical `Observation` resources (exempting status `entered-in-error` and `cancelled`) to verify that either a root `value[x]`, at least one child `component.value[x]`, or an explicit `dataAbsentReason` is present, reporting a `COMP-002` warning issue when an observation lacks both data and an absence explanation.
- **FR-012**: System MUST organize quality rules into declarative, modular definitions each declaring a unique rule identifier, human-readable name, quality category (`CONSISTENCY`, `DUPLICATE`, `TERMINOLOGY`, `COMPLETENESS`), default severity (`ERROR`, `WARNING`, `INFO`), and targeted resource types.
- **FR-013**: System MUST execute rules with access to evaluation context that provides the target resource, the complete dataset, the pre-computed resource relationship graph index (from Phase 3), and standard terminology definitions.
- **FR-014**: System MUST assign every detected finding to an explicit quality category and produce an actionable diagnostic issue containing the rule ID, severity, category, affected resource type and ID, FHIRPath element path, descriptive explanation, and concrete remediation advice.
- **FR-015**: System MUST execute all data quality evaluations entirely in memory without requiring external databases, background services, or outbound network calls.
- **FR-016**: System MUST skip chronology rules gracefully when referenced patient demographic dates are unresolvable or missing, without producing unhandled exceptions.
- **FR-017**: System MUST provide the collected quality issues to downstream scoring and reporting modules.

### Key Entities

- **Quality Rule**: A declarative validation component defining a specific quality check, containing an identifier, name, category, default severity, applicable resource types, and evaluation logic.
- **Quality Issue**: A structured finding produced by a rule evaluation, capturing rule identifier, severity, category, target resource type and ID, FHIRPath location, problem description, and actionable remediation advice.
- **Rule Context**: The contextual data supplied to rules during evaluation, exposing the current resource, the dataset collection, the resource relationship graph index, and embedded terminology references.
- **Rule Category**: The functional domain of a quality rule, specifically `CONSISTENCY` (cross-resource and chronological coherence), `DUPLICATE` (identity and entity duplication), `TERMINOLOGY` (vocabulary and code system validity), and `COMPLETENESS` (missing context and clinical completeness).
- **Rule Registry**: The collection and dispatcher of registered quality rules responsible for executing rules against applicable resources in a dataset.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of chronological inversions (`period.end < period.start` and clinical events predating patient birth) in test datasets are detected and reported under rules `CONS-001` and `CONS-002` with `ERROR` severity and exact FHIRPath element paths.
- **SC-002**: 100% of clinical events occurring after a patient's `deceasedDateTime` are detected and reported under rule `CONS-003` with `WARNING` severity.
- **SC-003**: 100% of final diagnostic reports referencing entered-in-error or cancelled observations are detected and reported under rule `CONS-004` with `ERROR` severity.
- **SC-004**: 100% of duplicate patient records sharing identical identifier values (system + value) are flagged under rule `DUP-001` with `ERROR` severity, identifying all colliding resource IDs.
- **SC-005**: 100% of duplicate patient records matching core demographic attributes (name, birth date, postal code) are flagged under rule `DUP-002` with `WARNING` severity.
- **SC-006**: 100% of invalid or non-canonical terminology system URIs for LOINC, SNOMED CT, RxNorm, and ICD-10-CM in test datasets are flagged under rule `TERM-001` with `WARNING` severity.
- **SC-007**: 100% of vital sign observations lacking canonical UCUM units or system URI are flagged under rule `TERM-002` with `ERROR` severity.
- **SC-008**: 100% of invalid codes in core fixed value sets (`AdministrativeGender`, `EncounterStatus`, `ConditionClinicalStatusCodes`) are detected and reported under rule `TERM-003` with `ERROR` severity.
- **SC-009**: 100% of clinical resources (`Observation`, `Condition`) missing a required `subject` reference are detected and reported under rule `COMP-001` with `ERROR` severity.
- **SC-010**: 100% of active clinical observations lacking values, component values, and data absent reasons are flagged under rule `COMP-002` with `WARNING` severity.
- **SC-011**: 0% false-positive consistency or completeness errors emitted when validating clean, standard conformant benchmark bundles.
- **SC-012**: Evaluation of the complete 11-rule catalog across a dataset of 5,000 resources completes in under 1.5 seconds on standard developer hardware.
- **SC-013**: 100% of generated data quality issues provide an actionable remediation suggestion and a valid FHIRPath element location.

## Assumptions

- Input datasets have been syntactically ingested and parsed as FHIR R4 resources through the ingestion pipeline (Phase 1).
- The dataset's resource relationship graph and reference index (Phase 3) is pre-computed and available in memory for cross-resource queries and relationship lookups.
- Terminology validation in this phase relies on offline, embedded tables and canonical URI sets (LOINC, SNOMED CT, RxNorm, ICD-10-CM, core FHIR value sets, UCUM) without making external network calls to remote terminology servers, consistent with Constitution Principles II and V.
- Vital signs are identified by standard categorization (category `vital-signs`) or standard LOINC vital codes.
- Compound or panel observations containing measurements in child `component` elements satisfy data presence requirements even if the top-level `value[x]` is absent.
- Open-ended periods (e.g. an active encounter with `period.start` but no `period.end`) are valid clinical states and are not flagged as chronological inversions.
- Data quality rules run entirely in-memory with zero data retention and zero persistent state.
