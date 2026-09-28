package org.fhirlint.core.rules.terminology;

import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;
import org.fhirlint.core.rules.RuleContext;
import org.hl7.fhir.r4.model.Observation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalSystemUriRuleTest {

    private CanonicalSystemUriRule rule;
    private TerminologyService terminologyService;

    @BeforeEach
    void setUp() {
        rule = new CanonicalSystemUriRule();
        terminologyService = new DefaultTerminologyService();
    }

    @Test
    void shouldFlagNonCanonicalLoincWithTrailingSlash() {
        Observation obs = new Observation();
        obs.setId("obs-1");
        obs.getCode().addCoding().setSystem("http://loinc.org/").setCode("883-9");

        RuleContext context = new RuleContext(obs, List.of(obs), null, terminologyService);
        List<QualityIssue> issues = rule.evaluate(context);

        assertThat(issues).hasSize(1);
        QualityIssue issue = issues.get(0);
        assertThat(issue.ruleId()).isEqualTo("TERM-001");
        assertThat(issue.severity()).isEqualTo(Severity.WARNING);
        assertThat(issue.category()).isEqualTo(IssueCategory.TERMINOLOGY);
        assertThat(issue.suggestion()).contains("http://loinc.org");
    }

    @Test
    void shouldPassCanonicalLoincWithoutTrailingSlash() {
        Observation obs = new Observation();
        obs.setId("obs-2");
        obs.getCode().addCoding().setSystem("http://loinc.org").setCode("883-9");

        RuleContext context = new RuleContext(obs, List.of(obs), null, terminologyService);
        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    void shouldFlagNonCanonicalSnomed() {
        Observation obs = new Observation();
        obs.setId("obs-3");
        obs.getCode().addCoding().setSystem("http://snomed.info").setCode("12345");

        RuleContext context = new RuleContext(obs, List.of(obs), null, terminologyService);
        List<QualityIssue> issues = rule.evaluate(context);

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).suggestion()).contains("http://snomed.info/sct");
    }
}
