package org.fhirlint.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ReferentialIntegrityCliTest {

    @Test
    @DisplayName("Broken reference fixture triggers quality gate exit code 1")
    void testBrokenReferenceExitsWithOne() {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        int exitCode = cmd.execute("validate", "sample-data/referential/broken-reference.json");
        assertThat(exitCode).isEqualTo(1);
    }

    @Test
    @DisplayName("Reference target type mismatch triggers exit code 1")
    void testTypeMismatchExitsWithOne() {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        int exitCode = cmd.execute("validate", "sample-data/referential/type-mismatch.json");
        assertThat(exitCode).isEqualTo(1);
    }

    @Test
    @DisplayName("Orphaned record triggers exit code 1 when --fail-on warning is specified")
    void testOrphanedRecordWithFailOnWarning() {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        int exitCode = cmd.execute("validate", "sample-data/referential/orphaned-observation.json", "--profile", "BASE_R4", "--fail-on", "warning");
        assertThat(exitCode).isEqualTo(1);

        // Under default --fail-on error, warnings pass the quality gate
        int defaultExitCode = cmd.execute("validate", "sample-data/referential/orphaned-observation.json", "--profile", "BASE_R4");
        assertThat(defaultExitCode).isZero();
    }

    @Test
    @DisplayName("External reference does not fail default quality gate")
    void testExternalReferencePassesDefaultGate() {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        int exitCode = cmd.execute("validate", "sample-data/referential/external-reference.json");
        assertThat(exitCode).isZero();
    }

    @Test
    @DisplayName("Multi-file directory cross-references resolve cleanly with exit code 0")
    void testMultiFileDirectoryResolvesCleanly() {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        int exitCode = cmd.execute("validate", "sample-data/referential/multi-file/");
        assertThat(exitCode).isZero();
    }

    @Test
    @DisplayName("JSON format renders referential integrity category breakdown")
    void testJsonOutputIncludesReferentialCategory() {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(outContent));
            cmd.execute("validate", "sample-data/referential/broken-reference.json", "--format", "json");
            String output = outContent.toString(StandardCharsets.UTF_8);

            assertThat(output).contains("\"REFERENTIAL_INTEGRITY\"");
            assertThat(output).contains("\"REF-001\"");
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("SARIF format registers REF rules in driver rules table")
    void testSarifOutputIncludesDriverRules() {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(outContent));
            cmd.execute("validate", "sample-data/referential/broken-reference.json", "--format", "sarif");
            String output = outContent.toString(StandardCharsets.UTF_8);

            assertThat(output).contains("\"REF-001\"");
            assertThat(output).contains("\"Broken Local Reference\"");
        } finally {
            System.setOut(originalOut);
        }
    }
}
