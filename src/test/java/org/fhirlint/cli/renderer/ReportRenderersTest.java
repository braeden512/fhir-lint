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
    @DisplayName("ConsoleTableRenderer should display normalized ruleId, message, and suggestion")
    void consoleTableRendererShouldDisplayNormalizedIssue() {
        ConsoleTableRenderer renderer = new ConsoleTableRenderer();
        LintReport report = createTestReport();

        String output = renderer.render(report, true);

        assertThat(output).contains("USCORE_OBS_CATEGORY");
        assertThat(output).contains("Missing required slice category:VSCat");
        assertThat(output).contains("Observation.category");
        assertThat(output).contains("Suggestion: Provide category coding");
        assertThat(output).contains(QualityScore.NON_CLINICAL_DISCLAIMER);
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
    @DisplayName("SarifReportRenderer should format SARIF result with message and suggestion")
    void sarifReportRendererShouldIncludeMessageAndSuggestion() {
        SarifReportRenderer renderer = new SarifReportRenderer();
        LintReport report = createTestReport();

        String sarif = renderer.render(report, "test-bundle.json");

        assertThat(sarif).contains("\"ruleId\" : \"USCORE_OBS_CATEGORY\"");
        assertThat(sarif).contains("\"level\" : \"error\"");
        assertThat(sarif).contains("Missing required slice category:VSCat");
        assertThat(sarif).contains("Suggestion: Provide category coding");
        assertThat(sarif).contains("\"uri\" : \"test-bundle.json\"");
    }
}
