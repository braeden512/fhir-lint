# CLI Interface Contract: Phase 1 — FHIR Dataset Ingestion

This document defines the formal command-line interface contract for FHIRLint Phase 1.

---

## 1. Command Syntax

```bash
fhir-lint validate <file|directory|-> [options]
```

### Positional Parameters
- `inputSource` (Required):
  - Path to a FHIR JSON file (e.g. `bundle.json`)
  - Path to a directory containing `.json` files (e.g. `./data-dir/`)
  - `-` to read JSON from standard input (`stdin`).

### Options
- `-p, --profile <NAME>`: Target validation profile (`US_CORE`, `BASE_R4`). Default: `US_CORE`.
- `-f, --format <FORMAT>`: Output format: `table` (default ANSI color table), `json`, `sarif`.
- `--min-score <0-100>`: Minimum passing quality score. Exits with code `1` if the overall score is below this threshold. Default: `0`.
- `--fail-on <SEVERITY>`: Exit with code `1` if any issue of this severity or higher is detected (`error`, `warning`). Default: `error`.
- `-v, --verbose`: Display detailed issue listings in terminal table output.
- `-o, --output <PATH>`: Write output to a file instead of `stdout`.

---

## 2. Standard Exit Codes

| Exit Code | Meaning | Condition |
| :---: | :--- | :--- |
| **`0`** | **SUCCESS / QUALITY PASSED** | Dataset ingested and parsed successfully. Quality score $\ge$ `--min-score` and no issues violating `--fail-on`. |
| **`1`** | **QUALITY GATE FAILURE** | Ingestion succeeded, but dataset quality score is below `--min-score` or contains issues violating `--fail-on`. |
| **`2`** | **SYNTAX / BOUNDARY ERROR** | Pre-flight verification failed: malformed JSON, missing `resourceType`, file not found, or invalid CLI flags. Actionable error emitted to `stderr`. |

---

## 3. Output Formats

### 3.1 `table` (Default)
Colorized ANSI terminal table rendering:
- Header box with working name
- Quality Score badge (`Score: 100/100 (EXCELLENT)`)
- Resource inventory metrics (`Resources: 5 | Errors: 0 | Warnings: 0 | Duration: Xms`)
- Resource type distribution breakdown
- Category score progress bars (`Structural Conformance [████████████████████] 100%`)
- Prioritized findings with FHIRPath locations and suggestions.

### 3.2 `json`
Machine-readable JSON output:
```json
{
  "targetProfile" : "US_CORE",
  "inventory" : {
    "totalResources" : 5,
    "resourceTypeCounts" : {
      "Condition" : 1,
      "Encounter" : 1,
      "MedicationRequest" : 1,
      "Observation" : 1,
      "Patient" : 1
    },
    "parseDurationMs" : 576
  },
  "issues" : [ ],
  "qualityScore" : {
    "overallScore" : 100,
    "grade" : "EXCELLENT",
    "categoryScores" : { ... },
    "errorCount" : 0,
    "warningCount" : 0,
    "infoCount" : 0
  },
  "durationMs" : 576
}
```

### 3.3 `sarif`
OASIS SARIF 2.1.0 JSON format for GitHub PR Code Scanning annotations.
