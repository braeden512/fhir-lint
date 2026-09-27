package org.fhirlint.core.graph;

import org.fhirlint.core.FhirLinter;
import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.LintReport;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;
import org.fhirlint.core.parser.FhirBundleParser;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BrokenReferenceDetectionTest {

    private ReferentialIntegrityEngine engine;
    private FhirBundleParser parser;

    @BeforeEach
    void setUp() {
        engine = ReferentialIntegrityEngine.create();
        parser = new FhirBundleParser();
    }

    @Test
    @DisplayName("Should detect broken relative and UUID references in synthetic fixture")
    void shouldDetectBrokenReferencesInFixture() {
        File file = new File("sample-data/referential/broken-reference.json");
        List<IBaseResource> resources = parser.parse(file).resources();

        List<QualityIssue> issues = engine.analyze(resources);

        List<QualityIssue> ref001Issues = issues.stream()
                .filter(i -> "REF-001".equals(i.ruleId()))
                .toList();

        assertThat(ref001Issues).hasSize(2);
        assertThat(ref001Issues).allMatch(i -> i.severity() == Severity.ERROR);
        assertThat(ref001Issues).allMatch(i -> i.category() == IssueCategory.REFERENTIAL_INTEGRITY);

        QualityIssue obsIssue = ref001Issues.stream()
                .filter(i -> "Observation".equals(i.resourceType()))
                .findFirst()
                .orElseThrow();
        assertThat(obsIssue.path()).contains("Observation.subject.reference");
        assertThat(obsIssue.message()).contains("Patient/pat-missing");

        QualityIssue encIssue = ref001Issues.stream()
                .filter(i -> "Encounter".equals(i.resourceType()))
                .findFirst()
                .orElseThrow();
        assertThat(encIssue.path()).contains("Encounter.subject.reference");
        assertThat(encIssue.message()).contains("urn:uuid:99999999-9999-9999-9999-999999999999");
    }

    @Test
    @DisplayName("Should report broken contained fragment reference")
    void shouldDetectBrokenContainedReference() {
        File file = new File("sample-data/referential/contained-reference.json");
        List<IBaseResource> resources = parser.parse(file).resources();

        List<QualityIssue> issues = engine.analyze(resources);

        List<QualityIssue> ref001Issues = issues.stream()
                .filter(i -> "REF-001".equals(i.ruleId()))
                .toList();

        assertThat(ref001Issues).hasSize(1);
        QualityIssue brokenContained = ref001Issues.get(0);
        assertThat(brokenContained.path()).contains("evidence[0].detail[0].reference");
        assertThat(brokenContained.message()).contains("#missing-contained-sub");
    }

    @Test
    @DisplayName("Should report empty or blank reference as REF-001 ERROR")
    void shouldReportEmptyReferenceAsRef001() {
        Observation obs = new Observation();
        obs.setId("obs-blank");
        obs.setSubject(new Reference("   "));

        List<QualityIssue> issues = engine.analyze(List.of(obs));

        assertThat(issues).anyMatch(i -> 
                "REF-001".equals(i.ruleId()) &&
                i.severity() == Severity.ERROR &&
                i.message().contains("empty, whitespace, or malformed"));
    }

    @Test
    @DisplayName("Should pass cleanly when all relative and contained references resolve")
    void shouldPassCleanlyOnResolvedReferences() {
        Patient pat = new Patient();
        pat.setId("pat-1");

        Observation obs = new Observation();
        obs.setId("obs-1");
        obs.setSubject(new Reference("Patient/pat-1"));

        List<QualityIssue> issues = engine.analyze(List.of(pat, obs));

        List<QualityIssue> ref001Issues = issues.stream()
                .filter(i -> "REF-001".equals(i.ruleId()))
                .toList();

        assertThat(ref001Issues).isEmpty();
    }
}
