package org.fhirlint.core.rules.duplicate;

import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;
import org.fhirlint.core.rules.RuleContext;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PatientDemographicDuplicateRuleTest {

    private PatientDemographicDuplicateRule rule;

    @BeforeEach
    void setUp() {
        rule = new PatientDemographicDuplicateRule();
    }

    @Test
    void shouldFlagPatientsWithMatchingDemographics() {
        Patient pat1 = new Patient();
        pat1.setId("pat-1");
        pat1.addName().setFamily("Smith").addGiven("John");
        pat1.setBirthDateElement(new DateType("1985-04-12"));
        pat1.addAddress().setPostalCode("90210");

        Patient pat2 = new Patient();
        pat2.setId("pat-2");
        pat2.addName().setFamily("SMITH").addGiven("John");
        pat2.setBirthDateElement(new DateType("1985-04-12"));
        pat2.addAddress().setPostalCode("90210-1234");

        RuleContext context = new RuleContext(null, List.of(pat1, pat2), null, null);
        List<QualityIssue> issues = rule.evaluate(context);

        assertThat(issues).hasSize(2);
        assertThat(issues).allMatch(i -> i.ruleId().equals("DUP-002"));
        assertThat(issues).allMatch(i -> i.severity().equals(Severity.WARNING));
        assertThat(issues).allMatch(i -> i.category().equals(IssueCategory.DUPLICATE));
    }

    @Test
    void shouldPassWhenDemographicsDiffer() {
        Patient pat1 = new Patient();
        pat1.setId("pat-1");
        pat1.addName().setFamily("Smith").addGiven("John");
        pat1.setBirthDateElement(new DateType("1985-04-12"));
        pat1.addAddress().setPostalCode("90210");

        Patient pat2 = new Patient();
        pat2.setId("pat-2");
        pat2.addName().setFamily("Smith").addGiven("Jane");
        pat2.setBirthDateElement(new DateType("1985-04-12"));
        pat2.addAddress().setPostalCode("90210");

        RuleContext context = new RuleContext(null, List.of(pat1, pat2), null, null);
        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    void shouldNotMatchWhenDemographicFieldsAreMissing() {
        Patient pat1 = new Patient();
        pat1.setId("pat-1");
        pat1.addName().setFamily("Smith").addGiven("John");
        pat1.setBirthDateElement(new DateType("1985-04-12"));
        // missing address

        Patient pat2 = new Patient();
        pat2.setId("pat-2");
        pat2.addName().setFamily("Smith").addGiven("John");
        pat2.setBirthDateElement(new DateType("1985-04-12"));
        // missing address

        RuleContext context = new RuleContext(null, List.of(pat1, pat2), null, null);
        assertThat(rule.evaluate(context)).isEmpty();
    }
}
