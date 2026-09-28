package org.fhirlint.core;

import org.fhirlint.core.model.CategoryScoreDetail;
import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.QualityScore;
import org.fhirlint.core.model.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityScoreExplainabilityTest {

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
    @DisplayName("Non-clinical engineering disclaimer matches ADR-005 and Constitution IV")
    void testDisclaimerRetrieval() {
        assertEquals(
                "Quality scores produced by FHIRLint reflect technical data hygiene and engineering standards rather than clinical, medical, or regulatory compliance measurements.",
                QualityScore.NON_CLINICAL_DISCLAIMER
        );

        QualityScore score = QualityScore.calculate(10, List.of());
        assertEquals(QualityScore.NON_CLINICAL_DISCLAIMER, score.getDisclaimer());
    }

    @Test
    @DisplayName("CategoryScoreDetail exposes full explainability metrics for all 6 categories")
    void testCategoryScoreDetailMetrics() {
        // N = 20 resources
        // STRUCTURAL: 2 errors (30 penalty), 1 warning (3 penalty), 1 info (0 penalty)
        // DefectPenalty = 33
        // DensityFactor = 33 / (20 * 15) = 33 / 300 = 0.11
        // Score = round(100 * (1.0 - 0.11)) = round(89.0) = 89
        // WeightedContribution = 0.20 * 89 = 17.8
        List<QualityIssue> issues = List.of(
                createIssue("STR-001", IssueCategory.STRUCTURAL, Severity.ERROR),
                createIssue("STR-002", IssueCategory.STRUCTURAL, Severity.ERROR),
                createIssue("STR-003", IssueCategory.STRUCTURAL, Severity.WARNING),
                createIssue("STR-004", IssueCategory.STRUCTURAL, Severity.INFO)
        );

        QualityScore score = QualityScore.calculate(20, issues);
        Map<IssueCategory, CategoryScoreDetail> details = score.getCategoryDetails();

        assertNotNull(details);
        assertEquals(6, details.size());
        assertFalse(details.containsKey(IssueCategory.DUPLICATE));

        CategoryScoreDetail strDetail = details.get(IssueCategory.STRUCTURAL);
        assertNotNull(strDetail);
        assertEquals(IssueCategory.STRUCTURAL, strDetail.category());
        assertEquals("Structural Conformance", strDetail.displayName());
        assertEquals(0.20, strDetail.weight(), 1e-6);
        assertEquals(2, strDetail.errorCount());
        assertEquals(1, strDetail.warningCount());
        assertEquals(1, strDetail.infoCount());
        assertEquals(33, strDetail.defectPenalty());
        assertEquals(0.11, strDetail.densityFactor(), 1e-6);
        assertEquals(89, strDetail.score());
        assertEquals(17.8, strDetail.weightedContribution(), 1e-6);
    }

    @Test
    @DisplayName("Backward-compatible constructor retains empty map if details not supplied")
    void testBackwardCompatibleConstructor() {
        QualityScore legacyScore = new QualityScore(
                95,
                QualityScore.Grade.EXCELLENT,
                Map.of(IssueCategory.STRUCTURAL, 95),
                1,
                0,
                0
        );

        assertEquals(95, legacyScore.getOverallScore());
        assertEquals(QualityScore.Grade.EXCELLENT, legacyScore.getGrade());
        assertEquals(org.fhirlint.core.model.EngineeringGrade.EXCELLENT, legacyScore.getEngineeringGrade());
        assertNotNull(legacyScore.getCategoryDetails());
        assertTrue(legacyScore.getCategoryDetails().isEmpty());
    }

    @Test
    @DisplayName("Defensive null handling for issues list in QualityScore.calculate")
    void testNullIssuesListDefensiveCalculation() {
        QualityScore score = QualityScore.calculate(10, null);
        assertEquals(100, score.getOverallScore());
        assertEquals(QualityScore.Grade.EXCELLENT, score.getGrade());
        assertEquals(org.fhirlint.core.model.EngineeringGrade.EXCELLENT, score.getEngineeringGrade());
        assertEquals(0, score.getErrorCount());
        assertEquals(0, score.getWarningCount());
        assertEquals(0, score.getInfoCount());
    }
}
