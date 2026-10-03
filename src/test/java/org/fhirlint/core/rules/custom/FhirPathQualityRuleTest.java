package org.fhirlint.core.rules.custom;

import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;
import org.fhirlint.core.rules.RuleContext;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FhirPathQualityRuleTest {

    @Test
    @DisplayName("Should emit QualityIssue when FHIRPath invariant evaluates to false")
    void shouldEmitIssueOnInvariantViolation() {
        String yaml = """
            rules:
              - id: custom-vital-final
                name: "Vital Signs Must Be Final"
                resourceType: Observation
                category: completeness
                severity: error
                fhirpath: "status = 'final'"
                message: "Observation is not final."
                suggestion: "Transition observation to final."
            """;

        List<FhirPathQualityRule> rules = CustomRuleLoader.loadRulesFromString(yaml);
        FhirPathQualityRule rule = rules.get(0);

        Observation obs = new Observation();
        obs.setId("obs-prelim");
        obs.setStatus(Observation.ObservationStatus.PRELIMINARY);

        RuleContext ctx = RuleContext.builder().resource(obs).build();
        List<QualityIssue> issues = rule.evaluate(ctx);

        assertThat(issues).hasSize(1);
        QualityIssue issue = issues.get(0);
        assertThat(issue.ruleId()).isEqualTo("custom-vital-final");
        assertThat(issue.severity()).isEqualTo(Severity.ERROR);
        assertThat(issue.category()).isEqualTo(IssueCategory.COMPLETENESS);
        assertThat(issue.resourceType()).isEqualTo("Observation");
        assertThat(issue.resourceId()).isEqualTo("obs-prelim");
        assertThat(issue.message()).isEqualTo("Observation is not final.");
        assertThat(issue.suggestion()).isEqualTo("Transition observation to final.");
    }

    @Test
    @DisplayName("Should emit no issues when FHIRPath invariant passes")
    void shouldPassWhenInvariantSatisfied() {
        String yaml = """
            rules:
              - id: custom-vital-final
                name: "Vital Signs Must Be Final"
                resourceType: Observation
                category: completeness
                severity: error
                fhirpath: "status = 'final'"
                message: "Observation is not final."
            """;

        List<FhirPathQualityRule> rules = CustomRuleLoader.loadRulesFromString(yaml);
        FhirPathQualityRule rule = rules.get(0);

        Observation obs = new Observation();
        obs.setId("obs-final");
        obs.setStatus(Observation.ObservationStatus.FINAL);

        RuleContext ctx = RuleContext.builder().resource(obs).build();
        List<QualityIssue> issues = rule.evaluate(ctx);

        assertThat(issues).isEmpty();
    }

    @Test
    @DisplayName("Should skip evaluation when resource type does not match rule target")
    void shouldSkipNonMatchingResourceType() {
        String yaml = """
            rules:
              - id: custom-vital-final
                name: "Vital Signs Must Be Final"
                resourceType: Observation
                category: completeness
                severity: error
                fhirpath: "status = 'final'"
                message: "Observation is not final."
            """;

        List<FhirPathQualityRule> rules = CustomRuleLoader.loadRulesFromString(yaml);
        FhirPathQualityRule rule = rules.get(0);

        Patient patient = new Patient();
        patient.setId("pat-1");

        RuleContext ctx = RuleContext.builder().resource(patient).build();
        List<QualityIssue> issues = rule.evaluate(ctx);

        assertThat(issues).isEmpty();
    }
}
