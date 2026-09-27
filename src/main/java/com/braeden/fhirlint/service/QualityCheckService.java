package com.braeden.fhirlint.service;

import com.braeden.fhirlint.dto.JobCreatedResponse;
import com.braeden.fhirlint.dto.JobDetailResponse;
import com.braeden.fhirlint.exception.MalformedPayloadException;
import com.braeden.fhirlint.model.JobStatus;
import com.braeden.fhirlint.model.QualityCheckJob;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class QualityCheckService {

    private final JobLifecycleManager jobLifecycleManager;
    private final AsyncIngestionWorker asyncIngestionWorker;
    private final ObjectMapper objectMapper;

    public QualityCheckService(
            JobLifecycleManager jobLifecycleManager,
            AsyncIngestionWorker asyncIngestionWorker,
            ObjectMapper objectMapper) {
        this.jobLifecycleManager = jobLifecycleManager;
        this.asyncIngestionWorker = asyncIngestionWorker;
        this.objectMapper = objectMapper;
    }

    public JobCreatedResponse submitQualityCheck(String rawBody, String requestedProfile) {
        if (rawBody == null || rawBody.trim().isEmpty()) {
            throw new MalformedPayloadException("Request body cannot be empty.");
        }

        JsonNode rootNode;
        try {
            rootNode = objectMapper.readTree(rawBody);
        } catch (JsonProcessingException e) {
            throw new MalformedPayloadException("Malformed JSON payload in request body.");
        }

        if (!rootNode.isObject()) {
            throw new MalformedPayloadException("Payload must be a JSON object.");
        }

        String profile = requestedProfile != null ? requestedProfile : "BASE_R4";
        String fhirPayload = rawBody;

        // Check if payload is wrapped in { "profile": "...", "bundle": { ... } } or { "resource": { ... } }
        if (rootNode.has("profile") && rootNode.get("profile").isTextual()) {
            profile = rootNode.get("profile").asText();
        }

        if (rootNode.has("bundle") && rootNode.get("bundle").isObject()) {
            JsonNode bundleNode = rootNode.get("bundle");
            validateResourceTypeNode(bundleNode, "Wrapped bundle must contain a valid FHIR 'resourceType' declaration.");
            fhirPayload = bundleNode.toString();
        } else if (rootNode.has("resource") && rootNode.get("resource").isObject()) {
            JsonNode resourceNode = rootNode.get("resource");
            validateResourceTypeNode(resourceNode, "Wrapped resource must contain a valid FHIR 'resourceType' declaration.");
            fhirPayload = resourceNode.toString();
        } else {
            // Direct FHIR resource/bundle submission
            validateResourceTypeNode(rootNode, "Payload must contain a valid FHIR 'resourceType' declaration.");
        }

        UUID jobId = UUID.randomUUID();
        QualityCheckJob job = jobLifecycleManager.createJob(jobId, profile);

        // Dispatch background processing
        asyncIngestionWorker.processJobAsync(jobId, fhirPayload, profile);

        String location = "/api/v1/quality-checks/" + jobId;
        return new JobCreatedResponse(jobId, JobStatus.QUEUED, job.getCreatedAt(), location);
    }

    private void validateResourceTypeNode(JsonNode node, String errorMessage) {
        if (!node.hasNonNull("resourceType") || !node.get("resourceType").isTextual() || node.get("resourceType").asText().isBlank()) {
            throw new MalformedPayloadException(errorMessage);
        }
    }

    public JobDetailResponse getJobStatus(UUID id) {
        return jobLifecycleManager.getJobStatus(id);
    }
}
