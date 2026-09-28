package org.fhirlint.core.rules.duplicate;

import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;
import org.fhirlint.core.rules.RuleContext;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PatientIdentifierDuplicateRuleTest {

    private PatientIdentifierDuplicateRule rule;

    @BeforeEach
    void setUp() {
        rule = new PatientIdentifierDuplicateRule();
    }

    @Test
    void shouldDetectDuplicatePatientsWithSameIdentifier() {
        Patient pat1 = new Patient();
        pat1.setId("pat-1");
        pat1.addIdentifier().setSystem("http://hl7.org/fhir/sid/us-ssn").setValue("000-12-3456");

        Patient pat2 = new Patient();
        pat2.setId("pat-2");
        pat2.addIdentifier().setSystem("http://hl7.org/fhir/sid/us-ssn").setValue("000-12-3456");

        RuleContext context = new RuleContext(null, List.of(pat1, pat2), null, null);
        List<QualityIssue> issues = rule.evaluate(context);

        assertThat(issues).hasSize(2);
        assertThat(issues).allMatch(i -> i.ruleId().equals("DUP-001"));
        assertThat(issues).allMatch(i -> i.severity().equals(Severity.ERROR));
        assertThat(issues).allMatch(i -> i.category().equals(IssueCategory.DUPLICATE));
        assertThat(issues).anyMatch(i -> "pat-1".equals(i.resourceId()));
        assertThat(issues).anyMatch(i -> "pat-2".equals(i.resourceId()));
    }

    @Test
    void shouldPassDistinctPatientsWithDifferentIdentifiers() {
        Patient pat1 = new Patient();
        pat1.setId("pat-1");
        pat1.addIdentifier().setSystem("http://hl7.org/fhir/sid/us-ssn").setValue("000-12-3456");

        Patient pat2 = new Patient();
        pat2.setId("pat-2");
        pat2.addIdentifier().setSystem("http://hl7.org/fhir/sid/us-ssn").setValue("999-88-7777");

        RuleContext context = new RuleContext(null, List.of(pat1, pat2), null, null);
        assertThat(rule.evaluate(context)).isEmpty();
    }
}
