# SARIF Report Contract: `--format sarif`

**Version**: 1.0.0 | **Status**: Active | **Standard**: OASIS SARIF v2.1.0 | **Domain**: GitHub Code Scanning CI/CD

---

## 1. Specification Overview

The SARIF report output complies with the **OASIS Static Analysis Results Interchange Format (SARIF) v2.1.0** specification, enabling GitHub Actions to automatically parse the file and annotate pull request diffs via the GitHub Code Scanning API.

---

## 2. Structure & Element Mapping

| SARIF Element | Mapping Source / Content | Description |
| :--- | :--- | :--- |
| `version` | `"2.1.0"` | Mandatory OASIS SARIF version identifier. |
| `$schema` | `"https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json"` | Official schema definition URI. |
| `runs[0].tool.driver.name` | `"FHIRLint"` | Tool identity. |
| `runs[0].tool.driver.version` | `"0.1.0"` | Tool release version. |
| `runs[0].tool.driver.informationUri` | `"https://github.com/braeden512/fhir-lint"` | Documentation and project repository URI. |
| `runs[0].tool.driver.rules[]` | Core catalog rules + dynamically declared rules | Array of rule definition objects (`id`, `name`, `shortDescription`, `fullDescription`). |
| `runs[0].properties.disclaimer` | `QualityScore.NON_CLINICAL_DISCLAIMER` | Embedded in SARIF property bag to avoid schema validation errors. |
| `runs[0].results[]` | Mapped from `LintReport.issues()` | Array of individual detected defect results. |
| `results[i].ruleId` | `issue.ruleId()` | Standard rule identifier (e.g. `REF-001`, `CONS-001`). |
| `results[i].level` | Mapped from `issue.severity()`: `ERROR` → `"error"`, `WARNING` → `"warning"`, `INFO` → `"note"` | Standard SARIF severity level. |
| `results[i].message.text` | `issue.message() + " Suggestion: " + issue.suggestion()` | Combined diagnostic description and concrete remediation advice. |
| `results[i].locations[0].physicalLocation.artifactLocation.uri` | Target file path, or `"stdin"` when reading from standard input | RFC-3986 relative or absolute URI. |
| `results[i].locations[0].physicalLocation.region.message.text` | `issue.path()` (or `"root"`) | Element path or FHIRPath expression. |

---

## 3. Example Output

```json
{
  "$schema" : "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json",
  "version" : "2.1.0",
  "runs" : [ {
    "tool" : {
      "driver" : {
        "name" : "FHIRLint",
        "version" : "0.1.0",
        "informationUri" : "https://github.com/braeden512/fhir-lint",
        "rules" : [ {
          "id" : "REF-001",
          "name" : "Broken Local Reference",
          "shortDescription" : {
            "text" : "Broken Local Reference"
          },
          "fullDescription" : {
            "text" : "Target resource does not exist in dataset (broken local/relative path, UUID URN, broken #contained fragment, or empty reference)."
          }
        } ]
      }
    },
    "properties" : {
      "disclaimer" : "Quality scores produced by FHIRLint reflect technical data hygiene and engineering standards rather than clinical, medical, or regulatory compliance measurements."
    },
    "results" : [ {
      "ruleId" : "REF-001",
      "level" : "error",
      "message" : {
        "text" : "Referenced Patient/p-999 does not exist in dataset. Suggestion: Verify the Patient reference or include Patient/p-999 in the dataset."
      },
      "locations" : [ {
        "physicalLocation" : {
          "artifactLocation" : {
            "uri" : "sample-data/messy/messy-bundle.json"
          },
          "region" : {
            "message" : {
              "text" : "Observation.subject.reference"
            }
          }
        }
      } ]
    } ]
  } ]
}
```
