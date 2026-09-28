package org.fhirlint.core.model;

import java.util.Collections;
import java.util.List;

/**
 * Root result produced by FHIRLint.
 */
public record LintReport(
    ValidationProfile targetProfile,
    IngestionInventory inventory,
    List<QualityIssue> issues,
    QualityScore qualityScore,
    long durationMs
) {
    public LintReport {
        issues = issues == null ? Collections.emptyList() : Collections.unmodifiableList(issues);
    }

    public boolean hasErrors() {
        return issues.stream().anyMatch(i -> i.severity() == Severity.ERROR);
    }

    public boolean hasWarnings() {
        return issues.stream().anyMatch(i -> i.severity() == Severity.WARNING);
    }

    public long getErrorCount() {
        return issues.stream().filter(i -> i.severity() == Severity.ERROR).count();
    }

    public ValidationProfile getTargetProfile() {
        return targetProfile;
    }

    public List<QualityIssue> getIssues() {
        return issues;
    }

    public QualityScore getQualityScore() {
        return qualityScore;
    }

    public QualityGateResult evaluateGate(QualityGateConfig config) {
        return qualityScore.evaluateGate(config);
    }

    public boolean passes(QualityGateConfig config) {
        return evaluateGate(config).passed();
    }

    public boolean passes(int minScore, Severity failOn) {
        return passes(QualityGateConfig.of(minScore, failOn));
    }
}
