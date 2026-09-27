package com.braeden.fhirlint.core.model;

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

    public boolean passes(int minScore, Severity failOn) {
        if (qualityScore.getOverallScore() < minScore) {
            return false;
        }
        if (failOn != null) {
            return issues.stream().noneMatch(i -> i.severity().isAtLeast(failOn));
        }
        return true;
    }
}
