# Phase 1: Quickstart & End-to-End Validation Guide

This guide describes runnable verification scenarios to validate that **Phase 2: Structural and Profile Validation** functions correctly from end to end.

---

## Prerequisites & Setup

1. **Java Runtime**: JDK 21+ installed and configured on `$PATH`.
2. **Build Tool**: Gradle wrapper (`./gradlew`) in project root.
3. **Build the CLI executable**:
   ```bash
   ./gradlew assemble
   ```

---

## Scenario 1: Base FHIR R4 Structural Error Detection

**Objective**: Verify that an invalid primitive data format (e.g. invalid date syntax) is caught as a `STRUCTURAL` error.

### Input Fixture: `sample-invalid-date.json`
```json
{
  "resourceType": "Patient",
  "id": "pat-invalid-date",
  "birthDate": "1985-99-99"
}
```

### Execution
```bash
./gradlew run --args="validate sample-invalid-date.json --profile BASE_R4"
```

### Expected Outcome
- **Exit Code**: `1` (Quality gate failed due to errors).
- **Issue Count**: 1+ Error.
- **Category**: `STRUCTURAL`.
- **Location**: `Patient.birthDate`.
- **Message**: Mentions invalid date format.
- **Remediation**: Recommends providing a valid date in `YYYY-MM-DD` format.

---

## Scenario 2: US Core Profile Conformance Detection

**Objective**: Verify that a resource conforming to base FHIR R4 but missing a required US Core element (e.g. `Patient.name` or US Core identifier) triggers a `PROFILE_CONFORMANCE` error under US Core validation.

### Input Fixture: `sample-base-only-patient.json`
```json
{
  "resourceType": "Patient",
  "id": "pat-no-name",
  "gender": "male"
}
```

### Execution
```bash
# Validating under default US_CORE profile
./gradlew run --args="validate sample-base-only-patient.json --profile US_CORE"
```

### Expected Outcome
- **Exit Code**: `1`.
- **Category**: `PROFILE_CONFORMANCE`.
- **Issue Path**: `Patient.name` or `Patient.identifier`.
- **Message**: Indicates missing US Core required element (US Core Patient requires name and identifier).

---

## Scenario 3: Profile Target Switching (`BASE_R4` vs `US_CORE`)

**Objective**: Verify that `--profile BASE_R4` passes for base-valid resources while `--profile US_CORE` flags profile constraints on the same payload.

### Execution
```bash
# Pass 1: Validate against BASE_R4
./gradlew run --args="validate sample-base-only-patient.json --profile BASE_R4"
# Expected Exit Code: 0 (No structural errors in base schema)

# Pass 2: Validate against US_CORE
./gradlew run --args="validate sample-base-only-patient.json --profile US_CORE"
# Expected Exit Code: 1 (Fails US Core conformance constraints)
```

---

## Scenario 4: Clean Synthetic Bundle Validation

**Objective**: Verify that a fully conformant FHIR R4 Bundle satisfying both structural and US Core constraints completes cleanly with a 100/100 score.

### Execution
```bash
./gradlew run --args="validate sample-data/clean/clean-bundle.json --profile US_CORE --format json"
```

### Expected Outcome
- **Exit Code**: `0`.
- **Quality Score**: `100/100` (`EXCELLENT`).
- **Issues**: 0 Errors, 0 Warnings.
- **Execution Time**: Reported in duration metrics (< 2000ms).

---

## Scenario 5: Automated Test Suite Execution

**Objective**: Execute all automated unit and integration tests covering structural validation, profile conformance, message normalization, and CLI exit codes.

### Execution
```bash
./gradlew test
```

### Expected Outcome
- All tests pass in under 3 seconds.
- Test report generated in `build/reports/tests/test/index.html`.
