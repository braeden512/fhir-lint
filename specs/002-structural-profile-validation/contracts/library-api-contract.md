# Library API Contract: `fhir-lint-core`

This document defines the programmatic Java API contract for embedding `fhir-lint-core` into Java applications, data pipelines (e.g. Apache Camel, Spring Batch, Kafka consumers), and backend microservices.

---

## 1. Primary Entry Point: `FhirLinter`

```java
package com.braeden.fhirlint.core;

import com.braeden.fhirlint.core.model.LintReport;
import com.braeden.fhirlint.core.model.ValidationProfile;
import org.hl7.fhir.r4.model.IBaseResource;

import java.io.File;
import java.io.InputStream;
import java.util.List;

public class FhirLinter {

    /**
     * Creates a new instance of FhirLinter with default configuration (US_CORE profile).
     */
    public static FhirLinter create();

    /**
     * Configures the target validation profile (e.g. BASE_R4, US_CORE).
     */
    public FhirLinter withProfile(ValidationProfile profile);

    /**
     * Returns the currently configured validation profile.
     */
    public ValidationProfile getProfile();

    /**
     * Lints a FHIR JSON file.
     * @param file Local file containing a FHIR resource or Bundle
     * @return Complete LintReport including issues, scores, and inventory
     */
    public LintReport lint(File file);

    /**
     * Lints a FHIR JSON payload from an input stream.
     * @param inputStream Stream providing FHIR JSON content
     * @return Complete LintReport
     */
    public LintReport lint(InputStream inputStream);

    /**
     * Lints a raw FHIR JSON string.
     * @param jsonContent String containing FHIR JSON
     * @return Complete LintReport
     */
    public LintReport lint(String jsonContent);

    /**
     * Lints multiple FHIR JSON files, aggregating findings into a unified report.
     * @param files List of files to analyze
     * @return Aggregated LintReport
     */
    public LintReport lint(List<File> files);
}
```

---

## 2. Validation Engine SPI: `FhirValidationEngine`

```java
package com.braeden.fhirlint.core.validation;

import com.braeden.fhirlint.core.model.QualityIssue;
import com.braeden.fhirlint.core.model.ValidationProfile;
import org.hl7.fhir.r4.model.Resource;

import java.util.List;

public interface FhirValidationEngine {

    /**
     * Validates an individual FHIR R4 resource against the specified profile.
     * @param resource In-memory HAPI FHIR resource
     * @param profile Target profile (BASE_R4 or US_CORE)
     * @return List of normalized QualityIssue findings
     */
    List<QualityIssue> validateResource(Resource resource, ValidationProfile profile);

    /**
     * Validates a collection of FHIR R4 resources against the specified profile.
     * @param resources In-memory list of FHIR resources
     * @param profile Target profile
     * @return Aggregated list of QualityIssue findings
     */
    List<QualityIssue> validateAll(List<Resource> resources, ValidationProfile profile);
}
```

---

## 3. Usage Example

```java
import com.braeden.fhirlint.core.FhirLinter;
import com.braeden.fhirlint.core.model.LintReport;
import com.braeden.fhirlint.core.model.ValidationProfile;
import com.braeden.fhirlint.core.model.QualityIssue;

import java.io.File;

public class IngestionService {

    private final FhirLinter linter = FhirLinter.create()
            .withProfile(ValidationProfile.US_CORE);

    public void processIncomingBundle(File bundleFile) {
        LintReport report = linter.lint(bundleFile);

        if (report.hasErrors()) {
            System.err.println("Rejected Bundle due to " + report.getErrorCount() + " errors:");
            for (QualityIssue issue : report.getIssues()) {
                System.err.printf("[%s] %s at %s: %s (Fix: %s)%n",
                        issue.severity(),
                        issue.category(),
                        issue.path(),
                        issue.message(),
                        issue.suggestion());
            }
            throw new IllegalArgumentException("Invalid FHIR Bundle");
        }

        System.out.println("Quality Score: " + report.score().overallScore() + "/100 (" + report.score().grade() + ")");
    }
}
```
