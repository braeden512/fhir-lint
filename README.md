# FHIRLint

> **The Developer-Focused Data Quality Linter for FHIR Healthcare Data**

FHIR provides a standardized schema for healthcare data, but "valid FHIR" does not necessarily mean "good healthcare data." Broken cross-resource references, chronological contradictions, duplicate records, missing mandatory clinical elements, and non-canonical terminology can easily slip through schema validation and crash downstream clinical pipelines.

**FHIRLint** answers the question:
> **"Can my application safely and reliably use this healthcare data, and what problems should I fix first?"**

FHIRLint operates **locally and in-memory with zero infrastructure dependencies**. No databases, no external network calls, and no cloud hosting costs—guaranteeing 100% zero-retention privacy for sensitive healthcare data.

---

## Key Features

- **Local-First & Privacy Guaranteed**: Runs locally in your terminal or in your private CI/CD pipeline. No Protected Health Information (PHI) ever leaves your security perimeter.
- **Deep Conformance & Profile Validation**: Powered by [HAPI FHIR R4](https://hapifhir.io/)—the world gold standard for HL7 FHIR compliance.
- **Cross-Resource Integrity**: Detects broken internal references, dangling IDs, and orphaned clinical records within Bundles.
- **Clinical Chronology & Coherence**: Catches temporal inversions (e.g. encounter end date before start date, or procedures authored before birth).
- **Deterministic Quality Scoring**: Generates an engineering score (0–100) and grade (`EXCELLENT`, `ACCEPTABLE`, `DEGRADED`, `CRITICAL`) with defect density calculations.
- **CI/CD Quality Gates**: Built for automated pipelines (GitHub Actions, GitLab CI) with POSIX exit codes (`0` for pass, `1` for quality failure) and **SARIF 2.1.0** export for inline pull request annotations.
- **Dual Interfaces**: High-performance **CLI tool** for terminal/CI workflows and a pure, framework-agnostic **Java library API** for embedding directly into ingestion pipelines.

---

## Quickstart

### 1. Build
Requires Java 21+:
```bash
./gradlew assemble
```

### 2. Run the CLI
Validate a sample FHIR Bundle with a colorized terminal report:
```bash
./gradlew run --args="validate sample-data/messy/messy-bundle.json"
```

### 3. CI/CD Pipeline Quality Gate
Enforce a minimum quality score of 85 and fail on errors:
```bash
fhir-lint validate bundle.json --min-score 85 --fail-on error
```
*Returns exit code `0` if passed, `1` if the quality gate fails.*

### 4. UNIX Pipes (`stdin`)
Pipe JSON directly from curl, jq, or other tools:
```bash
cat sample-data/clean/clean-bundle.json | fhir-lint validate - --format table
```

### 5. Generate SARIF for GitHub Pull Request Annotations
```bash
fhir-lint validate bundle.json --format sarif -o results.sarif
```

---

## Programmatic Java API

Embed FHIRLint directly in your Java/Kotlin/JVM applications, ETL jobs, or Spring Batch pipelines without any web server or database overhead:

```java
import com.braeden.fhirlint.core.FhirLinter;
import com.braeden.fhirlint.core.model.LintReport;
import com.braeden.fhirlint.core.model.ValidationProfile;

FhirLinter linter = FhirLinter.create()
    .withProfile(ValidationProfile.US_CORE);

LintReport report = linter.lint(new File("patient-bundle.json"));

System.out.println("Score: " + report.getQualityScore().getOverallScore());
if (report.hasErrors()) {
    report.getIssues().forEach(issue -> 
        System.err.println(issue.getRuleId() + " [" + issue.getPath() + "]: " + issue.getMessage())
    );
}
```

---

## Development Roadmap

| Phase | Description | Status |
| :--- | :--- | :--- |
| **Phase 0** | Research, HAPI FHIR Architecture, Synthetic Benchmark Datasets | Completed |
| **Phase 1** | Local Dataset Ingestion, HAPI R4 Parsing, Boundary Verification | In Progress |
| **Phase 2** | Structural and US Core Profile Validation | Next |
| **Phase 3** | Referential Integrity & In-Memory Resource Graph | Planned |
| **Phase 4** | Pluggable Rule Engine & Data Quality Checks | Planned |
| **Phase 5** | Deterministic Quality Scoring & Defect Density Model | Planned |
| **Phase 6** | Polished CLI Experience (Picocli), ANSI Tables & SARIF Output | Planned |
| **Phase 7** | Standalone Packaging (Fat JAR, GraalVM Native Image, GitHub Action) | Planned |
| **Phase 8** | Advanced Extensibility (Custom Rules, Dataset Diffs) | Proposed |

---

## Documentation

- [Product Specification](PRODUCT_SPEC.md)
- [Project Constitution](.specify/memory/constitution.md)
- [Architecture Decision Records (ADRs)](docs/adr/README.md)
- [Phase 0 Research & Technical Findings](docs/research/phase-0-research-findings.md)

---

## License

Apache 2.0. See [LICENSE](LICENSE) for details.
