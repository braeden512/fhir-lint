package org.fhirlint.core;

import org.fhirlint.core.model.EngineeringGrade;
import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.QualityScore;
import org.fhirlint.core.model.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityScoreOverallTest {

    private QualityIssue createIssue(String ruleId, IssueCategory category, Severity severity) {
        return QualityIssue.builder()
                .ruleId(ruleId)
                .category(category)
                .severity(severity)
                .message("Test issue")
                .resourceType("Patient")
                .resourceId("123")
                .path("Patient.name")
                .suggestion("Fix issue")
                .build();
    }

    @Test
    @DisplayName("Grade boundary: 90 is EXCELLENT, 89 is ACCEPTABLE")
    void testExcellentBoundary() {
        assertEquals(QualityScore.Grade.EXCELLENT, QualityScore.Grade.forScore(100));
        assertEquals(QualityScore.Grade.EXCELLENT, QualityScore.Grade.forScore(90));
        assertEquals(QualityScore.Grade.ACCEPTABLE, QualityScore.Grade.forScore(89));

        assertEquals(EngineeringGrade.EXCELLENT, EngineeringGrade.forScore(100));
        assertEquals(EngineeringGrade.EXCELLENT, EngineeringGrade.forScore(90));
        assertEquals(EngineeringGrade.ACCEPTABLE, EngineeringGrade.forScore(89));
    }

    @Test
    @DisplayName("Grade boundary: 75 is ACCEPTABLE, 74 is DEGRADED")
    void testAcceptableBoundary() {
        assertEquals(QualityScore.Grade.ACCEPTABLE, QualityScore.Grade.forScore(75));
        assertEquals(QualityScore.Grade.DEGRADED, QualityScore.Grade.forScore(74));

        assertEquals(EngineeringGrade.ACCEPTABLE, EngineeringGrade.forScore(75));
        assertEquals(EngineeringGrade.DEGRADED, EngineeringGrade.forScore(74));
    }

    @Test
    @DisplayName("Grade boundary: 50 is DEGRADED, 49 is CRITICAL")
    void testDegradedBoundary() {
        assertEquals(QualityScore.Grade.DEGRADED, QualityScore.Grade.forScore(50));
        assertEquals(QualityScore.Grade.CRITICAL, QualityScore.Grade.forScore(49));
        assertEquals(QualityScore.Grade.CRITICAL, QualityScore.Grade.forScore(0));

        assertEquals(EngineeringGrade.DEGRADED, EngineeringGrade.forScore(50));
        assertEquals(EngineeringGrade.CRITICAL, EngineeringGrade.forScore(49));
        assertEquals(EngineeringGrade.CRITICAL, EngineeringGrade.forScore(0));
    }

    @Test
    @DisplayName("Composite score calculation correctly applies category weights")
    void compositeScoreAppliesCategoryWeights() {
        // N = 10 resources
        // 1 error in REFERENTIAL_INTEGRITY (weight 0.25):
        //   Ref defect penalty = 15 -> density = 15/150 = 0.1 -> score = 90
        // All other 5 categories have 0 issues -> score = 100
        // Expected weighted sum:
        //   0.25 * 90 + 0.20 * 100 + 0.20 * 100 + 0.15 * 100 + 0.10 * 100 + 0.10 * 100
        //   = 22.5 + 20 + 20 + 15 + 10 + 10 = 97.5
        // Round half-up: 98
        QualityIssue refError = createIssue("REF-001", IssueCategory.REFERENTIAL_INTEGRITY, Severity.ERROR);
        QualityScore score = QualityScore.calculate(10, List.of(refError));

        assertEquals(90, score.getCategoryScores().get(IssueCategory.REFERENTIAL_INTEGRITY));
        assertEquals(98, score.getOverallScore());
        assertEquals(QualityScore.Grade.EXCELLENT, score.getGrade());
    }

    @Test
    @DisplayName("Deterministic reproducibility: 10,000 iterations produce bit-identical scores")
    void testDeterministicReproducibility() {
        List<QualityIssue> sampleIssues = List.of(
                createIssue("STR-001", IssueCategory.STRUCTURAL, Severity.ERROR),
                createIssue("REF-001", IssueCategory.REFERENTIAL_INTEGRITY, Severity.WARNING),
                createIssue("DUP-001", IssueCategory.DUPLICATE, Severity.WARNING),
                createIssue("TRM-001", IssueCategory.TERMINOLOGY, Severity.INFO)
        );

        QualityScore baseline = QualityScore.calculate(100, sampleIssues);
        int expectedScore = baseline.getOverallScore();
        QualityScore.Grade expectedGrade = baseline.getGrade();

        for (int i = 0; i < 10_000; i++) {
            QualityScore run = QualityScore.calculate(100, sampleIssues);
            assertEquals(expectedScore, run.getOverallScore());
            assertEquals(expectedGrade, run.getGrade());
            assertEquals(baseline.getCategoryScores(), run.getCategoryScores());
        }
    }
}
