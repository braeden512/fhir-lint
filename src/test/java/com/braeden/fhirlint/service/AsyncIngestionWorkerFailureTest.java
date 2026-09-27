package com.braeden.fhirlint.service;

import ca.uhn.fhir.context.FhirContext;
import com.braeden.fhirlint.model.JobStatus;
import com.braeden.fhirlint.model.QualityCheckJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsyncIngestionWorkerFailureTest {

    @Mock
    private JobLifecycleManager jobLifecycleManager;

    private FhirContext fhirContext;
    private AsyncIngestionWorker worker;

    @BeforeEach
    void setUp() {
        fhirContext = FhirContext.forR4();
        worker = new AsyncIngestionWorker(jobLifecycleManager, fhirContext);
    }

    @Test
    void shouldTransitionJobToFailedWithSanitizedReasonWhenFhirParserFails() {
        UUID jobId = UUID.randomUUID();
        QualityCheckJob job = new QualityCheckJob(jobId, JobStatus.PROCESSING, "BASE_R4", Instant.now());
        when(jobLifecycleManager.startJob(jobId)).thenReturn(job);

        // Unknown FHIR resourceType with sensitive token
        String corruptedFhir = """
                {
                  "resourceType": "NonExistentResourceType12345",
                  "id": "unknown-1",
                  "patientSecret": "PatientJohnDoeSSN999-88-7777"
                }
                """;

        worker.processJobAsync(jobId, corruptedFhir, "BASE_R4");

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(jobLifecycleManager).failJob(eq(jobId), reasonCaptor.capture());
        String sanitizedReason = reasonCaptor.getValue();

        assertThat(sanitizedReason).isNotNull();
        assertThat(sanitizedReason).doesNotContain("PatientJohnDoeSSN999-88-7777");
        assertThat(sanitizedReason).startsWith("FHIR");
    }

    @Test
    void shouldSanitizeCustomExceptionTokens() {
        Exception ex = new RuntimeException("Syntax error near token 'SensitivePatientNameJaneDoe' in line 4");
        String sanitized = worker.sanitizeErrorMessage(ex);

        assertThat(sanitized).doesNotContain("SensitivePatientNameJaneDoe");
        assertThat(sanitized).contains("[REDACTED]");
    }
}
