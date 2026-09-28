# Contract: Scoring Engine Programmatic API

## Purpose

Defines the Java API contract for calculating deterministic quality scores and inspecting score breakdowns within the `fhir-lint-core` library.

---

## 1. Class: `QualityScore`

```java
package org.fhirlint.core.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Deterministic quality scoring model adhering to Section 6 of PRODUCT_SPEC.md and ADR-005.
 */
public final class QualityScore {

    public static final String NON_CLINICAL_DISCLAIMER =
        "Quality scores produced by FHIRLint reflect technical data hygiene and engineering standards rather than clinical, medical, or regulatory compliance measurements.";

    public enum Grade {
        EXCELLENT(90, 100, "High integrity, safe for automated ingestion"),
        ACCEPTABLE(75, 89, "Minor non-critical warnings; downstream review recommended"),
        DEGRADED(50, 74, "Contains broken references or chronological inconsistencies"),
        CRITICAL(0, 49, "Severe structural or relational failures; ingestion should halt");

        private final int minScore;
        private final int maxScore;
        private final String description;

        Grade(int minScore, int maxScore, String description) {
            this.minScore = minScore;
            this.maxScore = maxScore;
            this.description = description;
        }

        public int getMinScore() { return minScore; }
        public int getMaxScore() { return maxScore; }
        public String getDescription() { return description; }

        public static Grade forScore(int score) {
            if (score >= 90) return EXCELLENT;
            if (score >= 75) return ACCEPTABLE;
            if (score >= 50) return DEGRADED;
            return CRITICAL;
        }
    }

    // Constructors (Primary + Backward-Compatible Overload)
    public QualityScore(int overallScore, Grade grade, Map<IssueCategory, Integer> categoryScores,
                        Map<IssueCategory, CategoryScoreDetail> categoryDetails,
                        int errorCount, int warningCount, int infoCount);

    public QualityScore(int overallScore, Grade grade, Map<IssueCategory, Integer> categoryScores,
                        int errorCount, int warningCount, int infoCount);

    // Accessors
    public int getOverallScore();
    public Grade getGrade();
    public Map<IssueCategory, Integer> getCategoryScores(); // Exactly the 6 scored canonical categories
    public Map<IssueCategory, CategoryScoreDetail> getCategoryDetails(); // Exactly the 6 scored canonical categories
    public int getErrorCount();
    public int getWarningCount();
    public int getInfoCount();
    public String getDisclaimer();

    /**
     * Evaluates quality gate configuration against this score metrics in O(1).
     */
    public QualityGateResult evaluateGate(QualityGateConfig config);

    /**
     * Computes deterministic score from total resources and detected issues.
     */
    public static QualityScore calculate(int totalResources, List<QualityIssue> issues);
}
```

---

## 2. Class: `LintReport` (Quality Gate Extensions)

```java
package org.fhirlint.core.model;

public record LintReport(
    ValidationProfile targetProfile,
    IngestionInventory inventory,
    List<QualityIssue> issues,
    QualityScore qualityScore,
    long durationMs
) {
    /**
     * Evaluates quality gate policies against this report.
     */
    public QualityGateResult evaluateGate(QualityGateConfig config) {
        return qualityScore.evaluateGate(config);
    }

    public boolean passes(QualityGateConfig config) {
        return evaluateGate(config).passed();
    }

    /**
     * Backward-compatible overload for existing Phase 1-4 tests and callers.
     */
    public boolean passes(int minScore, Severity failOn) {
        return passes(QualityGateConfig.of(minScore, failOn));
    }
}
```

---

## 2. Record: `CategoryScoreDetail`

```java
package org.fhirlint.core.model;

/**
 * Detailed explainability breakdown for a single quality scoring category.
 */
public record CategoryScoreDetail(
    IssueCategory category,
    String displayName,
    double weight,
    int errorCount,
    int warningCount,
    int infoCount,
    int defectPenalty,
    double densityFactor,
    int score,
    double weightedContribution
) {}
```

---

## 3. Usage Example (Pure Java Library)

```java
import org.fhirlint.core.model.*;
import java.util.List;

// 1. Compute score
List<QualityIssue> issues = List.of(
    new QualityIssue("iss-1", Severity.ERROR, IssueCategory.REFERENTIAL_INTEGRITY, "Observation", "obs-1", "subject.reference", "Broken ref", "REF-001", "Fix ref"),
    new QualityIssue("iss-2", Severity.WARNING, IssueCategory.TERMINOLOGY, "Observation", "obs-1", "code.system", "Non-canonical URI", "TERM-001", "Use canonical")
);

QualityScore score = QualityScore.calculate(10, issues);

// 2. Inspect aggregate metrics
int overall = score.getOverallScore();               // e.g. 96
QualityScore.Grade grade = score.getGrade();         // EXCELLENT
String disclaimer = score.getDisclaimer();           // Non-clinical disclaimer

// 3. Inspect category breakdown
CategoryScoreDetail refDetail = score.getCategoryDetails().get(IssueCategory.REFERENTIAL_INTEGRITY);
int refScore = refDetail.score();                    // 90
int refPenalty = refDetail.defectPenalty();          // 15
double refDensity = refDetail.densityFactor();        // 0.10

// 4. Quality gate evaluation
QualityGateConfig gateConfig = QualityGateConfig.of(80, Severity.ERROR);
QualityGateResult gateResult = score.evaluateGate(gateConfig);
boolean passes = gateResult.passed();                // false (contains ERROR)
List<String> breaches = gateResult.breaches();       // ["Dataset contains 1 issue(s) with severity ERROR or higher."]
```
