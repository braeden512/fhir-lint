package com.braeden.fhirlint.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;
import com.braeden.fhirlint.model.QualityCheckJob;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AsyncIngestionWorker {

    private static final Logger log = LoggerFactory.getLogger(AsyncIngestionWorker.class);
    private static final Pattern QUOTED_TOKEN_PATTERN = Pattern.compile("(['\"][^'\"]*['\"])");

    private final JobLifecycleManager jobLifecycleManager;
    private final FhirContext fhirContext;

    public AsyncIngestionWorker(JobLifecycleManager jobLifecycleManager, FhirContext fhirContext) {
        this.jobLifecycleManager = jobLifecycleManager;
        this.fhirContext = fhirContext;
    }

    @Async("qualityCheckExecutor")
    public void processJobAsync(UUID jobId, String payloadJson, String targetProfile) {
        log.info("Starting asynchronous processing for job {}", jobId);

        // Commit transition to PROCESSING immediately in an isolated transaction
        QualityCheckJob job = jobLifecycleManager.startJob(jobId);
        if (job == null) {
            log.error("QualityCheckJob with ID {} not found for asynchronous processing", jobId);
            return;
        }

        // HAPI FHIR parsing executes OUTSIDE of any database transaction context
        try {
            IParser parser = fhirContext.newJsonParser();
            IBaseResource resource = parser.parseResource(payloadJson);

            Map<String, Integer> counts = new HashMap<>();
            int totalResources;

            if (resource instanceof Bundle bundle) {
                for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                    if (entry.getResource() != null) {
                        String type = entry.getResource().fhirType();
                        counts.put(type, counts.getOrDefault(type, 0) + 1);
                    }
                }
                totalResources = counts.values().stream().mapToInt(Integer::intValue).sum();
            } else {
                String type = resource.fhirType();
                counts.put(type, 1);
                totalResources = 1;
            }

            jobLifecycleManager.completeJob(jobId, totalResources, counts);
            log.info("Completed asynchronous processing for job {}: {} resources analyzed", jobId, totalResources);
        } catch (Exception ex) {
            String sanitizedReason = sanitizeErrorMessage(ex);
            log.error("Failed asynchronous processing for job {}: {}", jobId, sanitizedReason);
            jobLifecycleManager.failJob(jobId, sanitizedReason);
        } finally {
            // Nullify local reference to assist garbage collection
            payloadJson = null;
        }
    }

    String sanitizeErrorMessage(Exception ex) {
        if (ex instanceof DataFormatException) {
            return "FHIR R4 Schema Parse Error: Invalid data format or unrecognized element in resource definition";
        }
        if (ex instanceof JsonProcessingException) {
            return "Syntax Error: Malformed JSON syntax in submitted resource";
        }

        String raw = ex.getMessage();
        if (raw == null || raw.isBlank()) {
            return "Unknown error during resource parsing";
        }

        // Scrub any quoted token/literal values from the error message to eliminate potential PHI fragments
        String sanitized = QUOTED_TOKEN_PATTERN.matcher(raw).replaceAll("[REDACTED]");
        if (sanitized.length() > 500) {
            sanitized = sanitized.substring(0, 500) + "...";
        }
        return "FHIR Parse Error: " + sanitized;
    }
}
