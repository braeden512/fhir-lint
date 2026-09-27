package com.braeden.fhirlint.repository;

import com.braeden.fhirlint.model.JobStatus;
import com.braeden.fhirlint.model.QualityCheckJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface QualityCheckJobRepository extends JpaRepository<QualityCheckJob, UUID> {

    List<QualityCheckJob> findByStatusAndStartedAtBefore(JobStatus status, Instant threshold);

    List<QualityCheckJob> findByStatusAndCreatedAtBefore(JobStatus status, Instant threshold);
}
