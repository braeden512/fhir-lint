package org.fhirlint.core.rules.consistency;

import org.fhirlint.core.graph.DefaultReferentialIntegrityEngine;
import org.fhirlint.core.graph.ResourceGraphIndex;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;
import org.fhirlint.core.rules.RuleContext;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BirthToEventChronologyRuleTest {

    private BirthToEventChronologyRule rule;
    private SimpleDateFormat dateFormat;

    @BeforeEach
    void setUp() {
        rule = new BirthToEventChronologyRule();
        dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    }

    @Test
    void shouldFlagMedicationRequestBeforeBirth() throws Exception {
        Patient patient = new Patient();
        patient.setId("pat-1");
        patient.setBirthDate(dateFormat.parse("1980-05-15"));

        MedicationRequest medReq = new MedicationRequest();
        medReq.setId("med-1");
        medReq.setSubject(new Reference("Patient/pat-1"));
        medReq.setAuthoredOn(dateFormat.parse("1975-04-12"));

        DefaultReferentialIntegrityEngine engine = new DefaultReferentialIntegrityEngine();
        ResourceGraphIndex graphIndex = engine.buildIndex(List.of(patient, medReq));

        RuleContext context = new RuleContext(medReq, List.of(patient, medReq), graphIndex, null);
        List<QualityIssue> issues = rule.evaluate(context);

        assertThat(issues).hasSize(1);
        QualityIssue issue = issues.get(0);
        assertThat(issue.ruleId()).isEqualTo("CONS-002");
        assertThat(issue.severity()).isEqualTo(Severity.ERROR);
        assertThat(issue.path()).isEqualTo("MedicationRequest.authoredOn");
        assertThat(issue.message()).contains("1975-04-12").contains("1980-05-15");
    }

    @Test
    void shouldPassObservationAfterBirth() throws Exception {
        Patient patient = new Patient();
        patient.setId("pat-1");
        patient.setBirthDate(dateFormat.parse("1980-05-15"));

        Observation obs = new Observation();
        obs.setId("obs-1");
        obs.setSubject(new Reference("Patient/pat-1"));
        obs.setEffective(new DateTimeType("1995-10-01T12:00:00Z"));

        DefaultReferentialIntegrityEngine engine = new DefaultReferentialIntegrityEngine();
        ResourceGraphIndex graphIndex = engine.buildIndex(List.of(patient, obs));

        RuleContext context = new RuleContext(obs, List.of(patient, obs), graphIndex, null);
        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    void shouldSkipGracefullyWhenPatientNotFoundOrNoBirthDate() throws Exception {
        MedicationRequest medReq = new MedicationRequest();
        medReq.setId("med-2");
        medReq.setSubject(new Reference("Patient/missing"));
        medReq.setAuthoredOn(dateFormat.parse("1975-04-12"));

        DefaultReferentialIntegrityEngine engine = new DefaultReferentialIntegrityEngine();
        ResourceGraphIndex graphIndex = engine.buildIndex(List.of(medReq));

        RuleContext context = new RuleContext(medReq, List.of(medReq), graphIndex, null);
        assertThat(rule.evaluate(context)).isEmpty();
    }
}
