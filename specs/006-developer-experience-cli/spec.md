# Feature Specification: Phase 6 — Developer Experience and Standalone CLI Design

**Feature Branch**: `006-developer-experience-cli`

**Created**: 2026-09-27

**Status**: Draft

**Input**: User description: "Create the spec for phase 6. Use the documentation in @docs/ , specifically the ADR for @docs/adr/ADR-006-phase-6-developer-experience-and-api-design.md , as well as the @PRODUCT_SPEC.md to guide your thinking."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - POSIX-Compliant CLI Execution & Flexible Input Sinks (Priority: P1)

As a health-tech software engineer or DevOps engineer, I want to execute FHIRLint from the command line against local FHIR JSON files, directories of files, or standard input (`stdin`), specifying target validation profiles and destination output files, so that I can seamlessly incorporate healthcare data linting into my local terminal workflows, pre-commit hooks, and automated shell scripts.

**Why this priority**: Command-line execution is the primary touchpoint for developers and CI pipelines. Without standard input processing, flexible source handling, and POSIX-compliant exit conventions, developers cannot integrate the linter into existing developer toolchains or UNIX pipelines.

**Independent Test**: Can be tested independently by invoking the CLI entry point with individual JSON files, directory paths containing multiple JSON resources, or piped stdin (`cat bundle.json | fhir-lint validate -`), verifying that the dataset is parsed and evaluated and returns appropriate POSIX exit codes.

**Acceptance Scenarios**:

1. **Given** a valid local FHIR JSON file, **When** the developer executes `fhir-lint validate <file-path>`, **Then** the system analyzes the file using the default profile (`US_CORE`) and outputs the analysis result.
2. **Given** a directory containing multiple `.json` files, **When** the developer executes `fhir-lint validate <directory-path>`, **Then** the system recursively discovers all `.json` files in the directory tree, analyzes all discovered resources as an aggregated dataset, and outputs the unified analysis result.
3. **Given** a FHIR JSON stream piped into standard input, **When** the developer executes `fhir-lint validate -`, **Then** the system reads from standard input until EOF, processes the input stream, and renders the analysis result.
4. **Given** an explicit target profile flag (e.g., `-p BASE_R4` or `--profile US_CORE`), **When** the command is executed, **Then** the system validates data against the specified conformance profile.
5. **Given** an output file destination specified via `-o <path>` or `--output <path>`, **When** the command is executed, **Then** the system writes the rendered report directly to the specified file path and informs the user on standard output without polluting the generated report.
6. **Given** a request for help or version information (`-h`, `--help`, `-V`, `--version`), **When** the developer executes the command, **Then** the system prints standard usage options or version details and exits with code `0`.

---

### User Story 2 - Human-Centric ANSI Colorized Console Reporting (Priority: P2)

As a developer debugging data quality defects in my terminal, I want FHIRLint to render a formatted, colorized ANSI console report displaying a summary scorecard, engineering grade tier, category progress bars, and prioritized diagnostics with precise locations and actionable remediation advice, so that I can rapidly understand dataset health and fix root-cause defects.

**Why this priority**: High-density textual data must be immediately understandable at a glance. Clear visual hierarchy, color-coded severity badges, and progress indicators reduce cognitive load and accelerate debugging of complex healthcare payloads.

**Independent Test**: Can be tested independently by executing the CLI with `--format table` (the default) on clean and defective datasets, verifying that the output contains the scorecard header, colorized grade badges, resource inventory distribution, category score bars, and actionable findings with FHIRPath locations and suggestions.

**Acceptance Scenarios**:

1. **Given** a completed linting pass rendered in table format, **When** the terminal output is generated, **Then** the report includes:
   - A distinct header banner identifying FHIRLint
   - Overall Quality Score badge (0–100) and Engineering Grade tier (`EXCELLENT`, `ACCEPTABLE`, `DEGRADED`, `CRITICAL`) colorized appropriately (green, blue, yellow, red)
   - Target profile applied
   - Execution metrics: total resource count, error count, warning count, and processing duration in milliseconds.
2. **Given** an analyzed dataset with multiple resource types, **When** the console table is rendered, **Then** the report presents a resource distribution table summarizing counts by FHIR resource type.
3. **Given** calculated category scores across the six dimensions (`structural`, `profileConformance`, `referentialIntegrity`, `consistency`, `terminology`, `completeness`), **When** the console table is rendered, **Then** each category is displayed with an ANSI visual progress bar and percentage score.
4. **Given** detected diagnostic issues, **When** the findings section is rendered, **Then** each issue displays:
   - Severity tag (`[ERROR]`, `[WARNING]`, `[INFO]`) with corresponding ANSI coloring
   - Rule identifier (e.g., `REF-001`, `CONS-001`)
   - Human-readable issue description
   - Resource type and ID (or path) where the defect occurs
   - Concrete remediation suggestion explaining how to fix the issue.
5. **Given** a dataset with numerous issues, **When** executed in default mode, **Then** the console table displays the top 10 prioritized issues to avoid terminal flooding; **When** executed with `--verbose` or `-v`, **Then** the complete list of all issues is rendered.
6. **Given** any generated console table report, **When** rendered, **Then** the mandatory non-clinical engineering indicator disclaimer mandated by Constitution Principle IV is clearly displayed at the bottom of the scorecard.

---

### User Story 3 - Machine-Readable CI/CD Integrations: Structured JSON & SARIF 2.1.0 (Priority: P3)

As a DevOps engineer configuring automated CI/CD pipelines and GitHub Actions workflows, I want FHIRLint to output structured, machine-readable JSON and standardized OASIS SARIF 2.1.0 reports, so that my automated workflows can programmatically parse quality metrics or annotate pull request diffs directly in GitHub Code Scanning.

**Why this priority**: CI/CD automation requires reliable, standardized machine-readable outputs. JSON allows scripts and downstream services (e.g., `jq`, dashboard aggregators) to ingest results, while SARIF 2.1.0 enables native code scanning integrations that highlight defects directly on pull request lines.

**Independent Test**: Can be tested independently by executing the CLI with `--format json` and `--format sarif` against a defective bundle and validating that the resulting outputs conform to the `LintReport` JSON schema and the official OASIS SARIF 2.1.0 schema, respectively.

**Acceptance Scenarios**:

1. **Given** a completed linting pass, **When** executed with `--format json` or `-f json`, **Then** the system outputs a valid JSON document containing the target profile, ingestion inventory, issue list with remediation suggestions, category and overall quality scores with grade, execution duration, and the non-clinical engineering disclaimer.
2. **Given** a completed linting pass, **When** executed with `--format sarif` or `-f sarif`, **Then** the system outputs a valid SARIF 2.1.0 JSON document conforming to the OASIS standard schema.
3. **Given** a SARIF 2.1.0 output, **When** inspected for GitHub Code Scanning compatibility, **Then** the document contains:
   - `$schema` referencing `sarif-schema-2.1.0.json`
   - `version` set to `"2.1.0"`
   - Tool driver definition with name `"FHIRLint"`, version, and information URI
   - Rules array declaring rule metadata (rule ID, name, short description, full description) for all active rules
   - Results array mapping each issue to rule ID, severity level (`error`, `warning`, `note`), message with remediation suggestion, and physical location referencing the target file and path
   - The mandatory non-clinical engineering indicator disclaimer placed within the run property bag (`runs[0].properties.disclaimer`) to maintain strict OASIS schema validity.
4. **Given** piped output to a downstream tool (such as `jq`), **When** `--format json` or `--format sarif` is specified without `-o`, **Then** the output emitted to standard output contains strictly the raw JSON document without extraneous terminal styling or log banners.

---

### User Story 4 - Automated CI Quality Gates & Standard Exit Codes (Priority: P4)

As a continuous integration engineer, I want the CLI to enforce configurable quality policies via `--min-score` and `--fail-on` options and return predictable UNIX exit codes, so that pull requests and deployment pipelines automatically fail when data quality degrades below defined organizational thresholds.

**Why this priority**: Linters in CI/CD are only effective if they can break the build when defects occur. Predictable POSIX exit codes (`0`, `1`, `2`) enable deterministic pipeline gate enforcement.

**Independent Test**: Can be tested independently by running the CLI with varied combinations of `--min-score` and `--fail-on` flags on clean, degraded, and invalid inputs, verifying that exit codes adhere strictly to the `0` (pass), `1` (quality gate breach), and `2` (syntax/invocation error) contract.

**Acceptance Scenarios**:

1. **Given** a dataset where the quality score meets or exceeds `--min-score` and no issues violate `--fail-on`, **When** the CLI completes execution, **Then** it exits with code `0`.
2. **Given** a dataset where the overall score is below the configured `--min-score` threshold, **When** the CLI completes execution, **Then** it prints a quality gate breach explanation to standard error and exits with code `1`.
3. **Given** a dataset containing an issue at or above the configured `--fail-on` threshold (e.g., `--fail-on error` with 1 error present), **When** the CLI completes execution, **Then** it prints a quality gate breach explanation to standard error and exits with code `1`.
4. **Given** multiple quality gate breaches (e.g., score below `--min-score` AND presence of unpermitted severities), **When** the CLI evaluates the gate, **Then** it reports all detected breaches to standard error and exits with code `1`.
5. **Given** an invalid command invocation (e.g., non-existent file, empty directory, invalid JSON syntax, unknown profile name, unknown format, negative min-score), **When** the CLI executes, **Then** it prints an actionable error message to standard error and exits with code `2`.

---

### User Story 5 - Embeddable Fluent Java Library API (Priority: P5)

As a Java application engineer developing an ETL pipeline, Kafka ingestion consumer, or Spring Batch job, I want to use `FhirLinter` as an in-memory Java library with a fluent API, so that I can lint FHIR payloads in-process without invoking external processes or provisioning external databases.

**Why this priority**: Healthcare ingestion services often run within JVM pipelines. An embeddable Java library API (Constitution Principle VII and Section 8.2 of PRODUCT_SPEC.md) enables high-throughput in-memory linting with zero subprocess overhead.

**Independent Test**: Can be tested independently by instantiating `FhirLinter.create()` in a unit test, configuring profiles and custom engine components, executing `.lint(...)` across `File`, `InputStream`, `String`, and `List<File>`, and asserting against the resulting `LintReport` object.

**Acceptance Scenarios**:

1. **Given** the `FhirLinter` fluent entry point, **When** initialized with `FhirLinter.create().withProfile(ValidationProfile.US_CORE)`, **Then** a configured, ready-to-use linter instance is created.
2. **Given** a `FhirLinter` instance, **When** passed a `File`, `String`, or `InputStream`, **Then** it returns an immutable `LintReport` containing inventory metrics, issues, quality score breakdown, and execution duration.
3. **Given** a collection of `File` objects, **When** passed to `.lint(List<File>)`, **Then** it aggregates resources and executes holistic referential integrity and quality rule analysis across all files.
4. **Given** an in-process caller, **When** inspecting the resulting `LintReport`, **Then** the caller can directly evaluate quality gates programmatically via `report.evaluateGate(QualityGateConfig.of(minScore, failOnSeverity))`.
5. **Given** library execution, **When** processing any dataset, **Then** the engine runs strictly in-memory without persistent disk caching or external network calls (Constitution Principles II and V).

---

### Edge Cases

- **Non-Existent Input File/Directory**: When a provided path does not exist, the CLI prints `Error: File or directory not found: <path>` to `stderr` and exits immediately with code `2`.
- **Empty Directory or Non-JSON Directory**: When a target directory contains zero `.json` files, the CLI reports `Error: Directory contains no .json files: <path>` to `stderr` and exits with code `2`.
- **Empty Standard Input (0-byte Stream)**: When standard input (`-`) is connected to an empty stream (0 bytes), the system triggers boundary verification failure, outputs `Error: Boundary verification failed: Input stream is empty` to `stderr`, and exits with code `2`.
- **Malformed FHIR JSON / Syntax Failure**: When input is not valid JSON or fails fundamental FHIR R4 syntax parsing, the CLI outputs `Error: Boundary verification failed: <message>` to `stderr` and exits with code `2`.
- **Invalid Profile or Format Flags**: When an unsupported value is supplied to `--profile` (e.g. `UNKNOWN`) or `--format` (e.g. `xml`), the CLI reports the error to `stderr` with valid alternatives and exits with code `2`.
- **Invalid Minimum Score (< 0 or > 100)**: When `--min-score` is outside the permissible range [0, 100] (negative or greater than 100), the CLI reports `Error: --min-score must be between 0 and 100` to `stderr` and exits with code `2`.
- **Unwritable Output File Destination**: When the path provided to `-o` / `--output` cannot be opened or written to (e.g. permission denied or invalid directory), the CLI reports the I/O error to `stderr` and exits with code `2`.
- **Piped Stdin with Non-Terminal stdout**: When output is redirected or piped to another process (e.g. `cat file.json | fhir-lint validate - -f json | jq .`), the stdout stream contains strictly valid machine-readable JSON without ANSI escape sequences or diagnostic banner text.
- **Large Dataset Issue Truncation**: When a dataset yields more than 10 issues, the console table defaults to rendering the first 10 findings and appends a truncation notice instructing the user to supply `--verbose` to view all findings.
- **Zero Issues in Clean Dataset**: When a clean dataset has 0 issues, the console table renders an explicit success indication with 100/100 EXCELLENT score, and the findings section confirms zero defects detected.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a command-line interface executable via `fhir-lint validate <file|directory|-> [options]`.
- **FR-002**: System MUST accept three types of input sources: a single local FHIR JSON file, a directory containing `.json` files (recursively scanned), or standard input indicated by `-`.
- **FR-003**: System MUST support the `-p, --profile <NAME>` option accepting `US_CORE` (default) and `BASE_R4` (case-insensitive).
- **FR-004**: System MUST support the `-f, --format <FORMAT>` option accepting `table` (default ANSI console table), `json`, and `sarif` (case-insensitive).
- **FR-005**: System MUST support the `--min-score <0-100>` option defining the minimum passing quality score, rejecting values outside [0, 100] with exit code `2`.
- **FR-006**: System MUST support the `--fail-on <SEVERITY>` option accepting `error`, `warning`, `info`, and `none` (case-insensitive, defaulting to `error`).
- **FR-007**: System MUST support the `-v, --verbose` flag to display all issues in terminal output instead of truncating at 10 items.
- **FR-008**: System MUST support the `-o, --output <PATH>` option to write rendered output to a designated file path instead of standard output.
- **FR-009**: System MUST adhere to standard POSIX exit codes:
  - Exit `0`: Validation completed successfully and quality gate passed.
  - Exit `1`: Quality gate failure (overall score below `--min-score` or detected issues violating `--fail-on`).
  - Exit `2`: Syntax, argument, or input invocation error (file not found, invalid JSON, invalid CLI option values).
- **FR-010**: System MUST render a colorized ANSI terminal table report when `--format table` is selected, containing:
  - Header title banner
  - Overall quality score, grade tier, and status description
  - Target profile name
  - Summary metrics (total resources, error count, warning count, duration)
  - Resource type distribution breakdown
  - Category breakdown with visual ANSI progress bars and percentage scores
  - Prioritized findings list displaying severity, rule ID, message, path, and actionable remediation suggestions
  - Mandatory non-clinical engineering indicator disclaimer.
- **FR-011**: System MUST render a clean, structured JSON report when `--format json` is selected, serializing the full `LintReport` model including inventory, issues, scores, duration, and disclaimer.
- **FR-012**: System MUST render a valid SARIF 2.1.0 JSON report conforming to the OASIS standard schema when `--format sarif` is selected, serializing tool driver metadata, rule definitions with descriptions, result locations with file URI and path/region, and the mandatory non-clinical disclaimer placed within the SARIF run-level property bag (`runs[0].properties.disclaimer`) to preserve strict OASIS schema validity.
- **FR-013**: System MUST output only the raw formatted data (JSON or SARIF) to `stdout` when machine-readable formats are selected, writing quality gate breach summaries to `stderr`.
- **FR-014**: System MUST evaluate quality gate criteria without early termination, reporting all applicable breach reasons to `stderr` when a quality gate fails.
- **FR-015**: System MUST provide an embeddable, framework-agnostic fluent Java library API supporting in-memory linting of files, strings, input streams, and multi-file collections.
- **FR-016**: System MUST ensure the embeddable library API returns an immutable, strongly-typed report model providing programmatic access to inventory, issues, scores, and quality gate evaluation.
- **FR-017**: System MUST operate with a strict zero-retention posture: all analysis and rendering data MUST be processed transiently in memory without writing to persistent databases, log sinks, or remote network endpoints.
- **FR-018**: System MUST complete CLI argument parsing, input processing, and output rendering in under 500 milliseconds for standard bundles (< 1,000 resources) excluding JVM startup.

### Key Entities

- **CLI Command Controller**: The application controller managing command-line argument parsing, input source resolution, linter orchestration, output renderer dispatch, and POSIX exit code enforcement.
- **Console Table Renderer**: The terminal output renderer responsible for generating human-readable, colorized ANSI scorecards, progress bars, and prioritized diagnostic listings.
- **JSON Report Renderer**: The machine-readable serialization renderer converting linting results into formatted JSON documents.
- **SARIF Report Renderer**: The OASIS SARIF 2.1.0 serialization renderer transforming linting findings into standardized static analysis results for automated CI/CD code scanning annotations.
- **Lint Report Model**: The comprehensive in-memory domain model encapsulating target profile, ingestion inventory, detected quality issues, deterministic quality score breakdown, and execution duration.
- **Quality Gate Configuration & Verdict**: The policy model and evaluation outcome governing pass/fail decisions based on minimum score thresholds and severity limits.
- **Embeddable Linter Library Interface**: The fluent in-process programmatic interface (`FhirLinter`) providing direct, zero-infrastructure linting capabilities for Java data pipelines.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of CLI executions on clean sample data (e.g. `clean-bundle.json`) complete with exit code `0`.
- **SC-002**: 100% of CLI executions on datasets violating `--min-score` or `--fail-on` exit with code `1` and print breach details to `stderr`.
- **SC-003**: 100% of CLI invocations with invalid syntax, missing files, or illegal flags exit with code `2` and print actionable error diagnostics to `stderr`.
- **SC-004**: 100% of JSON reports emitted via `--format json` parse successfully as valid JSON containing all mandatory `LintReport` fields.
- **SC-005**: 100% of SARIF reports emitted via `--format sarif` validate against the OASIS SARIF 2.1.0 schema and contain tool, rule, and result location metadata compatible with GitHub Code Scanning.
- **SC-006**: Default ANSI table output displays top 10 issues with a clear truncation message, while `-v` / `--verbose` renders 100% of detected issues.
- **SC-007**: 100% of output formats (Table, JSON, SARIF) include or serialize the non-clinical engineering indicator disclaimer mandated by Constitution Principle IV.
- **SC-008**: Piped execution via standard input (`cat bundle.json | fhir-lint validate -`) succeeds with 100% parity to direct file execution.
- **SC-009**: Output file redirection (`-o <path>`) writes the exact rendered report to the destination file with zero pollution of the output file by console logs.
- **SC-010**: Automated CLI and renderer test suite executes and passes in under 3 seconds on standard developer hardware.

---

## Assumptions

- Users have a Java 21+ runtime environment installed when running FHIRLint from the command line.
- The command-line interface is powered by Picocli, providing standard POSIX flag parsing, help options, and ANSI styling.
- Standard input streaming (`-`) consumes the entire input stream into memory before linting, in accordance with the local in-memory dataset architecture.
- When scanning directories, the linter recursively finds all files ending with `.json` (case-insensitive) and processes them as an aggregated dataset.
- In accordance with ADR-006 and PRODUCT_SPEC.md, default values are: `--profile US_CORE`, `--format table`, `--min-score 0`, and `--fail-on error`.
- Terminal color formatting uses standard ANSI escape sequences; terminals without color support will display plain text or ignore formatting without throwing exceptions.
- Output file paths specified via `-o` are created or overwritten if permitted by OS file system permissions.
- In-memory execution preserves strict zero-retention privacy with no external telemetry, database connections, or remote data transmission (Constitution Principles II and V).
