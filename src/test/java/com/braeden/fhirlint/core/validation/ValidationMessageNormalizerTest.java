package com.braeden.fhirlint.core.validation;

import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import com.braeden.fhirlint.core.model.IssueCategory;
import com.braeden.fhirlint.core.model.QualityIssue;
import com.braeden.fhirlint.core.model.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationMessageNormalizerTest {

    private ValidationMessageNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new ValidationMessageNormalizer();
    }

    @Test
    @DisplayName("Severity mappings match ADR-002 specification")
    void testSeverityMapping() {
        assertThat(normalizer.mapSeverity(ResultSeverityEnum.FATAL)).isEqualTo(Severity.ERROR);
        assertThat(normalizer.mapSeverity(ResultSeverityEnum.ERROR)).isEqualTo(Severity.ERROR);
        assertThat(normalizer.mapSeverity(ResultSeverityEnum.WARNING)).isEqualTo(Severity.WARNING);
        assertThat(normalizer.mapSeverity(ResultSeverityEnum.INFORMATION)).isEqualTo(Severity.INFO);
        assertThat(normalizer.mapSeverity(null)).isEqualTo(Severity.WARNING);
    }

    @Test
    @DisplayName("Structural vs Profile Conformance categories are distinguished correctly")
    void testCategoryDetermination() {
        assertThat(normalizer.determineCategory("Invalid date format", "Patient.birthDate"))
                .isEqualTo(IssueCategory.STRUCTURAL);
        assertThat(normalizer.determineCategory("Unknown element 'customField'", "Patient.customField"))
                .isEqualTo(IssueCategory.STRUCTURAL);

        assertThat(normalizer.determineCategory("Element does not match slice vital-signs", "Observation.category[0]"))
                .isEqualTo(IssueCategory.PROFILE_CONFORMANCE);
        assertThat(normalizer.determineCategory("Failed invariant us-core-8", "Patient"))
                .isEqualTo(IssueCategory.PROFILE_CONFORMANCE);
        assertThat(normalizer.determineCategory("Must support element missing", "Patient.name"))
                .isEqualTo(IssueCategory.PROFILE_CONFORMANCE);
    }

    @Test
    @DisplayName("Deterministic rule IDs are assigned based on category and failure pattern")
    void testRuleIdAssignment() {
        assertThat(normalizer.determineRuleId("Invalid primitive format: does not match regex", IssueCategory.STRUCTURAL))
                .isEqualTo("STRUCT_PRIMITIVE_FORMAT");
        assertThat(normalizer.determineRuleId("Unknown element 'foo'", IssueCategory.STRUCTURAL))
                .isEqualTo("STRUCT_UNKNOWN_ELEMENT");
        assertThat(normalizer.determineRuleId("Minimum allowed cardinality is 1", IssueCategory.STRUCTURAL))
                .isEqualTo("STRUCT_CARDINALITY");
        assertThat(normalizer.determineRuleId("Expected String but found Integer", IssueCategory.STRUCTURAL))
                .isEqualTo("STRUCT_DATATYPE");

        assertThat(normalizer.determineRuleId("Does not match slicing discriminator", IssueCategory.PROFILE_CONFORMANCE))
                .isEqualTo("USCORE_SLICING");
        assertThat(normalizer.determineRuleId("Failed us-core-8 invariant", IssueCategory.PROFILE_CONFORMANCE))
                .isEqualTo("USCORE_INVARIANT");
        assertThat(normalizer.determineRuleId("Missing required must support element", IssueCategory.PROFILE_CONFORMANCE))
                .isEqualTo("USCORE_MUST_SUPPORT");
        assertThat(normalizer.determineRuleId("Minimum allowed is 1 from profile", IssueCategory.PROFILE_CONFORMANCE))
                .isEqualTo("USCORE_MANDATORY_FIELD");
    }

    @Test
    @DisplayName("HAPI prefix is stripped and actionable suggestion is generated")
    void testMessageCleanupAndSuggestion() {
        SingleValidationMessage msg = new SingleValidationMessage();
        msg.setSeverity(ResultSeverityEnum.ERROR);
        msg.setLocationString("Patient.birthDate");
        msg.setMessage("HAPI-1821: Invalid date/time format: \"1985-99-99\"");

        QualityIssue issue = normalizer.normalizeSingle(msg, "Patient", "pat-123");

        assertThat(issue).isNotNull();
        assertThat(issue.severity()).isEqualTo(Severity.ERROR);
        assertThat(issue.category()).isEqualTo(IssueCategory.STRUCTURAL);
        assertThat(issue.ruleId()).isEqualTo("STRUCT_PRIMITIVE_FORMAT");
        assertThat(issue.message()).isEqualTo("Invalid date/time format: \"1985-99-99\"");
        assertThat(issue.suggestion()).contains("Ensure the value conforms to the FHIR R4 primitive specification");
        assertThat(issue.resourceType()).isEqualTo("Patient");
        assertThat(issue.resourceId()).isEqualTo("pat-123");
    }
}
