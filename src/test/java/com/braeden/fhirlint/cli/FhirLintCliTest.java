package com.braeden.fhirlint.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FhirLintCliTest {

    @Test
    @DisplayName("Should execute CLI validate on clean-bundle.json and return 0")
    void shouldValidateCleanBundleSuccessfully() {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        int exitCode = cmd.execute("validate", "sample-data/clean/clean-bundle.json");
        assertThat(exitCode).isZero();
    }

    @Test
    @DisplayName("Should execute CLI with JSON format and print valid JSON report")
    void shouldRenderJsonFormat() {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(outContent));
            int exitCode = cmd.execute("validate", "sample-data/clean/clean-bundle.json", "--format", "json");
            assertThat(exitCode).isZero();
            String output = outContent.toString(StandardCharsets.UTF_8);
            assertThat(output).contains("\"totalResources\" : 5");
            assertThat(output).contains("\"grade\" : \"EXCELLENT\"");
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("Should execute CLI with SARIF format")
    void shouldRenderSarifFormat() {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(outContent));
            int exitCode = cmd.execute("validate", "sample-data/clean/clean-bundle.json", "--format", "sarif");
            assertThat(exitCode).isZero();
            String output = outContent.toString(StandardCharsets.UTF_8);
            assertThat(output).contains("\"version\" : \"2.1.0\"");
            assertThat(output).contains("FHIRLint");
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("Should return exit code 1 when quality score is below min-score")
    void shouldReturnCode1WhenScoreBelowMin() {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        int exitCode = cmd.execute("validate", "sample-data/clean/clean-bundle.json", "--min-score", "101");
        assertThat(exitCode).isEqualTo(1);
    }

    @Test
    @DisplayName("Should return exit code 2 when file does not exist")
    void shouldReturnCode2ForNonExistentFile() {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        int exitCode = cmd.execute("validate", "non-existent-bundle.json");
        assertThat(exitCode).isEqualTo(2);
    }

    @Test
    @DisplayName("Should validate from standard input using '-' parameter")
    void shouldValidateFromStdin() {
        String bundleJson = """
            {
              "resourceType": "Bundle",
              "type": "collection",
              "entry": [
                {
                  "resource": {
                    "resourceType": "Patient",
                    "id": "pat-stdin-1",
                    "identifier": [
                      {
                        "system": "http://hospital.smarthealth.org/mrn",
                        "value": "MRN-STDIN-01"
                      }
                    ],
                    "name": [
                      {
                        "family": "Doe",
                        "given": ["Jane"]
                      }
                    ],
                    "gender": "female"
                  }
                }
              ]
            }
            """;

        InputStream originalIn = System.in;
        try {
            System.setIn(new ByteArrayInputStream(bundleJson.getBytes(StandardCharsets.UTF_8)));
            FhirLintApplication app = new FhirLintApplication();
            CommandLine cmd = new CommandLine(app);

            int exitCode = cmd.execute("validate", "-");
            assertThat(exitCode).isZero();
        } finally {
            System.setIn(originalIn);
        }
    }

    @Test
    @DisplayName("Should execute CLI validate on directory and return 0")
    void shouldValidateDirectorySuccessfully() {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        int exitCode = cmd.execute("validate", "sample-data/clean");
        assertThat(exitCode).isZero();
    }
}
