package org.fhirlint.core;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityScoreCategoryTest {

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
    @DisplayName("Zero resources and zero issues produces 100 across all 6 categories")
    void zeroResourcesProduces100AcrossAllCategories() {
        QualityScore score = QualityScore.calculate(0, List.of());

        assertEquals(100, score.getOverallScore());
        Map<IssueCategory, Integer> categoryScores = score.getCategoryScores();

        assertEquals(6, categoryScores.size(), "Only 6 canonical categories should be scored");
        assertFalse(categoryScores.containsKey(IssueCategory.DUPLICATE), "DUPLICATE must not be in category scores");

        for (IssueCategory cat : IssueCategory.values()) {
            if (cat.isScored()) {
                assertEquals(100, categoryScores.get(cat), "Expected 100 for category " + cat);
            }
        }
    }

    @Test
    @DisplayName("Single error reduces category score according to penalty weight 15")
    void singleErrorCalculatesCorrectScore() {
        // N = 10 resources, 1 error in STRUCTURAL
        // DefectPenalty = 15
        // DensityFactor = 15 / (10 * 15) = 15 / 150 = 0.1
        // Score = round(100 * (1.0 - 0.1)) = 90
        QualityIssue errorIssue = createIssue("STR-001", IssueCategory.STRUCTURAL, Severity.ERROR);
        QualityScore score = QualityScore.calculate(10, List.of(errorIssue));

        assertEquals(90, score.getCategoryScores().get(IssueCategory.STRUCTURAL));
        assertEquals(100, score.getCategoryScores().get(IssueCategory.PROFILE_CONFORMANCE));
    }

    @Test
    @DisplayName("Single warning reduces category score according to penalty weight 3")
    void singleWarningCalculatesCorrectScore() {
        // N = 10 resources, 1 warning in PROFILE_CONFORMANCE
        // DefectPenalty = 3
        // DensityFactor = 3 / (10 * 15) = 3 / 150 = 0.02
        // Score = round(100 * (1.0 - 0.02)) = round(98.0) = 98
        QualityIssue warningIssue = createIssue("PRF-001", IssueCategory.PROFILE_CONFORMANCE, Severity.WARNING);
        QualityScore score = QualityScore.calculate(10, List.of(warningIssue));

        assertEquals(98, score.getCategoryScores().get(IssueCategory.PROFILE_CONFORMANCE));
        assertEquals(100, score.getCategoryScores().get(IssueCategory.STRUCTURAL));
    }

    @Test
    @DisplayName("INFO notices carry zero penalty and do not degrade category score")
    void infoSeverityCarriesZeroPenalty() {
        // N = 5 resources, 10 info issues in TERMINOLOGY
        // DefectPenalty = 0
        // DensityFactor = 0
        // Score = 100
        List<QualityIssue> infoIssues = List.of(
                createIssue("TRM-001", IssueCategory.TERMINOLOGY, Severity.INFO),
                createIssue("TRM-002", IssueCategory.TERMINOLOGY, Severity.INFO),
                createIssue("TRM-003", IssueCategory.TERMINOLOGY, Severity.INFO)
        );
        QualityScore score = QualityScore.calculate(5, infoIssues);

        assertEquals(100, score.getCategoryScores().get(IssueCategory.TERMINOLOGY));
        assertEquals(3, score.getInfoCount());
    }

    @Test
    @DisplayName("Category scores clamp at zero when defect density exceeds 1.0")
    void categoryScoresClampAtZero() {
        // N = 1 resource, 5 errors in REFERENTIAL_INTEGRITY
        // DefectPenalty = 5 * 15 = 75
        // DensityFactor = 75 / (1 * 15) = 5.0 > 1.0
        // Score = 0 (clamped, never negative)
        List<QualityIssue> errors = List.of(
                createIssue("REF-001", IssueCategory.REFERENTIAL_INTEGRITY, Severity.ERROR),
                createIssue("REF-002", IssueCategory.REFERENTIAL_INTEGRITY, Severity.ERROR),
                createIssue("REF-003", IssueCategory.REFERENTIAL_INTEGRITY, Severity.ERROR),
                createIssue("REF-004", IssueCategory.REFERENTIAL_INTEGRITY, Severity.ERROR),
                createIssue("REF-005", IssueCategory.REFERENTIAL_INTEGRITY, Severity.ERROR)
        );
        QualityScore score = QualityScore.calculate(1, errors);

        assertEquals(0, score.getCategoryScores().get(IssueCategory.REFERENTIAL_INTEGRITY));
    }

    @Test
    @DisplayName("DUPLICATE issues remap into CONSISTENCY per FR-013")
    void duplicateIssuesRemapToConsistency() {
        // N = 10 resources
        // 1 ERROR in CONSISTENCY (15 penalty)
        // 1 WARNING in DUPLICATE (3 penalty) -> mapped to CONSISTENCY
        // Total CONSISTENCY penalty = 18
        // DensityFactor = 18 / 150 = 0.12
        // Score = round(100 * (1 - 0.12)) = round(88.0) = 88
        List<QualityIssue> issues = List.of(
                createIssue("CON-001", IssueCategory.CONSISTENCY, Severity.ERROR),
                createIssue("DUP-001", IssueCategory.DUPLICATE, Severity.WARNING)
        );
        QualityScore score = QualityScore.calculate(10, issues);

        assertEquals(88, score.getCategoryScores().get(IssueCategory.CONSISTENCY));
        assertFalse(score.getCategoryScores().containsKey(IssueCategory.DUPLICATE));
    }

    @Test
    @DisplayName("Exactly 6 canonical scored categories exist in categoryScores map")
    void exactlySixCanonicalScoredCategoriesExist() {
        QualityScore score = QualityScore.calculate(50, List.of());
        Map<IssueCategory, Integer> categoryScores = score.getCategoryScores();

        assertEquals(6, categoryScores.size());
        assertTrue(categoryScores.containsKey(IssueCategory.STRUCTURAL));
        assertTrue(categoryScores.containsKey(IssueCategory.PROFILE_CONFORMANCE));
        assertTrue(categoryScores.containsKey(IssueCategory.REFERENTIAL_INTEGRITY));
        assertTrue(categoryScores.containsKey(IssueCategory.CONSISTENCY));
        assertTrue(categoryScores.containsKey(IssueCategory.TERMINOLOGY));
        assertTrue(categoryScores.containsKey(IssueCategory.COMPLETENESS));
        assertFalse(categoryScores.containsKey(IssueCategory.DUPLICATE));
    }
}
