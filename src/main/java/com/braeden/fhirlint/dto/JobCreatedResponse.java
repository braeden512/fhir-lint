package com.braeden.fhirlint.dto;

import com.braeden.fhirlint.model.JobStatus;

import java.time.Instant;
import java.util.UUID;

public record JobCreatedResponse(
        UUID id,
        JobStatus status,
        Instant createdAt,
        String location
) {
}
