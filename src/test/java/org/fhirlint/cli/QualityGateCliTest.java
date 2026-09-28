package org.fhirlint.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class QualityGateCliTest {

    @Test
    @DisplayName("Quality gate passes with exit code 0 when score meets min-score")
    void testGatePassesExitCode0() {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        try {
            System.setErr(new PrintStream(errContent));
            int exitCode = cmd.execute("validate", "sample-data/clean/clean-bundle.json", "--min-score", "90");
            assertThat(exitCode).isEqualTo(0);
            assertThat(errContent.toString(StandardCharsets.UTF_8)).isEmpty();
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    @DisplayName("Quality gate fails with exit code 1 when score is below min-score and prints breach to stderr")
    void testGateFailsOnMinScoreExitCode1() {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        try {
            System.setErr(new PrintStream(errContent));
            int exitCode = cmd.execute("validate", "sample-data/clean/clean-bundle.json", "--min-score", "101");
            assertThat(exitCode).isEqualTo(1);
            String errOutput = errContent.toString(StandardCharsets.UTF_8);
            assertThat(errOutput).contains("Quality gate breach:");
            assertThat(errOutput).contains("is below required minimum threshold (101)");
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    @DisplayName("Quality gate fails with exit code 1 when errors present and --fail-on error")
    void testGateFailsOnSeverityExitCode1() {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        try {
            System.setErr(new PrintStream(errContent));
            int exitCode = cmd.execute("validate", "sample-data/referential/broken-reference.json", "--fail-on", "error");
            assertThat(exitCode).isEqualTo(1);
            String errOutput = errContent.toString(StandardCharsets.UTF_8);
            assertThat(errOutput).contains("Quality gate breach:");
            assertThat(errOutput).contains("with severity ERROR or higher");
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    @DisplayName("Quality gate reports multiple breaches to stderr when both score and severity fail")
    void testGateReportsMultipleBreachesToStderr() {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        try {
            System.setErr(new PrintStream(errContent));
            int exitCode = cmd.execute("validate", "sample-data/referential/broken-reference.json", "--min-score", "101", "--fail-on", "error");
            assertThat(exitCode).isEqualTo(1);
            String errOutput = errContent.toString(StandardCharsets.UTF_8);
            assertThat(errOutput).contains("is below required minimum threshold (101)");
            assertThat(errOutput).contains("with severity ERROR or higher");
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    @DisplayName("Pre-flight boundary check rejects negative --min-score with exit code 2")
    void testNegativeMinScoreRejection() {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        try {
            System.setErr(new PrintStream(errContent));
            int exitCode = cmd.execute("validate", "sample-data/clean/clean-bundle.json", "--min-score", "-5");
            assertThat(exitCode).isEqualTo(2);
            assertThat(errContent.toString(StandardCharsets.UTF_8)).contains("--min-score cannot be negative");
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    @DisplayName("--fail-on none allows dataset with errors to pass if min-score is satisfied")
    void testFailOnNoneDisablesSeverityGating() {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        try {
            System.setErr(new PrintStream(errContent));
            // broken-reference.json has errors, but with min-score 50 and fail-on none, it should pass with exit code 0
            int exitCode = cmd.execute("validate", "sample-data/referential/broken-reference.json", "--min-score", "50", "--fail-on", "none");
            assertThat(exitCode).isEqualTo(0);
            assertThat(errContent.toString(StandardCharsets.UTF_8)).isEmpty();
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    @DisplayName("Invalid --fail-on string exits with code 2 and lists supported options")
    void testInvalidFailOnExitsCode2() {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        try {
            System.setErr(new PrintStream(errContent));
            int exitCode = cmd.execute("validate", "sample-data/clean/clean-bundle.json", "--fail-on", "fatal");
            assertThat(exitCode).isEqualTo(2);
            assertThat(errContent.toString(StandardCharsets.UTF_8)).contains("Supported: error, warning, info, none.");
        } finally {
            System.setErr(originalErr);
        }
    }
}
