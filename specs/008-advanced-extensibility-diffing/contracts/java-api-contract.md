# Contract: Core Java API for Dataset Comparison

**Package**: `org.fhirlint.core.comparison`

---

## 1. `DatasetComparator`

Pure framework-agnostic Java service for in-memory dataset diffing.

```java
package org.fhirlint.core.comparison;

import org.fhirlint.core.model.LintReport;

public final class DatasetComparator {

    /**
     * Compares two in-memory LintReports and produces an immutable ComparisonReport.
     *
     * @param baseline the reference baseline dataset lint report (must not be null)
     * @param target the target dataset lint report (must not be null)
     * @return the comparison differential report
     * @throws NullPointerException if either report is null
     */
    public static ComparisonReport compare(LintReport baseline, LintReport target) {
        // Implementation logic
    }
}
```

---

## 2. `ComparisonReport`

Immutable model encapsulating all comparison results.

```java
package org.fhirlint.core.comparison;

import org.fhirlint.core.model.LintReport;
import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import java.util.List;
import java.util.Map;

public record ComparisonReport(
    LintReport baseline,
    LintReport target,
    int scoreDelta,
    Map<IssueCategory, Integer> categoryDeltas,
    Map<String, Integer> resourceCountDeltas,
    List<QualityIssue> newIssues,
    List<QualityIssue> resolvedIssues,
    List<QualityIssue> persistentIssues
) {
    /**
     * Evaluates quality gates against the comparison results.
     *
     * @param failOnRegression if true, fails on any new error-level defect or score drop
     * @param maxScoreDrop optional maximum allowed score drop
     * @return the gate evaluation outcome
     */
    public ComparisonGateResult evaluateGates(boolean failOnRegression, Integer maxScoreDrop) {
        // Evaluation logic
    }
}
```

---

## 3. Custom Rule Engine Integration

**Package**: `org.fhirlint.core.rules.custom`

```java
package org.fhirlint.core.rules.custom;

import org.fhirlint.core.rules.QualityRule;
import java.nio.file.Path;
import java.util.List;

public final class CustomRuleLoader {

    /**
     * Loads and pre-compiles custom YAML rules from a file, comma-separated list, or directory.
     *
     * @param paths rule file/directory paths
     * @return list of executable QualityRule instances
     * @throws CustomRuleException if YAML is malformed or FHIRPath expression has syntax errors
     */
    public static List<QualityRule> loadRules(String... paths) {
        // Parsing and AST pre-compilation
    }
}
```
