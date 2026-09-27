package com.braeden.fhirlint.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "quality_check_jobs")
public class QualityCheckJob {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private JobStatus status;

    @Column(name = "target_profile", length = 64)
    private String targetProfile;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failure_reason", length = 4000)
    private String failureReason;

    @Column(name = "resources_analyzed")
    private Integer resourcesAnalyzed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resource_type_counts", columnDefinition = "jsonb")
    private Map<String, Integer> resourceTypeCounts;

    public QualityCheckJob() {
    }

    public QualityCheckJob(UUID id, JobStatus status, String targetProfile, Instant createdAt) {
        this.id = id;
        this.status = status;
        this.targetProfile = targetProfile != null ? targetProfile : "BASE_R4";
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public String getTargetProfile() {
        return targetProfile;
    }

    public void setTargetProfile(String targetProfile) {
        this.targetProfile = targetProfile;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Integer getResourcesAnalyzed() {
        return resourcesAnalyzed;
    }

    public void setResourcesAnalyzed(Integer resourcesAnalyzed) {
        this.resourcesAnalyzed = resourcesAnalyzed;
    }

    public Map<String, Integer> getResourceTypeCounts() {
        return resourceTypeCounts;
    }

    public void setResourceTypeCounts(Map<String, Integer> resourceTypeCounts) {
        this.resourceTypeCounts = resourceTypeCounts;
    }
}
