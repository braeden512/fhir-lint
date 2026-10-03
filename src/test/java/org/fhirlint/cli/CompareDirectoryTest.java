package org.fhirlint.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CompareDirectoryTest {

    private static final String CLEAN_BUNDLE = "sample-data/clean/clean-bundle.json";
    private static final String MESSY_BUNDLE = "sample-data/messy/messy-bundle.json";

    @Test
    @DisplayName("Should compare two directories containing multiple FHIR JSON bundles")
    void shouldCompareTwoDirectories(@TempDir Path tempDir) throws Exception {
        Path dirA = tempDir.resolve("dirA");
        Path dirB = tempDir.resolve("dirB");
        Files.createDirectories(dirA);
        Files.createDirectories(dirB);

        // Copy clean bundle to dirA
        Files.copy(Path.of(CLEAN_BUNDLE), dirA.resolve("b1.json"));
        // Copy messy bundle to dirB
        Files.copy(Path.of(MESSY_BUNDLE), dirB.resolve("b2.json"));

        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(outContent));
            int exitCode = cmd.execute("compare", dirA.toString(), dirB.toString());
            assertThat(exitCode).isZero();
            String output = outContent.toString(StandardCharsets.UTF_8);
            assertThat(output).contains("FHIRLint Dataset Comparison & Regression Diff");
            assertThat(output).contains("Category Deltas:");
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("Should fail with exit code 2 when directory contains no JSON files")
    void shouldFailWhenDirectoryIsEmpty(@TempDir Path tempDir) throws Exception {
        Path emptyDir = tempDir.resolve("emptyDir");
        Files.createDirectories(emptyDir);

        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        try {
            System.setErr(new PrintStream(errContent));
            int exitCode = cmd.execute("compare", emptyDir.toString(), CLEAN_BUNDLE);
            assertThat(exitCode).isEqualTo(2);
            String errOutput = errContent.toString(StandardCharsets.UTF_8);
            assertThat(errOutput).contains("No JSON files discovered in directory");
        } finally {
            System.setErr(originalErr);
        }
    }
}
