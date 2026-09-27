# Quickstart Guide: Phase 3 — Referential Integrity and Resource Graph

This guide outlines runnable scenarios to validate referential integrity analysis and resource graph indexing in FHIRLint.

---

## Prerequisites

- **Java Development Kit**: JDK 21+ (`java -version` returns 21+)
- **Build Tool**: Gradle wrapper (`./gradlew`)
- **Built Artifact**: Build the CLI distribution or test suite via:
  ```bash
  ./gradlew testClasses
  ```

---

## Verification Scenarios

### Scenario 1: Broken Local Reference (`REF-001`)

**Goal**: Verify that a relative reference pointing to a missing resource generates a `REF-001` error with exit code `1`.

**Test Fixture**: `sample-data/referential/broken-reference.json` (An `Observation` pointing to `Patient/pat-missing` where no such Patient exists).

**Execution**:
```bash
./gradlew run --args="validate sample-data/referential/broken-reference.json"
```

**Expected Outcome**:
- Output displays `REF-001` with severity `[ERROR]` at `Observation.subject.reference`.
- Message: `Referenced target 'Patient/pat-missing' does not exist in dataset.`
- Overall score degraded due to 25% referential integrity weight.
- Exit code: `1`.

---

### Scenario 2: Target Resource Type Mismatch (`REF-002`)

**Goal**: Verify that pointing to an existing resource of the wrong type generates a `REF-002` error.

**Test Fixture**: `sample-data/referential/type-mismatch.json` (An `Observation` whose `subject.reference` points to an existing `Condition/cond-01`).

**Execution**:
```bash
./gradlew run --args="validate sample-data/referential/type-mismatch.json"
```

**Expected Outcome**:
- Output displays `REF-002` with severity `[ERROR]` at `Observation.subject.reference`.
- Message explains target type `Condition` is invalid for `Observation.subject` (which permits `Patient`, `Group`, `Device`, `Location`).
- Exit code: `1`.

---

### Scenario 3: Orphaned Clinical Resource (`REF-003`)

**Goal**: Verify that a clinical resource without any direct or indirect link to a `Patient` generates a `REF-003` warning.

**Test Fixture**: `sample-data/referential/orphaned-observation.json` (An `Observation` with null/omitted `subject` and zero incoming links).

**Execution**:
```bash
./gradlew run --args="validate sample-data/referential/orphaned-observation.json --fail-on warning"
```

**Expected Outcome**:
- Output displays `REF-003` with severity `[WARNING]` at `Observation`.
- Message highlights that the clinical resource lacks clinical context linkage to any Patient.
- Exit code: `1` (since `--fail-on warning` was provided; exit `0` under default `--fail-on error`).

---

### Scenario 4: External Absolute Reference Handling (`REF-004`)

**Goal**: Verify that external HTTP/HTTPS references outside the local bundle perimeter do not fail the quality gate.

**Test Fixture**: `sample-data/referential/external-reference.json` (A resource referencing `https://external-ehr.org/fhir/Patient/ext-123`).

**Execution**:
```bash
./gradlew run --args="validate sample-data/referential/external-reference.json"
```

**Expected Outcome**:
- Zero `REF-001` broken reference errors.
- External reference reported as informational `[INFO]` under `REF-004`.
- Referential integrity score: `100%`.
- Exit code: `0`.

---

### Scenario 5: Multi-File Directory Aggregation

**Goal**: Verify that cross-file references across a folder of individual JSON files resolve cleanly without false-positive missing reference errors.

**Test Directory**: `sample-data/referential/multi-file/`
- `patient.json` containing `Patient/pat-1`
- `observation.json` containing `Observation/obs-1` with `subject: Patient/pat-1`

**Execution**:
```bash
./gradlew run --args="validate sample-data/referential/multi-file/"
```

**Expected Outcome**:
- Total Resources: 2.
- 0 referential defects reported.
- Referential Integrity Score: `100%`.
- Exit code: `0`.

---

### Scenario 6: Automated Test Suite

Run the complete referential integrity test suite:
```bash
./gradlew test --tests "org.fhirlint.core.graph.*"
```

**Expected Outcome**: All graph indexing, reference resolution, cycle-safety, and orphan-detection tests pass in under 1 second.
