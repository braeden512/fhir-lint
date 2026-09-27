package com.braeden.fhirlint.dto;

import com.braeden.fhirlint.model.JobStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record JobDetailResponse(
        UUID id,
        JobStatus status,
        String targetProfile,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Integer resourcesAnalyzed,
        Map<String, Integer> resourceTypeCounts,
        String failureReason
) {
}
