package org.fhirlint.core.model;

import java.util.List;

/**
 * Result of evaluating quality gate policies against a dataset analysis.
 *
 * @param passed true if all configured quality gate criteria were satisfied
 * @param breaches unmodifiable list of human-readable rule breach descriptions (empty on pass)
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
