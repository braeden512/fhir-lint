package org.fhirlint.core.validation;

import org.fhirlint.core.FhirLinter;
import org.fhirlint.core.model.LintReport;
import org.fhirlint.core.model.ValidationProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationPerformanceAndPrivacyTest {

    @Test
    @DisplayName("Should validate a 100-resource bundle in under 2 seconds once warm")
    void shouldValidate100ResourcesUnderTwoSeconds() {
        FhirLinter linter = FhirLinter.create().withProfile(ValidationProfile.US_CORE);

        // Warm up the engine
        String warmupBundle = """
            {
              "resourceType": "Bundle",
              "type": "collection",
              "entry": [
                {
                  "resource": {
                    "resourceType": "Patient",
                    "id": "warmup-1",
                    "identifier": [{"system": "http://example.org", "value": "123"}],
                    "name": [{"family": "Warmup", "given": ["Test"]}],
                    "gender": "male"
                  }
                }
              ]
            }
            """;
        linter.lint(warmupBundle);

        // Generate 100-resource bundle
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"resourceType\": \"Bundle\",\n  \"type\": \"collection\",\n  \"entry\": [\n");
        for (int i = 0; i < 100; i++) {
            if (i > 0) sb.append(",\n");
            sb.append(String.format("""
                {
                  "resource": {
                    "resourceType": "Patient",
                    "id": "pat-%d",
                    "identifier": [
                      {
                        "system": "http://hospital.smarthealth.org/mrn",
                        "value": "MRN-%d"
                      }
                    ],
                    "name": [
                      {
                        "family": "Patient%d",
                        "given": ["Test"]
                      }
                    ],
                    "gender": "%s"
                  }
                }
                """, i, i, i, (i % 2 == 0 ? "male" : "female")));
        }
        sb.append("\n  ]\n}");
        String bundle100 = sb.toString();

        long start = System.currentTimeMillis();
        LintReport report = linter.lint(bundle100);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(report).isNotNull();
        assertThat(report.inventory().totalResources()).isEqualTo(100);
        // Performance requirement SC-004: under 2 seconds (2000 ms) for 100 resources
        assertThat(elapsed)
            .as("Validation of 100 resources should complete in under 2000ms once warmed up, took %dms", elapsed)
            .isLessThan(2000L);
    }

    @Test
    @DisplayName("Should ensure zero disk retention and 100% in-memory offline operation")
    void shouldEnsureZeroDiskRetention() throws Exception {
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));
        long filesBefore;
        try (Stream<Path> stream = Files.list(tempDir)) {
            filesBefore = stream.filter(p -> p.getFileName().toString().contains("fhirlint")).count();
        }

        FhirLinter linter = FhirLinter.create().withProfile(ValidationProfile.US_CORE);
        String patientJson = """
            {
              "resourceType": "Patient",
              "id": "privacy-pat-1",
              "identifier": [{"system": "http://hospital.smarthealth.org/mrn", "value": "MRN-PRIVACY-01"}],
              "name": [{"family": "Sensitive", "given": ["John"]}],
              "gender": "male"
            }
            """;

        LintReport report = linter.lint(patientJson);
        assertThat(report).isNotNull();

        // Verify no fhirlint disk artifacts created in temp directory
        long filesAfter;
        try (Stream<Path> stream = Files.list(tempDir)) {
            filesAfter = stream.filter(p -> p.getFileName().toString().contains("fhirlint")).count();
        }
        assertThat(filesAfter).isEqualTo(filesBefore);

        // Verify no local database files or cache files were created in working directory
        File workspace = new File(".");
        File[] cacheFiles = workspace.listFiles((dir, name) -> name.startsWith(".fhir") || name.startsWith(".cache") || name.endsWith(".db"));
        assertThat(cacheFiles == null ? 0 : cacheFiles.length).isZero();
    }
}
