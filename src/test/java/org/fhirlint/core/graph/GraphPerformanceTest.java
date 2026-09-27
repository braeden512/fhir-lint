package org.fhirlint.core.graph;

import org.fhirlint.core.model.QualityIssue;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GraphPerformanceTest {

    @Test
    @DisplayName("Referential integrity analysis of 5,000 resources completes in < 1.0s and within 64 MB memory")
    void testPerformanceOnLargeDataset() {
        ReferentialIntegrityEngine engine = ReferentialIntegrityEngine.create();

        // Generate synthetic dataset with 500 patients, 500 encounters, and 4,000 observations (total: 5,000 resources)
        List<IBaseResource> resources = new ArrayList<>(5000);
        int patientCount = 500;
        int encounterCount = 500;
        int observationCount = 4000;

        for (int i = 0; i < patientCount; i++) {
            Patient patient = new Patient();
            patient.setId("pat-" + i);
            resources.add(patient);
        }

        for (int i = 0; i < encounterCount; i++) {
            Encounter encounter = new Encounter();
            encounter.setId("enc-" + i);
            encounter.setSubject(new Reference("Patient/pat-" + (i % patientCount)));
            resources.add(encounter);
        }

        for (int i = 0; i < observationCount; i++) {
            Observation obs = new Observation();
            obs.setId("obs-" + i);
            obs.setSubject(new Reference("Patient/pat-" + (i % patientCount)));
            obs.setEncounter(new Reference("Encounter/enc-" + (i % encounterCount)));
            // Add member references
            obs.addHasMember(new Reference("Observation/obs-" + ((i + 1) % observationCount)));
            resources.add(obs);
        }

        // Measure memory before
        System.gc();
        Runtime runtime = Runtime.getRuntime();
        long memBefore = runtime.totalMemory() - runtime.freeMemory();

        // Warm up JIT slightly
        engine.analyze(resources.subList(0, 50));

        // Benchmark
        long startTime = System.nanoTime();
        List<QualityIssue> issues = engine.analyze(resources);
        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

        long memAfter = runtime.totalMemory() - runtime.freeMemory();
        long memDeltaMb = Math.max(0, (memAfter - memBefore) / (1024 * 1024));

        System.out.printf("Graph benchmark: 5,000 resources analyzed in %d ms (Memory delta: %d MB)%n", elapsedMs, memDeltaMb);

        // Verification
        assertThat(elapsedMs).isLessThanOrEqualTo(1000);
        assertThat(memDeltaMb).isLessThanOrEqualTo(64);
        assertThat(issues).isEmpty(); // All references resolve correctly to valid types and reach patients
    }
}
