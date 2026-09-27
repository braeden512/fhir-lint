# Quickstart Validation Guide: Phase 1 — FHIR Dataset Ingestion & Boundary Parsing

This guide describes how to validate the end-to-end functionality of Phase 1 using the CLI on sample datasets.

---

## Prerequisites

1. **Java 21 JDK** installed.
2. Build the project using Gradle:
   ```bash
   ./gradlew assemble
   ```

---

## Scenario 1: Ingesting a FHIR R4 Bundle from a File

Run the CLI against `sample-data/clean/clean-bundle.json`:

```bash
./gradlew run --args="validate sample-data/clean/clean-bundle.json"
```

**Expected Output**:
- Exit code: `0`
- Output: Colorized summary table indicating resources analyzed:
  ```text
  Resources: 5  │  Errors: 0  │  Warnings: 0
  Resource Distribution:
    Condition            : 1
    Encounter            : 1
    MedicationRequest    : 1
    Observation          : 1
    Patient              : 1
  ```

---

## Scenario 2: Ingesting via UNIX Standard Input (`stdin`)

Pipe raw JSON directly into the CLI:

```bash
cat sample-data/clean/clean-bundle.json | ./gradlew run --args="validate -"
```

**Expected Output**:
- Exit code: `0`
- Dataset is ingested from stdin stream and parsed successfully.

---

## Scenario 3: Boundary Rejection of Malformed JSON

Run the CLI on a malformed file:

```bash
./gradlew run --args="validate sample-data/invalid-syntax.json"
```

**Expected Output**:
- Exit code: `2`
- Diagnostic message printed to `stderr`:
  ```text
  Error: Failed to parse input: Malformed JSON syntax at line 1.
  ```

---

## Scenario 4: Boundary Rejection of Non-FHIR Payload

Run the CLI on a JSON file missing `resourceType`:

```bash
./gradlew run --args="validate sample-data/non-fhir.json"
```

**Expected Output**:
- Exit code: `2`
- Diagnostic message printed to `stderr`:
  ```text
  Error: Invalid FHIR payload: Missing required 'resourceType' declaration.
  ```

---

## Scenario 5: Automated Test Suite Execution

Run the complete test suite verifying parser mechanics and boundary validations:

```bash
./gradlew test
```

All unit tests execute and pass in sub-second time with zero Docker or database dependencies.
