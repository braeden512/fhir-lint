package org.fhirlint.core.rules.consistency;

import org.fhirlint.core.graph.DefaultReferentialIntegrityEngine;
import org.fhirlint.core.graph.ResourceGraphIndex;
import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;
import org.fhirlint.core.rules.RuleContext;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticReportObservationStateRuleTest {

    private DiagnosticReportObservationStateRule rule;

    @BeforeEach
    void setUp() {
        rule = new DiagnosticReportObservationStateRule();
    }

    @Test
    void shouldFlagFinalReportReferencingEnteredInErrorObservation() {
        Observation obs = new Observation();
        obs.setId("obs-bad");
        obs.setStatus(Observation.ObservationStatus.ENTEREDINERROR);

        DiagnosticReport report = new DiagnosticReport();
        report.setId("rep-1");
        report.setStatus(DiagnosticReport.DiagnosticReportStatus.FINAL);
        report.addResult(new Reference("Observation/obs-bad"));

        DefaultReferentialIntegrityEngine engine = new DefaultReferentialIntegrityEngine();
        ResourceGraphIndex graphIndex = engine.buildIndex(List.of(obs, report));

        RuleContext context = new RuleContext(report, List.of(obs, report), graphIndex, null);
        List<QualityIssue> issues = rule.evaluate(context);

        assertThat(issues).hasSize(1);
        QualityIssue issue = issues.get(0);
        assertThat(issue.ruleId()).isEqualTo("CONS-004");
        assertThat(issue.severity()).isEqualTo(Severity.ERROR);
        assertThat(issue.category()).isEqualTo(IssueCategory.CONSISTENCY);
        assertThat(issue.path()).isEqualTo("DiagnosticReport.result[0]");
        assertThat(issue.message()).contains("entered-in-error");
    }

    @Test
    void shouldPassFinalReportReferencingFinalObservation() {
        Observation obs = new Observation();
        obs.setId("obs-good");
        obs.setStatus(Observation.ObservationStatus.FINAL);

        DiagnosticReport report = new DiagnosticReport();
        report.setId("rep-2");
        report.setStatus(DiagnosticReport.DiagnosticReportStatus.FINAL);
        report.addResult(new Reference("Observation/obs-good"));

        DefaultReferentialIntegrityEngine engine = new DefaultReferentialIntegrityEngine();
        ResourceGraphIndex graphIndex = engine.buildIndex(List.of(obs, report));

        RuleContext context = new RuleContext(report, List.of(obs, report), graphIndex, null);
        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    void shouldIgnoreNonFinalReport() {
        Observation obs = new Observation();
        obs.setId("obs-bad");
        obs.setStatus(Observation.ObservationStatus.ENTEREDINERROR);

        DiagnosticReport report = new DiagnosticReport();
        report.setId("rep-prelim");
        report.setStatus(DiagnosticReport.DiagnosticReportStatus.PRELIMINARY);
        report.addResult(new Reference("Observation/obs-bad"));

        DefaultReferentialIntegrityEngine engine = new DefaultReferentialIntegrityEngine();
        ResourceGraphIndex graphIndex = engine.buildIndex(List.of(obs, report));

        RuleContext context = new RuleContext(report, List.of(obs, report), graphIndex, null);
        assertThat(rule.evaluate(context)).isEmpty();
    }
}
