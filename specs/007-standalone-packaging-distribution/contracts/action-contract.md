# GitHub Composite Action Interface Contract: `action.yml`

**Contract Name**: FHIRLint Official GitHub Composite Action
**Location**: `action.yml` (repository root)
**Reference**: [spec.md](../spec.md)

---

## 1. Action Metadata Definition

```yaml
name: 'FHIRLint Healthcare Data Quality Gate'
description: 'Validates FHIR healthcare datasets for interoperability, integrity, completeness, and profile conformance with automated quality gates and SARIF annotations.'
author: 'FHIRLint Maintainers'
branding:
  icon: 'activity'
  color: 'blue'

inputs:
  path:
    description: 'Path to local FHIR JSON file or directory containing FHIR resources'
    required: true
  profile:
    description: 'Target validation profile (US_CORE or BASE_R4)'
    required: false
    default: 'US_CORE'
  format:
    description: 'Output format rendered in GitHub Actions console log (table, json, sarif)'
    required: false
    default: 'table'
  min-score:
    description: 'Minimum passing quality score threshold (0-100)'
    required: false
  fail-on:
    description: 'Failure severity threshold (error, warning, info, none)'
    required: false
    default: 'error'
  output-file:
    description: 'Optional destination file path for rendered output report'
    required: false
  upload-sarif:
    description: 'Upload SARIF results to GitHub Code Scanning'
    required: false
    default: 'false'
  github-token:
    description: 'GitHub token for SARIF upload (requires security-events: write)'
    required: false
    default: ${{ github.token }}

outputs:
  score:
    description: 'Overall quality score (0-100)'
    value: ${{ steps.lint.outputs.score }}
  grade:
    description: 'Engineering grade tier (EXCELLENT, ACCEPTABLE, DEGRADED, CRITICAL)'
    value: ${{ steps.lint.outputs.grade }}
  errors:
    description: 'Total error-level issues detected'
    value: ${{ steps.lint.outputs.errors }}
  warnings:
    description: 'Total warning-level issues detected'
    value: ${{ steps.lint.outputs.warnings }}
  passed:
    description: 'Boolean quality gate verdict (true or false)'
    value: ${{ steps.lint.outputs.passed }}
  report-path:
    description: 'Path to saved output report'
    value: ${{ steps.lint.outputs.report_path }}

runs:
  using: 'composite'
  steps:
    - id: resolve-binary
      shell: bash
      run: |
        # Detect runner OS and locate platform binary or universal JAR
        ...
    - id: lint
      shell: bash
      run: |
        # 1. Execute linter generating internal JSON for deterministic output extraction
        # 2. Render user-requested log format to console
        # 3. Export variables to $GITHUB_OUTPUT
        # 4. Handle quality gate exit code (0 or 1)
    - id: upload-sarif-step
      if: inputs.upload-sarif == 'true'
      uses: github/codeql-action/upload-sarif@v3
      with:
        sarif_file: ${{ steps.lint.outputs.sarif_path }}
        token: ${{ inputs.github-token }}
      continue-on-error: true
```

---

## 2. Invocations & Usage Examples

### Minimal Quality Gate
```yaml
- name: Validate FHIR Bundles
  uses: fhir-lint/action@v1
  with:
    path: 'sample-data/clean/clean-bundle.json'
```

### Strict Threshold Gate with Code Scanning Annotations
```yaml
- name: Run FHIRLint Quality Gate
  uses: fhir-lint/action@v1
  with:
    path: 'fixtures/fhir-resources'
    profile: 'US_CORE'
    min-score: '85'
    fail-on: 'warning'
    upload-sarif: 'true'
    github-token: ${{ secrets.GITHUB_TOKEN }}
```

---

## 3. Exit Code and Verdict Contract

| Condition | Action Step Verdict | PR Annotation / Log |
| :--- | :---: | :--- |
| Score >= `min-score` AND no issues violating `fail-on` | **`SUCCESS` (Exit 0)** | Step passes, exports `$GITHUB_OUTPUT` variables (`passed=true`). |
| Score < `min-score` OR issues violate `fail-on` | **`FAILURE` (Exit 1)** | Step fails, exports `$GITHUB_OUTPUT` variables (`passed=false`), logs breach details. |
| Missing input file, invalid flags, malformed JSON syntax | **`FAILURE` (Exit 2)** | Step fails immediately with invocation diagnostics. |
| `upload-sarif: true` with missing GHAS or invalid token | **Warning** | Warning emitted to step log; quality check verdict remains governed by linting results. |
