# CLI Contract: `fhir-lint validate` (Phase 3 — Referential Integrity)

This document defines the command-line interface contract for the `validate` command with dataset-level referential integrity and resource graph analysis.

---

## 1. Command Syntax & Options

```bash
fhir-lint validate <input-source> [options]
```

### 1.1 Positional Arguments

| Argument | Type | Description |
| :--- | :--- | :--- |
| `<input-source>` | String (Path or `-`) | Path to a FHIR JSON file, directory of JSON files, or `-` to read from standard input (`stdin`). |

### 1.2 Options

| Option | Flag | Default | Description |
| :--- | :--- | :--- | :--- |
| `--profile` | `-p` | `US_CORE` | Target profile for schema/conformance validation (`US_CORE`, `BASE_R4`). |
| `--format` | `-f` | `table` | Output serialization format: `table` (ANSI terminal table), `json` (machine-readable), `sarif` (GitHub Code Scanning). |
| `--min-score` | None | `0` | Minimum acceptable overall quality score (0–100). Exits with `1` if overall score is below threshold. |
| `--fail-on` | None | `error` | Severity threshold triggering quality gate exit code `1`: `error`, `warning`, `info`. |
| `--verbose` | `-v` | `false` | Display full issue details and remediation suggestions in terminal output. |
| `--output` | `-o` | `stdout` | Write output report to the specified file path instead of stdout. |

---

## 2. Referential Integrity Diagnostic Rules

When executing `fhir-lint validate`, referential integrity analysis runs across all resources in the dataset and emits issues under `category: REFERENTIAL_INTEGRITY`:

| Rule ID | Severity | Description | Default Gate Behavior |
| :--- | :---: | :--- | :---: |
| `REF-001` | `ERROR` | Target resource does not exist in dataset (broken local/relative path, UUID URN, broken `#contained` fragment, ambiguous bare ID, or empty reference). | Fails gate (Exit `1`) |
| `REF-002` | `ERROR` | Target resource exists, but its resource type is not permitted for the referencing field by FHIR R4 schema. | Fails gate (Exit `1`) |
| `REF-003` | `WARNING` | Orphaned clinical resource (`Observation`, `Condition`, `DiagnosticReport`) lacks direct or indirect linkage to a `Patient`. | Passes default gate (Fails if `--fail-on warning`) |
| `REF-004` | `INFO` | Reference points to an external absolute HTTP/HTTPS URI outside local dataset boundary. | Passes default gate (Informational only) |

---

## 3. Exit Code Contract

| Exit Code | Meaning | Conditions |
| :---: | :--- | :--- |
| **`0`** | **Quality Gate Passed** | Dataset parsed successfully, referential integrity evaluated, score $\ge$ `--min-score`, and no issues at or above `--fail-on` threshold. |
| **`1`** | **Quality Gate Failed** | Dataset parsed successfully, but validation detected issues violating `--fail-on` (e.g. 1+ `REF-001` or `REF-002` errors under default settings) or overall score $<$ `--min-score`. |
| **`2`** | **Invocation / Syntax Error** | Malformed JSON input, missing FHIR `resourceType`, non-existent file path, or invalid CLI options. |

---

## 4. Output Serialization Formats

### 4.1 ANSI Terminal Output (`--format table`)
Includes a dedicated category score row for **Referential Integrity** (weight: 25%):

```text
================================================================================
                               FHIRLint Report                                  
================================================================================
Target Profile: US_CORE
Total Resources: 42 (1 Patient, 3 Encounters, 38 Observations)
Duration: 184ms

Overall Quality Score: 78/100 (ACCEPTABLE)

Category Breakdown:
  • Structural Conformance:  100% (Weight: 20%)
  • Profile Conformance:      95% (Weight: 20%)
  • Referential Integrity:    62% (Weight: 25%)

Issues Detected (3):
--------------------------------------------------------------------------------
[ERROR]   Observation/obs-01 at Observation.subject.reference [REF-001]
          Referenced target 'Patient/pat-missing' does not exist in dataset.
          -> Verify that the referenced Patient is included in the bundle.

[ERROR]   Observation/obs-02 at Observation.subject.reference [REF-002]
          Target type 'Condition' is not valid for Observation.subject. Expected: Patient, Group, Device, Location.
          -> Update the reference to point to the valid patient or device.

[WARNING] Condition/cond-09 at Condition [REF-003]
          Orphaned clinical resource lacks direct or indirect context link to a Patient.
          -> Link the Condition to a Patient or an Encounter with a valid patient subject.
================================================================================
```

### 4.2 JSON Output (`--format json`)
```json
{
  "profile": "US_CORE",
  "score": {
    "overallScore": 78,
    "grade": "ACCEPTABLE",
    "categoryScores": {
      "STRUCTURAL": 100,
      "PROFILE_CONFORMANCE": 95,
      "REFERENTIAL_INTEGRITY": 62,
      "CONSISTENCY": 100,
      "TERMINOLOGY": 100,
      "COMPLETENESS": 100
    },
    "errorCount": 2,
    "warningCount": 1,
    "infoCount": 0
  },
  "issues": [
    {
      "id": "issue_a7e2b19f",
      "severity": "ERROR",
      "category": "REFERENTIAL_INTEGRITY",
      "ruleId": "REF-001",
      "resourceType": "Observation",
      "resourceId": "obs-01",
      "path": "Observation.subject.reference",
      "message": "Referenced target 'Patient/pat-missing' does not exist in the dataset.",
      "suggestion": "Verify that the referenced Patient is included in the bundle or update the reference ID."
    }
  ]
}
```

### 4.3 SARIF Output (`--format sarif`)
Rules `REF-001`, `REF-002`, `REF-003`, and `REF-004` are declared in the SARIF `driver.rules` array and reported with appropriate SARIF levels (`error`, `warning`, `note`).
