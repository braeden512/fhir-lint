package org.fhirlint.cli.renderer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.fhirlint.core.comparison.ComparisonGateResult;
import org.fhirlint.core.comparison.ComparisonReport;
import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityScore;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serializes ComparisonReport to machine-readable JSON matching contracts/cli-compare-contract.md.
 */
public class ComparisonJsonRenderer {

    private final ObjectMapper objectMapper;

    public ComparisonJsonRenderer() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public String render(ComparisonReport report, ComparisonGateResult gateResult, boolean failOnRegression, Integer maxScoreDrop) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();

            // 1. Summary
            int baseScore = report.baseline().getQualityScore().getOverallScore();
            int targetScore = report.target().getQualityScore().getOverallScore();
            int baseTotal = report.baseline().inventory() != null ? report.baseline().inventory().totalResources() : 0;
            int targetTotal = report.target().inventory() != null ? report.target().inventory().totalResources() : 0;
            int resDelta = report.resourceCountDeltas().getOrDefault("TOTAL", targetTotal - baseTotal);

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("baselineScore", baseScore);
            summary.put("targetScore", targetScore);
            summary.put("scoreDelta", report.scoreDelta());
            summary.put("baselineGrade", report.baseline().getQualityScore().getGrade().name());
            summary.put("targetGrade", report.target().getQualityScore().getGrade().name());
            summary.put("totalBaselineResources", baseTotal);
            summary.put("totalTargetResources", targetTotal);
            summary.put("resourceDelta", resDelta);
            root.put("summary", summary);

            // 2. Gate Evaluation
            if (gateResult != null) {
                Map<String, Object> gate = new LinkedHashMap<>();
                gate.put("passed", gateResult.passed());
                gate.put("failOnRegression", failOnRegression);
                gate.put("maxScoreDrop", maxScoreDrop);
                gate.put("scoreDropViolated", gateResult.scoreDropViolated());
                gate.put("regressionViolated", gateResult.regressionViolated());
                gate.put("failureReason", gateResult.failureReason());
                root.put("gateEvaluation", gate);
            }

            // 3. Category Deltas
            Map<String, Integer> catDeltas = new LinkedHashMap<>();
            for (Map.Entry<IssueCategory, Integer> entry : report.categoryDeltas().entrySet()) {
                catDeltas.put(entry.getKey().name().toLowerCase(), entry.getValue());
            }
            root.put("categoryDeltas", catDeltas);

            // 4. Resource Count Deltas
            root.put("resourceCountDeltas", report.resourceCountDeltas());

            // 5. Issue Counts
            Map<String, Long> issueCounts = new LinkedHashMap<>();
            issueCounts.put("newErrors", report.getNewErrorCount());
            issueCounts.put("newWarnings", report.getNewWarningCount());
            issueCounts.put("resolvedErrors", report.getResolvedErrorCount());
            issueCounts.put("resolvedWarnings", report.getResolvedWarningCount());
            issueCounts.put("persistentErrors", report.getPersistentErrorCount());
            issueCounts.put("persistentWarnings", report.getPersistentWarningCount());
            root.put("issueCounts", issueCounts);

            // 6. Issues lists
            root.put("newIssues", report.newIssues());
            root.put("resolvedIssues", report.resolvedIssues());

            // 7. Disclaimer
            root.put("disclaimer", QualityScore.NON_CLINICAL_DISCLAIMER);

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to render comparison report as JSON: " + e.getMessage(), e);
        }
    }
}
