package org.fhirlint.core.rules.consistency;

import org.fhirlint.core.graph.DefaultReferentialIntegrityEngine;
import org.fhirlint.core.graph.ResourceGraphIndex;
import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;
import org.fhirlint.core.rules.RuleContext;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeceasedStatusRuleTest {

    private DeceasedStatusRule rule;
    private SimpleDateFormat dtFormat;

    @BeforeEach
    void setUp() {
        rule = new DeceasedStatusRule();
        dtFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    }

    @Test
    void shouldFlagEventOccurringAfterDeceasedDate() throws Exception {
        Patient patient = new Patient();
        patient.setId("pat-deceased");
        patient.setDeceased(new DateTimeType("2020-03-01T12:00:00Z"));

        Procedure proc = new Procedure();
        proc.setId("proc-1");
        proc.setSubject(new Reference("Patient/pat-deceased"));
        proc.setPerformed(new DateTimeType("2020-03-02T10:00:00Z"));

        DefaultReferentialIntegrityEngine engine = new DefaultReferentialIntegrityEngine();
        ResourceGraphIndex graphIndex = engine.buildIndex(List.of(patient, proc));

        RuleContext context = new RuleContext(proc, List.of(patient, proc), graphIndex, null);
        List<QualityIssue> issues = rule.evaluate(context);

        assertThat(issues).hasSize(1);
        QualityIssue issue = issues.get(0);
        assertThat(issue.ruleId()).isEqualTo("CONS-003");
        assertThat(issue.severity()).isEqualTo(Severity.WARNING);
        assertThat(issue.category()).isEqualTo(IssueCategory.CONSISTENCY);
        assertThat(issue.path()).isEqualTo("Procedure.performed");
    }

    @Test
    void shouldPassEventOccurringBeforeDeceasedDate() throws Exception {
        Patient patient = new Patient();
        patient.setId("pat-deceased");
        patient.setDeceased(new DateTimeType("2020-03-01T12:00:00Z"));

        Observation obs = new Observation();
        obs.setId("obs-1");
        obs.setSubject(new Reference("Patient/pat-deceased"));
        obs.setEffective(new DateTimeType("2020-03-01T10:00:00Z"));

        DefaultReferentialIntegrityEngine engine = new DefaultReferentialIntegrityEngine();
        ResourceGraphIndex graphIndex = engine.buildIndex(List.of(patient, obs));

        RuleContext context = new RuleContext(obs, List.of(patient, obs), graphIndex, null);
        assertThat(rule.evaluate(context)).isEmpty();
    }
}
