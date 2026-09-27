package com.braeden.fhirlint.service;

import com.braeden.fhirlint.dto.JobDetailResponse;
import com.braeden.fhirlint.exception.ResourceNotFoundException;
import com.braeden.fhirlint.model.JobStatus;
import com.braeden.fhirlint.model.QualityCheckJob;
import com.braeden.fhirlint.repository.QualityCheckJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class JobLifecycleManager {

    private final QualityCheckJobRepository jobRepository;

    public JobLifecycleManager(QualityCheckJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Transactional
    public QualityCheckJob createJob(UUID id, String profile) {
        QualityCheckJob job = new QualityCheckJob(id, JobStatus.QUEUED, profile, Instant.now());
        return jobRepository.saveAndFlush(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public QualityCheckJob startJob(UUID id) {
        Optional<QualityCheckJob> optionalJob = jobRepository.findById(id);
        if (optionalJob.isEmpty()) {
            return null;
        }
        QualityCheckJob job = optionalJob.get();
        job.setStatus(JobStatus.PROCESSING);
        job.setStartedAt(Instant.now());
        return jobRepository.saveAndFlush(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeJob(UUID id, int totalResources, Map<String, Integer> counts) {
        jobRepository.findById(id).ifPresent(job -> {
            job.setStatus(JobStatus.COMPLETED);
            job.setCompletedAt(Instant.now());
            job.setResourcesAnalyzed(totalResources);
            job.setResourceTypeCounts(counts);
            job.setFailureReason(null);
            jobRepository.saveAndFlush(job);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failJob(UUID id, String sanitizedReason) {
        jobRepository.findById(id).ifPresent(job -> {
            job.setStatus(JobStatus.FAILED);
            job.setCompletedAt(Instant.now());
            job.setFailureReason(sanitizedReason);
            jobRepository.saveAndFlush(job);
        });
    }

    @Transactional(readOnly = true)
    public JobDetailResponse getJobStatus(UUID id) {
        QualityCheckJob job = jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quality check job '" + id + "' was not found."));

        return new JobDetailResponse(
                job.getId(),
                job.getStatus(),
                job.getTargetProfile(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getResourcesAnalyzed(),
                job.getResourceTypeCounts(),
                job.getFailureReason()
        );
    }
}
