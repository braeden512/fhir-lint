# CLI Contract: `fhir-lint compare`

**Command**: `fhir-lint compare <baseline> <target> [OPTIONS]`  
**Description**: Compare two FHIR datasets, analyze quality regressions, and verify delivery gates.

---

## 1. Arguments

| Argument | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `<baseline>` | `Path` | Yes | Path to the baseline FHIR bundle file or directory. |
| `<target>` | `Path` | Yes | Path to the target FHIR bundle file or directory. |

---

## 2. Options

| Option | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `-p, --profile <name>` | `String` | `US_CORE` | Target validation profile (`US_CORE` or `BASE_R4`) applied to both datasets. |
| `-f, --format <type>` | `String` | `table` | Output format: `table` (colorized ANSI summary) or `json` (structured machine diff). |
| `-o, --output <file>` | `Path` | Stdout | Write comparison output directly to the specified file path. |
| `--fail-on-regression` | `Flag` | `false` | Fail with exit code `1` if any new error-level defect is detected or score drops. |
| `--max-score-drop <N>` | `Integer` | `null` | Fail with exit code `1` if target score drops by more than $N$ points ($\text{baseline} - \text{target} > N$). |
| `-r, --rules <path>` | `String` | `null` | Path to custom YAML rules file, comma-separated list, or directory. |
| `-v, --verbose` | `Flag` | `false` | Expand individual new and resolved issue line items in table output. |
| `-h, --help` | `Flag` | - | Display help message and exit. |

---

## 3. Exit Codes

Following standard POSIX and FHIRLint convention:

| Exit Code | Meaning | Condition |
| :---: | :--- | :--- |
| **`0`** | **PASS / CLEAN** | Comparison completed successfully and no enabled quality gates were violated. |
| **`1`** | **GATE FAILURE** | Quality regression detected: score drop exceeded `--max-score-drop` OR `--fail-on-regression` detected new errors / score drop. |
| **`2`** | **SYNTAX / FILE ERROR** | Invalid CLI arguments, non-existent files/directories, unparseable FHIR JSON, or malformed YAML rules. |

### Exit Code Precedence Contract
```text
Exit 2 (Pre-flight / File / Syntax Error)
  ↳ Trumps all others. If any file cannot be read or parsed, abort immediately with 2.

Exit 1 (Regression Gate Breach)
  ↳ Evaluated if both datasets lint successfully. If EITHER --fail-on-regression OR --max-score-drop fails, exit 1.

Exit 0 (Gate Compliance)
  ↳ Emitted only when all enabled gates evaluate to true.
```

---

## 4. Structured JSON Output Schema (`--format json`)

```json
{
  "summary": {
    "baselineScore": 92,
    "targetScore": 86,
    "scoreDelta": -6,
    "baselineGrade": "EXCELLENT",
    "targetGrade": "ACCEPTABLE",
    "totalBaselineResources": 1050,
    "totalTargetResources": 1080,
    "resourceDelta": 30
  },
  "gateEvaluation": {
    "passed": false,
    "failOnRegression": true,
    "maxScoreDrop": 5,
    "scoreDropViolated": true,
    "regressionViolated": true,
    "failureReason": "Target score dropped by 6 points (maximum allowed: 5); 2 new error-level issues detected."
  },
  "categoryDeltas": {
    "structural": 0,
    "profileConformance": -4,
    "referentialIntegrity": -8,
    "consistency": 0,
    "terminology": 0,
    "completeness": 0
  },
  "resourceCountDeltas": {
    "Patient": 0,
    "Observation": 30,
    "Encounter": 0
  },
  "issueCounts": {
    "newErrors": 2,
    "newWarnings": 1,
    "resolvedErrors": 1,
    "resolvedWarnings": 0,
    "persistentErrors": 0,
    "persistentWarnings": 2
  },
  "newIssues": [
    {
      "ruleId": "REF-001",
      "severity": "ERROR",
      "category": "REFERENTIAL_INTEGRITY",
      "resourceType": "Observation",
      "resourceId": "obs-new-99",
      "path": "Observation.subject.reference",
      "message": "Referenced resource 'Patient/missing-1' does not exist in the dataset.",
      "suggestion": "Verify patient ID or ensure target Patient resource is included."
    }
  ],
  "resolvedIssues": [
    {
      "ruleId": "STRUCT-001",
      "severity": "ERROR",
      "category": "STRUCTURAL",
      "resourceType": "Patient",
      "resourceId": "pat-1",
      "path": "Patient.gender",
      "message": "Invalid code 'unknown_val'",
      "suggestion": "Use standard administrative gender code."
    }
  ]
}
```
