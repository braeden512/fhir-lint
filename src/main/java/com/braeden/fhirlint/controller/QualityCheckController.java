package com.braeden.fhirlint.controller;

import com.braeden.fhirlint.dto.ErrorResponse;
import com.braeden.fhirlint.dto.JobCreatedResponse;
import com.braeden.fhirlint.dto.JobDetailResponse;
import com.braeden.fhirlint.service.QualityCheckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/quality-checks")
@Tag(name = "Quality Checks", description = "Endpoints for submitting and monitoring FHIR data quality checks")
public class QualityCheckController {

    private final QualityCheckService qualityCheckService;

    public QualityCheckController(QualityCheckService qualityCheckService) {
        this.qualityCheckService = qualityCheckService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Submit a FHIR dataset for asynchronous quality analysis",
            description = "Accepts a FHIR R4 dataset (single resource or Bundle) as raw JSON or wrapped object. " +
                    "Validates basic syntax synchronously, creates a tracked job, and returns 202 Accepted.",
            responses = {
                    @ApiResponse(
                            responseCode = "202",
                            description = "Quality check job accepted and queued for background processing",
                            headers = @Header(name = HttpHeaders.LOCATION, description = "URI to check status", schema = @Schema(type = "string")),
                            content = @Content(schema = @Schema(implementation = JobCreatedResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Malformed JSON or invalid submission boundary",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    )
            }
    )
    public ResponseEntity<JobCreatedResponse> submitQualityCheck(
            @RequestBody String rawBody,
            @RequestParam(required = false) String profile) {
        JobCreatedResponse response = qualityCheckService.submitQualityCheck(rawBody, profile);
        return ResponseEntity
                .accepted()
                .location(URI.create(response.location()))
                .body(response);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Retrieve quality check job status and results",
            description = "Returns the current state and execution metadata for a given quality check job.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Job status and execution details",
                            content = @Content(schema = @Schema(implementation = JobDetailResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Quality check job not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    )
            }
    )
    public ResponseEntity<JobDetailResponse> getQualityCheckJob(@PathVariable UUID id) {
        JobDetailResponse response = qualityCheckService.getJobStatus(id);
        return ResponseEntity.ok(response);
    }
}
