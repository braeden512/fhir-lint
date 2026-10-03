# Feature Specification: Phase 8 — Advanced Extensibility, Dataset Comparison, and Dynamic FHIRPath Rules

**Feature Branch**: `008-advanced-extensibility-diffing`

**Created**: 2026-10-03

**Status**: Draft

**Input**: User description: "Phase 8: Advanced Extensibility, Dataset Comparison (fhir-lint compare), and Dynamic YAML FHIRPath Rules based on ADR-008 and PRODUCT_SPEC.md"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Dataset Comparison & Quality Regression Diffing (Priority: P1)

As a healthcare data engineer or analytics developer, I want to compare two versions of a FHIR dataset (a baseline dataset and a target dataset) using `fhir-lint compare <baseline> <target>`, so that I can immediately detect whether ETL pipeline changes or synthetic data updates introduced new defects, regressed overall quality scores, or resolved existing data errors.

**Why this priority**: In healthcare data pipelines, data evolves constantly across exports. Engineers need an automated way to verify that a new pipeline version did not degrade data quality before loading data into production data lakes or EHRs. This delivers the core value of Phase 8.

**Independent Test**: Can be tested independently by running `fhir-lint compare baseline.json target.json` on synthetic datasets with known differences (e.g. baseline has 1 error, target has 3 errors and 1 fixed error), and verifying that the output accurately identifies:
1. Overall score delta ($\Delta \text{score}$) and category score shifts.
2. Newly introduced regressions (defects in target not present in baseline).
3. Resolved defects (defects in baseline no longer present in target).
4. Unchanged / persistent defects.
5. Resource count deltas across all resource types.

**Acceptance Scenarios**:

1. **Given** two valid FHIR dataset files (`baseline.json` and `target.json`), **When** the user executes `fhir-lint compare baseline.json target.json`, **Then** the application performs in-memory analysis of both datasets, compares findings by resource identity and defect type, and renders a side-by-side or differential summary table to stdout with exit code `0`.
2. **Given** comparison between a baseline dataset and a degraded target dataset, **When** executed with `--fail-on-regression`, **Then** the application detects that new error-level defects were introduced (or score decreased), emits a regression alert, and exits with code `1`.
3. **Given** comparison executed with a maximum score drop threshold (e.g., `--max-score-drop 5`), **When** the target score drops by more than the allowed delta (e.g., drops from 90 to 82, $\Delta = -8$), **Then** the command exits with code `1`.
4. **Given** comparison executed with `--format json`, **When** the command completes, **Then** a structured comparison JSON report is emitted containing `baseline`, `target`, `scoreDelta`, `newIssues`, `resolvedIssues`, and `resourceDeltas`.

---

### User Story 2 - Dynamic User-Defined Rules via YAML & FHIRPath (Priority: P2)

As a clinical integration specialist or hospital informatics team lead, I want to define custom data quality rules in a human-readable YAML file using standard FHIRPath invariants, and pass them via `--rules <file>`, so that my organization can enforce local clinical guidelines, custom business rules, or institutional constraints without modifying or recompiling FHIRLint source code.

**Why this priority**: Healthcare organizations each have unique data constraints (e.g., "Patients over 65 must have a Medicare identifier", "Observations with code X must include component Y"). Hardcoding every organizational variant into the Java engine is impossible; dynamic FHIRPath rule definitions provide unlimited extensibility while maintaining zero infrastructure overhead.

**Independent Test**: Can be tested independently by creating a YAML rule file containing FHIRPath expressions (e.g., `Observation.status = 'final'`), executing `fhir-lint validate bundle.json --rules custom-rules.yaml`, and verifying that resources violating the FHIRPath expression are reported as lint issues with the custom rule ID, severity, message, and target FHIRPath location.

**Acceptance Scenarios**:

1. **Given** a valid custom rules YAML file containing rule definitions with `id`, `name`, `category`, `severity`, `fhirpath`, and `message`, **When** executed with `fhir-lint validate bundle.json --rules custom-rules.yaml`, **Then** the engine parses the YAML rules, evaluates the FHIRPath expressions against matching resources in the bundle using HAPI FHIR's FHIRPath engine, and includes any custom rule violations in the final report and score calculation.
2. **Given** an invalid rules YAML file (e.g., malformed YAML syntax, missing mandatory fields, or invalid FHIRPath syntax), **When** executed, **Then** the application terminates with an actionable error message indicating the exact YAML line or FHIRPath syntax error and exits with code `2`.
3. **Given** a custom rule defined with `category: completeness` and `severity: error`, **When** evaluated against a dataset with violations, **Then** the violations decrement the completeness category score according to the standard deterministic scoring formula.
4. **Given** both default rules and custom rules enabled, **When** linting executes, **Then** both built-in core rules and custom FHIRPath rules are executed in a single pass over the resource graph.

---

### User Story 3 - Dataset Comparison Directory & Multi-File Support (Priority: P3)

As a data pipeline engineer, I want to compare directories of FHIR bundles or NDJSON files (e.g., `fhir-lint compare ./exports/2026-09/ ./exports/2026-10/`), so that I can compare entire monthly or daily batch exports comprising multiple files.

**Why this priority**: Production healthcare data often resides in partitioned directories of NDJSON or multiple JSON bundle files. Directory-level diffing allows complete batch verification without manual concatenation.

**Independent Test**: Can be tested by running `fhir-lint compare dirA/ dirB/` with multiple files in each directory, ensuring all resources are aggregated, indexed, and compared accurately.

**Acceptance Scenarios**:

1. **Given** two directories containing multiple FHIR files, **When** executed with `fhir-lint compare dirA/ dirB/`, **Then** resources in `dirA` are unrolled into the baseline inventory and resources in `dirB` into the target inventory, followed by full regression analysis.
2. **Given** unmatched directories or missing files, **When** executed, **Then** clear errors are reported and the tool exits with code `2`.

---

## Edge Cases

- **Identical Datasets**: When comparing identical datasets, $\Delta \text{score} = 0$, 0 new issues, 0 resolved issues. Exit code is `0`.
- **Empty Datasets**: Comparing an empty file against a populated file cleanly reports resource delta differences without NullPointerException.
- **Resource ID Collisions / Non-matching IDs**: Resources that exist in baseline but not in target are tracked as removed resources; issues on removed resources are marked as resolved for the target dataset.
- **Malformed Custom Rules**: Syntactically invalid FHIRPath expressions in `--rules` must fail fast during CLI pre-flight configuration before ingesting any clinical data.
- **Large Dataset Memory Constraints**: Diffing two 20,000-resource datasets must remain memory-efficient and complete within JVM default heap limits without memory leaks.
- **Custom Rules with Stdin Streaming**: `--rules custom-rules.yaml` must work identically when data is piped via standard input (`cat bundle.json | fhir-lint validate - --rules custom-rules.yaml`).

---

## Requirements *(mandatory)*

### Functional Requirements

#### Dataset Comparison (`fhir-lint compare`)
- **FR-001**: The CLI MUST provide a top-level command `fhir-lint compare <baseline> <target>` accepting two files or directories.
- **FR-002**: The comparison engine MUST compute:
  - Total resource count deltas per resource type and overall.
  - Overall quality score delta ($\Delta \text{score} = \text{score}_{\text{target}} - \text{score}_{\text{baseline}}$).
  - Category score deltas for all 6 quality categories.
  - List of newly introduced issues (issues in target not present in baseline matching on ruleId, resourceType, and resourceId).
  - List of resolved issues (issues in baseline no longer present in target).
  - List of persistent / unchanged issues.
- **FR-003**: The CLI MUST support `--fail-on-regression` flag, exiting with code `1` if any new error-level defect is detected or overall score dropped.
- **FR-004**: The CLI MUST support `--max-score-drop <N>` flag, exiting with code `1` if $(\text{score}_{\text{baseline}} - \text{score}_{\text{target}}) > N$.
- **FR-005**: The comparison command MUST support `--format table` (default ANSI colorized diff table) and `--format json` (machine-readable structured diff).
- **FR-006**: The comparison command MUST support `--profile <name>` (e.g. `BASE_R4` or `US_CORE`) applied consistently to both datasets.

#### Custom YAML FHIRPath Rules (`--rules <file>`)
- **FR-007**: The CLI `validate` and `compare` commands MUST accept `--rules <file>` pointing to a YAML file containing custom rule definitions.
- **FR-008**: The custom rule schema MUST support:
  - `id` (String, unique identifier, e.g. `org-vital-bp-components`)
  - `name` (String, human-readable title)
  - `description` (Optional String, rationale/documentation)
  - `resourceType` (Optional String, target resource type e.g. `Observation`, or all resources if omitted)
  - `category` (Enum matching standard categories: `structural`, `profileConformance`, `referentialIntegrity`, `consistency`, `terminology`, `completeness`)
  - `severity` (Enum: `error`, `warning`, `info`)
  - `fhirpath` (String, invariant boolean expression that MUST evaluate to true for the resource to be valid)
  - `message` (String, error description emitted when expression evaluates to false or empty)
- **FR-009**: The engine MUST evaluate custom FHIRPath invariants using HAPI FHIR's `FhirPathR4` evaluator in-memory.
- **FR-010**: Violations of custom rules MUST participate directly in category defect penalties and deterministic score reduction.
- **FR-011**: Malformed YAML or invalid FHIRPath syntax MUST fail fast with exit code `2` during argument parsing.

---

## Non-Functional Requirements & Constraints

- **NFR-001 (Zero Telemetry & In-Memory Privacy)**: All comparison and FHIRPath evaluation must execute purely in-memory with zero disk persistence of clinical data and zero network egress, adhering to Constitution v2.0.0.
- **NFR-002 (Performance)**: Dynamic FHIRPath evaluation of 1,000 resources against 10 custom rules must complete in under 1 second.
- **NFR-003 (Deterministic Output)**: Repeated runs against the same baseline and target datasets must produce bit-for-bit identical diff reports.
- **NFR-004 (GraalVM Native Compatibility)**: Custom rule YAML parsing (via Jackson YAML) and dynamic FHIRPath evaluation must be configured with GraalVM reachability metadata to ensure native binaries continue compiling and functioning cleanly.

---

## Success Criteria

1. `fhir-lint compare baseline.json target.json` outputs a clean, colorized ANSI diff table showing score deltas, new defects, and resolved defects.
2. `--fail-on-regression` exits `1` when regressions exist and `0` when data is equal or improved.
3. Users can define custom YAML rules with FHIRPath expressions and enforce them via `fhir-lint validate bundle.json --rules custom-rules.yaml`.
4. 100% of existing unit tests continue to pass with sub-3 second total test execution.
