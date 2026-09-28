package org.fhirlint.core.rules.terminology;

import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;
import org.fhirlint.core.rules.RuleContext;
import org.hl7.fhir.r4.model.Observation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VitalSignsUcumUnitRuleTest {

    private VitalSignsUcumUnitRule rule;
    private TerminologyService terminologyService;

    @BeforeEach
    void setUp() {
        rule = new VitalSignsUcumUnitRule();
        terminologyService = new DefaultTerminologyService();
    }

    @Test
    void shouldFlagVitalSignWithMissingOrInvalidUcumUnit() {
        Observation obs = new Observation();
        obs.setId("obs-bp");
        obs.addCategory().addCoding().setSystem("http://terminology.hl7.org/CodeSystem/observation-category").setCode("vital-signs");
        obs.setValue(new org.hl7.fhir.r4.model.Quantity()
                .setValue(new BigDecimal("120"))
                .setUnit("mmHg") // missing system and non-UCUM format
        );

        RuleContext context = new RuleContext(obs, List.of(obs), null, terminologyService);
        List<QualityIssue> issues = rule.evaluate(context);

        assertThat(issues).hasSize(1);
        QualityIssue issue = issues.get(0);
        assertThat(issue.ruleId()).isEqualTo("TERM-002");
        assertThat(issue.severity()).isEqualTo(Severity.ERROR);
        assertThat(issue.category()).isEqualTo(IssueCategory.TERMINOLOGY);
        assertThat(issue.path()).isEqualTo("Observation.valueQuantity");
    }

    @Test
    void shouldPassVitalSignWithValidUcumUnitAndSystem() {
        Observation obs = new Observation();
        obs.setId("obs-bp-valid");
        obs.addCategory().addCoding().setSystem("http://terminology.hl7.org/CodeSystem/observation-category").setCode("vital-signs");
        obs.setValue(new org.hl7.fhir.r4.model.Quantity()
                .setValue(new BigDecimal("120"))
                .setSystem("http://unitsofmeasure.org")
                .setCode("mm[Hg]")
                .setUnit("mm[Hg]")
        );

        RuleContext context = new RuleContext(obs, List.of(obs), null, terminologyService);
        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    void shouldIgnoreNonVitalSignsObservations() {
        Observation obs = new Observation();
        obs.setId("obs-lab");
        obs.addCategory().addCoding().setSystem("http://terminology.hl7.org/CodeSystem/observation-category").setCode("laboratory");
        obs.setValue(new org.hl7.fhir.r4.model.Quantity()
                .setValue(new BigDecimal("5.4"))
                .setUnit("custom-units")
        );

        RuleContext context = new RuleContext(obs, List.of(obs), null, terminologyService);
        assertThat(rule.evaluate(context)).isEmpty();
    }
}
