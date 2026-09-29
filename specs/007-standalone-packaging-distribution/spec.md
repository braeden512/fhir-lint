# Feature Specification: Phase 7 — Standalone Packaging, Native Image Compilation, and CI/CD Automation

**Feature Branch**: `007-standalone-packaging-distribution`

**Created**: 2026-09-28

**Status**: Draft

**Input**: User description: "Create the spec for phase 7. Use the documentation in @docs/ , specifically the ADR for @docs/adr/ADR-007-phase-7-production-engineering-and-persistence.md , as well as the @PRODUCT_SPEC.md to guide your thinking."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Universal Self-Contained Executable JAR (Priority: P1)

As a health-tech software engineer or systems integrator operating in a Java environment, I want to download and run FHIRLint as a single standalone executable JAR file (`fhir-lint.jar`), so that I can immediately validate healthcare datasets on any operating system with an installed Java runtime without configuring multi-dependency classpaths, downloading build tools, or compiling source code.

**Why this priority**: A self-contained executable JAR is the universal baseline distribution format for JVM-based tooling. It runs everywhere Java 21+ is available, requires minimal packaging overhead, serves as the foundation for container images, and provides an immediate distribution vehicle for enterprise pipelines.

**Independent Test**: Can be tested independently by building the single executable JAR artifact, transferring it to a clean test environment containing only a standard Java runtime, executing `java -jar fhir-lint.jar validate <input>` across files, directories, and standard input, and verifying that all CLI options, exit codes, and output formats (ANSI table, JSON, SARIF) function identically to the development environment.

**Acceptance Scenarios**:

1. **Given** a built standalone JAR file, **When** executed with `java -jar fhir-lint.jar --help` or `--version`, **Then** the application launches, displays command usage and version details, and exits with code `0`.
2. **Given** a local FHIR bundle file and an executable JAR, **When** executed with `java -jar fhir-lint.jar validate bundle.json`, **Then** the application performs complete schema and data quality analysis and outputs results according to the selected format.
3. **Given** an executable JAR executed with quality threshold flags (e.g., `--min-score 85 --fail-on error`), **When** quality issues are detected that violate the threshold, **Then** the application emits quality breach details to standard error and exits with code `1`.
4. **Given** standard input piped into the executable JAR (e.g., `cat bundle.json | java -jar fhir-lint.jar validate -`), **When** the command executes, **Then** the input stream is read until EOF and evaluated with identical results to direct file invocation.

---

### User Story 2 - Zero-Prerequisite Native Executable Binary (Priority: P2)

As a software engineer or DevOps engineer (particularly on non-Java stacks such as Python, Go, or TypeScript), I want to download a single pre-compiled native machine executable (`fhir-lint`) and execute it directly from my terminal, so that I can lint FHIR data with instant startup (< 50ms) and low memory overhead without installing or managing a Java Virtual Machine on my host machine.

**Why this priority**: Requiring a 200MB+ JDK/JRE is the single largest adoption barrier for non-Java teams and terminal users. An ahead-of-time (AOT) compiled native binary provides instant startup, lightweight memory footprint, and standard command-line ergonomics expected of modern developer linters.

**Independent Test**: Can be tested independently by downloading or compiling the standalone native machine binary for the host operating system, removing Java from the execution path, executing `fhir-lint validate <dataset>` against clean and defective datasets, and verifying that startup duration is sub-50ms with 100% analytical parity and exit code consistency with the JVM runtime.

**Acceptance Scenarios**:

1. **Given** a compiled native binary on a system without a JVM installed, **When** the developer executes `fhir-lint --version` or `fhir-lint --help`, **Then** the command executes in under 50 milliseconds and displays proper usage and version information.
2. **Given** a target FHIR dataset, **When** evaluated using the native binary via `fhir-lint validate dataset.json`, **Then** the native binary executes complete parsing, profile validation, referential graph indexing, and quality rule evaluation with output identical to the JVM execution.
3. **Given** native binary execution against a dataset failing configured quality gates (e.g. `--min-score 90`), **When** the execution completes, **Then** the native binary exits with code `1` and emits gate breach details to standard error.
4. **Given** native binary execution with piped standard input (`cat bundle.json | fhir-lint validate -`), **When** the input is streamed, **Then** the native binary consumes standard input and renders the requested output format.

---

### User Story 3 - Official Cross-Platform GitHub Action for Automated Pull Request Quality Gates (Priority: P3)

As a repository maintainer or CI/CD engineer, I want to add an official FHIRLint GitHub Action (`uses: fhir-lint/action@v1`) to my repository's CI/CD workflows, so that incoming pull requests touching healthcare data or FHIR profiles are automatically validated across any GitHub runner (Ubuntu, macOS, Windows), quality thresholds are enforced, and defect annotations are displayed directly in GitHub pull request reviews and Code Scanning.

**Why this priority**: Healthcare software teams frequently store test fixtures, synthetic clinical bundles, and profile definitions in Git repositories. An official, drop-in GitHub Composite Action automates quality governance across all standard runner platforms, blocks regressions before merging, and provides rich PR annotations with zero custom script maintenance.

**Independent Test**: Can be tested independently by configuring a GitHub Action workflow file referencing the composite action across Ubuntu, macOS, and Windows test runners, providing valid and invalid FHIR bundles, and verifying that the workflow fails or passes based on configured inputs, exports step outputs via deterministic JSON extraction, and uploads SARIF reports to GitHub Code Scanning.

**Acceptance Scenarios**:

1. **Given** a repository workflow on any supported runner OS (`ubuntu-latest`, `macos-latest`, `windows-latest`) with the FHIRLint GitHub Composite Action configured, **When** a pull request contains FHIR JSON files meeting quality thresholds, **Then** the action detects the runner environment, invokes the appropriate binary or universal JAR, completes successfully with status `success`, and exports step output variables (`score`, `grade`, `passed=true`, `errors`, `warnings`).
2. **Given** a pull request containing FHIR data that breaches configured quality thresholds (`min-score` or `fail-on`), **When** the action executes, **Then** the action step internally populates all step outputs before failing with status `failure`, outputs descriptive failure summaries in the workflow log, and blocks pull request merging if branch protection is enabled.
3. **Given** the GitHub Action configured with SARIF upload enabled (`upload-sarif: true`) and a valid token, **When** the action completes analysis on a repository with GitHub Code Scanning enabled, **Then** detected issues are uploaded and rendered as inline annotations on the pull request diff with exact file paths and remediation suggestions; if Code Scanning is unconfigured or permissions are missing, a clear warning is emitted to the workflow log without crashing downstream steps.
4. **Given** workflow parameters specifying a custom target profile (e.g. `profile: BASE_R4`) and a target data path (e.g. `path: data/fhir`), **When** the action runs, **Then** the specified parameters are accurately forwarded to the underlying linter execution.
5. **Given** the action executing in user-facing table format (`format: table`), **When** extracting step outputs, **Then** the action internally generates a structured JSON report to deterministically extract `score`, `grade`, `errors`, and `warnings` without fragile ANSI regex scraping.

---

### User Story 4 - Lightweight Containerized Runner for Universal CI/CD Pipelines (Priority: P4)

As an enterprise DevOps engineer using container-native CI/CD platforms (such as GitLab CI, Tekton, Argo Workflows, or Jenkins), I want to execute FHIRLint using an official, minimal container image packaging the standalone native binary, so that I can run standardized healthcare data quality checks in any containerized environment without installing host binaries or maintaining custom runtime base images.

**Why this priority**: Many enterprise CI/CD systems run exclusively on container runners (e.g. Kubernetes pods). A secure, minimal container image packaging the Linux native binary allows non-GitHub CI platforms and automated ETL batch jobs to execute FHIRLint with sub-second startup and isolated dependencies.

**Independent Test**: Can be tested independently by running `docker run --rm -v $(pwd):/data fhir-lint validate /data/bundle.json` or piping stdin into `docker run -i --rm fhir-lint validate -`, verifying volume mounting, exit codes, and output formats in a clean container runner.

**Acceptance Scenarios**:

1. **Given** an official container image packaging the Linux x86_64 native executable, **When** executed with volume-mounted local files via `docker run --rm -v $(pwd):/workspace fhir-lint validate /workspace/bundle.json`, **Then** the container executes the validation with sub-second latency and returns the appropriate exit code (`0` for pass, `1` for gate breach).
2. **Given** a stream of FHIR data piped through standard input, **When** executed via `cat bundle.json | docker run -i --rm fhir-lint validate -`, **Then** the container reads standard input and writes results to standard output.
3. **Given** an enterprise security scanner inspecting the container image, **When** scanned for known vulnerabilities and user privileges, **Then** the container image runs as an unprivileged non-root user on a minimal distroless (`gcr.io/distroless/static-debian12`) or lightweight Alpine base.

---

### User Story 5 - Automated Multi-Platform Release & Integrity Verification (Priority: P5)

As an open-source user and security-conscious consumer, I want public releases to include pre-built native binaries compiled across a multi-runner CI matrix for major operating systems (Linux x86_64, macOS Apple Silicon/Intel, Windows x86_64), universal JARs, and cryptographic checksums (SHA-256), so that I can automatically download, verify the integrity of, and run the correct binary for my hardware architecture.

**Why this priority**: GraalVM Native Image does not support cross-compilation; release binaries must be compiled on native OS runners. Standardized artifact naming and cryptographic checksums ensure supply chain integrity and prevent corrupted or tampered downloads.

**Independent Test**: Can be tested independently by triggering the automated release pipeline on a version tag, verifying that distribution assets for all target platforms are compiled across native runner matrix jobs (`ubuntu-latest`, `macos-14`, `macos-13`, `windows-latest`), packaged, and published alongside matching SHA-256 checksum files, and validating that the published assets match the computed checksums.

**Acceptance Scenarios**:

1. **Given** a published version release, **When** inspecting release assets, **Then** downloadable artifacts exist with standardized naming:
   - `fhir-lint-linux-x86_64` (Linux x86_64 native binary)
   - `fhir-lint-macos-aarch64` (macOS Apple Silicon / ARM64 native binary)
   - `fhir-lint-macos-x86_64` (macOS Intel / x86_64 native binary)
   - `fhir-lint-windows-x86_64.exe` (Windows x86_64 native executable)
   - `fhir-lint-all.jar` (Universal executable Fat JAR)
2. **Given** published distribution artifacts, **When** downloading any artifact alongside its corresponding `.sha256` checksum file, **Then** running standard verification (`sha256sum -c`) confirms artifact authenticity and integrity.
3. **Given** an automated release workflow triggered by a release tag, **When** the workflow executes, **Then** a matrix of OS runners compiles the native binaries, builds the universal JAR, executes sanity validation checks (`--version`), computes SHA-256 hashes, and attaches all assets to the GitHub Release.

---

### Edge Cases

- **Native Image Dynamic Reflection & Resource Loading**: AOT compilation must bundle all necessary reflection metadata, serialization configurations, and resource bundles (HAPI FHIR structures, Jackson serializers, logging properties) generated via the GraalVM Native Image Tracing Agent (`-Pagent`) so that dynamic operations do not fail at runtime with `ClassNotFoundException` or `MissingResourceException`.
- **Operating System File Path & Permission Differences**: Executables must handle Windows path delimiters (`\`) and POSIX paths (`/`) uniformly, and packaged native binaries for Linux/macOS must retain executable permissions (`+x`) upon extraction or download.
- **Piped Standard Input in Container and Subprocess**: In containerized and piped execution (`stdin`), standard input streams must be read to EOF without hanging, prematurely terminating, or blocking on interactive terminal checks.
- **Container Volume Permissions**: When local directories are mounted into the container with varied host UID/GIDs, the container process running as a non-root user must successfully read input files with standard read permissions without permission denied errors.
- **Workspace Directory Traversal Safety**: When target paths contain non-FHIR files or directory trees (e.g. `.git/`, `node_modules/`, `build/`), the action and CLI must avoid indiscriminate recursion of non-FHIR directories; users must point `path` to a specific file or directory containing FHIR assets.
- **SARIF Upload Without Permissions or Advanced Security**: When `upload-sarif: true` is configured but the repository lacks GitHub Advanced Security or the workflow token lacks `security-events: write` permission, the action must log an actionable warning and continue without failing the step unexpectedly due to API 403 errors.
- **Action Output Determinism on Gate Failures**: When a quality gate fails (CLI exits with code 1), the action must ensure step outputs are written to `$GITHUB_OUTPUT` from an internal JSON report before terminating the step with failure status.
- **Zero-Byte or Truncated Release Artifacts**: The automated packaging pipeline must verify artifact non-emptiness and execute sanity checks (`--version`) against compiled binaries before generating checksums and publishing releases.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST package the complete application and all runtime dependencies into a single, self-contained executable Fat JAR with a manifest entry specifying `org.fhirlint.cli.FhirLintApplication`.
- **FR-002**: System MUST compile a standalone Ahead-Of-Time (AOT) native machine executable for the host platform with zero runtime JVM installation prerequisite.
- **FR-003**: System MUST provide complete Ahead-Of-Time reachability and reflection metadata covering HAPI FHIR R4 models, Jackson JSON serializers, Picocli commands, and validation resources (generated and maintained via the GraalVM Native Image Tracing Agent under `src/main/resources/META-INF/native-image/org.fhirlint/fhir-lint/`) to ensure 100% functional parity between JVM and Native execution.
- **FR-004**: Native binary executable MUST achieve cold launch startup duration of under 50 milliseconds for baseline commands (`--version`, `--help`).
- **FR-005**: System MUST provide an official GitHub Composite Action (`action.yml`) enabling cross-platform quality gating on `ubuntu-latest`, `macos-latest`, and `windows-latest` runners via `uses: fhir-lint/action@v1`.
- **FR-006**: GitHub Action MUST provide configurable input parameters:
  - `path`: Target file or directory path containing FHIR data (required, e.g. `sample-data/clean-bundle.json` or `fhir-resources/`).
  - `profile`: Target validation profile (`US_CORE` or `BASE_R4`, default: `US_CORE`).
  - `format`: Output format displayed in workflow log (`table`, `json`, `sarif`, default: `table`).
  - `min-score`: Minimum passing quality score threshold between 0 and 100 (optional).
  - `fail-on`: Failure severity threshold (`error`, `warning`, `info`, `none`, default: `error`).
  - `output-file`: Optional destination file path for rendered output.
  - `upload-sarif`: Boolean flag indicating whether to automatically upload SARIF results to GitHub Code Scanning (default: `false`).
  - `github-token`: GitHub token with `security-events: write` permission for SARIF uploads (default: `${{ github.token }}`).
- **FR-007**: GitHub Action MUST export step outputs for downstream pipeline consumption, populated via deterministic internal JSON report extraction regardless of log output format:
  - `score`: Overall quality score (0–100).
  - `grade`: Engineering grade tier (`EXCELLENT`, `ACCEPTABLE`, `DEGRADED`, `CRITICAL`).
  - `errors`: Count of detected error-level issues.
  - `warnings`: Count of detected warning-level issues.
  - `passed`: Boolean string (`true` or `false`) indicating quality gate verdict.
  - `report-path`: File path of the generated report if written to disk.
- **FR-008**: GitHub Action MUST fail the workflow step with a non-zero status code when quality gate thresholds (`min-score` or `fail-on`) are breached, while ensuring all step outputs are populated and printing full gate breach diagnostics in the job log.
- **FR-009**: System MUST provide an official, minimal container image packaging the Linux x86_64 native binary on a distroless or lightweight Alpine base, configured to run as an unprivileged non-root user.
- **FR-010**: Container image MUST support both volume-mounted filesystem analysis and piped standard input streaming (`docker run -i ... validate -`).
- **FR-011**: System MUST provide automated multi-platform release workflows utilizing a matrix of native OS runners (`ubuntu-latest`, `macos-14`, `macos-13`, `windows-latest`) to compile platform-specific native binaries with standardized naming alongside the universal Fat JAR.
- **FR-012**: System MUST generate SHA-256 cryptographic checksums for all published release artifacts and package them alongside the release downloads.
- **FR-013**: All distribution formats (Executable Fat JAR, Native Binary, Container, GitHub Action) MUST maintain identical CLI argument parsing, POSIX exit codes (`0`, `1`, `2`), and analytical scoring results.
- **FR-014**: All packaging and distribution artifacts MUST maintain strict compliance with Constitution Principle V (zero data retention, zero remote telemetry, local-only processing).

### Key Entities

- **Executable Fat JAR Artifact**: A single all-inclusive Java archive containing all compiled bytecode and transitively bundled dependencies, executable via standard Java runtimes (`java -jar`).
- **Native Binary Executable**: An ahead-of-time compiled, platform-specific machine binary containing pre-initialized application state and runtime substrate, requiring no external runtime dependencies.
- **AOT Reachability Metadata**: Configuration specifications (reflection, JNI, resources, serialization) declaring dynamic class lookups and resource access for native compilation.
- **GitHub Composite Action**: Cross-platform workflow action descriptor (`action.yml`) declaring inputs, execution steps, output exports, and optional integration with GitHub Code Scanning.
- **Container Distribution Image**: Lightweight OCI-compliant container image encapsulating the native executable with minimal base layers, non-root execution context, and container entry points.
- **Release Distribution Manifest**: Package containing multi-platform release binaries, universal JAR, version metadata, and SHA-256 checksum signatures.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Standalone native binary launches and outputs version/help information in under 50 milliseconds on standard developer hardware.
- **SC-002**: Standalone native binary validates a standard FHIR bundle (< 1,000 resources) in under 500 milliseconds end-to-end.
- **SC-003**: Executable Fat JAR and Native Binary achieve 100% output parity (scores, findings, exit codes) across all test datasets.
- **SC-004**: GitHub Composite Action executes successfully across standard GitHub-hosted runners (`ubuntu-latest`, `macos-latest`, `windows-latest`) and correctly passes or fails steps based on quality gate policies.
- **SC-005**: GitHub Action with SARIF export enabled produces annotations visible in GitHub Pull Request code scanning without formatting errors when permissions and GHAS are available.
- **SC-006**: Containerized execution via Docker achieves 100% functional and exit code parity compared to direct host CLI execution with sub-second cold start.
- **SC-007**: 100% of distribution formats operate with zero external network connectivity during linting operations, maintaining complete local-first data privacy.
- **SC-008**: 100% of release distribution artifacts are accompanied by matching, verifiable SHA-256 checksum files.

---

## Assumptions

- Users of the native executable do not need any Java runtime or development kit installed on their operating system.
- Users of the universal Fat JAR have a compatible Java 21+ JRE installed.
- GraalVM Native Image tooling is available in CI/CD build environments for ahead-of-time compilation.
- GitHub Actions runners have access to standard GitHub environment variables (`GITHUB_WORKSPACE`, `GITHUB_STEP_SUMMARY`) and GitHub Code Scanning APIs when configured.
- Containerized workflows run in OCI-compliant container runtimes (Docker, Podman, containerd).
- Release tagging adheres to Semantic Versioning (e.g. `v0.1.0`), triggering automated release packaging workflows across the runner matrix.
- In accordance with ADR-009 and Constitution Principle II, no database migration tools (Flyway), persistent relational databases (PostgreSQL), or daemon services are included in distribution artifacts.
