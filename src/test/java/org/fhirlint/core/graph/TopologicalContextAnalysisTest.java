package org.fhirlint.core.graph;

import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;
import org.fhirlint.core.parser.FhirBundleParser;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TopologicalContextAnalysisTest {

    private ReferentialIntegrityEngine engine;
    private FhirBundleParser parser;

    @BeforeEach
    void setUp() {
        engine = ReferentialIntegrityEngine.create();
        parser = new FhirBundleParser();
    }

    @Test
    @DisplayName("Should detect orphaned Observation and Condition in orphaned-observation fixture")
    void shouldDetectOrphanedClinicalResources() {
        File file = new File("sample-data/referential/orphaned-observation.json");
        List<IBaseResource> resources = parser.parse(file).resources();

        List<QualityIssue> issues = engine.analyze(resources);

        List<QualityIssue> ref003Issues = issues.stream()
                .filter(i -> "REF-003".equals(i.ruleId()))
                .toList();

        assertThat(ref003Issues).hasSize(1);
        assertThat(ref003Issues).allMatch(i -> i.severity() == Severity.WARNING);
        assertThat(ref003Issues).allMatch(i -> i.category() == IssueCategory.REFERENTIAL_INTEGRITY);
        assertThat(ref003Issues).anyMatch(i -> "Observation".equals(i.resourceType()));
    }

    @Test
    @DisplayName("Should pass when Observation is transitively linked to Patient via Encounter")
    void shouldPassTransitivePatientReachability() {
        Patient pat = new Patient();
        pat.setId("pat-1");

        Encounter enc = new Encounter();
        enc.setId("enc-1");
        enc.setSubject(new Reference("Patient/pat-1"));

        Observation obs = new Observation();
        obs.setId("obs-1");
        obs.setEncounter(new Reference("Encounter/enc-1"));

        List<QualityIssue> issues = engine.analyze(List.of(pat, enc, obs));

        List<QualityIssue> ref003Issues = issues.stream()
                .filter(i -> "REF-003".equals(i.ruleId()))
                .toList();

        assertThat(ref003Issues).isEmpty();
    }

    @Test
    @DisplayName("Should pass when Observation is referenced by a DiagnosticReport that reaches Patient")
    void shouldPassParentReportReachability() {
        Patient pat = new Patient();
        pat.setId("pat-2");

        Observation obs = new Observation();
        obs.setId("obs-2");

        DiagnosticReport report = new DiagnosticReport();
        report.setId("rep-1");
        report.setSubject(new Reference("Patient/pat-2"));
        report.addResult(new Reference("Observation/obs-2"));

        List<QualityIssue> issues = engine.analyze(List.of(pat, obs, report));

        List<QualityIssue> ref003Issues = issues.stream()
                .filter(i -> "REF-003".equals(i.ruleId()))
                .toList();

        assertThat(ref003Issues).isEmpty();
    }

    @Test
    @DisplayName("Should handle cyclic references safely without infinite recursion")
    void shouldHandleCyclicReferencesSafely() {
        Encounter enc1 = new Encounter();
        enc1.setId("enc-cycle-1");
        enc1.setPartOf(new Reference("Encounter/enc-cycle-2"));

        Encounter enc2 = new Encounter();
        enc2.setId("enc-cycle-2");
        enc2.setPartOf(new Reference("Encounter/enc-cycle-1"));

        Observation obs = new Observation();
        obs.setId("obs-cycle");
        obs.setEncounter(new Reference("Encounter/enc-cycle-1"));

        List<QualityIssue> issues = engine.analyze(List.of(enc1, enc2, obs));

        List<QualityIssue> ref003Issues = issues.stream()
                .filter(i -> "REF-003".equals(i.ruleId()))
                .toList();

        // Observation does not reach Patient, so REF-003 should be emitted
        assertThat(ref003Issues).hasSize(1);
        assertThat(ref003Issues.get(0).resourceType()).isEqualTo("Observation");
    }
}
