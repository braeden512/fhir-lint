package org.fhirlint.cli.renderer;

import org.fhirlint.core.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReportRenderersTest {

    private LintReport createTestReport() {
        QualityIssue issue = QualityIssue.builder()
            .id("issue_001")
            .ruleId("USCORE_OBS_CATEGORY")
            .category(IssueCategory.PROFILE_CONFORMANCE)
            .severity(Severity.ERROR)
            .resourceType("Observation")
            .resourceId("obs-1")
            .path("Observation.category")
            .message("Missing required slice category:VSCat")
            .suggestion("Provide category coding with system 'http://terminology.hl7.org/CodeSystem/observation-category' and code 'vital-signs'")
            .build();

        QualityScore score = new QualityScore(
            85,
            QualityScore.Grade.ACCEPTABLE,
            Map.of(IssueCategory.PROFILE_CONFORMANCE, 70),
            1,
            0,
            0
        );

        IngestionInventory inventory = new IngestionInventory(
            1,
            Map.of("Observation", 1),
            12L
        );

        return new LintReport(
            ValidationProfile.US_CORE,
            inventory,
            List.of(issue),
            score,
            42L
        );
    }

    @Test
    @DisplayName("ConsoleTableRenderer should display normalized ruleId, message, and suggestion with enriched location")
    void consoleTableRendererShouldDisplayNormalizedIssue() {
        ConsoleTableRenderer renderer = new ConsoleTableRenderer();
        LintReport report = createTestReport();

        String output = renderer.render(report, true);

        assertThat(output).contains("USCORE_OBS_CATEGORY");
        assertThat(output).contains("Missing required slice category:VSCat");
        assertThat(output).contains("(Observation/obs-1: Observation.category)");
        assertThat(output).contains("Suggestion: Provide category coding");
        assertThat(output).contains(QualityScore.NON_CLINICAL_DISCLAIMER);
    }

    @Test
    @DisplayName("ConsoleTableRenderer should truncate at 10 items in default mode and expand with verbose")
    void consoleTableRendererShouldTruncateAt10Items() {
        ConsoleTableRenderer renderer = new ConsoleTableRenderer();
        java.util.List<QualityIssue> issues = new java.util.ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            issues.add(QualityIssue.builder()
                .id("issue_" + i)
                .ruleId("REF-001")
                .category(IssueCategory.REFERENTIAL_INTEGRITY)
                .severity(Severity.ERROR)
                .resourceType("Observation")
                .resourceId("obs-" + i)
                .path("Observation.subject")
                .message("Broken reference " + i)
                .build());
        }

        LintReport report = new LintReport(
            ValidationProfile.US_CORE,
            new IngestionInventory(15, Map.of("Observation", 15), 10L),
            issues,
            QualityScore.calculate(15, issues),
            25L
        );

        String defaultOutput = renderer.render(report, false);
        assertThat(defaultOutput).contains("... and 5 more findings. Pass -v or --verbose to view all.");
        assertThat(defaultOutput).contains("Broken reference 1");
        assertThat(defaultOutput).contains("Broken reference 10");
        assertThat(defaultOutput).doesNotContain("Broken reference 11");

        String verboseOutput = renderer.render(report, true);
        assertThat(verboseOutput).doesNotContain("... and 5 more findings");
        assertThat(verboseOutput).contains("Broken reference 15");
    }

    @Test
    @DisplayName("JsonReportRenderer should serialize normalized issue fields including suggestion and disclaimer")
    void jsonReportRendererShouldIncludeSuggestion() {
        JsonReportRenderer renderer = new JsonReportRenderer();
        LintReport report = createTestReport();

        String json = renderer.render(report);

        assertThat(json).contains("\"ruleId\" : \"USCORE_OBS_CATEGORY\"");
        assertThat(json).contains("\"category\" : \"PROFILE_CONFORMANCE\"");
        assertThat(json).contains("\"severity\" : \"ERROR\"");
        assertThat(json).contains("\"path\" : \"Observation.category\"");
        assertThat(json).contains("\"suggestion\" : \"Provide category coding with system 'http://terminology.hl7.org/CodeSystem/observation-category' and code 'vital-signs'\"");
        assertThat(json).contains("\"disclaimer\" : \"" + QualityScore.NON_CLINICAL_DISCLAIMER + "\"");
    }

    @Test
    @DisplayName("SarifReportRenderer should format SARIF result with message, suggestion, disclaimer, dynamic rules, and sanitized URI")
    void sarifReportRendererShouldIncludeMessageAndSuggestion() {
        SarifReportRenderer renderer = new SarifReportRenderer();
        LintReport report = createTestReport();

        String sarif = renderer.render(report, "test-bundle.json");

        assertThat(sarif).contains("\"ruleId\" : \"USCORE_OBS_CATEGORY\"");
        assertThat(sarif).contains("\"level\" : \"error\"");
        assertThat(sarif).contains("Missing required slice category:VSCat");
        assertThat(sarif).contains("Suggestion: Provide category coding");
        assertThat(sarif).contains("\"uri\" : \"test-bundle.json\"");
        assertThat(sarif).contains("\"properties\" : {\n      \"disclaimer\" : \"" + QualityScore.NON_CLINICAL_DISCLAIMER + "\"\n    }");
        assertThat(sarif).contains("\"id\" : \"USCORE_OBS_CATEGORY\"");

        // Test stdin URI sanitization
        String stdinSarif = renderer.render(report, "-");
        assertThat(stdinSarif).contains("\"uri\" : \"stdin\"");

        // Test Windows path normalization
        String windowsSarif = renderer.render(report, "C:\\data\\test-bundle.json");
        assertThat(windowsSarif).contains("\"uri\" : \"C:/data/test-bundle.json\"");
    }

    @Test
    @DisplayName("ConsoleTableRenderer should prioritize ERROR over WARNING in top-10 display")
    void consoleTableRendererShouldPrioritizeErrorsBeforeWarningsInTop10() {
        ConsoleTableRenderer renderer = new ConsoleTableRenderer();
        java.util.List<QualityIssue> issues = new java.util.ArrayList<>();
        // Add 10 warnings first
        for (int i = 1; i <= 10; i++) {
            issues.add(QualityIssue.builder()
                .id("warn_" + i)
                .ruleId("WARN-001")
                .category(IssueCategory.TERMINOLOGY)
                .severity(Severity.WARNING)
                .message("Low priority warning " + i)
                .build());
        }
        // Add 1 critical error at the end
        issues.add(QualityIssue.builder()
            .id("err_1")
            .ruleId("ERR-001")
            .category(IssueCategory.STRUCTURAL)
            .severity(Severity.ERROR)
            .message("Critical error that must be visible")
            .build());

        LintReport report = new LintReport(
            ValidationProfile.US_CORE,
            new IngestionInventory(11, Map.of("Observation", 11), 10L),
            issues,
            QualityScore.calculate(11, issues),
            25L
        );

        String output = renderer.render(report, false);
        // The critical error must be prioritized and visible in top 10 despite being inserted after 10 warnings
        assertThat(output).contains("Critical error that must be visible");
        assertThat(output).contains("... and 1 more findings. Pass -v or --verbose to view all.");
    }
}
