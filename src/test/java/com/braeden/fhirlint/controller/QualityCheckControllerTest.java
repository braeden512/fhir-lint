package com.braeden.fhirlint.controller;

import com.braeden.fhirlint.dto.JobCreatedResponse;
import com.braeden.fhirlint.model.JobStatus;
import com.braeden.fhirlint.service.QualityCheckService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QualityCheckController.class)
@Import(RestExceptionHandler.class)
class QualityCheckControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QualityCheckService qualityCheckService;

    @Test
    void shouldAcceptValidFhirResourceAndReturn202() throws Exception {
        UUID jobId = UUID.randomUUID();
        Instant now = Instant.now();
        JobCreatedResponse response = new JobCreatedResponse(
                jobId,
                JobStatus.QUEUED,
                now,
                "/api/v1/quality-checks/" + jobId
        );

        when(qualityCheckService.submitQualityCheck(any(), any())).thenReturn(response);

        String payload = """
                {
                  "resourceType": "Patient",
                  "id": "pat-123"
                }
                """;

        mockMvc.perform(post("/api/v1/quality-checks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/quality-checks/" + jobId))
                .andExpect(jsonPath("$.id").value(jobId.toString()))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.location").value("/api/v1/quality-checks/" + jobId));
    }
}
