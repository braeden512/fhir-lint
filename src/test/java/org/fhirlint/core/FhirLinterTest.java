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
}
