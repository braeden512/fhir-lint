package org.fhirlint.core.comparison;

import org.fhirlint.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatasetComparatorTest {

    private QualityIssue issueRef1;
    private QualityIssue issueRef2;
    private QualityIssue issueStruct1;

    @BeforeEach
    void setUp() {
        issueRef1 = QualityIssue.builder()
                .ruleId("REF-001")
                .severity(Severity.ERROR)
                .category(IssueCategory.REFERENTIAL_INTEGRITY)
                .resourceType("Observation")
                .resourceId("obs-1")
                .path("Observation.subject.reference")
                .message("Patient not found")
                .build();

        issueRef2 = QualityIssue.builder()
                .ruleId("REF-001")
                .severity(Severity.ERROR)
                .category(IssueCategory.REFERENTIAL_INTEGRITY)
                .resourceType("Observation")
                .resourceId("obs-2")
                .path("Observation.subject.reference")
                .message("Patient not found")
                .build();

        issueStruct1 = QualityIssue.builder()
                .ruleId("STRUCT-001")
                .severity(Severity.WARNING)
                .category(IssueCategory.STRUCTURAL)
                .resourceType("Patient")
                .resourceId("pat-1")
                .path("Patient.gender")
                .message("Invalid code")
                .build();
    }

    private LintReport createReport(int score, List<QualityIssue> issues, Map<String, Integer> resourceCounts) {
        Map<IssueCategory, Integer> categoryScores = new EnumMap<>(IssueCategory.class);
        for (IssueCategory cat : IssueCategory.values()) {
            categoryScores.put(cat, score);
        }
        QualityScore qualityScore = new QualityScore(
                score,
                QualityScore.Grade.forScore(score),
                categoryScores,
                Collections.emptyMap(),
                (int) issues.stream().filter(i -> i.severity() == Severity.ERROR).count(),
                (int) issues.stream().filter(i -> i.severity() == Severity.WARNING).count(),
                0
        );
        IngestionInventory inventory = new IngestionInventory(
                resourceCounts.values().stream().mapToInt(Integer::intValue).sum(),
                resourceCounts,
                10
        );
        return new LintReport(ValidationProfile.BASE_R4, inventory, issues, qualityScore, 100);
    }

    @Test
    @DisplayName("Identical datasets produce delta of 0 and no new or resolved issues")
    void testIdenticalDatasets() {
        Map<String, Integer> counts = Map.of("Patient", 5, "Observation", 10);
        LintReport baseline = createReport(90, List.of(issueRef1), counts);
        LintReport target = createReport(90, List.of(issueRef1), counts);

        ComparisonReport report = DatasetComparator.compare(baseline, target);

        assertThat(report.scoreDelta()).isEqualTo(0);
        assertThat(report.newIssues()).isEmpty();
        assertThat(report.resolvedIssues()).isEmpty();
        assertThat(report.persistentIssues()).hasSize(1);
        assertThat(report.resourceCountDeltas().get("TOTAL")).isEqualTo(0);

        ComparisonGateResult gate = report.evaluateGates(true, 5);
        assertThat(gate.passed()).isTrue();
    }

    @Test
    @DisplayName("Detects new regressions and score drops")
    void testRegressionDetection() {
        Map<String, Integer> baseCounts = Map.of("Patient", 5, "Observation", 10);
        Map<String, Integer> targetCounts = Map.of("Patient", 5, "Observation", 12);

        LintReport baseline = createReport(95, List.of(issueStruct1), baseCounts);
        // Target has issueStruct1 AND issueRef1 (new error) and score drops to 80
        LintReport target = createReport(80, List.of(issueStruct1, issueRef1), targetCounts);

        ComparisonReport report = DatasetComparator.compare(baseline, target);

        assertThat(report.scoreDelta()).isEqualTo(-15);
        assertThat(report.newIssues()).containsExactly(issueRef1);
        assertThat(report.resolvedIssues()).isEmpty();
        assertThat(report.persistentIssues()).containsExactly(issueStruct1);
        assertThat(report.getNewErrorCount()).isEqualTo(1);
        assertThat(report.resourceCountDeltas().get("Observation")).isEqualTo(2);

        // Fail-on-regression active -> fails
        ComparisonGateResult gateRegression = report.evaluateGates(true, null);
        assertThat(gateRegression.passed()).isFalse();
        assertThat(gateRegression.regressionViolated()).isTrue();

        // Max-score-drop = 10 -> fails because drop is 15
        ComparisonGateResult gateScoreDrop = report.evaluateGates(false, 10);
        assertThat(gateScoreDrop.passed()).isFalse();
        assertThat(gateScoreDrop.scoreDropViolated()).isTrue();

        // Max-score-drop = 20 -> passes
        ComparisonGateResult gatePass = report.evaluateGates(false, 20);
        assertThat(gatePass.passed()).isTrue();
    }

    @Test
    @DisplayName("Detects resolved defects and quality improvements")
    void testResolvedIssues() {
        Map<String, Integer> counts = Map.of("Patient", 5, "Observation", 10);
        LintReport baseline = createReport(80, List.of(issueRef1, issueRef2), counts);
        LintReport target = createReport(90, List.of(issueRef1), counts); // issueRef2 resolved

        ComparisonReport report = DatasetComparator.compare(baseline, target);

        assertThat(report.scoreDelta()).isEqualTo(10);
        assertThat(report.resolvedIssues()).containsExactly(issueRef2);
        assertThat(report.newIssues()).isEmpty();
        assertThat(report.persistentIssues()).containsExactly(issueRef1);

        ComparisonGateResult gate = report.evaluateGates(true, 5);
        assertThat(gate.passed()).isTrue();
    }

    @Test
    @DisplayName("Throws exception if baseline or target is null")
    void testNullValidation() {
        LintReport report = createReport(90, Collections.emptyList(), Collections.emptyMap());
        assertThatThrownBy(() -> DatasetComparator.compare(null, report))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> DatasetComparator.compare(report, null))
                .isInstanceOf(NullPointerException.class);
    }
}
