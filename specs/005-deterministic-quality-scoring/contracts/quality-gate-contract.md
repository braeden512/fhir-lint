# Contract: Quality Gate Evaluation Contract

## Purpose

Defines the contract for CI/CD quality gate policy enforcement in FHIRLint, including input configuration options, pass/fail evaluation rules, and output breach diagnostics.

---

## 1. Quality Gate Configuration (`QualityGateConfig`)

```java
package org.fhirlint.core.model;

import java.util.Optional;

/**
 * Configuration options for quality gate threshold evaluation.
 */
public record QualityGateConfig(
    Integer minScore,
    Severity failOn
) {
    public QualityGateConfig {
        if (minScore != null && minScore < 0) {
            throw new IllegalArgumentException("minScore cannot be negative, was: " + minScore);
        }
    }

    public static QualityGateConfig of(Integer minScore, Severity failOn) {
        return new QualityGateConfig(minScore, failOn);
    }

    public static QualityGateConfig minScore(int minScore) {
        return new QualityGateConfig(minScore, null);
    }

    public static QualityGateConfig failOn(Severity failOn) {
        return new QualityGateConfig(null, failOn);
    }

    public static QualityGateConfig none() {
        return new QualityGateConfig(null, null);
    }
}
```

---

## 2. Quality Gate Result (`QualityGateResult`)

```java
package org.fhirlint.core.model;

import java.util.Collections;
import java.util.List;

/**
 * Result of evaluating quality gate policies against a dataset analysis.
 */
public record QualityGateResult(
    boolean passed,
    List<String> breaches
) {
    public QualityGateResult {
        breaches = breaches == null ? List.of() : List.copyOf(breaches);
    }

    public static QualityGateResult pass() {
        return new QualityGateResult(true, List.of());
    }

    public static QualityGateResult fail(List<String> breaches) {
        return new QualityGateResult(false, breaches);
    }
}
```

---

## 3. Evaluation Rules & Logic

Given `QualityScore score` with recorded `errorCount`, `warningCount`, and `infoCount`:

1. **Minimum Score Evaluation**:
   - Condition: `config.minScore() != null && score.getOverallScore() < config.minScore()`.
   - Breach message format:
     `"Overall quality score (" + score.getOverallScore() + ") is below required minimum threshold (" + config.minScore() + ")."`

2. **Severity Threshold Evaluation**:
   - Evaluated in $O(1)$ from `score` metrics:
     - If `config.failOn() == ERROR`: matching count = `score.getErrorCount()`.
     - If `config.failOn() == WARNING`: matching count = `score.getErrorCount() + score.getWarningCount()`.
     - If `config.failOn() == INFO`: matching count = `score.getErrorCount() + score.getWarningCount() + score.getInfoCount()`.
   - Condition: `config.failOn() != null && matchingCount > 0`.
   - Breach message format:
     `"Dataset contains " + matchingCount + " issue(s) with severity " + config.failOn() + " or higher."`

3. **Multi-Breach Non-Short-Circuiting**:
   - If both conditions trigger, both breach messages are appended to `breaches`.
   - Evaluation returns `QualityGateResult.fail(breaches)`.
   - If neither condition triggers, evaluation returns `QualityGateResult.pass()`.

4. **LintReport Delegation & Backward-Compatible Overloads**:
   ```java
   public QualityGateResult evaluateGate(QualityGateConfig config) {
       return qualityScore.evaluateGate(config);
   }

   public boolean passes(QualityGateConfig config) {
       return evaluateGate(config).passed();
   }

   public boolean passes(int minScore, Severity failOn) {
       return passes(QualityGateConfig.of(minScore, failOn));
   }
   ```

---

## 4. CLI Quality Gate Exit Code Mapping

When invoked via Picocli CLI (`fhir-lint validate`):

| Result | Exit Code | Terminal Output |
| :--- | :---: | :--- |
| `passed == true` | `0` | Standard ANSI/JSON/SARIF output. |
| `passed == false` | `1` | Standard output plus summary of quality gate breaches printed to stderr. |
| Syntax / Parsing / I/O Error | `2` | Error message printed to stderr. |
