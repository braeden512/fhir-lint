# ADR-006: Phase 6 — Developer Experience and Standalone CLI Design

## Status
Accepted (Amended to reflect ADR-009)

## Context & Problem Statement
FHIRLint is an infrastructure developer tool. Its primary consumers are software engineers, data engineers, and DevOps/CI-CD specialists building health-tech integrations. The developer experience (DX) must be frictionless, predictable, and self-documenting.

Constitution Principle VII mandates: "FHIRLint is a developer tool; its primary interfaces are its command-line interface (CLI) and an embeddable Java library API. The CLI MUST adhere to standard POSIX conventions: accept files, directories, or standard input; return standard exit codes (0 for success, non-zero for quality gate failures or syntax errors); and provide human-friendly colorized ANSI terminal formatting as well as machine-readable JSON and SARIF outputs."

## Decision Drivers
1. **Ergonomic CLI Execution**: Single-command execution against local JSON files, directories, or stdin pipes (`cat bundle.json | fhir-lint validate -`).
2. **Standard Quality Gates**: Standard UNIX exit codes (`0` on pass, `1` on quality defects violating `--min-score` or `--fail-on`, `2` on syntax/argument errors) for CI/CD pipelines.
3. **Actionable Visual Reporting**: Colorized ANSI terminal tables with summary scorecards, category progress bars, and prioritized issue diagnostics.
4. **Machine-Readable Outputs**: Support `--format json` (for piping into `jq`) and `--format sarif` (for native GitHub Pull Request Code Scanning annotations).

## Considered Options
1. **Picocli (`info.picocli:picocli`)**: Industry-standard Java CLI library. Supports POSIX-compliant flag parsing, automatic subcommands, rich ANSI color styling, shell autocompletion generation, and ahead-of-time (AOT) GraalVM Native Image compilation.
2. **Spring Shell**: Heavy dependency pulling in the entire Spring framework; slow startup time, ill-suited for standalone CLI execution.
3. **Bash / Python wrapper script**: Requires external interpreters, fragile cross-platform compatibility, and decouples the CLI from the core engine.

## Decision Outcome
Adopt **Option 1: Picocli standalone CLI application**.

### Command Specification

```bash
fhir-lint validate <file|directory|-> [options]
```

#### Options:
- `-p, --profile <NAME>`: Target validation profile (`BASE_R4`, `US_CORE`). Default: `US_CORE`.
- `-f, --format <FORMAT>`: Output format: `table` (default ANSI color table), `json`, `sarif`.
- `--min-score <0-100>`: Minimum passing score. Exits with code `1` if overall score is below threshold.
- `--fail-on <SEVERITY>`: Exit with code `1` if any issue of this severity or higher is detected (`error`, `warning`).
- `-v, --verbose`: Display detailed issue listings in terminal table output.
- `-o, --output <PATH>`: Write output to a file instead of stdout.

### Output Formats
1. **ANSI Console Table**: Designed for human eyes in terminals. Displays score badge, grade tier, defect count, category breakdown, and critical issues with FHIRPath locations.
2. **JSON**: Clean, structured representation of `LintReport` for programmatic consumption.
3. **SARIF (OASIS Standard)**: Static Analysis Results Interchange Format (v2.1.0) allowing GitHub Actions to automatically annotate pull request diffs at the exact line/path where issues occur.

## Consequences
### Positive
- Instant terminal startup without web servers or background processes.
- First-class CI/CD integration with standard exit codes and SARIF reports.
- Picocli provides built-in help (`--help`, `-h`), versioning (`--version`), and auto-completion.
- Fully compatible with GraalVM Native Image for standalone binary distribution.

### Negative / Trade-offs
- Requires `info.picocli:picocli` dependency (lightweight, ~400KB).
