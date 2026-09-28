package org.fhirlint.core.rules.terminology;

import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;
import org.fhirlint.core.rules.RuleContext;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CoreValueSetBindingRuleTest {

    private CoreValueSetBindingRule rule;
    private TerminologyService terminologyService;

    @BeforeEach
    void setUp() {
        rule = new CoreValueSetBindingRule();
        terminologyService = new DefaultTerminologyService();
    }

    @Test
    void shouldFlagInvalidGenderCode() {
        Patient patient = new Patient();
        patient.setId("pat-invalid-gender");
        patient.setGenderElement(new org.hl7.fhir.r4.model.Enumeration<>(new Enumerations.AdministrativeGenderEnumFactory()) {
            @Override
            public String getCode() {
                return "invalid-gender";
            }
            @Override
            public boolean hasCode() {
                return true;
            }
            @Override
            public boolean isEmpty() {
                return false;
            }
        });

        RuleContext context = new RuleContext(patient, List.of(patient), null, terminologyService);
        List<QualityIssue> issues = rule.evaluate(context);

        assertThat(issues).hasSize(1);
        QualityIssue issue = issues.get(0);
        assertThat(issue.ruleId()).isEqualTo("TERM-003");
        assertThat(issue.severity()).isEqualTo(Severity.ERROR);
        assertThat(issue.category()).isEqualTo(IssueCategory.TERMINOLOGY);
        assertThat(issue.path()).isEqualTo("Patient.gender");
    }

    @Test
    void shouldPassValidGenderCode() {
        Patient patient = new Patient();
        patient.setId("pat-valid-gender");
        patient.setGender(Enumerations.AdministrativeGender.MALE);

        RuleContext context = new RuleContext(patient, List.of(patient), null, terminologyService);
        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    void shouldFlagInvalidConditionClinicalStatus() {
        Condition condition = new Condition();
        condition.setId("cond-1");
        condition.getClinicalStatus().addCoding().setCode("bogus-status");

        RuleContext context = new RuleContext(condition, List.of(condition), null, terminologyService);
        List<QualityIssue> issues = rule.evaluate(context);

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).path()).isEqualTo("Condition.clinicalStatus");
    }
}
