# Interface Contract: Quality Rule API

**Feature Branch**: `004-rule-engine-data-quality` | **Date**: 2026-09-27  
**Specification**: [`specs/004-rule-engine-data-quality/spec.md`](../spec.md)

---

## 1. Overview

The Quality Rule API provides the public contract for defining, registering, and executing data quality rules in `fhir-lint-core`. It is framework-agnostic, strictly stateless, and runs entirely in-memory with zero external database dependencies.

---

## 2. Java Interface Contracts

### 2.1 `QualityRule`

```java
package org.fhirlint.core.rules;

import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;

import java.util.List;
import java.util.Set;

/**
 * Base contract for pluggable healthcare data quality checks.
 */
public interface QualityRule {

    /**
     * Unique identifier for this rule (e.g. "CONS-001", "DUP-001").
     */
    String getRuleId();

    /**
     * Human-readable display name for reporting.
     */
    String getName();

    /**
     * Category of quality defect evaluated by this rule.
     */
    IssueCategory getCategory();

    /**
     * Default severity level assigned to issues emitted by this rule.
     */
    Severity getDefaultSeverity();

    /**
     * Execution scope (RESOURCE for per-resource evaluation, DATASET for global population analysis).
     */
    RuleScope getScope();

    /**
     * Resource types to which this rule applies (e.g. Set.of("Encounter", "Coverage")).
     * An empty set denotes universal applicability across all resource types.
     */
    Set<String> getApplicableResourceTypes();

    /**
     * Evaluates this rule against the provided context.
     *
     * @param context the evaluation environment exposing the resource, dataset, graph index, and terminology
     * @return list of detected quality issues (empty if no issues found)
     */
    List<QualityIssue> evaluate(RuleContext context);
}
```

### 2.2 `RuleScope`

```java
package org.fhirlint.core.rules;

public enum RuleScope {
    /**
     * Evaluated once per resource whose type matches getApplicableResourceTypes().
     * RuleContext.getResource() is non-null.
     */
    RESOURCE,

    /**
     * Evaluated once per dataset collection.
     * RuleContext.getDataset() is non-empty and RuleContext.getResource() is null.
     */
    DATASET
}
```

### 2.3 `RuleContext`

```java
package org.fhirlint.core.rules;

import org.fhirlint.core.graph.ResourceGraphIndex;
import org.fhirlint.core.rules.terminology.TerminologyService;
import org.hl7.fhir.instance.model.api.IBaseResource;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Evaluation context passed to QualityRule implementations.
 */
public class RuleContext {

    private final IBaseResource resource;
    private final List<IBaseResource> dataset;
    private final ResourceGraphIndex graphIndex;
    private final TerminologyService terminologyService;

    public RuleContext(
            IBaseResource resource,
            List<IBaseResource> dataset,
            ResourceGraphIndex graphIndex,
            TerminologyService terminologyService) {
        this.resource = resource;
        this.dataset = dataset != null ? Collections.unmodifiableList(dataset) : Collections.emptyList();
        this.graphIndex = graphIndex;
        this.terminologyService = terminologyService;
    }

    /**
     * Returns the target resource when evaluating a RESOURCE-scoped rule, or empty if DATASET-scoped.
     */
    public Optional<IBaseResource> getResource() {
        return Optional.ofNullable(resource);
    }

    /**
     * Returns all resources in the active dataset.
     */
    public List<IBaseResource> getDataset() {
        return dataset;
    }

    /**
     * Returns the pre-computed ResourceGraphIndex for cross-resource relationship resolution.
     */
    public ResourceGraphIndex getGraphIndex() {
        return graphIndex;
    }

    /**
     * Returns the terminology service for code and system validation.
     */
    public TerminologyService getTerminologyService() {
        return terminologyService;
    }
}
```

### 2.4 `QualityRuleEngine`

```java
package org.fhirlint.core.rules;

import org.fhirlint.core.graph.ResourceGraphIndex;
import org.fhirlint.core.model.QualityIssue;
import org.hl7.fhir.instance.model.api.IBaseResource;

import java.util.List;

public interface QualityRuleEngine {

    /**
     * Evaluates all registered quality rules against the given dataset and pre-computed graph index.
     *
     * @param dataset list of parsed FHIR resources
     * @param graphIndex pre-computed graph relationship index
     * @return list of detected quality issues
     */
    List<QualityIssue> evaluate(List<? extends IBaseResource> dataset, ResourceGraphIndex graphIndex);
}
```
