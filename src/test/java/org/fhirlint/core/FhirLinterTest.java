package org.fhirlint.core;

import org.fhirlint.core.model.LintReport;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.ValidationProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FhirLinterTest {

    @Test
    @DisplayName("Should successfully lint file using fluent API")
    void shouldLintFileWithFluentApi() {
        FhirLinter linter = FhirLinter.create()
            .withProfile(ValidationProfile.US_CORE);

        File cleanFile = new File("sample-data/clean/clean-bundle.json");
        LintReport report = linter.lint(cleanFile);

        assertThat(report).isNotNull();
        assertThat(report.targetProfile()).isEqualTo(ValidationProfile.US_CORE);
        assertThat(report.inventory().totalResources()).isEqualTo(5);
        assertThat(report.hasErrors()).isFalse();
        assertThat(report.qualityScore().getOverallScore()).isGreaterThanOrEqualTo(90);
        assertThat(report.passes(80, null)).isTrue();
    }

    @Test
    @DisplayName("Should inject and use custom referential integrity engine")
    void shouldInjectCustomReferentialIntegrityEngine() {
        org.fhirlint.core.graph.ReferentialIntegrityEngine customEngine = new org.fhirlint.core.graph.DefaultReferentialIntegrityEngine();
        FhirLinter linter = FhirLinter.create()
            .withReferentialIntegrityEngine(customEngine);

        assertThat(linter.getReferentialIntegrityEngine()).isSameAs(customEngine);
    }

    @Test
    @DisplayName("Should resolve urn:uuid references in bundle without false-positive REF-001")
    void shouldResolveUuidUrnInBundleWithoutFalsePositive() {
        String bundleJson = """
            {
              "resourceType": "Bundle",
              "type": "collection",
              "entry": [
                {
                  "fullUrl": "urn:uuid:patient-1111-2222",
                  "resource": {
                    "resourceType": "Patient",
                    "id": "pat-1"
                  }
                },
                {
                  "fullUrl": "urn:uuid:obs-3333-4444",
                  "resource": {
                    "resourceType": "Observation",
                    "id": "obs-1",
                    "status": "final",
                    "code": { "text": "Blood Pressure" },
                    "subject": {
                      "reference": "urn:uuid:patient-1111-2222"
                    }
                  }
                }
              ]
            }
            """;

        FhirLinter linter = FhirLinter.create();
        LintReport report = linter.lint(bundleJson);

        List<QualityIssue> refIssues = report.issues().stream()
            .filter(i -> i.category() == org.fhirlint.core.model.IssueCategory.REFERENTIAL_INTEGRITY)
            .toList();
        assertThat(refIssues).isEmpty();
    }

    @Test
    @DisplayName("Should successfully lint InputStream using fluent API")
    void shouldLintInputStreamWithFluentApi() throws Exception {
        FhirLinter linter = FhirLinter.create();
        try (java.io.InputStream is = java.nio.file.Files.newInputStream(java.nio.file.Path.of("sample-data/clean/clean-bundle.json"))) {
            LintReport report = linter.lint(is);
            assertThat(report).isNotNull();
            assertThat(report.inventory().totalResources()).isEqualTo(5);
            assertThat(report.qualityScore().getOverallScore()).isGreaterThanOrEqualTo(90);
        }
    }

    @Test
    @DisplayName("Should successfully lint multi-file collection and aggregate resources")
    void shouldLintFileListWithAggregation() {
        FhirLinter linter = FhirLinter.create();
        List<File> files = List.of(
            new File("sample-data/clean/clean-bundle.json"),
            new File("sample-data/referential/broken-reference.json")
        );

        LintReport report = linter.lint(files);
        assertThat(report).isNotNull();
        // 5 resources from clean bundle + 2 from broken reference bundle = 7
        assertThat(report.inventory().totalResources()).isEqualTo(7);
        assertThat(report.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("Should programmatically evaluate quality gate via LintReport")
    void shouldEvaluateQualityGateProgrammatically() {
        FhirLinter linter = FhirLinter.create();
        LintReport report = linter.lint(new File("sample-data/clean/clean-bundle.json"));

        org.fhirlint.core.model.QualityGateResult passResult = report.evaluateGate(
            org.fhirlint.core.model.QualityGateConfig.of(80, org.fhirlint.core.model.Severity.ERROR)
        );
        assertThat(passResult.passed()).isTrue();
        assertThat(passResult.breaches()).isEmpty();

        org.fhirlint.core.model.QualityGateResult failResult = report.evaluateGate(
            org.fhirlint.core.model.QualityGateConfig.of(100, org.fhirlint.core.model.Severity.INFO)
        );
        // Clean bundle has 0 errors/warnings, but info count or score check
        assertThat(failResult).isNotNull();
    }

    @Test
    @DisplayName("Should throw FhirParseException when file list is empty or null")
    void shouldRejectEmptyOrNullFileList() {
        FhirLinter linter = FhirLinter.create();
        org.junit.jupiter.api.Assertions.assertThrows(
            org.fhirlint.core.parser.FhirParseException.class,
            () -> linter.lint((List<File>) null)
        );
        org.junit.jupiter.api.Assertions.assertThrows(
            org.fhirlint.core.parser.FhirParseException.class,
            () -> linter.lint(List.of())
        );
    }

    @Test
    @DisplayName("Should throw NullPointerException when file, stream, or payload is null")
    void shouldRejectNullSingleInputs() {
        FhirLinter linter = FhirLinter.create();
        org.junit.jupiter.api.Assertions.assertThrows(
            NullPointerException.class,
            () -> linter.lint((File) null)
        );
        org.junit.jupiter.api.Assertions.assertThrows(
            NullPointerException.class,
            () -> linter.lint((java.io.InputStream) null)
        );
        org.junit.jupiter.api.Assertions.assertThrows(
            NullPointerException.class,
            () -> linter.lint((String) null)
        );
    }
}
