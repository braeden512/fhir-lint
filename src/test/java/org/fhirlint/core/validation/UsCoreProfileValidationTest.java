package org.fhirlint.core.validation;

import org.fhirlint.core.FhirLinter;
import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.LintReport;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;
import org.fhirlint.core.model.ValidationProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UsCoreProfileValidationTest {

    private FhirLinter linter;

    @BeforeEach
    void setUp() {
        linter = FhirLinter.create().withProfile(ValidationProfile.US_CORE);
    }

    @Test
    @DisplayName("US Core Patient missing mandatory name or identifier triggers PROFILE_CONFORMANCE error")
    void testPatientMissingNameOrIdentifierFailsUsCore() {
        // Valid in base FHIR R4, but violates US Core Patient mandatory constraints
        String json = """
            {
              "resourceType": "Patient",
              "id": "pat-missing-name",
              "gender": "female"
            }
            """;
        LintReport report = linter.lint(json);

        assertThat(report.targetProfile()).isEqualTo(ValidationProfile.US_CORE);
        List<QualityIssue> profileErrors = report.issues().stream()
                .filter(i -> i.severity() == Severity.ERROR && i.category() == IssueCategory.PROFILE_CONFORMANCE)
                .toList();

        assertThat(profileErrors).isNotEmpty();
        assertThat(profileErrors).anyMatch(i -> i.message().toLowerCase().contains("name") 
                || i.message().toLowerCase().contains("identifier"));
    }

    @Test
    @DisplayName("US Core Encounter missing required type triggers PROFILE_CONFORMANCE error")
    void testEncounterMissingTypeFailsUsCore() {
        String json = """
            {
              "resourceType": "Encounter",
              "id": "enc-no-type",
              "status": "finished",
              "class": {
                "system": "http://terminology.hl7.org/CodeSystem/v3-ActCode",
                "code": "AMB",
                "display": "ambulatory"
              },
              "subject": {
                "reference": "Patient/pat-001"
              }
            }
            """;
        LintReport report = linter.lint(json);

        List<QualityIssue> profileErrors = report.issues().stream()
                .filter(i -> i.severity() == Severity.ERROR && i.category() == IssueCategory.PROFILE_CONFORMANCE)
                .toList();

        assertThat(profileErrors).isNotEmpty();
        assertThat(profileErrors).anyMatch(i -> i.message().toLowerCase().contains("type"));
    }

    @Test
    @DisplayName("US Core Vital Signs Observation missing UCUM units or required category triggers PROFILE_CONFORMANCE issue")
    void testVitalSignsMissingUcumUnitFailsUsCore() {
        String json = """
            {
              "resourceType": "Observation",
              "id": "obs-bad-unit",
              "status": "final",
              "category": [
                {
                  "coding": [
                    {
                      "system": "http://terminology.hl7.org/CodeSystem/observation-category",
                      "code": "vital-signs"
                    }
                  ]
                }
              ],
              "code": {
                "coding": [
                  {
                    "system": "http://loinc.org",
                    "code": "8867-4"
                  }
                ]
              },
              "subject": {
                "reference": "Patient/pat-001"
              },
              "effectiveDateTime": "2026-05-01T09:15:00Z"
            }
            """;
        LintReport report = linter.lint(json);

        List<QualityIssue> issues = report.issues().stream()
                .filter(i -> i.category() == IssueCategory.PROFILE_CONFORMANCE)
                .toList();

        assertThat(issues).isNotEmpty();
    }

    @Test
    @DisplayName("Conformant US Core Patient resource passes with 0 errors")
    void testValidUsCorePatientPassesValidation() {
        String json = """
            {
              "resourceType": "Patient",
              "id": "pat-conformant",
              "identifier": [
                {
                  "system": "http://hospital.smarthealth.org/mrn",
                  "value": "MRN-12345"
                }
              ],
              "name": [
                {
                  "use": "official",
                  "family": "Doe",
                  "given": ["Jane"]
                }
              ],
              "gender": "female",
              "birthDate": "1990-01-01"
            }
            """;
        LintReport report = linter.lint(json);

        List<QualityIssue> errors = report.issues().stream()
                .filter(i -> i.severity() == Severity.ERROR)
                .toList();

        assertThat(errors).isEmpty();
        assertThat(report.qualityScore().getOverallScore()).isGreaterThanOrEqualTo(90);
    }

    @Test
    @DisplayName("US Core MedicationRequest missing requester triggers PROFILE_CONFORMANCE error")
    void testMedicationRequestMissingRequesterFailsUsCore() {
        String json = """
            {
              "resourceType": "MedicationRequest",
              "id": "med-no-requester",
              "status": "active",
              "intent": "order",
              "medicationCodeableConcept": {
                "coding": [
                  {
                    "system": "http://www.nlm.nih.gov/research/umls/rxnorm",
                    "code": "860975"
                  }
                ]
              },
              "subject": {
                "reference": "Patient/pat-001"
              },
              "authoredOn": "2026-05-01T09:30:00Z"
            }
            """;
        LintReport report = linter.lint(json);

        List<QualityIssue> profileErrors = report.issues().stream()
                .filter(i -> i.severity() == Severity.ERROR && i.category() == IssueCategory.PROFILE_CONFORMANCE)
                .toList();

        assertThat(profileErrors).isNotEmpty();
        assertThat(profileErrors).anyMatch(i -> i.message().toLowerCase().contains("requester"));
    }
}
