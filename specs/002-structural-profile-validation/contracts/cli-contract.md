# CLI Contract: `fhir-lint validate`

This document defines the command-line interface contract for the `validate` command, including options, arguments, input handling, exit codes, and output serialization for **Phase 2: Structural and Profile Validation**.

---

## 1. Command Syntax

```bash
fhir-lint validate <input-source> [options]
```

### 1.1 Positional Arguments

| Argument | Type | Description |
| :--- | :--- | :--- |
| `<input-source>` | String (Path or `-`) | Path to a FHIR JSON file, directory of JSON files, or `-` to read from standard input (`stdin`). |

### 1.2 Options

| Option | Flag | Default | Description |
| :--- | :--- | :--- | :--- |
| `--profile` | `-p` | `US_CORE` | Target validation profile. Supported values: `US_CORE`, `BASE_R4`. |
| `--format` | `-f` | `table` | Output format: `table` (ANSI terminal table), `json` (machine-readable), `sarif` (GitHub annotations). |
| `--min-score` | None | `0` | Minimum acceptable quality score (0–100). Exits with code `1` if the calculated overall score is below this threshold. |
| `--fail-on` | None | `error` | Severity threshold that triggers exit code `1`: `error`, `warning`, `info`. |
| `--verbose` | `-v` | `false` | Display full issue details and suggestions in terminal table output. |
| `--output` | `-o` | `stdout` | Write output report to the specified file path instead of standard out. |

---

## 2. Exit Code Contract

| Exit Code | Meaning | Conditions |
| :---: | :--- | :--- |
| **`0`** | **Quality Gate Passed** | Dataset parsed successfully, structural and profile issues evaluated, score $\ge$ `--min-score`, and no issues at or above `--fail-on` threshold. |
| **`1`** | **Quality Gate Failed** | Dataset parsed successfully, but validation detected issues violating `--fail-on` (e.g. 1+ `ERROR` under default settings) or score $<$ `--min-score`. |
| **`2`** | **Invocation / Syntax Error** | Malformed JSON input, missing FHIR `resourceType`, non-existent file path, or invalid CLI options (e.g., unsupported `--profile`). |

---

## 3. Example Terminal Invocations

### 3.1 Validate with US Core (Default)
```bash
fhir-lint validate sample-data/messy/messy-bundle.json
```

### 3.2 Validate against Base FHIR R4 Schema Only
```bash
fhir-lint validate bundle.json --profile BASE_R4
```

### 3.3 Validate via UNIX Pipe with JSON Output and Quality Gate
```bash
cat bundle.json | fhir-lint validate - --format json --min-score 85 --fail-on error
```

### 3.4 Produce SARIF for GitHub Code Scanning
```bash
fhir-lint validate bundle.json --format sarif -o results.sarif
```
