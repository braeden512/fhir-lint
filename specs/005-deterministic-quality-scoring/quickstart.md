# Quickstart Validation Guide: Phase 5 — Deterministic Multi-Category Quality Scoring Model

## Overview

This guide provides runnable scenarios to verify end-to-end functionality of the Phase 5 deterministic quality scoring engine and CI/CD quality gates.

---

## Prerequisites

- **Java Development Kit**: JDK 21+ (`java -version`)
- **Build Tool**: Gradle wrapper included (`./gradlew`)
- **Codebase State**: Built through Phase 4 (`./gradlew testClasses`)

---

## Scenario 1: Verify Deterministic Defect-Density Calculation & Category Weights

Proves that category scores, volume normalization, and weighted overall score match ADR-005.

### Steps
1. Execute the dedicated unit test suite for scoring:
   ```bash
   ./gradlew test --tests "org.fhirlint.core.QualityScoreTest"
   ```
2. Verify test outcomes:
   - Zero issues in 10 resources computes `100/100` across all categories and composite.
   - 1 `ERROR` in `referentialIntegrity` for 10 resources yields `penalty = 15`, `density = 0.10`, `score = 90`.
   - 2 `WARNING` in `terminology` for 10 resources yields `penalty = 6`, `density = 0.04`, `score = 96`.
   - Category scores clamp at 0 under extreme defect densities (e.g., 50 errors in 1 resource).
   - Overall score rounds half-up deterministically across all weights.

---

## Scenario 2: Verify Duplicate Category Mapping into Consistency (FR-013)

Proves that findings from Phase 4 duplicate rules (`DUP-001`, `DUP-002`) contribute directly to the `CONSISTENCY` category score and overall score.

### Steps
1. Run duplicate scoring integration tests:
   ```bash
   ./gradlew test --tests "org.fhirlint.core.QualityScoreTest.testDuplicateIssuesMapToConsistency"
   ```
2. Expected Outcome:
   - A dataset with 1 `DUP-001` error incurs 15 penalty points in `CONSISTENCY`.
   - The `CONSISTENCY` category score is reduced accordingly, and the composite score reflects the 15% weight.

---

## Scenario 3: Verify Quality Gate Evaluation & Multi-Breach Capture

Proves that `--min-score` and `--fail-on` evaluate correctly without premature short-circuiting.

### Steps
1. Execute quality gate unit tests:
   ```bash
   ./gradlew test --tests "org.fhirlint.core.QualityGateTest"
   ```
2. Expected Outcome:
   - Score of 84 with `--min-score 80` passes.
   - Score of 79 with `--min-score 80` fails with:
     `Overall quality score (79) is below required minimum threshold (80).`
   - Score of 65 with `--min-score 80` AND 1 `ERROR` with `--fail-on error` captures **both** breach reasons in `QualityGateResult.breaches()`.

---

## Scenario 4: Verify Non-Clinical Engineering Disclaimer

Proves that the mandatory non-clinical disclaimer is accessible and rendered.

### Steps
1. Execute disclaimer verification test:
   ```bash
   ./gradlew test --tests "org.fhirlint.core.QualityScoreTest.testNonClinicalDisclaimerPresent"
   ```
2. Verify output string matches the canonical constant:
   > *"Quality scores produced by FHIRLint reflect technical data hygiene and engineering standards rather than clinical, medical, or regulatory compliance measurements."*

---

## Scenario 5: End-to-End CLI Quality Gate Execution

Proves that `fhir-lint validate` respects exit code contracts (`0` on pass, `1` on gate failure).

### Steps
1. Build the distribution:
   ```bash
   ./gradlew installDist
   ```
2. Run clean dataset against strict gate:
   ```bash
   ./build/install/fhir-lint/bin/fhir-lint validate sample-data/clean/clean-bundle.json --min-score 90 --fail-on error
   echo "Exit code: $?"
   # Expected exit code: 0
   ```
3. Run messy dataset against gate:
   ```bash
   ./build/install/fhir-lint/bin/fhir-lint validate sample-data/messy/messy-bundle.json --min-score 95
   echo "Exit code: $?"
   # Expected exit code: 1 (Gate breach printed to stderr)
   ```
