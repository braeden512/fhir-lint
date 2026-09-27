# Tasks: Phase 2 — Structural and Profile Validation

**Feature Branch**: `002-structural-profile-validation`
**Implementation Plan**: [plan.md](plan.md)
**Feature Spec**: [spec.md](spec.md)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Configure build dependencies, parser error handling, and resource assets required for HAPI FHIR validation and US Core profiles.

- [ ] T001 Add HAPI FHIR validation dependencies (`ca.uhn.hapi.fhir:hapi-fhir-validation:6.10.0` and `ca.uhn.hapi.fhir:hapi-fhir-validation-resources-r4:6.10.0`) to `build.gradle`
- [ ] T002 [P] Package official HL7 US Core v3.1.1 package (`package.tgz`) and transitive definitions into `src/main/resources/profiles/us-core/`
- [ ] T003 Configure `FhirBundleParser.java` in `src/main/java/com/braeden/fhirlint/core/parser/FhirBundleParser.java` with lenient error handling so primitive format defects and unknown fields are preserved for structural validation rather than triggering pre-flight syntax aborts (code 2)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core validation components that MUST be complete before user story implementation can begin.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [ ] T004 Implement `ValidationSupportFactory` in `src/main/java/com/braeden/fhirlint/core/validation/ValidationSupportFactory.java` assembling `ValidationSupportChain` with base R4, snapshot generation, local US Core package, terminology, and LRU caching support
- [ ] T005 Implement `ValidationMessageNormalizer` in `src/main/java/com/braeden/fhirlint/core/validation/ValidationMessageNormalizer.java` to map HAPI `SingleValidationMessage` to `QualityIssue` with severity (`ERROR`, `WARNING`, `INFO`), category (`STRUCTURAL` vs `PROFILE_CONFORMANCE`), deterministic rule ID scheme, FHIRPath, and remediation suggestions
- [ ] T006 Implement `FhirValidationEngine` in `src/main/java/com/braeden/fhirlint/core/validation/FhirValidationEngine.java` managing `FhirValidator`, `FhirInstanceValidator`, and support chain lifecycle

**Checkpoint**: Foundation ready — user story implementation can now begin.

---

## Phase 3: User Story 1 - Base FHIR R4 Structural Conformance Validation (Priority: P1) 🎯 MVP

**Goal**: Validate incoming FHIR resources against core HL7 R4 schema (datatypes, cardinalities, primitive regexes, mandatory base elements, and unrecognized fields).

**Independent Test**: Execute validation against resources with intentional schema violations (e.g., invalid date format, missing mandatory base fields, unknown fields) and verify `STRUCTURAL` error issues are reported citing the exact field path.

### Tests for User Story 1

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T007 [P] [US1] Unit test for base R4 structural validation in `src/test/java/com/braeden/fhirlint/core/validation/BaseR4StructuralValidationTest.java`

### Implementation for User Story 1

- [ ] T008 [US1] Implement base structural validation evaluation logic in `src/main/java/com/braeden/fhirlint/core/validation/FhirValidationEngine.java` using `DefaultProfileValidationSupport`
- [ ] T009 [US1] Integrate `FhirValidationEngine` into `src/main/java/com/braeden/fhirlint/core/FhirLinter.java` in both `evaluate()` and `lint(List<File> files)` methods to populate `QualityIssue` list across single and multi-file inputs
- [ ] T010 [US1] Verify structural issues are categorized as `STRUCTURAL` and reflected in `LintReport` inventory and score calculations in `src/main/java/com/braeden/fhirlint/core/FhirLinter.java`

**Checkpoint**: User Story 1 is fully functional and testable independently (Base R4 Structural Validation MVP).

---

## Phase 4: User Story 2 - US Core Implementation Guide Profile Validation (Priority: P2)

**Goal**: Validate resources against official US Core profile constraints (Patient, Encounter, Condition, Observation [vital signs & lab], MedicationRequest, DiagnosticReport), enforcing slices, invariants, and Must Support elements.

**Independent Test**: Provide resources conforming to base FHIR R4 but violating US Core constraints (e.g., Patient lacking name/identifier, Observation missing vital-signs slice) and verify that `PROFILE_CONFORMANCE` errors are generated.

### Tests for User Story 2

- [ ] T011 [P] [US2] Unit and integration tests for US Core profile validation in `src/test/java/com/braeden/fhirlint/core/validation/UsCoreProfileValidationTest.java`

### Implementation for User Story 2

- [ ] T012 [US2] Configure US Core StructureDefinition package loader in `src/main/java/com/braeden/fhirlint/core/validation/ValidationSupportFactory.java` using `NpmPackageValidationSupport` with local classpath package
- [ ] T013 [US2] Implement default US Core profile URL association in `src/main/java/com/braeden/fhirlint/core/validation/FhirValidationEngine.java` with category-based routing for `Observation` (`vital-signs` vs `laboratory`) and `DiagnosticReport` (`lab` vs `note`)
- [ ] T014 [US2] Configure `FhirInstanceValidator` in `src/main/java/com/braeden/fhirlint/core/validation/FhirValidationEngine.java` to enable snapshot generation, slice validation, invariant checking, and Must Support constraint evaluation

**Checkpoint**: User Stories 1 and 2 are functional independently and combined.

---

## Phase 5: User Story 3 - Configurable Validation Profile Target (Priority: P3)

**Goal**: Enable users to select target validation profile (`BASE_R4` vs `US_CORE`) via CLI `--profile` flag and fluent API `withProfile()`, defaulting to `US_CORE`.

**Independent Test**: Run the same base-valid/profile-invalid resource against `--profile BASE_R4` (passes with 0 errors) and `--profile US_CORE` (fails with profile errors). Verify invalid profile arguments exit with code `2`.

### Tests for User Story 3

- [ ] T015 [P] [US3] Unit and CLI integration tests for profile switching and invalid profile handling in `src/test/java/com/braeden/fhirlint/cli/ProfileSelectionTest.java`

### Implementation for User Story 3

- [ ] T016 [US3] Wire profile routing in `src/main/java/com/braeden/fhirlint/core/FhirLinter.java` to pass configured `ValidationProfile` to `FhirValidationEngine`
- [ ] T017 [US3] Update `ValidationProfile.fromString()` in `src/main/java/com/braeden/fhirlint/core/model/ValidationProfile.java` to throw `IllegalArgumentException` on invalid names, and update `ValidateCommand.java` in `src/main/java/com/braeden/fhirlint/cli/command/ValidateCommand.java` to reject invalid profile arguments with exit code 2

**Checkpoint**: Validation profile switching works across both CLI and Java API.

---

## Phase 6: User Story 4 - Normalized and Actionable Issue Reporting (Priority: P4)

**Goal**: Translate low-level validation engine messages into developer-friendly `QualityIssue` records with accurate FHIRPath, severity mapping, human prose, deterministic ruleId, and remediation advice.

**Independent Test**: Assert that all generated issues conform to `contracts/quality-issue.schema.json`, contain non-empty suggestions, and clean up raw compiler prefixes.

### Tests for User Story 4

- [ ] T018 [P] [US4] Contract and normalization unit tests in `src/test/java/com/braeden/fhirlint/core/validation/ValidationMessageNormalizerTest.java`

### Implementation for User Story 4

- [ ] T019 [US4] Implement compiler message cleanup, FHIRPath extraction, deterministic ruleId assignment, and remediation suggestion generator in `src/main/java/com/braeden/fhirlint/core/validation/ValidationMessageNormalizer.java`
- [ ] T020 [US4] Verify table, JSON, and SARIF output renderers in `src/main/java/com/braeden/fhirlint/cli/renderer/` correctly display normalized issues and suggestions

**Checkpoint**: Normalized, actionable issues render seamlessly across ANSI tables, JSON, and SARIF.

---

## Phase 7: User Story 5 - In-Memory Fast Execution and Zero Data Retention (Priority: P5)

**Goal**: Ensure all validation definitions and cache structures operate purely in memory with zero disk writes, zero external network calls, and sub-2s execution on 100-resource bundles.

**Independent Test**: Execute validation in offline mode, audit file system for zero created cache/temp files, and benchmark 100-resource bundle validation time.

### Tests for User Story 5

- [ ] T021 [P] [US5] Performance and zero-retention privacy test in `src/test/java/com/braeden/fhirlint/core/validation/ValidationPerformanceAndPrivacyTest.java`

### Implementation for User Story 5

- [ ] T022 [US5] Configure thread-safe LRU caching in `src/main/java/com/braeden/fhirlint/core/validation/ValidationSupportFactory.java` using `CachingValidationSupport`
- [ ] T023 [US5] Verify offline execution by ensuring `InMemoryTerminologyServerValidationSupport` handles terminology without remote lookups in `src/main/java/com/braeden/fhirlint/core/validation/ValidationSupportFactory.java`

**Checkpoint**: Sub-second in-memory performance and zero-retention posture confirmed.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: End-to-end integration, quickstart validation, documentation, and overall test suite verification.

- [ ] T024 [P] End-to-end quickstart validation against scenarios in `specs/002-structural-profile-validation/quickstart.md`
- [ ] T025 [P] Update documentation in `docs/` and `README.md` with Phase 2 capabilities and `--profile` usage
- [ ] T026 Run full project verification via `./gradlew check` and verify all tests pass

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user stories.
- **User Stories (Phases 3–7)**: All depend on Foundational phase completion.
  - Can proceed sequentially in priority order (P1 → P2 → P3 → P4 → P5) or in parallel where noted.
- **Polish (Phase 8)**: Depends on completion of User Stories 1–5.

### User Story Dependencies

- **User Story 1 (P1)**: Starts immediately after Phase 2 (Foundational). Delivers MVP.
- **User Story 2 (P2)**: Extends US1 validation engine with US Core profile support.
- **User Story 3 (P3)**: Builds on US1 and US2 to provide profile switching between `BASE_R4` and `US_CORE`.
- **User Story 4 (P4)**: Enhances issue formatting and reporting produced by US1/US2.
- **User Story 5 (P5)**: Tunes performance and ensures offline caching across US1/US2.

---

## Parallel Execution Examples

### Parallel Setup & Foundational Tasks
```bash
# Setup tasks that can run in parallel:
Task T002: Package US Core package in src/main/resources/profiles/us-core/

# Foundational tasks:
Task T005: Implement ValidationMessageNormalizer in src/main/java/com/braeden/fhirlint/core/validation/ValidationMessageNormalizer.java
```

### Parallel Tests Across Stories
```bash
# Unit and contract tests that can be written in parallel:
Task T007: [US1] BaseR4StructuralValidationTest.java
Task T011: [US2] UsCoreProfileValidationTest.java
Task T015: [US3] ProfileSelectionTest.java
Task T018: [US4] ValidationMessageNormalizerTest.java
Task T021: [US5] ValidationPerformanceAndPrivacyTest.java
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)
1. Complete Phase 1: Setup (T001, T002, T003)
2. Complete Phase 2: Foundational (T004, T005, T006)
3. Complete Phase 3: User Story 1 (T007, T008, T009, T010)
4. **STOP and VALIDATE**: Verify base structural validation independently using `./gradlew test`
5. At this point, FHIRLint has a functioning structural validation engine!

### Incremental Delivery
1. Add User Story 2 (US Core Profile Validation) $\to$ Test independently against US Core sample fixtures.
2. Add User Story 3 (Configurable Profile Target) $\to$ Test `--profile BASE_R4` vs `--profile US_CORE`.
3. Add User Story 4 (Normalized Issue Reporting) $\to$ Validate clean messages and suggestions in terminal table, JSON, and SARIF.
4. Add User Story 5 (In-Memory Performance & Offline Validation) $\to$ Verify sub-2s execution on 100-resource bundle and zero disk persistence.
5. Complete Phase 8: Polish, documentation, and `./gradlew check`.
