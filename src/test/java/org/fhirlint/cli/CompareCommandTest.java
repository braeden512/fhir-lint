package org.fhirlint.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CompareCommandTest {

    private static final String CLEAN_BUNDLE = "sample-data/clean/clean-bundle.json";
    private static final String MESSY_BUNDLE = "sample-data/messy/messy-bundle.json";

    @Test
    @DisplayName("Should compare clean and messy bundles successfully without gates and return 0")
    void shouldCompareCleanWithMessySuccessfully() {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(outContent));
            int exitCode = cmd.execute("compare", CLEAN_BUNDLE, MESSY_BUNDLE);
            assertThat(exitCode).isZero();
            String output = outContent.toString(StandardCharsets.UTF_8);
            assertThat(output).contains("FHIRLint Dataset Comparison & Regression Diff");
            assertThat(output).contains("Baseline Score:");
            assertThat(output).contains("Target Score:");
            assertThat(output).contains("Category Deltas:");
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("Should fail with exit code 1 when --fail-on-regression is specified on degraded dataset")
    void shouldFailOnRegressionWhenDefectsAdded() {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        try {
            System.setErr(new PrintStream(errContent));
            int exitCode = cmd.execute("compare", CLEAN_BUNDLE, MESSY_BUNDLE, "--fail-on-regression");
            assertThat(exitCode).isEqualTo(1);
            String errOutput = errContent.toString(StandardCharsets.UTF_8);
            assertThat(errOutput).contains("Quality regression detected:");
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    @DisplayName("Should fail with exit code 1 when score drop exceeds --max-score-drop")
    void shouldFailWhenScoreDropExceedsThreshold() {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        int exitCode = cmd.execute("compare", CLEAN_BUNDLE, MESSY_BUNDLE, "--max-score-drop", "5");
        assertThat(exitCode).isEqualTo(1);
    }

    @Test
    @DisplayName("Should pass with exit code 0 when score drop is within --max-score-drop threshold")
    void shouldPassWhenScoreDropWithinThreshold() {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        int exitCode = cmd.execute("compare", CLEAN_BUNDLE, MESSY_BUNDLE, "--max-score-drop", "50");
        assertThat(exitCode).isZero();
    }

    @Test
    @DisplayName("Should render comparison in valid machine-readable JSON format")
    void shouldRenderJsonFormat() throws Exception {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(outContent));
            int exitCode = cmd.execute("compare", CLEAN_BUNDLE, MESSY_BUNDLE, "--format", "json");
            assertThat(exitCode).isZero();
            String output = outContent.toString(StandardCharsets.UTF_8);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(output);
            assertThat(root.has("summary")).isTrue();
            assertThat(root.has("categoryDeltas")).isTrue();
            assertThat(root.has("resourceCountDeltas")).isTrue();
            assertThat(root.has("issueCounts")).isTrue();
            assertThat(root.has("newIssues")).isTrue();
            assertThat(root.has("resolvedIssues")).isTrue();
            assertThat(root.get("summary").has("scoreDelta")).isTrue();
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("Should write report directly to file when -o option is specified")
    void shouldOutputToFile(@TempDir Path tempDir) throws Exception {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        Path outputFile = tempDir.resolve("diff-report.json");
        int exitCode = cmd.execute("compare", CLEAN_BUNDLE, MESSY_BUNDLE, "--format", "json", "-o", outputFile.toString());
        assertThat(exitCode).isZero();
        assertThat(Files.exists(outputFile)).isTrue();

        String fileContent = Files.readString(outputFile);
        assertThat(fileContent).contains("\"summary\"");
    }

    @Test
    @DisplayName("Should return exit code 2 when baseline or target file does not exist")
    void shouldReturnExitCode2OnMissingFiles() {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        int exitCode = cmd.execute("compare", "non-existent-base.json", CLEAN_BUNDLE);
        assertThat(exitCode).isEqualTo(2);

        exitCode = cmd.execute("compare", CLEAN_BUNDLE, "non-existent-target.json");
        assertThat(exitCode).isEqualTo(2);
    }

    @Test
    @DisplayName("Should return exit code 2 when --max-score-drop is negative")
    void shouldReturnExitCode2OnNegativeScoreDrop() {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        int exitCode = cmd.execute("compare", CLEAN_BUNDLE, MESSY_BUNDLE, "--max-score-drop", "-5");
        assertThat(exitCode).isEqualTo(2);
    }
}
