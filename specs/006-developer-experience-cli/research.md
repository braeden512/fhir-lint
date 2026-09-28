# Research & Technical Decisions: Phase 6 — Developer Experience and Standalone CLI Design

**Branch**: `006-developer-experience-cli` | **Date**: 2026-09-27 | **Spec**: [spec.md](spec.md)

## Summary of Decisions

This document captures the architectural research, trade-offs, and technical decisions governing the command-line interface, output renderers, error contracts, and programmatic Java library API for FHIRLint Phase 6.

---

## 1. CLI Framework & POSIX Argument Parsing

### Decision
Utilize **Picocli (`info.picocli:picocli:4.7.6`)** for declarative command-line argument parsing, subcommands, POSIX flag parsing, ANSI styling, and standard exit code handling.

### Rationale
- **Zero Startup Overhead**: Picocli runs natively in-process without requiring heavy framework containers or reflection proxies.
- **POSIX Conformance**: Built-in support for short/long flags (`-p`, `--profile`), standard help (`-h`, `--help`), versioning (`-V`, `--version`), and piped standard input (`-`).
- **Standard Exit Codes**: Picocli cleanly maps execution outcomes and exceptions to standard UNIX exit codes: `0` for success, `1` for quality gate violations, and `2` for syntax/argument/boundary errors.
- **GraalVM Native Image Ready**: Ahead-of-time (AOT) metadata generation ensures immediate compatibility with single-binary compilation in Phase 7.

### Alternatives Considered
- *Spring Shell*: Rejected. Pulls in the entire Spring runtime, drastically increasing fat JAR size (50MB+), slowing startup time to multiple seconds, and violating Constitution Principle II (Lean Architecture).
- *Commons CLI*: Rejected. Outdated API lacking modern type-safe annotations, ANSI color integration, and automated help/version generation.
- *Custom Hand-Rolled Argument Parser*: Rejected. Reinvents standard POSIX flag parsing, increasing maintenance surface and edge-case fragility.

---

## 2. Standardized Exit Code Contract & Gate Evaluation

### Decision
Adhere strictly to a three-tier POSIX exit code contract:
- `0`: Successful validation pass meeting all quality gate requirements.
- `1`: Quality gate breach (dataset overall score `< --min-score` or presence of issues violating `--fail-on`).
- `2`: Invocation, syntax, argument, or input boundary failure (file not found, empty directory, invalid JSON syntax, illegal argument values like `--min-score < 0` or `> 100`, unsupported `--format` or `--profile`).

### Rationale
- Standard CI/CD systems (GitHub Actions, GitLab CI, Jenkins) rely on exit code `0` for passing jobs and non-zero for failed jobs.
- Differentiating between a quality defect (`1`) and a script/invocation misconfiguration (`2`) allows automated pipelines to distinguish actionable healthcare data quality failures from operational tooling errors.

### Implementation Specifics
- `ValidateCommand.java` validates arguments upfront before invoking `FhirLinter`.
- Both `--min-score` boundary checks (`minScore < 0 || minScore > 100`) and `--format` validity checks (`table`, `json`, `sarif`) immediately return exit code `2` with actionable stderr messages.
- Existing tests that used `--min-score 101` on clean bundles to simulate gate failures are updated to use `--min-score 100` on defective bundles (e.g., `sample-data/messy/messy-bundle.json`) to preserve exit code `1` semantics without triggering argument validation failures.

---

## 3. Human-Centric Terminal Output (ANSI Table Renderer)

### Decision
Implement `ConsoleTableRenderer` using standard ANSI escape sequences to provide an informative terminal dashboard:
1. Header banner with FHIRLint branding.
2. Score badge with grade tier and color coding (green for `EXCELLENT`, blue for `ACCEPTABLE`, yellow for `DEGRADED`, red for `CRITICAL`).
3. Metrics summary: total resources, error count, warning count, duration.
4. Resource type distribution summary table.
5. Category breakdown table with visual ANSI progress bars (`[████████████████████] 100%`).
6. Prioritized findings section displaying severity tag, rule ID, message, enriched resource location context `(ResourceType/ResourceId: path)`, and remediation suggestions.
7. Truncation to the top 10 findings in default mode with a notice prompting `-v, --verbose` for the full issue catalog.
8. Mandatory non-clinical engineering indicator disclaimer anchored at the bottom.

### Rationale
- High-density healthcare diagnostics must be visually scannable at a glance.
- Enriched location context `(ResourceType/Id: path)` reduces developer cognitive overhead when pinpointing issues within multi-resource bundles.
- Truncation prevents terminal flooding when linting large, heavily degraded datasets.

---

## 4. OASIS SARIF 2.1.0 Conformance & GitHub Code Scanning

### Decision
Implement `SarifReportRenderer` producing standards-compliant OASIS SARIF v2.1.0 documents.

### Key Architectural Choices
- **Disclaimer Placement**: In accordance with OASIS SARIF 2.1.0 schema rules, top-level custom properties are invalid. The mandatory non-clinical disclaimer is serialized cleanly into the run property bag:
  ```json
  "runs": [
    {
      "properties": {
        "disclaimer": "Quality scores produced by FHIRLint reflect technical data hygiene and engineering standards rather than clinical, medical, or regulatory compliance measurements."
      }
    }
  ]
  ```
- **Dynamic Rule Declarations**: While core rules (`REF-001` through `COMP-002`) are pre-registered with rich descriptions, any rule ID present in the issues list (e.g. profile conformance rules like `USCORE_OBS_CATEGORY` or parser errors) is dynamically declared in `runs[0].tool.driver.rules` if not already present. This ensures GitHub Actions Code Scanning renders full rule metadata rather than generic unindexed alerts.
- **URI Sanitization for Stdin**: When input is read from standard input (`-`), `artifactLocation.uri` defaults to `"stdin"` or `"input.json"` to satisfy RFC-3986 relative URI requirements.

### Alternatives Considered
- *GitHub Actions Workflow Commands (`::error file=...`)*: Rejected as primary format. Workflow commands pollute console logs and are specific to GitHub, whereas SARIF 2.1.0 is an OASIS open standard supported by GitHub, GitLab, Azure DevOps, and IDEs.

---

## 5. Machine-Readable JSON Output

### Decision
Implement `JsonReportRenderer` using Jackson `ObjectMapper` configured with `SerializationFeature.INDENT_OUTPUT` and `JavaTimeModule`.

### Rationale
- Serializes the entire `LintReport` model into clean, formatted JSON.
- Standard output receives strictly the JSON payload without log prefixes or ANSI styling, enabling direct UNIX pipe chaining (e.g., `fhir-lint validate bundle.json -f json | jq '.qualityScore.overallScore'`).
- Any quality gate failure notices are written exclusively to `System.err`, preserving pure JSON on `stdout`.

---

## 6. Output File Redirection (`-o, --output`)

### Decision
Support direct file writing in `ValidateCommand.java` with safety hardening:
- Ensure parent directories are created automatically via `outputFile.getParentFile().mkdirs()`.
- Use `FileWriter` with explicit `StandardCharsets.UTF_8` to guarantee deterministic cross-platform output.
- Print confirmation message to `System.out` when file writing succeeds: `Report successfully written to: <path>`.

---

## 7. Embeddable Fluent Java Library API (`FhirLinter`)

### Decision
Provide `FhirLinter` as a fluent, framework-agnostic builder in `fhir-lint-core`:
```java
FhirLinter linter = FhirLinter.create()
    .withProfile(ValidationProfile.US_CORE);

LintReport report = linter.lint(new File("bundle.json"));
```

### Supported Ingestion Sources:
- `lint(File)`: Single local file.
- `lint(InputStream)`: Input stream (stdin, network buffer, S3 stream).
- `lint(String)`: Raw JSON string payload.
- `lint(List<File>)`: Multi-file collection aggregated into a single unified report.

### Principles Compliance
- Strictly in-memory and stateless (Constitution Principle II).
- Zero external database or web server dependencies.
- Returns immutable, strongly typed `LintReport` instances.
