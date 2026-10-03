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

class CustomRulesCliTest {

    private static final String CLEAN_BUNDLE = "sample-data/clean/clean-bundle.json";
    private static final String MESSY_BUNDLE = "sample-data/messy/messy-bundle.json";

    @Test
    @DisplayName("Should execute validate with custom rules and detect custom violations")
    void shouldExecuteValidateWithCustomRules(@TempDir Path tempDir) throws Exception {
        // Rule: observation status must be preliminary (clean-bundle has final observations, so this will trigger)
        String yaml = """
            rules:
              - id: custom-vital-must-be-preliminary
                name: "Vital Signs Preliminary"
                resourceType: Observation
                category: completeness
                severity: error
                fhirpath: "status = 'preliminary'"
                message: "Observation is not preliminary."
                suggestion: "Set status to preliminary."
            """;

        Path ruleFile = tempDir.resolve("vital-rule.yaml");
        Files.writeString(ruleFile, yaml);

        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(outContent));
            int exitCode = cmd.execute("validate", CLEAN_BUNDLE, "--rules", ruleFile.toString(), "--format", "json");
            assertThat(exitCode).isEqualTo(1); // Default --fail-on error triggers gate failure
            String output = outContent.toString(StandardCharsets.UTF_8);
            assertThat(output).contains("custom-vital-must-be-preliminary");
            assertThat(output).contains("Observation is not preliminary.");
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("Should return exit code 2 when custom rules YAML is malformed")
    void shouldFailWithExitCode2OnMalformedYamlRules(@TempDir Path tempDir) throws Exception {
        String badYaml = """
            rules:
              - id: unclosed-quote
                name: "Bad YAML
            """;

        Path ruleFile = tempDir.resolve("bad-rule.yaml");
        Files.writeString(ruleFile, badYaml);

        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        try {
            System.setErr(new PrintStream(errContent));
            int exitCode = cmd.execute("validate", CLEAN_BUNDLE, "--rules", ruleFile.toString());
            assertThat(exitCode).isEqualTo(2);
            String errOutput = errContent.toString(StandardCharsets.UTF_8);
            assertThat(errOutput).contains("Error: Failed parsing custom rules YAML");
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    @DisplayName("Should return exit code 2 when custom rule has invalid FHIRPath syntax")
    void shouldFailWithExitCode2OnInvalidFhirPath(@TempDir Path tempDir) throws Exception {
        String badFhirPath = """
            rules:
              - id: invalid-syntax
                name: "Invalid Syntax"
                category: completeness
                severity: error
                fhirpath: "status = ((unclosed"
                message: "Syntax error"
            """;

        Path ruleFile = tempDir.resolve("bad-syntax.yaml");
        Files.writeString(ruleFile, badFhirPath);

        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        try {
            System.setErr(new PrintStream(errContent));
            int exitCode = cmd.execute("validate", CLEAN_BUNDLE, "--rules", ruleFile.toString());
            assertThat(exitCode).isEqualTo(2);
            String errOutput = errContent.toString(StandardCharsets.UTF_8);
            assertThat(errOutput).contains("Invalid FHIRPath invariant expression");
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    @DisplayName("Should execute compare with custom rules applied to both datasets")
    void shouldExecuteCompareWithCustomRules(@TempDir Path tempDir) throws Exception {
        String yaml = """
            rules:
              - id: custom-vital-must-be-final
                name: "Vital Signs Final"
                resourceType: Observation
                category: completeness
                severity: error
                fhirpath: "status = 'final'"
                message: "Observation is not final."
            """;

        Path ruleFile = tempDir.resolve("final-rule.yaml");
        Files.writeString(ruleFile, yaml);

        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(outContent));
            int exitCode = cmd.execute("compare", CLEAN_BUNDLE, MESSY_BUNDLE, "--rules", ruleFile.toString(), "--format", "json");
            assertThat(exitCode).isZero();
            String output = outContent.toString(StandardCharsets.UTF_8);
            assertThat(output).contains("\"scoreDelta\"");
        } finally {
            System.setOut(originalOut);
        }
    }
}
