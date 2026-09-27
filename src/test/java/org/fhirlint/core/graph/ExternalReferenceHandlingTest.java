package org.fhirlint.core.graph;

import org.fhirlint.core.FhirLinter;
import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.LintReport;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;
import org.fhirlint.core.model.ValidationProfile;
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

class ExternalReferenceHandlingTest {

    private ReferentialIntegrityEngine engine;
    private FhirBundleParser parser;

    @BeforeEach
    void setUp() {
        engine = ReferentialIntegrityEngine.create();
        parser = new FhirBundleParser();
    }

    @Test
    @DisplayName("Should classify external HTTP/HTTPS reference as REF-004 INFO in external-reference fixture")
    void shouldReportExternalReferenceAsInfo() {
        File file = new File("sample-data/referential/external-reference.json");
        List<IBaseResource> resources = parser.parse(file).resources();

        List<QualityIssue> issues = engine.analyze(resources);

        // No REF-001 errors
        assertThat(issues.stream().filter(i -> "REF-001".equals(i.ruleId()))).isEmpty();

        // Must emit REF-004 with INFO severity
        List<QualityIssue> ref004Issues = issues.stream()
                .filter(i -> "REF-004".equals(i.ruleId()))
                .toList();

        assertThat(ref004Issues).hasSize(1);
        QualityIssue externalIssue = ref004Issues.get(0);
        assertThat(externalIssue.severity()).isEqualTo(Severity.INFO);
        assertThat(externalIssue.category()).isEqualTo(IssueCategory.REFERENTIAL_INTEGRITY);
        assertThat(externalIssue.path()).contains("Observation.subject.reference");
        assertThat(externalIssue.message()).contains("https://external-hospital.org/fhir/Patient/ext-999");
    }

    @Test
    @DisplayName("Should resolve absolute URL internally if matching fullUrl exists in bundle")
    void shouldResolveAbsoluteUrlMatchingFullUrl() {
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.COLLECTION);

        Patient patient = new Patient();
        patient.setId("pat-abs-1");
        Bundle.BundleEntryComponent patEntry = bundle.addEntry();
        patEntry.setFullUrl("http://myhospital.org/fhir/Patient/pat-abs-1");
        patEntry.setResource(patient);

        Observation observation = new Observation();
        observation.setId("obs-abs-1");
        observation.setSubject(new Reference("http://myhospital.org/fhir/Patient/pat-abs-1"));
        Bundle.BundleEntryComponent obsEntry = bundle.addEntry();
        obsEntry.setFullUrl("http://myhospital.org/fhir/Observation/obs-abs-1");
        obsEntry.setResource(observation);

        List<QualityIssue> issues = engine.analyze(bundle);

        // Should resolve cleanly with zero errors and zero REF-004
        assertThat(issues.stream().filter(i -> "REF-001".equals(i.ruleId()))).isEmpty();
        assertThat(issues.stream().filter(i -> "REF-004".equals(i.ruleId()))).isEmpty();
    }

    @Test
    @DisplayName("Should not block quality gate when only REF-004 INFO is present")
    void shouldNotBlockQualityGateOnExternalReference() {
        FhirLinter linter = FhirLinter.create().withProfile(ValidationProfile.BASE_R4);
        File file = new File("sample-data/referential/external-reference.json");
        LintReport report = linter.lint(file);

        assertThat(report.hasErrors()).isFalse();
        assertThat(report.passes(80, Severity.ERROR)).isTrue();
    }
}
