# Quickstart & Integration Scenarios: Phase 8 — Advanced Extensibility, Dataset Comparison, and Dynamic FHIRPath Rules

**Branch**: `008-advanced-extensibility-diffing` | **Date**: 2026-10-03 | **Spec**: [spec.md](spec.md)

---

## 1. Prerequisites

- Built or installed `fhir-lint` CLI (or running via `./gradlew run --args="..."`)
- Java 21+ (if compiling from source)
- Sample datasets:
  - Baseline dataset: `sample-data/clean/clean-bundle.json`
  - Target dataset: `sample-data/messy/messy-bundle.json`

---

## 2. Scenario A: Compare Two Bundles (CLI Diffing)

### Command
```bash
fhir-lint compare sample-data/clean/clean-bundle.json sample-data/messy/messy-bundle.json
```

### Expected Behavior
1. Parses `clean-bundle.json`, runs analysis, produces baseline report in memory.
2. Parses `messy-bundle.json`, runs analysis, produces target report in memory.
3. Renders a colorized ANSI differential report showing:
   - Score Delta: e.g. `96 -> 74 (-22) [REGRESSION]`
   - Resource Counts: `Patient (+1), Observation (+4)`
   - Category breakdowns with $\Delta$ shifts
   - New Issues: Lists broken references, invalid codes, etc. introduced in `messy-bundle.json`
   - Resolved Issues: Lists any issues present in baseline but fixed in target.
4. Exits with code `0` (since `--fail-on-regression` was not specified).

---

## 3. Scenario B: CI/CD Regression Gate Enforcement

### Command
```bash
fhir-lint compare sample-data/clean/clean-bundle.json sample-data/messy/messy-bundle.json --fail-on-regression
```

### Expected Behavior
1. Performs full comparison.
2. Detects that new error-level issues exist in the target dataset and score dropped.
3. Prints regression alert to `stderr`.
4. Exits with code `1`.

---

## 4. Scenario C: Maximum Allowed Score Drop Threshold

### Passing Case ($\Delta$ score within threshold):
```bash
# Target score drops by 22, but threshold allows up to 25 drop
fhir-lint compare sample-data/clean/clean-bundle.json sample-data/messy/messy-bundle.json --max-score-drop 25
echo $?
# Output: 0
```

### Failing Case ($\Delta$ score exceeds threshold):
```bash
# Threshold allows maximum 10 drop, but score dropped by 22
fhir-lint compare sample-data/clean/clean-bundle.json sample-data/messy/messy-bundle.json --max-score-drop 10
echo $?
# Output: 1
```

---

## 5. Scenario D: Custom Rules via YAML and FHIRPath

### Create Custom Rule File: `vital-rules.yaml`
```yaml
rules:
  - id: custom-vital-final
    name: "Vital Signs Must Be Finalized"
    description: "Observations representing vital signs must possess status 'final'."
    resourceType: Observation
    category: completeness
    severity: error
    fhirpath: "status = 'final'"
    message: "Vital sign observation status is not 'final'."
    suggestion: "Ensure clinical vitals workflow transitions observations to final."
```

### Execute Validation with Custom Rules
```bash
fhir-lint validate sample-data/clean/clean-bundle.json --rules vital-rules.yaml
```

### Expected Behavior
1. Parses `vital-rules.yaml`, pre-compiles `status = 'final'` using HAPI `FHIRPathEngine`.
2. Evaluates against all `Observation` resources in `clean-bundle.json`.
3. If any Observation is in `preliminary` or `registered` status, emits `custom-vital-final` error.
4. Deducts 15 penalty points from the Completeness category score.

---

## 6. Scenario E: Machine-Readable JSON Comparison Output

### Command
```bash
fhir-lint compare sample-data/clean/clean-bundle.json sample-data/messy/messy-bundle.json --format json -o diff-report.json
```

### Expected Behavior
1. Emits 100% valid JSON matching the schema defined in `contracts/cli-compare-contract.md`.
2. Writes output directly to `diff-report.json`.
3. Emits zero non-JSON characters to `diff-report.json`.
