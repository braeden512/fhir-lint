# Feature Specification: Phase 8 — Advanced Extensibility, Dataset Comparison, and Dynamic FHIRPath Rules

**Feature Branch**: `008-advanced-extensibility-diffing`

**Created**: 2026-10-03

**Status**: Draft (Updated with Reviewer Recommendations)

**Input**: User description: "Phase 8: Advanced Extensibility, Dataset Comparison (fhir-lint compare), and Dynamic YAML FHIRPath Rules based on ADR-008 and PRODUCT_SPEC.md"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Dataset Comparison & Quality Regression Diffing (Priority: P1)

As a healthcare data engineer or analytics developer, I want to compare two versions of a FHIR dataset (a baseline dataset and a target dataset) using `fhir-lint compare <baseline> <target>` or programmatically via `DatasetComparator.compare(baselineReport, targetReport)`, so that I can immediately detect whether ETL pipeline changes or synthetic data updates introduced new defects, regressed overall quality scores, or resolved existing data errors.

**Why this priority**: In healthcare data pipelines, data evolves constantly across exports. Engineers need an automated way to verify that a new pipeline version did not degrade data quality before loading data into production data lakes or EHRs. This delivers the core value of Phase 8.

**Independent Test**: Can be tested independently by running `fhir-lint compare baseline.json target.json` on synthetic datasets with known differences (e.g. baseline has 1 error, target has 3 errors and 1 fixed error), and verifying that the output accurately identifies:
1. Overall score delta ($\Delta \text{score}$) and category score shifts.
2. Newly introduced regressions (defects in target not present in baseline).
3. Resolved defects (defects in baseline no longer present in target).
4. Unchanged / persistent defects.
5. Resource count deltas across all resource types.

**Acceptance Scenarios**:

1. **Given** two valid FHIR dataset files (`baseline.json` and `target.json`), **When** the user executes `fhir-lint compare baseline.json target.json`, **Then** the application performs sequential in-memory analysis of both datasets, compares findings by canonical issue identity key, and renders a side-by-side differential summary table to stdout with exit code `0`.
2. **Given** comparison between a baseline dataset and a degraded target dataset, **When** executed with `--fail-on-regression`, **Then** the application detects that new error-level defects were introduced (or score decreased), emits a regression alert, and exits with code `1`.
3. **Given** comparison executed with a maximum score drop threshold (e.g., `--max-score-drop 5`), **When** the target score drops by more than the allowed delta (e.g., drops from 90 to 82, $\Delta = -8$), **Then** the command exits with code `1`.
4. **Given** comparison executed with `--format json`, **When** the command completes, **Then** a structured comparison JSON report is emitted containing `baseline`, `target`, `scoreDelta`, `newIssues`, `resolvedIssues`, and `resourceDeltas`.
5. **Given** a developer embedding the Java library, **When** invoking `DatasetComparator.compare(baselineReport, targetReport)`, **Then** a pure in-memory `ComparisonReport` model is returned without invoking CLI subshells.

---

### User Story 2 - Dynamic User-Defined Rules via YAML & FHIRPath (Priority: P2)

As a clinical integration specialist or hospital informatics team lead, I want to define custom data quality rules in a human-readable YAML file using standard FHIRPath invariants, and pass them via `--rules <file>`, so that my organization can enforce local clinical guidelines, custom business rules, or institutional constraints without modifying or recompiling FHIRLint source code.

**Why this priority**: Healthcare organizations each have unique data constraints (e.g., "Patients over 65 must have a Medicare identifier", "Observations with code X must include component Y"). Hardcoding every organizational variant into the Java engine is impossible; dynamic FHIRPath rule definitions provide unlimited extensibility while maintaining zero infrastructure overhead.

**Independent Test**: Can be tested independently by creating a YAML rule file containing FHIRPath expressions (e.g., `status = 'final'`), executing `fhir-lint validate bundle.json --rules custom-rules.yaml`, and verifying that resources violating the FHIRPath expression are reported as lint issues with the custom rule ID, severity, message, suggestion, and target FHIRPath location.

**Acceptance Scenarios**:

1. **Given** a valid custom rules YAML file containing rule definitions with `id`, `name`, `category`, `severity`, `fhirpath`, `message`, and optional `suggestion`, **When** executed with `fhir-lint validate bundle.json --rules custom-rules.yaml`, **Then** the engine parses the YAML rules, evaluates the pre-compiled FHIRPath expressions against matching resources in the bundle using HAPI FHIR's `FhirPathR4`, and includes any custom rule violations in the final report and score calculation.
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

## Edge Cases & Execution Guarantees

- **Canonical Issue Identity Tuple**: When diffing baseline issues against target issues, an issue is uniquely identified by the tuple:
  $$\text{IssueKey} = (\text{ruleId}, \text{resourceType}, \text{resourceId} \neq \text{null} ? \text{resourceId} : \text{"__NO_ID__"}, \text{path} \neq \text{null} ? \text{path} : \text{"__ROOT__"})$$
  If a resource lacks an ID, matching is scoped to `(ruleId, resourceType, path, messageHash)`. This guarantees deterministic resolution even when multiple issues share a rule or resources lack logical IDs.
- **Sequential Evaluation for Memory Safety**: To avoid memory pressure on large datasets (e.g., two 20,000-resource bundles), datasets MUST be evaluated sequentially:
  1. Parse and lint `baseline` $\to$ generate in-memory `LintReport` $\to$ release baseline HAPI AST for garbage collection.
  2. Parse and lint `target` $\to$ generate in-memory `LintReport` $\to$ release target HAPI AST.
  3. Execute `DatasetComparator.compare(baselineReport, targetReport)` to produce `ComparisonReport`.
- **Exit Code Precedence Contract**:
  - `Exit 2`: Parameter error, missing/unreadable file, malformed FHIR syntax, or invalid YAML rules.
  - `Exit 1`: Quality regression detected by ANY active gate:
    - Target score drops by more than `--max-score-drop <N>` OR
    - `--fail-on-regression` is enabled and any new error-level defect is detected or score drops.
  - `Exit 0`: All active comparison gates pass (datasets equal, score improved, or regressions within configured thresholds).
- **Identical Datasets**: When comparing identical datasets, $\Delta \text{score} = 0$, 0 new issues, 0 resolved issues. Exit code is `0`.
- **Empty Datasets**: Comparing an empty file against a populated file cleanly reports resource delta differences without NullPointerException.
- **Custom Rules with Stdin Streaming**: `--rules custom-rules.yaml` must work identically when data is piped via standard input (`cat bundle.json | fhir-lint validate - --rules custom-rules.yaml`).

---

## Requirements *(mandatory)*

### Functional Requirements

#### Core Engine API (`org.fhirlint.core`)
- **FR-001a**: The core engine MUST provide a framework-agnostic `DatasetComparator` class with method:
  `ComparisonReport compare(LintReport baseline, LintReport target)`.
- **FR-001b**: The core engine MUST provide an immutable `ComparisonReport` model containing:
  - `LintReport baseline` and `LintReport target`.
  - `int scoreDelta` ($\text{targetScore} - \text{baselineScore}$).
  - `Map<QualityCategory, Integer> categoryDeltas`.
  - `Map<String, Integer> resourceCountDeltas` (per resource type and total).
  - `List<QualityIssue> newIssues` (issues in target not in baseline using canonical identity key).
  - `List<QualityIssue> resolvedIssues` (issues in baseline not in target).
  - `List<QualityIssue> persistentIssues` (issues in both baseline and target).
  - `boolean hasRegressions(boolean failOnRegression, Integer maxScoreDrop)`.

#### Dataset Comparison CLI (`fhir-lint compare`)
- **FR-001**: The CLI MUST provide a top-level command `fhir-lint compare <baseline> <target>` accepting two files or directories.
- **FR-002**: The comparison command MUST delegate directly to `DatasetComparator` and compute all diff metrics defined in FR-001b.
- **FR-003**: The CLI MUST support `--fail-on-regression` flag, exiting with code `1` if any new error-level defect is detected or overall score dropped.
- **FR-004**: The CLI MUST support `--max-score-drop <N>` flag, exiting with code `1` if $(\text{score}_{\text{baseline}} - \text{score}_{\text{target}}) > N$.
- **FR-005**: The comparison command MUST support output format options:
  - `--format table` (default ANSI colorized diff table).
  - `--format json` (machine-readable structured JSON diff matching `ComparisonReport`).
- **FR-006**: The comparison command MUST support `-o, --output <path>` to write the comparison output directly to a file.
- **FR-007**: The comparison command MUST support `--profile <name>` (e.g. `BASE_R4` or `US_CORE`) applied consistently to both datasets.

#### Custom YAML FHIRPath Rules (`--rules <files...>`)
- **FR-008**: The CLI `validate` and `compare` commands MUST accept `--rules <path>` (accepting a file, comma-separated list of files, or directory of `.yaml` / `.yml` files).
- **FR-009**: The custom rule schema MUST support:
  - `id` (String, unique identifier, e.g. `org-vital-bp-components`)
  - `name` (String, human-readable title)
  - `description` (Optional String, rationale/documentation)
  - `resourceType` (Optional String, target resource type e.g. `Observation`, or evaluated on all resources if omitted)
  - `category` (Enum matching standard categories: `structural`, `profileConformance`, `referentialIntegrity`, `consistency`, `terminology`, `completeness`)
  - `severity` (Enum: `error`, `warning`, `info`)
  - `fhirpath` (String, invariant boolean expression evaluated in the context of the resource instance that MUST evaluate to true for the resource to be valid)
  - `message` (String, error description emitted when expression evaluates to false or empty)
  - `suggestion` (Optional String, actionable developer fix hint)
- **FR-010**: The engine MUST parse custom rule FHIRPath expressions at initialization time into pre-compiled ASTs, and evaluate them in-memory against candidate resources using HAPI FHIR's `FhirPathR4`.
- **FR-011**: Violations of custom rules MUST be emitted as standard `QualityIssue` instances and participate directly in category defect penalties and deterministic score reduction.
- **FR-012**: Malformed YAML or invalid FHIRPath syntax MUST fail fast with exit code `2` during argument parsing and pre-flight validation.

---

## Non-Functional Requirements & Constraints

- **NFR-001 (Zero Telemetry & In-Memory Privacy)**: All comparison and FHIRPath evaluation must execute purely in-memory with zero disk persistence of clinical data and zero network egress, adhering to Constitution v2.0.0.
- **NFR-002 (Performance)**: Dynamic FHIRPath evaluation of 1,000 resources against 10 custom rules must complete in under 1 second.
- **NFR-003 (Deterministic Output)**: Repeated runs against the same baseline and target datasets must produce bit-for-bit identical diff reports.
- **NFR-004 (Dependency Justification & GraalVM Compatibility)**:
  - Adding `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml` is justified under Constitution Principle VI as YAML is the universal industry-standard for human-authored linter rules (like ESLint, Ruff, Spectral).
  - Custom rule POJOs / Records and Jackson YAML deserializers must be registered in GraalVM reachability metadata to ensure native compilation (`./gradlew nativeCompile`) succeeds.

---

## Success Criteria

1. `fhir-lint compare baseline.json target.json` outputs a clean, colorized ANSI diff table showing score deltas, new defects, and resolved defects.
2. Core developers can invoke `DatasetComparator.compare(reportA, reportB)` directly in Java code without CLI dependencies.
3. `--fail-on-regression` exits `1` when regressions exist and `0` when data is equal or improved, obeying explicit exit code precedence.
4. Users can define custom YAML rules with FHIRPath expressions and enforce them via `fhir-lint validate bundle.json --rules custom-rules.yaml`.
5. 100% of existing unit tests continue to pass with sub-3 second total test execution.
