package org.fhirlint.cli;

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
            // Verify output is strictly valid JSON without terminal escape codes
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            assertThat(om.readTree(output)).isNotNull();
            assertThat(output).doesNotContain("\u001B");
        } catch (Exception e) {
            org.junit.jupiter.api.Assertions.fail("JSON output failed to parse: " + e.getMessage());
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("Should validate from stdin with pure JSON output to stdout")
    void shouldValidateStdinWithPureJsonOutput() throws Exception {
        String bundleJson = """
            {
              "resourceType": "Bundle",
              "type": "collection",
              "entry": [
                {
                  "resource": {
                    "resourceType": "Patient",
                    "id": "pat-stdin-json",
                    "identifier": [
                      {
                        "system": "http://hospital.smarthealth.org/mrn",
                        "value": "MRN-STDIN-JSON"
                      }
                    ],
                    "name": [
                      {
                        "family": "Smith",
                        "given": ["John"]
                      }
                    ],
                    "gender": "male"
                  }
                }
              ]
            }
            """;

        InputStream originalIn = System.in;
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setIn(new ByteArrayInputStream(bundleJson.getBytes(StandardCharsets.UTF_8)));
            System.setOut(new PrintStream(outContent));
            FhirLintApplication app = new FhirLintApplication();
            CommandLine cmd = new CommandLine(app);

            int exitCode = cmd.execute("validate", "-", "-f", "json");
            assertThat(exitCode).isZero();
            String output = outContent.toString(StandardCharsets.UTF_8);
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode rootNode = om.readTree(output);
            assertThat(rootNode.has("inventory")).isTrue();
            assertThat(rootNode.get("inventory").get("totalResources").asInt()).isEqualTo(1);
            assertThat(output).doesNotContain("\u001B");
        } finally {
            System.setIn(originalIn);
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

        int exitCode = cmd.execute("validate", "sample-data/messy/messy-bundle.json", "--min-score", "100");
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

    @Test
    @DisplayName("Should write report to output file specified via -o option")
    void shouldWriteReportToOutputFile() throws Exception {
        java.io.File tempFile = java.io.File.createTempFile("fhir-lint-test-report", ".txt");
        tempFile.deleteOnExit();

        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        int exitCode = cmd.execute("validate", "sample-data/clean/clean-bundle.json", "-o", tempFile.getAbsolutePath());
        assertThat(exitCode).isZero();
        assertThat(tempFile).exists();
        String content = java.nio.file.Files.readString(tempFile.toPath());
        assertThat(content).contains("Quality Score:");
    }

    @Test
    @DisplayName("Should reject empty 0-byte standard input stream with exit code 2")
    void shouldRejectEmptyStdin() {
        InputStream originalIn = System.in;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        try {
            System.setIn(new ByteArrayInputStream(new byte[0]));
            System.setErr(new PrintStream(errContent));
            FhirLintApplication app = new FhirLintApplication();
            CommandLine cmd = new CommandLine(app);

            int exitCode = cmd.execute("validate", "-");
            assertThat(exitCode).isEqualTo(2);
            assertThat(errContent.toString(StandardCharsets.UTF_8)).contains("Input stream is empty");
        } finally {
            System.setIn(originalIn);
            System.setErr(originalErr);
        }
    }

    @Test
    @DisplayName("Should reject directory with no .json files with exit code 2")
    void shouldRejectDirectoryWithNoJsonFiles() throws Exception {
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("empty-json-test");
        tempDir.toFile().deleteOnExit();

        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        try {
            System.setErr(new PrintStream(errContent));
            FhirLintApplication app = new FhirLintApplication();
            CommandLine cmd = new CommandLine(app);

            int exitCode = cmd.execute("validate", tempDir.toString());
            assertThat(exitCode).isEqualTo(2);
            assertThat(errContent.toString(StandardCharsets.UTF_8)).contains("Directory contains no .json files:");
        } finally {
            System.setErr(originalErr);
            java.nio.file.Files.deleteIfExists(tempDir);
        }
    }

    @Test
    @DisplayName("Should reject unsupported format with exit code 2")
    void shouldRejectUnsupportedFormat() {
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        try {
            System.setErr(new PrintStream(errContent));
            FhirLintApplication app = new FhirLintApplication();
            CommandLine cmd = new CommandLine(app);

            int exitCode = cmd.execute("validate", "sample-data/clean/clean-bundle.json", "-f", "xml");
            assertThat(exitCode).isEqualTo(2);
            assertThat(errContent.toString(StandardCharsets.UTF_8)).contains("Unsupported format 'xml'");
        } finally {
            System.setErr(originalErr);
        }
    }
}
