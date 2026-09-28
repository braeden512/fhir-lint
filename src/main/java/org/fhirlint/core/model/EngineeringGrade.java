package org.fhirlint.core.model;

/**
 * Four-tier engineering grade classification for quality scores.
 * Corresponds to {@link QualityScore.Grade}.
 */
public enum EngineeringGrade {
    EXCELLENT(90, 100, "High integrity, safe for automated ingestion"),
    ACCEPTABLE(75, 89, "Minor non-critical warnings; downstream review recommended"),
    DEGRADED(50, 74, "Contains broken references or chronological inconsistencies"),
    CRITICAL(0, 49, "Severe structural or relational failures; ingestion should halt");

    private final int minScore;
    private final int maxScore;
    private final String description;

    EngineeringGrade(int minScore, int maxScore, String description) {
        this.minScore = minScore;
        this.maxScore = maxScore;
        this.description = description;
    }

    public int getMinScore() {
        return minScore;
    }

    public int getMaxScore() {
        return maxScore;
    }

    public String getDescription() {
        return description;
    }

    public static EngineeringGrade forScore(int score) {
        if (score >= 90) return EXCELLENT;
        if (score >= 75) return ACCEPTABLE;
        if (score >= 50) return DEGRADED;
        return CRITICAL;
    }
}
