package org.fhirlint.core.rules.completeness;

import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;
import org.fhirlint.core.rules.RuleContext;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MissingObservationValueRuleTest {

    private MissingObservationValueRule rule;

    @BeforeEach
    void setUp() {
        rule = new MissingObservationValueRule();
    }

    @Test
    void shouldFlagActiveObservationWithNoValueAndNoDataAbsentReason() {
        Observation obs = new Observation();
        obs.setId("obs-empty");
        obs.setStatus(Observation.ObservationStatus.FINAL);

        RuleContext context = new RuleContext(obs, List.of(obs), null, null);
        List<QualityIssue> issues = rule.evaluate(context);

        assertThat(issues).hasSize(1);
        QualityIssue issue = issues.get(0);
        assertThat(issue.ruleId()).isEqualTo("COMP-002");
        assertThat(issue.severity()).isEqualTo(Severity.WARNING);
        assertThat(issue.category()).isEqualTo(IssueCategory.COMPLETENESS);
        assertThat(issue.path()).isEqualTo("Observation");
    }

    @Test
    void shouldPassWhenValuePresent() {
        Observation obs = new Observation();
        obs.setId("obs-val");
        obs.setValue(new StringType("Negative"));

        RuleContext context = new RuleContext(obs, List.of(obs), null, null);
        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    void shouldPassWhenDataAbsentReasonPresent() {
        Observation obs = new Observation();
        obs.setId("obs-absent");
        obs.setDataAbsentReason(new CodeableConcept().setText("Unknown"));

        RuleContext context = new RuleContext(obs, List.of(obs), null, null);
        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    void shouldPassWhenComponentHasValue() {
        Observation obs = new Observation();
        obs.setId("obs-panel");
        obs.addComponent().setValue(new Quantity().setValue(new BigDecimal("120")));

        RuleContext context = new RuleContext(obs, List.of(obs), null, null);
        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    void shouldExemptEnteredInErrorObservations() {
        Observation obs = new Observation();
        obs.setId("obs-err");
        obs.setStatus(Observation.ObservationStatus.ENTEREDINERROR);

        RuleContext context = new RuleContext(obs, List.of(obs), null, null);
        assertThat(rule.evaluate(context)).isEmpty();
    }
}
