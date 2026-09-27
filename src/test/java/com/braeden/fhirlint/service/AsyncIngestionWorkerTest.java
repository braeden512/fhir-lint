package com.braeden.fhirlint.service;

import ca.uhn.fhir.context.FhirContext;
import com.braeden.fhirlint.model.JobStatus;
import com.braeden.fhirlint.model.QualityCheckJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsyncIngestionWorkerTest {

    @Mock
    private JobLifecycleManager jobLifecycleManager;

    @Captor
    private ArgumentCaptor<Map<String, Integer>> countsCaptor;

    private FhirContext fhirContext;
    private AsyncIngestionWorker worker;

    @BeforeEach
    void setUp() {
        fhirContext = FhirContext.forR4();
        worker = new AsyncIngestionWorker(jobLifecycleManager, fhirContext);
    }

    @Test
    void shouldParseSingleResourceAndCompleteJob() {
        UUID jobId = UUID.randomUUID();
        QualityCheckJob job = new QualityCheckJob(jobId, JobStatus.PROCESSING, "BASE_R4", Instant.now());
        when(jobLifecycleManager.startJob(jobId)).thenReturn(job);

        String patientJson = """
                {
                  "resourceType": "Patient",
                  "id": "pat-1",
                  "name": [{"family": "Smith", "given": ["Alice"]}]
                }
                """;

        worker.processJobAsync(jobId, patientJson, "BASE_R4");

        verify(jobLifecycleManager).startJob(jobId);
        verify(jobLifecycleManager).completeJob(eq(jobId), eq(1), countsCaptor.capture());

        Map<String, Integer> counts = countsCaptor.getValue();
        assertThat(counts).containsEntry("Patient", 1);
    }

    @Test
    void shouldParseBundleAndExtractResourceCounts() throws IOException {
        UUID jobId = UUID.randomUUID();
        QualityCheckJob job = new QualityCheckJob(jobId, JobStatus.PROCESSING, "BASE_R4", Instant.now());
        when(jobLifecycleManager.startJob(jobId)).thenReturn(job);

        String bundleJson = Files.readString(Path.of("sample-data/clean/clean-bundle.json"));

        worker.processJobAsync(jobId, bundleJson, "BASE_R4");

        verify(jobLifecycleManager).startJob(jobId);
        ArgumentCaptor<Integer> totalCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(jobLifecycleManager).completeJob(eq(jobId), totalCaptor.capture(), countsCaptor.capture());

        assertThat(totalCaptor.getValue()).isGreaterThan(0);
        assertThat(countsCaptor.getValue()).containsKey("Patient");
    }
}
