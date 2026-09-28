# Contract: Rule Catalog Specifications (11 Rules)

**Feature Branch**: `004-rule-engine-data-quality` | **Date**: 2026-09-27  
**Specification**: [`specs/004-rule-engine-data-quality/spec.md`](../spec.md)

---

## 1. Rule Definitions

### 1.1 Category: CONSISTENCY

#### `CONS-001`: `PeriodChronologyRule`
- **Applicable Types**: `Encounter`, `Coverage`
- **Scope**: `RESOURCE`
- **Severity**: `ERROR`
- **Target Fields**: `period.start`, `period.end`
- **Trigger**: `period.end` is chronologically strictly before `period.start`.
- **Exemptions**: Open-ended intervals (missing `period.end` or `period.start`), equal timestamps (`period.start == period.end`).
- **Path**: `${resourceType}.period.end`
- **Remediation**: *"Ensure period.end occurs chronologically at or after period.start."*

#### `CONS-002`: `BirthToEventChronologyRule`
- **Applicable Types**: `MedicationRequest`, `Observation`, `Condition`
- **Scope**: `RESOURCE`
- **Severity**: `ERROR`
- **Target Fields**: `MedicationRequest.authoredOn`, `Observation.effectiveDateTime` / `effectivePeriod.start`, `Condition.onsetDateTime` / `onsetPeriod.start`
- **Trigger**: Event timestamp strictly precedes the linked `Patient.birthDate`.
- **Exemptions**: Same-calendar-day events (due to date truncation / unknown birth time), missing or unresolvable `Patient` references (handled by Phase 3).
- **Path**: `${resourceType}.${dateElementPath}`
- **Remediation**: *"Verify clinical event date; clinical events cannot precede the patient's birth date."*

#### `CONS-003`: `DeceasedStatusRule`
- **Applicable Types**: `Observation`, `Procedure`, `MedicationRequest`, `Encounter`
- **Scope**: `RESOURCE`
- **Severity**: `WARNING`
- **Target Fields**: Event timestamps (`Observation.effective[x]`, `Procedure.performed[x]`, `MedicationRequest.authoredOn`, `Encounter.period.start`)
- **Trigger**: Event timestamp strictly after the linked `Patient.deceasedDateTime`.
- **Exemptions**: Living patients, patients with `deceasedBoolean: true` but no timestamp, post-mortem pathology procedures explicitly flagged.
- **Path**: `${resourceType}.${dateElementPath}`
- **Remediation**: *"Verify clinical event date; clinical activity was recorded after the patient's declared date of death."*

#### `CONS-004`: `DiagnosticReportObservationStateRule`
- **Applicable Types**: `DiagnosticReport`
- **Scope**: `RESOURCE`
- **Severity**: `ERROR`
- **Target Fields**: `DiagnosticReport.result`
- **Trigger**: Report has `status = "final"` and references an `Observation` whose `status` is `entered-in-error` or `cancelled`.
- **Path**: `DiagnosticReport.result[${index}]`
- **Remediation**: *"Final diagnostic report references an invalid observation with status 'entered-in-error' or 'cancelled'."*

---

### 1.2 Category: DUPLICATE

#### `DUP-001`: `PatientIdentifierDuplicateRule`
- **Applicable Types**: `Patient`
- **Scope**: `DATASET`
- **Severity**: `ERROR`
- **Target Fields**: `Patient.identifier.system`, `Patient.identifier.value`
- **Trigger**: Two or more distinct `Patient` resources declare the exact same non-blank `system` and `value`.
- **Path**: `Patient.identifier`
- **Remediation**: *"Multiple Patient resources share the same identifier system and value. Reconcile or deduplicate patient identities."*

#### `DUP-002`: `PatientDemographicDuplicateRule`
- **Applicable Types**: `Patient`
- **Scope**: `DATASET`
- **Severity**: `WARNING`
- **Target Fields**: `Patient.name.family`, `Patient.name.given`, `Patient.birthDate`, `Patient.address.postalCode`
- **Trigger**: Distinct `Patient` resources match across all four fields (case-insensitive, trimmed, 5-digit postal prefix) where all four attributes are present and non-blank.
- **Exemptions**: Missing or null demographic attributes (never matches `null == null`).
- **Path**: `Patient`
- **Remediation**: *"Probable demographic duplicate detected. Multiple Patient resources share matching name, birthDate, and postal code."*

---

### 1.3 Category: TERMINOLOGY

#### `TERM-001`: `CanonicalSystemUriRule`
- **Applicable Types**: All resources containing `Coding`
- **Scope**: `RESOURCE`
- **Severity**: `WARNING`
- **Target Fields**: `Coding.system`
- **Trigger**: Coding system matches a known non-canonical, misspelled, or legacy URI:
  - `http://loinc.org/` $\to$ Suggests `http://loinc.org`
  - `http://snomed.info` or `snomed` $\to$ Suggests `http://snomed.info/sct`
  - `http://www.nlm.nih.gov/research/umls/rxnorm` $\to$ Suggests `http://hl7.org/fhir/sid/rxnorm`
  - `http://hl7.org/fhir/sid/icd-10` $\to$ Suggests `http://hl7.org/fhir/sid/icd-10-cm`
- **Path**: `${resourceType}.${path}.system`
- **Remediation**: *"Replace non-canonical system URI with canonical URI: ${canonicalUri}"*

#### `TERM-002`: `VitalSignsUcumUnitRule`
- **Applicable Types**: `Observation`
- **Scope**: `RESOURCE`
- **Severity**: `ERROR`
- **Target Fields**: `Observation.valueQuantity`
- **Trigger**: Observation is categorized as a vital sign (`category = vital-signs` or standard LOINC vital code) and its `valueQuantity`:
  - Lacks `system`, or `system` is not `http://unitsofmeasure.org`, OR
  - Lacks `code` or `unit`, or uses an unrecognized UCUM string.
- **Path**: `Observation.valueQuantity`
- **Remediation**: *"Vital sign observations must specify canonical UCUM units with system 'http://unitsofmeasure.org'."*

#### `TERM-003`: `CoreValueSetBindingRule`
- **Applicable Types**: `Patient`, `Encounter`, `Condition`
- **Scope**: `RESOURCE`
- **Severity**: `ERROR`
- **Target Fields**: `Patient.gender`, `Encounter.status`, `Condition.clinicalStatus`
- **Trigger**: Field value does not belong to the required fixed value set.
- **Path**: `${resourceType}.${field}`
- **Remediation**: *"Value '${invalidCode}' is not a permitted code in the required value set for ${field}."*

---

### 1.4 Category: COMPLETENESS

#### `COMP-001`: `MissingSubjectContextRule`
- **Applicable Types**: `Observation`, `Condition`
- **Scope**: `RESOURCE`
- **Severity**: `ERROR`
- **Target Fields**: `subject`
- **Trigger**: Resource has no `subject` reference (or reference is empty).
- **Path**: `${resourceType}.subject`
- **Remediation**: *"Clinical resource must declare a subject reference linking it to a Patient."*

#### `COMP-002`: `MissingObservationValueRule`
- **Applicable Types**: `Observation`
- **Scope**: `RESOURCE`
- **Severity**: `WARNING`
- **Target Fields**: `value[x]`, `component.value[x]`, `dataAbsentReason`
- **Trigger**: Active observation (status not in `entered-in-error`, `cancelled`) has no top-level `value[x]`, no child `component.value[x]`, and no `dataAbsentReason`.
- **Exemptions**: Compound observations with at least one `component.value[x]`, observations declaring `dataAbsentReason`.
- **Path**: `Observation`
- **Remediation**: *"Observation has neither a value nor a dataAbsentReason. Document clinical findings or explain absence reason."*
