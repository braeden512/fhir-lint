package org.fhirlint.core.validation;

import org.fhirlint.core.FhirLinter;
import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.LintReport;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;
import org.fhirlint.core.model.ValidationProfile;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BaseR4StructuralValidationTest {

    private FhirLinter linter;

    @BeforeEach
    void setUp() {
        linter = FhirLinter.create().withProfile(ValidationProfile.BASE_R4);
    }

    @Test
    void testValidPatientPassesStructuralValidation() {
        String json = """
            {
              "resourceType": "Patient",
              "id": "pat-valid",
              "gender": "male",
              "birthDate": "1990-05-15"
            }
            """;
        LintReport report = linter.lint(json);

        assertThat(report.targetProfile()).isEqualTo(ValidationProfile.BASE_R4);
        List<QualityIssue> structuralErrors = report.issues().stream()
                .filter(i -> i.category() == IssueCategory.STRUCTURAL && i.severity() == Severity.ERROR)
                .toList();
        assertThat(structuralErrors).isEmpty();
        assertThat(report.qualityScore().getOverallScore()).isEqualTo(100);
    }

    @Test
    void testInvalidPrimitiveDateProducesStructuralError() {
        String json = """
            {
              "resourceType": "Patient",
              "id": "pat-invalid-date",
              "birthDate": "1985-99-99"
            }
            """;
        LintReport report = linter.lint(json);

        List<QualityIssue> errors = report.issues().stream()
                .filter(i -> i.severity() == Severity.ERROR)
                .toList();

        assertThat(errors).isNotEmpty();
        QualityIssue dateIssue = errors.stream()
                .filter(i -> i.path() != null && i.path().contains("birthDate"))
                .findFirst()
                .orElse(null);

        assertThat(dateIssue).isNotNull();
        assertThat(dateIssue.category()).isEqualTo(IssueCategory.STRUCTURAL);
        assertThat(dateIssue.suggestion()).isNotBlank();
        assertThat(report.qualityScore().getOverallScore()).isLessThan(100);
    }

    @Test
    void testMissingMandatoryFieldProducesStructuralError() {
        // In FHIR R4, Encounter requires 'status' and 'class'
        String json = """
            {
              "resourceType": "Encounter",
              "id": "enc-missing-status"
            }
            """;
        LintReport report = linter.lint(json);

        List<QualityIssue> errors = report.issues().stream()
                .filter(i -> i.severity() == Severity.ERROR && i.category() == IssueCategory.STRUCTURAL)
                .toList();

        assertThat(errors).isNotEmpty();
        assertThat(errors).anyMatch(i -> i.message().toLowerCase().contains("status") || i.message().toLowerCase().contains("class"));
    }

    @Test
    void testUnrecognizedElementProducesStructuralIssue() {
        String json = """
            {
              "resourceType": "Patient",
              "id": "pat-extra",
              "gender": "female",
              "nonExistentCustomField": "unexpectedValue"
            }
            """;
        LintReport report = linter.lint(json);

        List<QualityIssue> issues = report.issues().stream()
                .filter(i -> i.category() == IssueCategory.STRUCTURAL)
                .toList();

        assertThat(issues).isNotEmpty();
        assertThat(issues).anyMatch(i -> i.message().toLowerCase().contains("nonexistentcustomfield")
                || i.message().toLowerCase().contains("unknown"));
    }
}
