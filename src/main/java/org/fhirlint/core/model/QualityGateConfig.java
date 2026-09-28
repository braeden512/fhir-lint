package org.fhirlint.core.model;

/**
 * Configuration options for quality gate threshold evaluation.
 *
 * @param minScore minimum acceptable quality score (0-100, or higher to force failure; non-negative)
 * @param failOn maximum permissible issue severity before triggering gate failure
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
