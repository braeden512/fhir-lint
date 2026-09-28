package org.fhirlint.core.model;

/**
 * Detailed explainability breakdown for a single quality scoring category.
 *
 * @param category the quality category
 * @param displayName human-readable display label
 * @param weight canonical weighting factor in overall composite score
 * @param errorCount number of error-severity issues mapped to this category
 * @param warningCount number of warning-severity issues mapped to this category
 * @param infoCount number of informational issues mapped to this category
 * @param defectPenalty total computed defect penalty: (errors * 15) + (warnings * 3)
 * @param densityFactor defect density relative to volume: penalty / (max(N, 1) * 15)
 * @param score clamped category score in range [0, 100]
 * @param weightedContribution contribution to composite overall score: weight * score
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
