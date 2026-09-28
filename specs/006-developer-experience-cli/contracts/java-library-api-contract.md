# Java Library API Contract: `FhirLinter`

**Version**: 1.0.0 | **Status**: Active | **Domain**: Embeddable In-Process Library

---

## 1. Overview

`FhirLinter` is the primary programmatic interface of `fhir-lint-core`. It provides a fluent, immutable, and thread-safe builder for executing healthcare data quality analysis in JVM-based pipelines (e.g. Apache Camel, Spring Batch, Kafka streams, Spark jobs) without requiring external processes or databases.

---

## 2. API Signature & Methods

```java
package org.fhirlint.core;

public class FhirLinter {

    // Factory
    public static FhirLinter create();

    // Configuration
    public FhirLinter withProfile(ValidationProfile profile);
    public FhirLinter withReferentialIntegrityEngine(ReferentialIntegrityEngine engine);
    public FhirLinter withQualityRuleEngine(QualityRuleEngine engine);

    // Ingestion Methods
    public LintReport lint(File file);
    public LintReport lint(InputStream inputStream);
    public LintReport lint(String jsonContent);
    public LintReport lint(List<File> files);

    // Getters
    public ValidationProfile getProfile();
    public FhirBundleParser getParser();
    public FhirValidationEngine getValidationEngine();
    public ReferentialIntegrityEngine getReferentialIntegrityEngine();
    public QualityRuleEngine getQualityRuleEngine();
}
```

---

## 3. Ingestion Methods Behavior

| Method | Input Parameter | Pre-conditions | Exception Handling | Return Value |
| :--- | :--- | :--- | :--- | :--- |
| `lint(File)` | Local File | File must exist and be readable. | Throws `FhirParseException` on invalid JSON or FHIR syntax errors. | Immutable `LintReport` |
| `lint(InputStream)` | Input stream | Stream must be open and non-empty. Consumed to EOF. | Throws `FhirParseException` on empty stream or invalid JSON. | Immutable `LintReport` |
| `lint(String)` | Raw JSON string | String must be non-null and non-blank. | Throws `FhirParseException` on invalid JSON. | Immutable `LintReport` |
| `lint(List<File>)` | Collection of files | List must be non-empty; all files must exist. | Throws `FhirParseException` if list is empty or any file fails parsing. | Unified `LintReport` aggregated across all files |

---

## 4. Usage Example

```java
import org.fhirlint.core.FhirLinter;
import org.fhirlint.core.model.LintReport;
import org.fhirlint.core.model.QualityGateConfig;
import org.fhirlint.core.model.QualityGateResult;
import org.fhirlint.core.model.Severity;
import org.fhirlint.core.model.ValidationProfile;

import java.io.File;

// 1. Initialize linter with desired profile
FhirLinter linter = FhirLinter.create()
    .withProfile(ValidationProfile.US_CORE);

// 2. Perform linting pass
LintReport report = linter.lint(new File("sample-data/clean/clean-bundle.json"));

// 3. Inspect results
int overallScore = report.qualityScore().getOverallScore();
String grade = report.qualityScore().getGrade().name();
System.out.printf("Quality: %d/100 (%s)\n", overallScore, grade);

// 4. Programmatic Quality Gate evaluation
QualityGateResult gate = report.evaluateGate(QualityGateConfig.of(85, Severity.ERROR));
if (!gate.passed()) {
    gate.breaches().forEach(System.err::println);
}
```
