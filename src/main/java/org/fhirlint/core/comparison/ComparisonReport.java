package org.fhirlint.core.comparison;

import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.LintReport;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable report representing the difference and regression status between two LintReports.
 */
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
    public ComparisonReport {
        Objects.requireNonNull(baseline, "baseline must not be null");
        Objects.requireNonNull(target, "target must not be null");
        categoryDeltas = categoryDeltas == null ? Collections.emptyMap() : Collections.unmodifiableMap(categoryDeltas);
        resourceCountDeltas = resourceCountDeltas == null ? Collections.emptyMap() : Collections.unmodifiableMap(resourceCountDeltas);
        newIssues = newIssues == null ? Collections.emptyList() : Collections.unmodifiableList(newIssues);
        resolvedIssues = resolvedIssues == null ? Collections.emptyList() : Collections.unmodifiableList(resolvedIssues);
        persistentIssues = persistentIssues == null ? Collections.emptyList() : Collections.unmodifiableList(persistentIssues);
    }

    public long getNewErrorCount() {
        return newIssues.stream().filter(i -> i.severity() == Severity.ERROR).count();
    }

    public long getNewWarningCount() {
        return newIssues.stream().filter(i -> i.severity() == Severity.WARNING).count();
    }

    public long getResolvedErrorCount() {
        return resolvedIssues.stream().filter(i -> i.severity() == Severity.ERROR).count();
    }

    public long getResolvedWarningCount() {
        return resolvedIssues.stream().filter(i -> i.severity() == Severity.WARNING).count();
    }

    public long getPersistentErrorCount() {
        return persistentIssues.stream().filter(i -> i.severity() == Severity.ERROR).count();
    }

    public long getPersistentWarningCount() {
        return persistentIssues.stream().filter(i -> i.severity() == Severity.WARNING).count();
    }

    /**
     * Evaluates whether quality gates (score drop or regressions) passed or failed.
     *
     * @param failOnRegression if true, fails on any new error-level defect or score drop
     * @param maxScoreDrop optional maximum allowed score drop
     * @return gate outcome
     */
    public ComparisonGateResult evaluateGates(boolean failOnRegression, Integer maxScoreDrop) {
        boolean scoreDropViolated = false;
        boolean regressionViolated = false;
        StringBuilder reason = new StringBuilder();

        // Check max score drop
        if (maxScoreDrop != null && maxScoreDrop >= 0) {
            int drop = baseline.getQualityScore().getOverallScore() - target.getQualityScore().getOverallScore();
            if (drop > maxScoreDrop) {
                scoreDropViolated = true;
                reason.append("Target score dropped by ").append(drop)
                      .append(" points (maximum allowed: ").append(maxScoreDrop).append("). ");
            }
        }

        // Check fail-on-regression
        if (failOnRegression) {
            long newErrors = getNewErrorCount();
            int scoreDrop = baseline.getQualityScore().getOverallScore() - target.getQualityScore().getOverallScore();
            if (newErrors > 0 || scoreDrop > 0) {
                regressionViolated = true;
                if (newErrors > 0) {
                    reason.append(newErrors).append(" new error-level issue(s) detected. ");
                }
                if (scoreDrop > 0 && !scoreDropViolated) {
                    reason.append("Score dropped by ").append(scoreDrop).append(" points. ");
                }
            }
        }

        if (scoreDropViolated || regressionViolated) {
            return ComparisonGateResult.failure(scoreDropViolated, regressionViolated, reason.toString().trim());
        }
        return ComparisonGateResult.pass();
    }
}
