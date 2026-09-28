# Quickstart & Verification Guide: Phase 6 — Developer Experience and Standalone CLI

**Branch**: `006-developer-experience-cli` | **Date**: 2026-09-27 | **Spec**: [spec.md](spec.md)

## Overview

This guide provides runnable terminal scenarios to verify the command-line interface, exit codes, output renderers, and quality gates for FHIRLint Phase 6.

---

## Prerequisites

- **Java Runtime**: JDK 21+ (`java -version`)
- **Gradle Wrapper**: `./gradlew`
- **Sample Datasets**:
  - `sample-data/clean/clean-bundle.json`
  - `sample-data/messy/messy-bundle.json`

---

## Scenario 1: Default Human-Centric Terminal Table (`--format table`)

Verify the default ANSI terminal scorecard output on clean and messy bundles.

```bash
# 1. Clean bundle pass (expect exit code 0, 100/100 EXCELLENT, 0 errors)
./gradlew run --args="validate sample-data/clean/clean-bundle.json"
echo "Exit code: $?"

# 2. Messy bundle pass (use --fail-on none to bypass default --fail-on error; expect exit code 0, DEGRADED grade, top 10 issues)
./gradlew run --args="validate sample-data/messy/messy-bundle.json --fail-on none"
echo "Exit code: $?"

# 3. Verbose messy bundle pass (displays all issues without truncation)
./gradlew run --args="validate sample-data/messy/messy-bundle.json --fail-on none -v"
```

**Expected Outcome**:
- Colored header banner, Quality Score badge, category progress bars.
- Enriched location context `(ResourceType/Id: path)` with remediation suggestions.
- Non-clinical engineering disclaimer at the bottom.

---

## Scenario 2: Machine-Readable JSON Output (`--format json`)

Verify structured JSON generation pipeable into `jq`.

```bash
# Lint clean bundle to JSON and inspect quality score
./gradlew run --args="validate sample-data/clean/clean-bundle.json -f json" | jq '.qualityScore'
```

**Expected Outcome**:
- Standard output contains strictly valid JSON.
- Contains `targetProfile`, `inventory`, `issues`, `qualityScore`, and `disclaimer`.

---

## Scenario 3: Standard Input Streaming (`-`)

Verify piping FHIR JSON payloads directly via UNIX stdin.

```bash
cat sample-data/clean/clean-bundle.json | ./gradlew run --args="validate - -f json" | jq '.inventory.totalResources'
```

**Expected Outcome**:
- Emits `5` total resources with exit code `0`.

---

## Scenario 4: SARIF 2.1.0 for GitHub Code Scanning (`--format sarif`)

Verify OASIS SARIF v2.1.0 output with file redirection.

```bash
mkdir -p build/reports
./gradlew run --args="validate sample-data/messy/messy-bundle.json -f sarif -o build/reports/results.sarif"
cat build/reports/results.sarif | jq '.version, .runs[0].properties.disclaimer'
```

**Expected Outcome**:
- Reports `"2.1.0"` and the non-clinical engineering disclaimer.
- Report written directly to `build/reports/results.sarif`.

---

## Scenario 5: CI/CD Quality Gate Enforcement (Exit Codes `0` vs `1`)

Verify quality gates breaking the build when thresholds are violated.

```bash
# Pass: Clean bundle with min-score 90 (score is 100) -> Exit Code 0
./gradlew run --args="validate sample-data/clean/clean-bundle.json --min-score 90"
echo "Exit code: $?" # Expect 0

# Fail: Messy bundle with min-score 90 (score is ~64) -> Exit Code 1
./gradlew run --args="validate sample-data/messy/messy-bundle.json --min-score 90"
echo "Exit code: $?" # Expect 1

# Fail: Messy bundle with --fail-on error -> Exit Code 1
./gradlew run --args="validate sample-data/messy/messy-bundle.json --fail-on error"
echo "Exit code: $?" # Expect 1
```

**Expected Outcome**:
- Exit code `0` when conditions are met.
- Exit code `1` when score falls below threshold or forbidden severities occur, with breach details on `stderr`.

---

## Scenario 6: Invocation & Boundary Error Handling (Exit Code `2`)

Verify strict rejection of invalid arguments or missing inputs.

```bash
# 1. Non-existent file
./gradlew run --args="validate missing-file.json"
echo "Exit code: $?" # Expect 2

# 2. Invalid minimum score (> 100 or < 0)
./gradlew run --args="validate sample-data/clean/clean-bundle.json --min-score 150"
echo "Exit code: $?" # Expect 2

# 3. Unsupported format
./gradlew run --args="validate sample-data/clean/clean-bundle.json -f xml"
echo "Exit code: $?" # Expect 2
```

**Expected Outcome**:
- Actionable diagnostic printed to `stderr`.
- Exit code `2`.

---

## Scenario 7: Running the Automated Test Suite

Verify all CLI, renderer, and core test suites pass in under 3 seconds.

```bash
./gradlew test
```

**Expected Outcome**:
- 100% of test suites pass in `< 3.0s`.
