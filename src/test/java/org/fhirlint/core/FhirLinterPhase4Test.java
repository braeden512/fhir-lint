package org.fhirlint.core;

import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.LintReport;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.ValidationProfile;
import org.fhirlint.core.rules.DefaultQualityRuleEngine;
import org.fhirlint.core.rules.QualityRuleEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FhirLinterPhase4Test {

    @Test
    @DisplayName("Should inject and use custom QualityRuleEngine")
    void shouldInjectCustomQualityRuleEngine() {
        QualityRuleEngine customEngine = new DefaultQualityRuleEngine();
        FhirLinter linter = FhirLinter.create()
                .withQualityRuleEngine(customEngine);

        assertThat(linter.getQualityRuleEngine()).isSameAs(customEngine);
    }

    @Test
    @DisplayName("Should evaluate multi-category rules and calculate category scores")
    void shouldEvaluateMultiCategoryRulesAndCalculateCategoryScores() {
        String bundleJson = """
            {
              "resourceType": "Bundle",
              "type": "collection",
              "entry": [
                {
                  "fullUrl": "urn:uuid:pat-1",
                  "resource": {
                    "resourceType": "Patient",
                    "id": "pat-1",
                    "identifier": [
                      {
                        "system": "http://hospital.example.org/patients",
                        "value": "MRN-12345"
                      }
                    ]
                  }
                },
                {
                  "fullUrl": "urn:uuid:pat-2",
                  "resource": {
                    "resourceType": "Patient",
                    "id": "pat-2",
                    "identifier": [
                      {
                        "system": "http://hospital.example.org/patients",
                        "value": "MRN-12345"
                      }
                    ]
                  }
                },
                {
                  "fullUrl": "urn:uuid:enc-1",
                  "resource": {
                    "resourceType": "Encounter",
                    "id": "enc-1",
                    "status": "finished",
                    "class": {
                      "system": "http://terminology.hl7.org/CodeSystem/v3-ActCode",
                      "code": "AMB"
                    },
                    "subject": {
                      "reference": "urn:uuid:pat-1"
                    },
                    "period": {
                      "start": "2023-05-10T10:00:00Z",
                      "end": "2023-05-01T10:00:00Z"
                    }
                  }
                },
                {
                  "fullUrl": "urn:uuid:obs-1",
                  "resource": {
                    "resourceType": "Observation",
                    "id": "obs-1",
                    "status": "final",
                    "code": {
                      "coding": [
                        {
                          "system": "http://loinc.org/",
                          "code": "8867-4"
                        }
                      ]
                    },
                    "subject": {
                      "reference": "urn:uuid:pat-1"
                    },
                    "valueQuantity": {
                      "value": 72,
                      "unit": "beats/minute"
                    }
                  }
                },
                {
                  "fullUrl": "urn:uuid:obs-2",
                  "resource": {
                    "resourceType": "Observation",
                    "id": "obs-2",
                    "status": "final",
                    "code": {
                      "coding": [
                        {
                          "system": "http://loinc.org",
                          "code": "8867-4"
                        }
                      ]
                    }
                  }
                }
              ]
            }
            """;

        FhirLinter linter = FhirLinter.create().withProfile(ValidationProfile.BASE_R4);
        LintReport report = linter.lint(bundleJson);

        assertThat(report).isNotNull();
        List<QualityIssue> issues = report.issues();

        // 1. Verify CONS-001 (Period inverted)
        assertThat(issues).anyMatch(i -> "CONS-001".equals(i.ruleId()) && i.category() == IssueCategory.CONSISTENCY);

        // 2. Verify DUP-001 (Identifier collision)
        assertThat(issues).anyMatch(i -> "DUP-001".equals(i.ruleId()) && i.category() == IssueCategory.DUPLICATE);

        // 3. Verify TERM-001 (Non-canonical LOINC trailing slash)
        assertThat(issues).anyMatch(i -> "TERM-001".equals(i.ruleId()) && i.category() == IssueCategory.TERMINOLOGY);

        // 4. Verify COMP-001 (Missing subject in obs-2)
        assertThat(issues).anyMatch(i -> "COMP-001".equals(i.ruleId()) && i.category() == IssueCategory.COMPLETENESS);

        // Verify category scores reflect penalties
        assertThat(report.qualityScore().getCategoryScores().get(IssueCategory.CONSISTENCY)).isLessThan(100);
        assertThat(report.qualityScore().getCategoryScores().get(IssueCategory.TERMINOLOGY)).isLessThan(100);
        assertThat(report.qualityScore().getCategoryScores().get(IssueCategory.COMPLETENESS)).isLessThan(100);
    }

    @Test
    @DisplayName("Clean benchmark bundle should yield zero quality issues")
    void cleanBenchmarkBundleShouldYieldZeroQualityIssues() {
        FhirLinter linter = FhirLinter.create().withProfile(ValidationProfile.US_CORE);
        File cleanFile = new File("sample-data/clean/clean-bundle.json");
        LintReport report = linter.lint(cleanFile);

        // SC-011: 0% false-positive consistency, completeness, or duplicate issues, and zero errors
        assertThat(report.hasErrors()).isFalse();
        assertThat(report.qualityScore().getOverallScore()).isGreaterThanOrEqualTo(90);

        List<QualityIssue> falsePositives = report.issues().stream()
                .filter(i -> i.category() == IssueCategory.CONSISTENCY
                        || i.category() == IssueCategory.COMPLETENESS
                        || i.category() == IssueCategory.DUPLICATE)
                .toList();

        assertThat(falsePositives).isEmpty();
    }
}
