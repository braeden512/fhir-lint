package com.braeden.fhirlint.service;

import com.braeden.fhirlint.model.JobStatus;
import com.braeden.fhirlint.model.QualityCheckJob;
import com.braeden.fhirlint.repository.QualityCheckJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class JobWatchdogService {

    private static final Logger log = LoggerFactory.getLogger(JobWatchdogService.class);

    private final QualityCheckJobRepository jobRepository;
    private final int timeoutMinutes;

    public JobWatchdogService(
            QualityCheckJobRepository jobRepository,
            @Value("${fhirlint.watchdog.timeout-minutes:5}") int timeoutMinutes) {
        this.jobRepository = jobRepository;
        this.timeoutMinutes = timeoutMinutes;
    }

    @Scheduled(fixedDelayString = "${fhirlint.watchdog.interval:60000}")
    @Transactional
    public void cleanupStalledJobs() {
        Instant now = Instant.now();
        Instant threshold = now.minus(timeoutMinutes, ChronoUnit.MINUTES);

        // Sweep stalled PROCESSING jobs
        List<QualityCheckJob> stalledProcessingJobs = jobRepository.findByStatusAndStartedAtBefore(JobStatus.PROCESSING, threshold);
        if (!stalledProcessingJobs.isEmpty()) {
            log.warn("Watchdog identified {} stalled jobs in PROCESSING past threshold {}", stalledProcessingJobs.size(), threshold);
            for (QualityCheckJob job : stalledProcessingJobs) {
                job.setStatus(JobStatus.FAILED);
                job.setCompletedAt(now);
                job.setFailureReason("Processing timed out or worker terminated unexpectedly");
                jobRepository.save(job);
                log.info("Watchdog marked stalled job {} as FAILED", job.getId());
            }
        }

        // Sweep abandoned QUEUED jobs (e.g. lost in in-memory queue on server crash/restart)
        List<QualityCheckJob> abandonedQueuedJobs = jobRepository.findByStatusAndCreatedAtBefore(JobStatus.QUEUED, threshold);
        if (!abandonedQueuedJobs.isEmpty()) {
            log.warn("Watchdog identified {} abandoned jobs in QUEUED past threshold {}", abandonedQueuedJobs.size(), threshold);
            for (QualityCheckJob job : abandonedQueuedJobs) {
                job.setStatus(JobStatus.FAILED);
                job.setCompletedAt(now);
                job.setFailureReason("Job queued but worker terminated or timed out before processing commenced");
                jobRepository.save(job);
                log.info("Watchdog marked abandoned queued job {} as FAILED", job.getId());
            }
        }
    }
}
