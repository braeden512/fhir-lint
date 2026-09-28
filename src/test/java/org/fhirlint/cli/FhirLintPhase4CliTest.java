package org.fhirlint.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FhirLintPhase4CliTest {

    @Test
    @DisplayName("Should execute CLI validate on clean-bundle.json and return exit code 0")
    void shouldValidateCleanBundleWithExitCodeZero() {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        int exitCode = cmd.execute("validate", "sample-data/clean/clean-bundle.json");
        assertThat(exitCode).isZero();
    }

    @Test
    @DisplayName("Should detect Phase 4 quality issues in messy-bundle.json and exit with code 1")
    void shouldDetectPhase4QualityIssuesInMessyBundle() {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(outContent));
            int exitCode = cmd.execute("validate", "sample-data/messy/messy-bundle.json", "--profile", "BASE_R4", "-v");
            assertThat(exitCode).isEqualTo(1);

            String output = outContent.toString(StandardCharsets.UTF_8);
            // Verify table output contains quality categories and Phase 4 rule IDs
            assertThat(output).contains("Category Breakdown:");
            assertThat(output).containsAnyOf("CONS-001", "DUP-001", "TERM-002");
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("Should output valid JSON report including quality rule categories and scores")
    void shouldOutputValidJsonReportWithQualityRuleCategories() {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(outContent));
            int exitCode = cmd.execute("validate", "sample-data/messy/messy-bundle.json", "--profile", "BASE_R4", "--format", "json");
            assertThat(exitCode).isEqualTo(1);

            String output = outContent.toString(StandardCharsets.UTF_8);
            assertThat(output).contains("\"CONSISTENCY\"");
            assertThat(output).contains("\"TERMINOLOGY\"");
            assertThat(output).contains("\"categoryScores\"");
            assertThat(output).contains("\"issues\"");
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("Should respect --min-score threshold for Phase 4 quality score")
    void shouldRespectMinScoreThreshold() {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        // Clean bundle achieves > 90, so min-score 80 passes
        int passCode = cmd.execute("validate", "sample-data/clean/clean-bundle.json", "--min-score", "80");
        assertThat(passCode).isZero();

        // Demanding 100% score must fail on messy bundle
        int failCode = cmd.execute("validate", "sample-data/messy/messy-bundle.json", "--min-score", "100");
        assertThat(failCode).isEqualTo(1);
    }
}
