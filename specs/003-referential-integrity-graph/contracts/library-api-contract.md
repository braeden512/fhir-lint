# Library API Contract: `fhir-lint-core` (Phase 3 — Referential Integrity)

This document defines the Java library API contract for referential integrity and resource graph analysis within `fhir-lint-core`.

---

## 1. Primary Entry Point: `FhirLinter`

The `FhirLinter` orchestrates parsing, structural/profile validation, and referential integrity analysis into an aggregated `LintReport`:

```java
package org.fhirlint.core;

import org.fhirlint.core.model.LintReport;
import org.fhirlint.core.model.ValidationProfile;
import org.fhirlint.core.graph.ReferentialIntegrityEngine;

import java.io.File;
import java.io.InputStream;
import java.util.List;

public class FhirLinter {

    /**
     * Creates a default FhirLinter configured with US_CORE profile and standard referential engine.
     */
    public static FhirLinter create();

    /**
     * Configures target validation profile (e.g. US_CORE, BASE_R4).
     */
    public FhirLinter withProfile(ValidationProfile profile);

    /**
     * Configures a custom ReferentialIntegrityEngine instance.
     */
    public FhirLinter withReferentialIntegrityEngine(ReferentialIntegrityEngine engine);

    /**
     * Returns the active referential integrity engine.
     */
    public ReferentialIntegrityEngine getReferentialIntegrityEngine();

    /**
     * Lints a single FHIR JSON file.
     */
    public LintReport lint(File file);

    /**
     * Lints a FHIR JSON string.
     */
    public LintReport lint(String jsonContent);

    /**
     * Lints a FHIR JSON InputStream.
     */
    public LintReport lint(InputStream inputStream);

    /**
     * Lints multiple FHIR JSON files, aggregating all resources into a single unified
     * ResourceGraphIndex before performing referential integrity and validation passes.
     */
    public LintReport lint(List<File> files);
}
```

---

## 2. Referential Integrity Engine: `ReferentialIntegrityEngine`

```java
package org.fhirlint.core.graph;

import org.fhirlint.core.model.QualityIssue;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;

import java.util.List;

public interface ReferentialIntegrityEngine {

    /**
     * Creates a new instance of the default in-memory ReferentialIntegrityEngine.
     */
    static ReferentialIntegrityEngine create() {
        return new DefaultReferentialIntegrityEngine();
    }

    /**
     * Analyzes a collection of in-memory FHIR resources, constructing a ResourceGraphIndex
     * and evaluating all inter-resource references for defects.
     *
     * @param resources List of parsed FHIR resources to analyze
     * @return List of detected QualityIssue findings in category REFERENTIAL_INTEGRITY
     */
    List<QualityIssue> analyze(List<IBaseResource> resources);

    /**
     * Analyzes entries within a FHIR Bundle resource.
     *
     * @param bundle Parsed Bundle resource
     * @return List of detected QualityIssue findings in category REFERENTIAL_INTEGRITY
     */
    List<QualityIssue> analyze(Bundle bundle);

    /**
     * Builds and returns an indexed ResourceGraphIndex from the provided resources.
     * Useful for applications that want to inspect the relationship graph directly.
     *
     * @param resources List of parsed FHIR resources
     * @return In-memory ResourceGraphIndex
     */
    ResourceGraphIndex buildIndex(List<IBaseResource> resources);
}
```

---

## 3. Relationship Graph Index: `ResourceGraphIndex`

```java
package org.fhirlint.core.graph;

import org.hl7.fhir.instance.model.api.IBaseResource;

import java.util.List;
import java.util.Optional;

public interface ResourceGraphIndex {

    /**
     * Look up a resource node by its fullUrl (e.g. urn:uuid:... or canonical URL).
     */
    Optional<ResourceNode> findByFullUrl(String fullUrl);

    /**
     * Look up a resource node by resource type and logical ID (e.g. "Patient/123").
     */
    Optional<ResourceNode> findByTypeAndId(String resourceType, String id);

    /**
     * Look up resource nodes matching a bare ID without type prefix.
     */
    List<ResourceNode> findByBareId(String id);

    /**
     * Resolves a reference to its target node or determines unresolvability status.
     */
    ReferenceResolution resolve(ResourceReference reference);

    /**
     * Determines whether the given resource node has a path to a Patient node.
     */
    boolean isReachableToPatient(ResourceNode node);

    /**
     * Returns all indexed resource nodes.
     */
    List<ResourceNode> getAllNodes();
}
```

---

## 4. Usage Example

```java
import org.fhirlint.core.FhirLinter;
import org.fhirlint.core.model.LintReport;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.IssueCategory;

import java.io.File;

public class ETLPipeline {

    private final FhirLinter linter = FhirLinter.create();

    public void processDataset(File datasetFile) {
        LintReport report = linter.lint(datasetFile);

        // Inspect referential integrity category score
        int refScore = report.score().getCategoryScores().getOrDefault(IssueCategory.REFERENTIAL_INTEGRITY, 100);
        System.out.printf("Referential Integrity Score: %d/100%n", refScore);

        // Filter and inspect referential issues
        report.issues().stream()
            .filter(i -> i.category() == IssueCategory.REFERENTIAL_INTEGRITY)
            .forEach(issue -> {
                System.out.printf("[%s] %s: %s (Fix: %s)%n",
                    issue.ruleId(), issue.path(), issue.message(), issue.suggestion());
            });
    }
}
```
