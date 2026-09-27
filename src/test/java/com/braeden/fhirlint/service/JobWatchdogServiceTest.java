package com.braeden.fhirlint.service;

import com.braeden.fhirlint.model.JobStatus;
import com.braeden.fhirlint.model.QualityCheckJob;
import com.braeden.fhirlint.repository.QualityCheckJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobWatchdogServiceTest {

    @Mock
    private QualityCheckJobRepository jobRepository;

    @Test
    void shouldTransitionStalledProcessingAndQueuedJobsToFailed() {
        JobWatchdogService watchdog = new JobWatchdogService(jobRepository, 5);

        UUID stalledJobId = UUID.randomUUID();
        QualityCheckJob stalledProcessingJob = new QualityCheckJob(stalledJobId, JobStatus.PROCESSING, "BASE_R4", Instant.now().minus(10, ChronoUnit.MINUTES));
        stalledProcessingJob.setStartedAt(Instant.now().minus(8, ChronoUnit.MINUTES));

        UUID queuedJobId = UUID.randomUUID();
        QualityCheckJob abandonedQueuedJob = new QualityCheckJob(queuedJobId, JobStatus.QUEUED, "BASE_R4", Instant.now().minus(12, ChronoUnit.MINUTES));

        when(jobRepository.findByStatusAndStartedAtBefore(eq(JobStatus.PROCESSING), any(Instant.class)))
                .thenReturn(List.of(stalledProcessingJob));
        when(jobRepository.findByStatusAndCreatedAtBefore(eq(JobStatus.QUEUED), any(Instant.class)))
                .thenReturn(List.of(abandonedQueuedJob));

        watchdog.cleanupStalledJobs();

        ArgumentCaptor<QualityCheckJob> captor = ArgumentCaptor.forClass(QualityCheckJob.class);
        verify(jobRepository, times(2)).save(captor.capture());
        List<QualityCheckJob> saved = captor.getAllValues();

        QualityCheckJob savedProcessing = saved.get(0);
        assertThat(savedProcessing.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(savedProcessing.getFailureReason()).isEqualTo("Processing timed out or worker terminated unexpectedly");

        QualityCheckJob savedQueued = saved.get(1);
        assertThat(savedQueued.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(savedQueued.getFailureReason()).isEqualTo("Job queued but worker terminated or timed out before processing commenced");
    }
}
