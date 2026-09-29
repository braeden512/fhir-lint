# FHIRLint

> **The Developer-Focused Data Quality Linter for FHIR Healthcare Data**

FHIR provides a standardized schema for healthcare data, but "valid FHIR" does not necessarily mean "good healthcare data." Broken cross-resource references, chronological contradictions, duplicate records, missing mandatory clinical elements, and non-canonical terminology can easily slip through schema validation and crash downstream clinical pipelines.

**FHIRLint** answers the question:
> **"Can my application safely and reliably use this healthcare data, and what problems should I fix first?"**

FHIRLint operates **locally and in-memory with zero infrastructure dependencies**. No databases, no external network calls, and no cloud hosting costs—guaranteeing 100% zero-retention privacy for sensitive healthcare data (PHI).

---

## Key Features

- **Local-First & Privacy Guaranteed**: Runs locally in your terminal or inside your private CI/CD runner. No Protected Health Information (PHI) ever leaves your security perimeter.
- **Deep Conformance & Profile Validation**: Powered by [HAPI FHIR R4](https://hapifhir.io/) for HL7 FHIR compliance, with built-in support for US Core v3.1.1 profiles.
- **Cross-Resource Integrity**: Detects broken internal references, dangling IDs, and orphaned clinical records within Bundles.
- **Clinical Chronology & Coherence**: Catches temporal inversions (e.g. encounter end date before start date, or procedures authored before birth).
- **Deterministic Quality Scoring**: Generates an engineering score (0–100) and grade (`EXCELLENT`, `ACCEPTABLE`, `DEGRADED`, `CRITICAL`) with defect density calculations.
- **CI/CD Quality Gates**: Built for automated pipelines (GitHub Actions, GitLab CI) with POSIX exit codes (`0` for pass, `1` for quality failure) and **SARIF 2.1.0** export for inline pull request annotations.
- **Dual Interfaces**: High-performance **CLI tool** for terminal and CI workflows alongside a lightweight, framework-agnostic **Java library API** for embedding directly into ingestion microservices.

---

## Installation & Distribution

FHIRLint offers multiple distribution formats to fit any developer or CI/CD workflow:

### 1. Homebrew (macOS & Linux)
Install the standalone native binary with zero Java runtime requirement:
```bash
# Tap and install
brew tap braeden512/fhir-lint
brew install fhir-lint

# Or in a single command:
brew install braeden512/fhir-lint/fhir-lint
```

### 2. Standalone Native Binary (Zero Java Prerequisite)
Download the pre-compiled native binary for your platform from [GitHub Releases](https://github.com/braeden512/fhir-lint/releases):
- **Linux (x86_64)**: `fhir-lint-linux-x86_64`
- **macOS (Apple Silicon)**: `fhir-lint-macos-aarch64`
- **macOS (Intel)**: `fhir-lint-macos-x86_64`
- **Windows**: `fhir-lint-windows-x86_64.exe`

Make it executable and run instantly (< 50ms startup):
```bash
chmod +x fhir-lint-linux-x86_64
./fhir-lint-linux-x86_64 validate sample-data/clean/clean-bundle.json
```

### 3. Universal Executable Fat JAR
If you have Java 21+ installed, run the universal standalone Fat JAR anywhere:
```bash
# Build locally
./gradlew fatJar

# Or download fhir-lint-all.jar from releases
java -jar build/libs/fhir-lint-all.jar validate sample-data/clean/clean-bundle.json
```

### 4. Official GitHub Action
Add automated healthcare data quality gates and SARIF annotations directly to your GitHub repository workflows:
```yaml
- name: Validate Healthcare Data
  uses: fhir-lint/action@v1
  with:
    path: 'sample-data/clean/clean-bundle.json'
    profile: 'US_CORE'
    min-score: 85
    fail-on: 'error'
    upload-sarif: 'true'
```

### 5. Lightweight Container Image
Run via Docker without installing host dependencies:
```bash
docker run --rm \
  -v $(pwd)/sample-data:/workspace/sample-data \
  ghcr.io/braeden512/fhir-lint:latest validate /workspace/sample-data/clean/clean-bundle.json
```

---

## Building from Source

### Prerequisites
- **Java 21+** (JDK 21 or later)
- **Gradle 9+** (wrapper provided)

```bash
git clone https://github.com/braeden512/fhir-lint.git
cd fhir-lint
./gradlew check
```

---

## Core Use Cases & CLI Workflows

### 1. Interactive Terminal Linting
Inspect a dataset with an ANSI colorized breakdown of scores, categories, and actionable remediation suggestions:

```bash
fhir-lint validate patient-bundle.json
```

Target specific validation profiles:
```bash
# Validate against US Core v3.1.1 (default)
fhir-lint validate bundle.json --profile US_CORE

# Validate strictly against HL7 FHIR R4 base schema
fhir-lint validate bundle.json --profile BASE_R4
```

---

### 2. Stream Processing & UNIX Pipes (`stdin`)
In command-line workflows, you can pass `-` to read directly from standard input. This enables seamless composition with tools like `curl`, `jq`, or decompression utilities without writing intermediate or unencrypted PHI to disk:

```bash
# Lint a payload fetched directly from a FHIR server endpoint
curl -s https://hapi.fhir.org/baseR4/Patient/123 | fhir-lint validate -

# Decompress and validate a gzip bundle in-memory
gzip -dc large-bundle.json.gz | fhir-lint validate -

# Filter or extract a resource slice with jq and lint it
jq '.entry[0].resource' messy-bundle.json | fhir-lint validate -
```

---

### 3. CI/CD Pipeline Quality Gate
Automate healthcare data verification in continuous integration pipelines (GitHub Actions, GitLab CI, Jenkins). Set score thresholds or strict failure modes to stop bad fixture data or pipeline regressions before deployment:

```bash
fhir-lint validate bundle.json --profile US_CORE --min-score 85 --fail-on error
```

#### Exit Codes
| Exit Code | Meaning |
| :--- | :--- |
| `0` | **Success**: Quality score meets threshold and no blocking issues found. |
| `1` | **Quality Gate Failed**: Score below `--min-score` or findings matched `--fail-on` (e.g. `error` or `warning`). |
| `2` | **Execution Error**: File unreadable, malformed JSON syntax, or invalid CLI arguments. |

---

### 4. GitHub Pull Request Annotations (SARIF 2.1.0)
Export findings in [SARIF](https://sarifweb.azurewebsites.net/) (Static Analysis Results Interchange Format) to render line-level annotations directly on GitHub Pull Request diffs:

```bash
fhir-lint validate data/bundle.json --format sarif -o results.sarif
```

#### GitHub Actions Workflow Example
```yaml
name: Healthcare Data Quality Gate

on: [pull_request]

jobs:
  lint-fhir:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'

      - name: Run FHIRLint
        run: ./gradlew run --args="validate fixtures/ --format sarif -o fhir-results.sarif --fail-on none"

      - name: Upload SARIF to GitHub Code Scanning
        uses: github/codeql-action/upload-sarif@v3
        with:
          sarif_file: fhir-results.sarif
```

---

## Programmatic Java API

Embed FHIRLint directly into your JVM applications, Spring services, or Apache Camel / Kafka ETL ingestion pipelines:

```java
import org.fhirlint.core.FhirLinter;
import org.fhirlint.core.model.LintReport;
import org.fhirlint.core.model.ValidationProfile;
import java.io.File;

// Initialize linter configured with target profile
FhirLinter linter = FhirLinter.create()
    .withProfile(ValidationProfile.US_CORE);

// Lint a file, string, or input stream
LintReport report = linter.lint(new File("patient-bundle.json"));

// Access deterministic quality score and grade
System.out.printf("Quality Score: %d/100 (%s)%n",
    report.getQualityScore().getOverallScore(),
    report.getQualityScore().getGrade());

// Inspect prioritized findings and suggestions
if (report.hasErrors()) {
    report.getIssues().forEach(issue ->
        System.err.printf("[%s] %s (%s): %s -> Fix: %s%n",
            issue.severity(),
            issue.ruleId(),
            issue.path(),
            issue.message(),
            issue.suggestion())
    );
}
```

---

## CLI Options & Flags

| Option | Description | Default |
| :--- | :--- | :--- |
| `[file]` | Path to FHIR JSON bundle file, or `-` for standard input. | Required |
| `--profile` | Validation profile (`US_CORE`, `BASE_R4`). | `US_CORE` |
| `--format` | Output format (`table`, `json`, `sarif`). | `table` |
| `-o, --output` | Write output report to a destination file path. | `stdout` |
| `--min-score` | Minimum passing score threshold (0–100). | `0` |
| `--fail-on` | Severity threshold that triggers exit code 1 (`none`, `warning`, `error`). | `error` |
| `--verbose` | Output full diagnostic details without truncating findings. | `false` |

---

## License

FHIRLint is distributed under the terms of the [Apache License (Version 2.0)](LICENSE).
