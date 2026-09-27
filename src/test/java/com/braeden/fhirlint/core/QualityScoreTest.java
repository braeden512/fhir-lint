package com.braeden.fhirlint.core;

import com.braeden.fhirlint.core.model.IssueCategory;
import com.braeden.fhirlint.core.model.QualityIssue;
import com.braeden.fhirlint.core.model.QualityScore;
import com.braeden.fhirlint.core.model.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QualityScoreTest {

    @Test
    @DisplayName("Should produce perfect 100 EXCELLENT score when no issues exist")
    void shouldProducePerfectScoreForCleanData() {
        QualityScore score = QualityScore.calculate(10, Collections.emptyList());

        assertThat(score.getOverallScore()).isEqualTo(100);
        assertThat(score.getGrade()).isEqualTo(QualityScore.Grade.EXCELLENT);
        assertThat(score.getErrorCount()).isZero();
        assertThat(score.getWarningCount()).isZero();
        assertThat(score.getInfoCount()).isZero();

        for (int catScore : score.getCategoryScores().values()) {
            assertThat(catScore).isEqualTo(100);
        }
    }

    @Test
    @DisplayName("Should penalize score when errors and warnings are present")
    void shouldPenalizeScoreForIssues() {
        QualityIssue errorIssue = QualityIssue.builder()
            .severity(Severity.ERROR)
            .category(IssueCategory.REFERENTIAL_INTEGRITY)
            .ruleId("REF-001")
            .message("Broken reference")
            .build();

        QualityIssue warningIssue = QualityIssue.builder()
            .severity(Severity.WARNING)
            .category(IssueCategory.COMPLETENESS)
            .ruleId("COMP-001")
            .message("Missing subject")
            .build();

        QualityScore score = QualityScore.calculate(5, List.of(errorIssue, warningIssue));

        assertThat(score.getErrorCount()).isEqualTo(1);
        assertThat(score.getWarningCount()).isEqualTo(1);
        assertThat(score.getOverallScore()).isLessThan(100);
        assertThat(score.getCategoryScores().get(IssueCategory.REFERENTIAL_INTEGRITY)).isLessThan(100);
        assertThat(score.getCategoryScores().get(IssueCategory.COMPLETENESS)).isLessThan(100);
        assertThat(score.getCategoryScores().get(IssueCategory.STRUCTURAL)).isEqualTo(100);
    }

    @Test
    @DisplayName("Should correctly evaluate grade tiers")
    void shouldEvaluateGradeTiers() {
        assertThat(QualityScore.Grade.forScore(95)).isEqualTo(QualityScore.Grade.EXCELLENT);
        assertThat(QualityScore.Grade.forScore(85)).isEqualTo(QualityScore.Grade.ACCEPTABLE);
        assertThat(QualityScore.Grade.forScore(65)).isEqualTo(QualityScore.Grade.DEGRADED);
        assertThat(QualityScore.Grade.forScore(30)).isEqualTo(QualityScore.Grade.CRITICAL);
    }
}
