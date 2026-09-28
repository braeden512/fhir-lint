package org.fhirlint.core.rules.completeness;

import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;
import org.fhirlint.core.rules.RuleContext;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MissingSubjectContextRuleTest {

    private MissingSubjectContextRule rule;

    @BeforeEach
    void setUp() {
        rule = new MissingSubjectContextRule();
    }

    @Test
    void shouldFlagObservationMissingSubject() {
        Observation obs = new Observation();
        obs.setId("obs-no-subj");

        RuleContext context = new RuleContext(obs, List.of(obs), null, null);
        List<QualityIssue> issues = rule.evaluate(context);

        assertThat(issues).hasSize(1);
        QualityIssue issue = issues.get(0);
        assertThat(issue.ruleId()).isEqualTo("COMP-001");
        assertThat(issue.severity()).isEqualTo(Severity.ERROR);
        assertThat(issue.category()).isEqualTo(IssueCategory.COMPLETENESS);
        assertThat(issue.path()).isEqualTo("Observation.subject");
    }

    @Test
    void shouldFlagConditionWithEmptySubjectReference() {
        Condition cond = new Condition();
        cond.setId("cond-empty-subj");
        cond.setSubject(new Reference(""));

        RuleContext context = new RuleContext(cond, List.of(cond), null, null);
        List<QualityIssue> issues = rule.evaluate(context);

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).ruleId()).isEqualTo("COMP-001");
        assertThat(issues.get(0).path()).isEqualTo("Condition.subject");
    }

    @Test
    void shouldPassClinicalResourceWithValidSubject() {
        Observation obs = new Observation();
        obs.setId("obs-with-subj");
        obs.setSubject(new Reference("Patient/pat-1"));

        RuleContext context = new RuleContext(obs, List.of(obs), null, null);
        assertThat(rule.evaluate(context)).isEmpty();
    }
}
