package org.fhirlint.core.rules;

import org.fhirlint.core.graph.ReferentialIntegrityEngine;
import org.fhirlint.core.graph.ResourceGraphIndex;
import org.fhirlint.core.model.IssueCategory;
import org.fhirlint.core.model.QualityIssue;
import org.fhirlint.core.model.Severity;
import org.fhirlint.core.parser.FhirBundleParser;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QualityRulePerformanceBenchmarkTest {

    @Test
    @DisplayName("Quality rule evaluation of 5,000 resources completes in < 1.5 seconds (SC-012)")
    void testPerformanceOn5000Resources() {
        QualityRuleEngine engine = new DefaultQualityRuleEngine();
        ReferentialIntegrityEngine graphEngine = ReferentialIntegrityEngine.create();

        // 500 Patients, 500 Encounters, 4,000 Observations = 5,000 resources
        int patientCount = 500;
        int encounterCount = 500;
        int observationCount = 4000;
        List<IBaseResource> resources = new ArrayList<>(5000);

        Date birthDate = new Date(100, 0, 1); // 2000-01-01
        Date eventStart = new Date(123, 0, 1); // 2023-01-01
        Date eventEnd = new Date(123, 0, 2);   // 2023-01-02

        for (int i = 0; i < patientCount; i++) {
            Patient p = new Patient();
            p.setId("pat-" + i);
            p.setBirthDate(birthDate);
            p.addIdentifier().setSystem("http://hospital.example.org/patients").setValue("PAT-" + i);
            resources.add(p);
        }

        for (int i = 0; i < encounterCount; i++) {
            Encounter enc = new Encounter();
            enc.setId("enc-" + i);
            enc.setStatus(Encounter.EncounterStatus.FINISHED);
            enc.setSubject(new Reference("Patient/pat-" + (i % patientCount)));
            enc.getPeriod().setStart(eventStart).setEnd(eventEnd);
            resources.add(enc);
        }

        for (int i = 0; i < observationCount; i++) {
            Observation obs = new Observation();
            obs.setId("obs-" + i);
            obs.setStatus(Observation.ObservationStatus.FINAL);
            obs.setSubject(new Reference("Patient/pat-" + (i % patientCount)));
            obs.setEffective(new DateTimeType(eventStart));
            obs.getCode().addCoding()
                    .setSystem("http://loinc.org")
                    .setCode("8867-4")
                    .setDisplay("Heart rate");
            obs.setValue(new Quantity()
                    .setValue(72)
                    .setUnit("beats/minute")
                    .setSystem("http://unitsofmeasure.org")
                    .setCode("/min"));
            resources.add(obs);
        }

        ResourceGraphIndex graphIndex = graphEngine.buildIndex(resources);

        // Warm up JIT slightly
        engine.evaluate(resources.subList(0, 100), graphIndex);

        // Timed execution of full 5,000 resources
        long startTime = System.nanoTime();
        List<QualityIssue> issues = engine.evaluate(resources, graphIndex);
        long durationMs = (System.nanoTime() - startTime) / 1_000_000;

        System.out.println("Evaluated 5,000 resources against all 11 quality rules in: " + durationMs + "ms");

        assertThat(issues).isNotNull();
        assertThat(durationMs)
                .withFailMessage("SC-012: Evaluation of 5,000 resources must complete in under 1,500ms, but took " + durationMs + "ms")
                .isLessThan(1500);
    }

    @Test
    @DisplayName("Synthea clean bundle has 0% false-positive consistency or completeness errors (SC-011)")
    void testZeroFalsePositivesOnSyntheaBundle() {
        File file = new File("sample-data/Alexander630_Kovacek682_b8c195d4-0396-fb84-3aa2-57dd23ff5a23.json");
        assertThat(file).exists();

        FhirBundleParser parser = new FhirBundleParser();
        List<IBaseResource> resources = parser.parse(file).resources();

        QualityRuleEngine engine = new DefaultQualityRuleEngine();
        ReferentialIntegrityEngine graphEngine = ReferentialIntegrityEngine.create();
        ResourceGraphIndex graphIndex = graphEngine.buildIndex(resources);

        List<QualityIssue> issues = engine.evaluate(resources, graphIndex);

        // Filter for any CONSISTENCY or COMPLETENESS errors
        List<QualityIssue> consistencyOrCompletenessErrors = issues.stream()
                .filter(i -> i.severity() == Severity.ERROR)
                .filter(i -> i.category() == IssueCategory.CONSISTENCY || i.category() == IssueCategory.COMPLETENESS)
                .toList();

        if (!consistencyOrCompletenessErrors.isEmpty()) {
            System.err.println("Unexpected consistency/completeness errors on Synthea bundle:");
            consistencyOrCompletenessErrors.forEach(System.err::println);
        }

        assertThat(consistencyOrCompletenessErrors)
                .withFailMessage("SC-011: Expected 0 consistency or completeness errors on clean Synthea bundle, but found " + consistencyOrCompletenessErrors.size())
                .isEmpty();
    }
}
