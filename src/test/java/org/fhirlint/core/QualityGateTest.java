package org.fhirlint.core;

import org.fhirlint.core.model.QualityGateConfig;
import org.fhirlint.core.model.QualityGateResult;
import org.fhirlint.core.model.QualityScore;
import org.fhirlint.core.model.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityGateTest {

    private QualityScore createScore(int overallScore, int errors, int warnings, int info) {
        return new QualityScore(
                overallScore,
                QualityScore.Grade.forScore(overallScore),
                Collections.emptyMap(),
                errors,
                warnings,
                info
        );
    }

    @Test
    @DisplayName("QualityGateConfig disallows negative minScore")
    void testConfigValidation() {
        assertThrows(IllegalArgumentException.class, () -> QualityGateConfig.minScore(-1));
        assertThrows(IllegalArgumentException.class, () -> QualityGateConfig.of(-5, Severity.ERROR));

        // Non-negative and > 100 are allowed (e.g., minScore = 101 to force failure in test)
        assertEquals(0, QualityGateConfig.minScore(0).minScore());
        assertEquals(101, QualityGateConfig.minScore(101).minScore());
    }

    @Test
    @DisplayName("Quality gate passes when score meets minScore and no issues violate failOn")
    void testGatePasses() {
        QualityScore score = createScore(85, 0, 2, 5);

        QualityGateConfig config = QualityGateConfig.of(80, Severity.ERROR);
        QualityGateResult result = score.evaluateGate(config);

        assertTrue(result.passed());
        assertTrue(result.breaches().isEmpty());
    }

    @Test
    @DisplayName("Quality gate fails when score is below minScore")
    void testGateFailsOnScore() {
        QualityScore score = createScore(70, 0, 0, 0);

        QualityGateConfig config = QualityGateConfig.minScore(75);
        QualityGateResult result = score.evaluateGate(config);

        assertFalse(result.passed());
        assertEquals(1, result.breaches().size());
        assertEquals(
                "Overall quality score (70) is below required minimum threshold (75).",
                result.breaches().get(0)
        );
    }

    @Test
    @DisplayName("Quality gate fails when severity matches or exceeds ERROR")
    void testGateFailsOnErrorSeverity() {
        QualityScore score = createScore(90, 2, 0, 0);

        QualityGateConfig config = QualityGateConfig.failOn(Severity.ERROR);
        QualityGateResult result = score.evaluateGate(config);

        assertFalse(result.passed());
        assertEquals(1, result.breaches().size());
        assertEquals(
                "Dataset contains 2 issue(s) with severity ERROR or higher.",
                result.breaches().get(0)
        );
    }

    @Test
    @DisplayName("Quality gate fails when severity matches or exceeds WARNING (including errors)")
    void testGateFailsOnWarningSeverity() {
        QualityScore score = createScore(90, 1, 3, 2);

        // failOn WARNING counts errors (1) + warnings (3) = 4
        QualityGateConfig config = QualityGateConfig.failOn(Severity.WARNING);
        QualityGateResult result = score.evaluateGate(config);

        assertFalse(result.passed());
        assertEquals(1, result.breaches().size());
        assertEquals(
                "Dataset contains 4 issue(s) with severity WARNING or higher.",
                result.breaches().get(0)
        );
    }

    @Test
    @DisplayName("Quality gate captures both score and severity breaches without short-circuiting")
    void testMultiBreachNonShortCircuiting() {
        QualityScore score = createScore(60, 2, 1, 0);

        QualityGateConfig config = QualityGateConfig.of(80, Severity.ERROR);
        QualityGateResult result = score.evaluateGate(config);

        assertFalse(result.passed());
        List<String> breaches = result.breaches();
        assertEquals(2, breaches.size());
        assertEquals(
                "Overall quality score (60) is below required minimum threshold (80).",
                breaches.get(0)
        );
        assertEquals(
                "Dataset contains 2 issue(s) with severity ERROR or higher.",
                breaches.get(1)
        );
    }

    @Test
    @DisplayName("Quality gate with no constraints passes unconditionally")
    void testNoConstraintsPasses() {
        QualityScore score = createScore(10, 5, 10, 20);

        QualityGateResult result = score.evaluateGate(QualityGateConfig.none());
        assertTrue(result.passed());
        assertTrue(result.breaches().isEmpty());
    }
}
