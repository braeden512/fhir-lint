package org.fhirlint.cli;

import org.fhirlint.core.model.ValidationProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileSelectionTest {

    @Test
    @DisplayName("ValidationProfile.fromString parses valid strings and throws on invalid")
    void testValidationProfileFromString() {
        assertThat(ValidationProfile.fromString("BASE_R4")).isEqualTo(ValidationProfile.BASE_R4);
        assertThat(ValidationProfile.fromString("base-r4")).isEqualTo(ValidationProfile.BASE_R4);
        assertThat(ValidationProfile.fromString("baser4")).isEqualTo(ValidationProfile.BASE_R4);
        assertThat(ValidationProfile.fromString("US_CORE")).isEqualTo(ValidationProfile.US_CORE);
        assertThat(ValidationProfile.fromString("us-core")).isEqualTo(ValidationProfile.US_CORE);
        assertThat(ValidationProfile.fromString(null)).isEqualTo(ValidationProfile.US_CORE);
        assertThat(ValidationProfile.fromString("")).isEqualTo(ValidationProfile.US_CORE);

        assertThatThrownBy(() -> ValidationProfile.fromString("UNKNOWN_PROFILE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown or unsupported validation profile: 'UNKNOWN_PROFILE'");
    }

    @Test
    @DisplayName("CLI returns exit code 2 when invalid --profile is supplied")
    void testCliWithInvalidProfileReturnsExitCode2() {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        try {
            System.setErr(new PrintStream(errContent));
            int exitCode = cmd.execute("validate", "sample-data/clean/clean-bundle.json", "--profile", "NONEXISTENT_PROFILE");
            assertThat(exitCode).isEqualTo(2);
            String errorOutput = errContent.toString(StandardCharsets.UTF_8);
            assertThat(errorOutput).contains("Unknown or unsupported validation profile: 'NONEXISTENT_PROFILE'");
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    @DisplayName("CLI returns exit code 2 when invalid --fail-on is supplied")
    void testCliWithInvalidFailOnReturnsExitCode2() {
        FhirLintApplication app = new FhirLintApplication();
        CommandLine cmd = new CommandLine(app);

        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        try {
            System.setErr(new PrintStream(errContent));
            int exitCode = cmd.execute("validate", "sample-data/clean/clean-bundle.json", "--fail-on", "invalid_severity");
            assertThat(exitCode).isEqualTo(2);
            String errorOutput = errContent.toString(StandardCharsets.UTF_8);
            assertThat(errorOutput).contains("Unknown or unsupported severity threshold 'invalid_severity'");
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    @DisplayName("CLI switches behavior between BASE_R4 (passes) and US_CORE (fails on US Core constraint)")
    void testProfileSwitchingBehavior() throws IOException {
        // Base-valid Patient lacking required US Core name and identifier
        String baseValidJson = """
            {
              "resourceType": "Patient",
              "id": "pat-base-only",
              "gender": "male",
              "birthDate": "1995-10-20"
            }
            """;

        File tempFile = File.createTempFile("patient-base-valid", ".json");
        tempFile.deleteOnExit();
        try (FileWriter writer = new FileWriter(tempFile, StandardCharsets.UTF_8)) {
            writer.write(baseValidJson);
        }

        FhirLintApplication app = new FhirLintApplication();

        // 1. BASE_R4 passes with exit code 0
        CommandLine cmdBase = new CommandLine(app);
        int exitCodeBase = cmdBase.execute("validate", tempFile.getAbsolutePath(), "--profile", "BASE_R4");
        assertThat(exitCodeBase).isEqualTo(0);

        // 2. US_CORE fails with exit code 1 due to missing mandatory US Core profile elements
        CommandLine cmdUsCore = new CommandLine(app);
        int exitCodeUsCore = cmdUsCore.execute("validate", tempFile.getAbsolutePath(), "--profile", "US_CORE");
        assertThat(exitCodeUsCore).isEqualTo(1);
    }
}
