# Tasks: Phase 4 — Pluggable Rule Engine and Data-Quality Checks

**Feature Branch**: `004-rule-engine-data-quality` | **Date**: 2026-09-27  
**Specification**: [`specs/004-rule-engine-data-quality/spec.md`](spec.md)  
**Implementation Plan**: [`specs/004-rule-engine-data-quality/plan.md`](plan.md)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project layout and rule package initialization

- [X] T001 Create package directories for rule categories in `src/main/java/org/fhirlint/core/rules/` (`consistency/`, `duplicate/`, `terminology/`, `completeness/`) and test directories in `src/test/java/org/fhirlint/core/rules/`
- [X] T002 [P] Verify build environment and existing test suite passes via `./gradlew test`

---

## Phase 2: Foundational (Core Rule Engine Abstractions)

**Purpose**: Core rule engine contracts, context, terminology provider, and registry that MUST be complete before rule implementations begin.

**⚠️ CRITICAL**: Blocking prerequisites for all user stories.

- [X] T003 [P] Create `RuleScope` enum (`RESOURCE`, `DATASET`) in `src/main/java/org/fhirlint/core/rules/RuleScope.java`
- [X] T004 [P] Create `TerminologyService` interface in `src/main/java/org/fhirlint/core/rules/terminology/TerminologyService.java`
- [X] T005 Create `DefaultTerminologyService` implementing canonical URI sets (LOINC `http://loinc.org`, SNOMED CT `http://snomed.info/sct`, RxNorm `http://hl7.org/fhir/sid/rxnorm`, ICD-10-CM `http://hl7.org/fhir/sid/icd-10-cm`), UCUM unit validation (`http://unitsofmeasure.org`), and core fixed value sets (`AdministrativeGender`, `EncounterStatus`, `ConditionClinicalStatusCodes`) in `src/main/java/org/fhirlint/core/rules/terminology/DefaultTerminologyService.java`
- [X] T006 [P] Unit test for `DefaultTerminologyService` in `src/test/java/org/fhirlint/core/rules/terminology/DefaultTerminologyServiceTest.java`
- [X] T007 [P] Create `RuleContext` evaluation context exposing `resource`, immutable `dataset`, `ResourceGraphIndex`, and `TerminologyService` in `src/main/java/org/fhirlint/core/rules/RuleContext.java`
- [X] T008 [P] Create `QualityRule` interface defining `getRuleId()`, `getName()`, `getCategory()`, `getDefaultSeverity()`, `getScope()`, `getApplicableResourceTypes()`, and `evaluate(RuleContext)` in `src/main/java/org/fhirlint/core/rules/QualityRule.java`
- [X] T009 [P] Create `QualityRuleEngine` interface in `src/main/java/org/fhirlint/core/rules/QualityRuleEngine.java`
- [X] T010 Create `RuleRegistry` for registering and dispatching rules by scope (`RESOURCE` vs `DATASET`) and resource type in `src/main/java/org/fhirlint/core/rules/RuleRegistry.java`
- [X] T011 Create `DefaultQualityRuleEngine` orchestrating dataset-scoped rules once across the dataset and resource-scoped rules against matching resources in `src/main/java/org/fhirlint/core/rules/DefaultQualityRuleEngine.java`
- [X] T012 [P] Unit tests for `RuleRegistry` and `DefaultQualityRuleEngine` in `src/test/java/org/fhirlint/core/rules/RuleRegistryTest.java` and `src/test/java/org/fhirlint/core/rules/DefaultQualityRuleEngineTest.java`

**Checkpoint**: Foundation ready — rule catalog implementation and story execution can now begin.

---

## Phase 3: User Story 1 - Chronological and Cross-Resource Consistency Validation (Priority: P1) 🎯 MVP

**Goal**: Detect chronological inversions on periods (`CONS-001`), events predating patient birth (`CONS-002`), post-mortem clinical activity (`CONS-003`), and final reports referencing cancelled or entered-in-error observations (`CONS-004`).

**Independent Test**: Submit synthetic datasets with known chronological errors (inverted encounter period, medication dated before patient birth, event dated after patient death, final diagnostic report referencing entered-in-error observation) and verify that `CONS-001` through `CONS-004` issues are reported with exact element paths and severities.

### Implementation for User Story 1

- [X] T013 [P] [US1] Create `ChronologyHelper` utility for temporal precision normalization and safe calendar date/timestamp comparisons in `src/main/java/org/fhirlint/core/rules/consistency/ChronologyHelper.java`
- [X] T014 [P] [US1] Unit test `ChronologyHelperTest` in `src/test/java/org/fhirlint/core/rules/consistency/ChronologyHelperTest.java`
- [X] T015 [P] [US1] Unit test `PeriodChronologyRuleTest` in `src/test/java/org/fhirlint/core/rules/consistency/PeriodChronologyRuleTest.java`
- [X] T016 [P] [US1] Implement `PeriodChronologyRule` (`CONS-001`, `ERROR`, `Encounter`, `Coverage`) in `src/main/java/org/fhirlint/core/rules/consistency/PeriodChronologyRule.java`
- [X] T017 [P] [US1] Unit test `BirthToEventChronologyRuleTest` in `src/test/java/org/fhirlint/core/rules/consistency/BirthToEventChronologyRuleTest.java`
- [X] T018 [P] [US1] Implement `BirthToEventChronologyRule` (`CONS-002`, `ERROR`, `MedicationRequest`, `Observation`, `Condition`) resolving patient via `ResourceGraphIndex` in `src/main/java/org/fhirlint/core/rules/consistency/BirthToEventChronologyRule.java`
- [X] T019 [P] [US1] Unit test `DeceasedStatusRuleTest` in `src/test/java/org/fhirlint/core/rules/consistency/DeceasedStatusRuleTest.java`
- [X] T020 [P] [US1] Implement `DeceasedStatusRule` (`CONS-003`, `WARNING`, `Observation`, `Procedure`, `MedicationRequest`, `Encounter`) in `src/main/java/org/fhirlint/core/rules/consistency/DeceasedStatusRule.java`
- [X] T021 [P] [US1] Unit test `DiagnosticReportObservationStateRuleTest` in `src/test/java/org/fhirlint/core/rules/consistency/DiagnosticReportObservationStateRuleTest.java`
- [X] T022 [P] [US1] Implement `DiagnosticReportObservationStateRule` (`CONS-004`, `ERROR`, `DiagnosticReport`) resolving result observations via `ResourceGraphIndex` in `src/main/java/org/fhirlint/core/rules/consistency/DiagnosticReportObservationStateRule.java`

**Checkpoint**: User Story 1 complete — all 4 consistency rules are operational and independently testable.

---

## Phase 4: User Story 2 - Duplicate Entity and Identity Reconciliation (Priority: P2)

**Goal**: Identify duplicate patient records in a dataset by identical identifier system and value (`DUP-001`) or matching demographic profile (`DUP-002`).

**Independent Test**: Submit a dataset with two distinct `Patient` resources sharing the same identifier system/value and two patients matching across name, birthDate, and postalCode, verifying `DUP-001` (`ERROR`) and `DUP-002` (`WARNING`) are emitted with colliding IDs.

### Implementation for User Story 2

- [X] T023 [P] [US2] Unit test `PatientIdentifierDuplicateRuleTest` in `src/test/java/org/fhirlint/core/rules/duplicate/PatientIdentifierDuplicateRuleTest.java`
- [X] T024 [P] [US2] Implement `PatientIdentifierDuplicateRule` (`DUP-001`, `DATASET` scope, `ERROR`, `Patient`) using $O(N)$ identifier mapping in `src/main/java/org/fhirlint/core/rules/duplicate/PatientIdentifierDuplicateRule.java`
- [X] T025 [P] [US2] Unit test `PatientDemographicDuplicateRuleTest` in `src/test/java/org/fhirlint/core/rules/duplicate/PatientDemographicDuplicateRuleTest.java`
- [X] T026 [P] [US2] Implement `PatientDemographicDuplicateRule` (`DUP-002`, `DATASET` scope, `WARNING`, `Patient`) requiring all four fields (`family`, `given`, `birthDate`, `postalCode`) present and non-blank in `src/main/java/org/fhirlint/core/rules/duplicate/PatientDemographicDuplicateRule.java`

**Checkpoint**: User Story 2 complete — duplicate detection operates independently across patient populations.

---

## Phase 5: User Story 3 - Healthcare Terminology and Coding Standards Validation (Priority: P3)

**Goal**: Detect non-canonical/misspelled system URIs (`TERM-001`), missing UCUM units on vital signs (`TERM-002`), and invalid fixed value set codes (`TERM-003`).

**Independent Test**: Submit resources with misspelled LOINC URIs (`http://loinc.org/`), vital signs lacking UCUM unit/system, and an invalid administrative gender code, verifying `TERM-001`, `TERM-002`, and `TERM-003` are emitted with exact paths and remediation suggestions.

### Implementation for User Story 3

- [X] T027 [P] [US3] Unit test `CanonicalSystemUriRuleTest` in `src/test/java/org/fhirlint/core/rules/terminology/CanonicalSystemUriRuleTest.java`
- [X] T028 [P] [US3] Implement `CanonicalSystemUriRule` (`TERM-001`, `WARNING`, any resource with `Coding`) in `src/main/java/org/fhirlint/core/rules/terminology/CanonicalSystemUriRule.java`
- [X] T029 [P] [US3] Unit test `VitalSignsUcumUnitRuleTest` in `src/test/java/org/fhirlint/core/rules/terminology/VitalSignsUcumUnitRuleTest.java`
- [X] T030 [P] [US3] Implement `VitalSignsUcumUnitRule` (`TERM-002`, `ERROR`, `Observation`) checking vital signs category, UCUM code, and `http://unitsofmeasure.org` in `src/main/java/org/fhirlint/core/rules/terminology/VitalSignsUcumUnitRule.java`
- [X] T031 [P] [US3] Unit test `CoreValueSetBindingRuleTest` in `src/test/java/org/fhirlint/core/rules/terminology/CoreValueSetBindingRuleTest.java`
- [X] T032 [P] [US3] Implement `CoreValueSetBindingRule` (`TERM-003`, `ERROR`, `Patient.gender`, `Encounter.status`, `Condition.clinicalStatus`) in `src/main/java/org/fhirlint/core/rules/terminology/CoreValueSetBindingRule.java`

**Checkpoint**: User Story 3 complete — offline terminology rules are operational and independently testable.

---

## Phase 6: User Story 4 - Clinical Data Completeness and Context Validation (Priority: P4)

**Goal**: Detect clinical resources lacking patient subject context (`COMP-001`) and observations lacking values or data absent reasons (`COMP-002`).

**Independent Test**: Submit an `Observation` with missing `subject` and an active `Observation` with no `value[x]` and no `dataAbsentReason`, verifying `COMP-001` (`ERROR`) and `COMP-002` (`WARNING`) are reported.

### Implementation for User Story 4

- [X] T033 [P] [US4] Unit test `MissingSubjectContextRuleTest` in `src/test/java/org/fhirlint/core/rules/completeness/MissingSubjectContextRuleTest.java`
- [X] T034 [P] [US4] Implement `MissingSubjectContextRule` (`COMP-001`, `ERROR`, `Observation`, `Condition`) in `src/main/java/org/fhirlint/core/rules/completeness/MissingSubjectContextRule.java`
- [X] T035 [P] [US4] Unit test `MissingObservationValueRuleTest` in `src/test/java/org/fhirlint/core/rules/completeness/MissingObservationValueRuleTest.java`
- [X] T036 [P] [US4] Implement `MissingObservationValueRule` (`COMP-002`, `WARNING`, `Observation`) exempting `entered-in-error` / `cancelled` and recognizing `component.value[x]` in `src/main/java/org/fhirlint/core/rules/completeness/MissingObservationValueRule.java`

**Checkpoint**: User Story 4 complete — data completeness checks are operational and independently testable.

---

## Phase 7: User Story 5 - Pluggable Pipeline Integration & Shared Graph Index (Priority: P5)

**Goal**: Wire the default 11-rule catalog into `RuleRegistry`, share a single-pass `ResourceGraphIndex` between Phase 3 and Phase 4 in `FhirLinter`, and ensure all issues flow to category scoring and CLI renderers.

**Independent Test**: Run `FhirLinter.lint(...)` on a comprehensive messy bundle and verify that issues from all four categories (`CONSISTENCY`, `DUPLICATE`, `TERMINOLOGY`, `COMPLETENESS`) appear in the `LintReport` and contribute deterministically to category scores.

### Implementation for User Story 5

- [X] T037 [US5] Register all 11 default catalog rules in `RuleRegistry.createDefault()` in `src/main/java/org/fhirlint/core/rules/RuleRegistry.java`
- [X] T038 [US5] Expose `buildIndex(List<? extends IBaseResource>)` and `analyze(ResourceGraphIndex)` in `ReferentialIntegrityEngine.java` and `DefaultReferentialIntegrityEngine.java` in `src/main/java/org/fhirlint/core/graph/ReferentialIntegrityEngine.java` and `src/main/java/org/fhirlint/core/graph/DefaultReferentialIntegrityEngine.java`
- [X] T039 [US5] Update `FhirLinter` to compute `ResourceGraphIndex` once, pass it to `referentialIntegrityEngine`, and pass it to `qualityRuleEngine.evaluate(...)` in `src/main/java/org/fhirlint/core/FhirLinter.java`
- [X] T040 [P] [US5] Integration test for multi-category rule evaluation and score calculation in `src/test/java/org/fhirlint/core/FhirLinterPhase4Test.java`
- [X] T041 [P] [US5] Regression test for CLI execution, category scoring, and exit codes in `src/test/java/org/fhirlint/cli/FhirLintPhase4CliTest.java`

**Checkpoint**: All user stories integrated into the central engine and CLI pipeline.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Verification of performance benchmarks, clean dataset passing gates, and documentation.

- [X] T042 [P] Run and verify end-to-end scenarios from `specs/004-rule-engine-data-quality/quickstart.md`
- [X] T043 [P] Validate performance benchmark (<1.5s on 5,000 resources) and 0% false positives on clean Synthea bundle in `src/test/java/org/fhirlint/core/rules/QualityRulePerformanceBenchmarkTest.java`
- [X] T044 Run full project quality gate (`./gradlew check`) and ensure all tests pass with zero warnings

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user story implementations.
- **User Story 1 (Phase 3, P1)**: Depends on Foundational completion.
- **User Story 2 (Phase 4, P2)**: Depends on Foundational completion.
- **User Story 3 (Phase 5, P3)**: Depends on Foundational completion.
- **User Story 4 (Phase 6, P4)**: Depends on Foundational completion.
- **User Story 5 (Phase 7, P5)**: Depends on Phases 3, 4, 5, and 6 to wire all 11 catalog rules into `RuleRegistry` and `FhirLinter`.
- **Polish (Phase 8)**: Depends on Phase 7 completion.

### Parallel Opportunities

- Within Phase 2: `RuleScope`, `TerminologyService`, `RuleContext`, `QualityRule`, and `QualityRuleEngine` interfaces can be created in parallel (`[P]`).
- Across User Stories: Phases 3, 4, 5, and 6 can be developed concurrently once Phase 2 is complete.
- Within each story: Unit test fixtures and rule implementations marked `[P]` can be developed independently.

---

## Implementation Strategy

### MVP Scope (User Story 1 Only)
1. Complete Phase 1 (Setup) and Phase 2 (Foundational).
2. Complete Phase 3 (User Story 1: Consistency & Chronology Rules).
3. Validate `CONS-001` through `CONS-004` independently against synthetic bundles.

### Incremental Delivery
1. Foundation: Engine abstractions + registry ready.
2. Increment 1: Consistency rules (`CONS-001..004`) operational.
3. Increment 2: Duplicate entity reconciliation (`DUP-001..002`) operational.
4. Increment 3: Terminology validation (`TERM-001..003`) operational.
5. Increment 4: Clinical data completeness (`COMP-001..002`) operational.
6. Increment 5: Full pipeline integration with shared graph index in `FhirLinter`.
7. Polish: Quickstart verification, clean bundle passing gate, and performance validation.
