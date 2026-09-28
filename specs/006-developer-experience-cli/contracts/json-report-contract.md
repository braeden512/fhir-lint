# JSON Report Contract: `--format json`

**Version**: 1.0.0 | **Status**: Active | **Domain**: Machine-Readable Outputs

---

## 1. Schema Definition

The JSON report emitted by `fhir-lint validate <input> --format json` serializes the `LintReport` model into a structured JSON object.

### JSON Schema (Informative)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "LintReport",
  "type": "object",
  "required": ["targetProfile", "inventory", "issues", "qualityScore", "durationMs"],
  "properties": {
    "targetProfile": {
      "type": "string",
      "enum": ["US_CORE", "BASE_R4"]
    },
    "inventory": {
      "type": "object",
      "required": ["totalResources", "resourceTypeCounts", "parseDurationMs"],
      "properties": {
        "totalResources": { "type": "integer", "minimum": 0 },
        "resourceTypeCounts": {
          "type": "object",
          "additionalProperties": { "type": "integer" }
        },
        "parseDurationMs": { "type": "integer", "minimum": 0 }
      }
    },
    "issues": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["id", "ruleId", "category", "severity", "message"],
        "properties": {
          "id": { "type": "string" },
          "ruleId": { "type": "string" },
          "category": {
            "type": "string",
            "enum": ["STRUCTURAL", "PROFILE_CONFORMANCE", "REFERENTIAL_INTEGRITY", "CONSISTENCY", "TERMINOLOGY", "COMPLETENESS"]
          },
          "severity": {
            "type": "string",
            "enum": ["ERROR", "WARNING", "INFO"]
          },
          "resourceType": { "type": ["string", "null"] },
          "resourceId": { "type": ["string", "null"] },
          "path": { "type": ["string", "null"] },
          "message": { "type": "string" },
          "suggestion": { "type": ["string", "null"] }
        }
      }
    },
    "qualityScore": {
      "type": "object",
      "required": ["overallScore", "grade", "categoryScores", "errorCount", "warningCount", "infoCount", "disclaimer"],
      "properties": {
        "overallScore": { "type": "integer", "minimum": 0, "maximum": 100 },
        "grade": {
          "type": "string",
          "enum": ["EXCELLENT", "ACCEPTABLE", "DEGRADED", "CRITICAL"]
        },
        "categoryScores": {
          "type": "object",
          "additionalProperties": { "type": "integer", "minimum": 0, "maximum": 100 }
        },
        "errorCount": { "type": "integer", "minimum": 0 },
        "warningCount": { "type": "integer", "minimum": 0 },
        "infoCount": { "type": "integer", "minimum": 0 },
        "disclaimer": { "type": "string" }
      }
    },
    "durationMs": { "type": "integer", "minimum": 0 }
  }
}
```

---

## 2. Example Output

```json
{
  "targetProfile": "US_CORE",
  "inventory": {
    "totalResources": 3,
    "resourceTypeCounts": {
      "Observation": 2,
      "Patient": 1
    },
    "parseDurationMs": 14
  },
  "issues": [
    {
      "id": "iss-001",
      "ruleId": "REF-001",
      "category": "REFERENTIAL_INTEGRITY",
      "severity": "ERROR",
      "resourceType": "Observation",
      "resourceId": "obs-1",
      "path": "Observation.subject.reference",
      "message": "Referenced Patient/p-999 does not exist in dataset.",
      "suggestion": "Verify the Patient reference or include Patient/p-999 in the dataset."
    }
  ],
  "qualityScore": {
    "overallScore": 88,
    "grade": "ACCEPTABLE",
    "categoryScores": {
      "REFERENTIAL_INTEGRITY": 60,
      "STRUCTURAL": 100,
      "PROFILE_CONFORMANCE": 100,
      "CONSISTENCY": 100,
      "TERMINOLOGY": 100,
      "COMPLETENESS": 100
    },
    "errorCount": 1,
    "warningCount": 0,
    "infoCount": 0,
    "disclaimer": "Quality scores produced by FHIRLint reflect technical data hygiene and engineering standards rather than clinical, medical, or regulatory compliance measurements."
  },
  "durationMs": 38
}
```
