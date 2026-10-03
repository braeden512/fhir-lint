# Contract: Custom YAML Rules Schema

**Usage**: `--rules <file|directory>`  
**Supported File Formats**: `.yaml`, `.yml`

---

## 1. YAML Document Structure

A custom rules file contains a top-level `rules` array:

```yaml
# custom-rules.yaml
rules:
  - id: custom-vital-bp-components
    name: "Blood Pressure Observation Must Have Two Components"
    description: "Ensures blood pressure panels contain both systolic and diastolic blood pressure components."
    resourceType: Observation
    category: completeness
    severity: error
    fhirpath: "code.coding.where(code = '85354-9').empty() or component.count() = 2"
    message: "Blood pressure observation (LOINC 85354-9) must contain exactly 2 components."
    suggestion: "Include systolic (8480-6) and diastolic (8462-4) components."

  - id: custom-patient-mrn
    name: "Patient Must Include Medical Record Number"
    description: "Requires all patients to possess an MRN identifier."
    resourceType: Patient
    category: completeness
    severity: warning
    fhirpath: "identifier.where(type.coding.where(code = 'MR').exists()).exists()"
    message: "Patient is missing a Medical Record Number (MRN) identifier."
    suggestion: "Ensure patient export includes type code 'MR' in identifier array."
```

---

## 2. Field Specifications

| Field | Type | Required | Allowed Values / Pattern | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `String` | **Yes** | `^[a-zA-Z0-9_-]+$` | Unique rule identifier. |
| `name` | `String` | **Yes** | Non-empty string | Human-readable title for the rule. |
| `description` | `String` | No | String | Detailed clinical or administrative explanation. |
| `resourceType` | `String` | No | Any FHIR R4 Resource name | If specified, rule is evaluated only on resources of this type. If omitted, evaluated on all indexed resources. |
| `category` | `String` | **Yes** | `structural`, `profileConformance`, `referentialIntegrity`, `consistency`, `terminology`, `completeness` | Quality category under which penalty points are scored. |
| `severity` | `String` | **Yes** | `error`, `warning`, `info` | Defect severity level. |
| `fhirpath` | `String` | **Yes** | Valid FHIRPath invariant expression | Invariant expression evaluated against the resource instance. **MUST** evaluate to boolean `true` for a resource to be considered compliant. If `false` or empty, an issue is emitted. |
| `message` | `String` | **Yes** | Non-empty string | Error diagnostic message displayed to the developer. |
| `suggestion` | `String` | No | Non-empty string | Actionable remediation advice (Constitution Principle IV). |

---

## 3. Evaluation Context

* The expression is evaluated with the resource instance as `%context` and the evaluation root.
* For example, on a resource with `resourceType: Observation`:
  - `status = 'final'` checks `Observation.status == 'final'`.
  - `value.exists() or dataAbsentReason.exists()` checks value presence.
* Invariant semantics: The expression declares what **MUST BE TRUE**.
  - Returns `true` $\to$ No issue emitted.
  - Returns `false` or empty $\to$ Defect issue emitted.
