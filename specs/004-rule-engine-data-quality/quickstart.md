# Quickstart & Verification Guide: Phase 4 — Pluggable Rule Engine and Data-Quality Checks

**Feature Branch**: `004-rule-engine-data-quality` | **Date**: 2026-09-27  
**Specification**: [`specs/004-rule-engine-data-quality/spec.md`](spec.md)

---

## 1. Prerequisites

- **Java**: OpenJDK 21 or higher
- **Build Tool**: Gradle 8.x (wrapper included)

Verify your environment:
```bash
java -version
./gradlew --version
```

---

## 2. Automated Test Verification

Run the entire automated test suite covering all 11 data quality rules, pluggable engine dispatching, and CLI regression tests:

```bash
./gradlew test --tests "org.fhirlint.core.rules.*"
```

To run the full suite across all phases:
```bash
./gradlew check
```

---

## 3. End-to-End Scenarios

### Scenario 1: Detect Chronological Inversions (`CONS-001` & `CONS-002`)

Validate a synthetic bundle containing:
1. An `Encounter` with `period.end` (2023-01-10) before `period.start` (2023-01-15).
2. A `MedicationRequest` authored in 1975 for a patient born in 1980.

```bash
./gradlew run --args="validate sample-data/messy/chronology-bundle.json --format table"
```

**Expected Outcome**:
- Output displays `[ERROR] CONS-001 Encounter/enc-01 period.end Encounter period ends before it begins.`
- Output displays `[ERROR] CONS-002 MedicationRequest/med-01 authoredOn Clinical event predates patient birthDate.`
- Exit code: `1`

---

### Scenario 2: Detect Duplicate Patients (`DUP-001` & `DUP-002`)

Validate a dataset containing two distinct `Patient` records sharing the same SSN identifier, and two patients sharing identical names, birth dates, and postal codes:

```bash
./gradlew run --args="validate sample-data/messy/duplicates-bundle.json --format json"
```

**Expected Outcome**:
- JSON output contains:
  - `ruleId`: `DUP-001`, `severity`: `ERROR`, identifying the matching identifier collision.
  - `ruleId`: `DUP-002`, `severity`: `WARNING`, identifying the demographic duplicate.
- Overall score degraded due to duplicate errors.

---

### Scenario 3: Terminology and UCUM Validation (`TERM-001` & `TERM-002`)

Validate an `Observation` with non-canonical system URI `http://loinc.org/` and a vital sign missing canonical UCUM units:

```bash
./gradlew run --args="validate sample-data/messy/terminology-bundle.json --format table"
```

**Expected Outcome**:
- Output displays `[WARNING] TERM-001 Observation/... Non-canonical system URI 'http://loinc.org/'. Use 'http://loinc.org'.`
- Output displays `[ERROR] TERM-002 Observation/... Vital signs observation missing canonical UCUM unit/system.`

---

### Scenario 4: Clinical Data Completeness (`COMP-001` & `COMP-002`)

Validate an `Observation` missing a `subject` reference and an `Observation` lacking both a `value[x]` and `dataAbsentReason`:

```bash
./gradlew run --args="validate sample-data/messy/completeness-bundle.json --format table"
```

**Expected Outcome**:
- Output displays `[ERROR] COMP-001 Observation/obs-orphan subject Clinical resource must declare a subject reference.`
- Output displays `[WARNING] COMP-002 Observation/obs-empty Observation has neither a value nor a dataAbsentReason.`

---

### Scenario 5: Clean Benchmark Dataset Passing Gate (SC-008, SC-011)

Validate a clean reference bundle (e.g. Synthea clean bundle):

```bash
./gradlew run --args="validate sample-data/clean/clean-bundle.json --min-score 90 --fail-on error"
```

**Expected Outcome**:
- Quality Score $\ge 90/100$ (`EXCELLENT`).
- Zero `CONSISTENCY`, `DUPLICATE`, `TERMINOLOGY`, or `COMPLETENESS` errors reported.
- Exit code: `0`.
