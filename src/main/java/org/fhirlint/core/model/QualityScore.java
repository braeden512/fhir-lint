package org.fhirlint.core.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic quality scoring model adhering to Section 6 of PRODUCT_SPEC.md.
 */
public class QualityScore {

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

    public static final String NON_CLINICAL_DISCLAIMER =
        "Quality scores produced by FHIRLint reflect technical data hygiene and engineering standards rather than clinical, medical, or regulatory compliance measurements.";

    private final int overallScore;
    private final Grade grade;
    private final Map<IssueCategory, Integer> categoryScores;
    private final Map<IssueCategory, CategoryScoreDetail> categoryDetails;
    private final int errorCount;
    private final int warningCount;
    private final int infoCount;

    public QualityScore(int overallScore, Grade grade, Map<IssueCategory, Integer> categoryScores,
                        Map<IssueCategory, CategoryScoreDetail> categoryDetails,
                        int errorCount, int warningCount, int infoCount) {
        this.overallScore = overallScore;
        this.grade = grade;
        this.categoryScores = categoryScores == null ? Collections.emptyMap() : Collections.unmodifiableMap(categoryScores);
        this.categoryDetails = categoryDetails == null ? Collections.emptyMap() : Collections.unmodifiableMap(categoryDetails);
        this.errorCount = errorCount;
        this.warningCount = warningCount;
        this.infoCount = infoCount;
    }

    public QualityScore(int overallScore, Grade grade, Map<IssueCategory, Integer> categoryScores,
                        int errorCount, int warningCount, int infoCount) {
        this(overallScore, grade, categoryScores, Collections.emptyMap(), errorCount, warningCount, infoCount);
    }

    public int getOverallScore() { return overallScore; }
    public Grade getGrade() { return grade; }
    public EngineeringGrade getEngineeringGrade() { return EngineeringGrade.valueOf(grade.name()); }
    public Map<IssueCategory, Integer> getCategoryScores() { return categoryScores; }
    public Map<IssueCategory, CategoryScoreDetail> getCategoryDetails() { return categoryDetails; }
    public String getDisclaimer() { return NON_CLINICAL_DISCLAIMER; }
    public int getErrorCount() { return errorCount; }
    public int getWarningCount() { return warningCount; }
    public int getInfoCount() { return infoCount; }

    /**
     * Evaluates quality gate policy rules against this score in O(1) time complexity.
     *
     * @param config the quality gate configuration
     * @return the evaluation result containing pass/fail verdict and any breach descriptions
     */
    public QualityGateResult evaluateGate(QualityGateConfig config) {
        if (config == null) {
            return QualityGateResult.pass();
        }

        java.util.List<String> breaches = new java.util.ArrayList<>();

        if (config.minScore() != null && overallScore < config.minScore()) {
            breaches.add("Overall quality score (" + overallScore + ") is below required minimum threshold (" + config.minScore() + ").");
        }

        if (config.failOn() != null) {
            int matchingCount = switch (config.failOn()) {
                case ERROR -> errorCount;
                case WARNING -> errorCount + warningCount;
                case INFO -> errorCount + warningCount + infoCount;
            };
            if (matchingCount > 0) {
                breaches.add("Dataset contains " + matchingCount + " issue(s) with severity " + config.failOn() + " or higher.");
            }
        }

        return breaches.isEmpty() ? QualityGateResult.pass() : QualityGateResult.fail(breaches);
    }

    /**
     * Computes deterministic score from total resources and detected issues.
     */
    public static QualityScore calculate(int totalResources, List<QualityIssue> issues) {
        int safeTotal = Math.max(totalResources, 1);
        List<QualityIssue> safeIssues = issues == null ? Collections.emptyList() : issues;

        Map<IssueCategory, Integer> errorsPerCategory = new EnumMap<>(IssueCategory.class);
        Map<IssueCategory, Integer> warningsPerCategory = new EnumMap<>(IssueCategory.class);
        Map<IssueCategory, Integer> infoPerCategory = new EnumMap<>(IssueCategory.class);

        int totalErrors = 0;
        int totalWarnings = 0;
        int totalInfo = 0;

        for (IssueCategory cat : IssueCategory.values()) {
            errorsPerCategory.put(cat, 0);
            warningsPerCategory.put(cat, 0);
            infoPerCategory.put(cat, 0);
        }

        for (QualityIssue issue : safeIssues) {
            IssueCategory cat = issue.category().scoringCategory();
            switch (issue.severity()) {
                case ERROR -> {
                    errorsPerCategory.put(cat, errorsPerCategory.get(cat) + 1);
                    totalErrors++;
                }
                case WARNING -> {
                    warningsPerCategory.put(cat, warningsPerCategory.get(cat) + 1);
                    totalWarnings++;
                }
                case INFO -> {
                    infoPerCategory.put(cat, infoPerCategory.get(cat) + 1);
                    totalInfo++;
                }
            }
        }

        Map<IssueCategory, Integer> categoryScores = new EnumMap<>(IssueCategory.class);
        Map<IssueCategory, CategoryScoreDetail> categoryDetails = new EnumMap<>(IssueCategory.class);
        double weightedSum = 0.0;
        double totalActiveWeight = 0.0;

        for (IssueCategory cat : IssueCategory.values()) {
            if (!cat.isScored()) {
                continue;
            }
            int nErrors = errorsPerCategory.get(cat);
            int nWarnings = warningsPerCategory.get(cat);
            int nInfo = infoPerCategory.get(cat);

            int defectPenalty = (nErrors * 15) + (nWarnings * 3);
            double densityFactor = (double) defectPenalty / (safeTotal * 15.0);
            int catScore = (int) Math.round(Math.max(0, 100.0 * (1.0 - Math.min(1.0, densityFactor))));
            categoryScores.put(cat, catScore);

            double weightedContribution = cat.getWeight() * catScore;
            categoryDetails.put(cat, new CategoryScoreDetail(
                cat,
                cat.getDisplayName(),
                cat.getWeight(),
                nErrors,
                nWarnings,
                nInfo,
                defectPenalty,
                densityFactor,
                catScore,
                weightedContribution
            ));

            if (cat.getWeight() > 0.0) {
                weightedSum += weightedContribution;
                totalActiveWeight += cat.getWeight();
            }
        }

        int overall = totalActiveWeight > 0 
            ? (int) Math.round(weightedSum / totalActiveWeight)
            : 100;
        overall = Math.clamp(overall, 0, 100);

        return new QualityScore(
            overall,
            Grade.forScore(overall),
            categoryScores,
            categoryDetails,
            totalErrors,
            totalWarnings,
            totalInfo
        );
    }
}
