# CLI Command Contract: `fhir-lint validate`

**Version**: 1.0.0 | **Status**: Active | **Domain**: Developer Interfaces

---

## 1. Syntax

```bash
fhir-lint validate <file|directory|-> [options]
```

---

## 2. Arguments & Options

### Positional Arguments
- `<file|directory|->` *(required)*:
  - **Single file**: Path to a single FHIR JSON resource or bundle (e.g. `bundle.json`).
  - **Directory**: Path to a directory. Scanned recursively for all files ending with `.json`.
  - **Standard input (`-`)**: Reads complete JSON payload from `stdin` until EOF.

### Options

| Short | Long | Value Format | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `-p` | `--profile` | `US_CORE \| BASE_R4` | `US_CORE` | Conformance validation profile (case-insensitive). |
| `-f` | `--format` | `table \| json \| sarif` | `table` | Output format (case-insensitive). |
| | `--min-score` | `0` to `100` | `0` | Minimum passing score. Rejects values `< 0` or `> 100` with exit code `2`. |
| | `--fail-on` | `error \| warning \| info \| none` | `error` | Severity threshold that triggers exit code `1`. `none`/`off` disables severity gating. |
| `-v` | `--verbose` | *(flag)* | `false` | Displays all issues in terminal table instead of truncating at 10 items. |
| `-o` | `--output` | `<file-path>` | `null` | Writes output report directly to specified file path instead of `stdout`. |
| `-h` | `--help` | *(flag)* | | Displays command usage and available options. Exits with code `0`. |
| `-V` | `--version` | *(flag)* | | Displays version information. Exits with code `0`. |

---

## 3. Exit Code Contract

| Exit Code | Classification | Trigger Conditions | Standard Error / Output Behavior |
| :---: | :--- | :--- | :--- |
| **`0`** | **Success** | Input valid, linting completed, and all quality gate criteria satisfied (`score >= --min-score` and no issues violating `--fail-on`). | Rendered report sent to `stdout` (or file if `-o`). |
| **`1`** | **Quality Gate Breach** | Overall quality score falls below `--min-score`, or at least one detected issue equals or exceeds `--fail-on` severity. | Rendered report sent to `stdout` (or file); breach summaries printed to `stderr`. |
| **`2`** | **Invocation / Syntax Error** | - File or directory does not exist<br>- Directory contains zero `.json` files<br>- Input stream is empty (0 bytes)<br>- Input is not valid JSON or fails boundary parsing<br>- Unsupported profile or format name<br>- `--min-score < 0` or `--min-score > 100`<br>- Output file write error | Actionable error message printed to `stderr`. No report generated. |

---

## 4. Standard Stream Conventions

- **`stdout`**:
  - For `--format table`: Full ANSI formatted report (or top 10 + truncation notice if not verbose).
  - For `--format json`: Raw, unadorned JSON string (pipeable into `jq`).
  - For `--format sarif`: Raw OASIS SARIF 2.1.0 JSON string.
  - When `-o <file>` is used: Only status confirmation `Report successfully written to: <file>` is emitted to `stdout`.
- **`stderr`**:
  - Quality gate breach descriptions (prefixed with `Quality gate breach: ...`).
  - Error messages for exit code `2` (prefixed with `Error: ...`).
