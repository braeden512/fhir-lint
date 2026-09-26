# ADR-004: Phase 4 — Pluggable Rule Engine and Data-Quality Checks

## Status
Accepted

## Context & Problem Statement
Beyond schema and referential checks, FHIRLint's core differentiator is detecting cross-resource inconsistencies, chronological contradictions, duplicate records, missing clinical context, and terminology errors.

If these checks are written as ad-hoc procedural `if-else` blocks in service classes, the codebase will quickly become unmaintainable and impossible to test or extend. We need a modular, declarative, and pluggable rule engine architecture.

## Decision Drivers
1. **Modularity & Pluggability**: Adding a new quality rule must only require creating a new class implementing a standard interface, without modifying the core pipeline.
2. **Context-Rich Execution**: Rules must have access to individual resources, the entire Bundle, and the pre-computed `ResourceGraphIndex`.
3. **Targeted Execution**: Rules should only run against applicable resource types.
4. **Testability**: Each rule must be independently unit-testable with focused synthetic test fixtures.

## Considered Options
1. **Heavyweight Rule Engine (e.g., Drools / Easy Rules)**: Unnecessary abstraction layer, steep learning curve, hard to debug with HAPI FHIR Java objects.
2. **Spring Component-based Pluggable Interface (`QualityRule`)**: Standard Spring design pattern where rules implement an interface and are auto-detected by Spring's dependency injection (`List<QualityRule>`).
3. **Pure FHIRPath Rule Scripting**: Defining all rules in external `.fhirpath` files. (Difficult for cross-resource correlation, duplicate matching, and custom scoring).

## Decision Outcome
Adopt **Option 2: Spring Component-based Pluggable Interface (`QualityRule`)**.

### Rule Engine Design

```java
public interface QualityRule {
    String getRuleId();
    String getName();
    RuleCategory getCategory();
    IssueSeverity getDefaultSeverity();
    Set<String> getApplicableResourceTypes();
    
    List<QualityIssue> evaluate(RuleContext context);
}
```

The `RuleContext` exposes:
- Current resource (for resource-scoped rules)
- Full parsed `Bundle` (for bundle-scoped rules)
- `ResourceGraphIndex` (for relationship lookups)
- `TerminologyService` (for code and system validations)

### Initial Rule Catalog for MVP

1. **Chronological & Consistency Rules (`RuleCategory.CONSISTENCY`)**:
   - `CONS-001 (PeriodChronologyRule)`: Ensures `period.end >= period.start` on `Encounter`, `Coverage`, etc.
   - `CONS-002 (BirthToEventChronologyRule)`: Ensures events (`MedicationRequest.authoredOn`, `Observation.effectiveDateTime`, `Condition.onsetDateTime`) do not occur prior to the patient's `birthDate`.
   - `CONS-003 (DeceasedStatusRule)`: Flags clinical events dated after a patient's `deceasedDateTime`.
   - `CONS-004 (DiagnosticReportObservationStateRule)`: Flags final `DiagnosticReport` resources that reference `entered-in-error` or `cancelled` observations.

2. **Deduplication Rules (`RuleCategory.DUPLICATE`)**:
   - `DUP-001 (PatientIdentifierDuplicateRule)`: Identifies distinct `Patient` resources in the same dataset sharing the same identifier system and value (e.g. SSN or MRN).
   - `DUP-002 (PatientDemographicDuplicateRule)`: Identifies distinct `Patient` resources with matching family name, given name, birthDate, and postal code.

3. **Terminology Rules (`RuleCategory.TERMINOLOGY`)**:
   - `TERM-001 (CanonicalSystemUriRule)`: Flags invalid or misspelled coding system URIs.
   - `TERM-002 (VitalSignsUcumUnitRule)`: Ensures vital signs have valid UCUM units and `system: "http://unitsofmeasure.org"`.
   - `TERM-003 (CoreValueSetBindingRule)`: Verifies adherence to required fixed value sets (AdministrativeGender, EncounterStatus, ConditionClinicalStatus).

4. **Completeness Rules (`RuleCategory.COMPLETENESS`)**:
   - `COMP-001 (MissingSubjectContextRule)`: Flags clinical resources missing mandatory or recommended patient link.
   - `COMP-002 (MissingObservationValueRule)`: Flags observations with neither a `value[x]` nor a `dataAbsentReason`.

## Consequences
### Positive
- Strict separation of concerns: each rule is an isolated, testable class.
- Extensible: new rules are registered automatically via Spring `@Component`.
- Powerful: direct access to graph relationships enables deep multi-resource checks.

### Negative / Trade-offs
- Developers must follow consistent naming conventions and rule ID taxonomies.
