package com.braeden.fhirlint.controller;

import com.braeden.fhirlint.dto.JobDetailResponse;
import com.braeden.fhirlint.exception.ResourceNotFoundException;
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
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QualityCheckController.class)
@Import(RestExceptionHandler.class)
class QualityCheckStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QualityCheckService qualityCheckService;

    @Test
    void shouldReturnJobDetailsWhenJobExists() throws Exception {
        UUID jobId = UUID.randomUUID();
        Instant now = Instant.now();
        JobDetailResponse response = new JobDetailResponse(
                jobId,
                JobStatus.COMPLETED,
                "BASE_R4",
                now.minusSeconds(10),
                now.minusSeconds(8),
                now,
                15,
                Map.of("Patient", 1, "Observation", 14),
                null
        );

        when(qualityCheckService.getJobStatus(jobId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/quality-checks/" + jobId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jobId.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.targetProfile").value("BASE_R4"))
                .andExpect(jsonPath("$.resourcesAnalyzed").value(15))
                .andExpect(jsonPath("$.resourceTypeCounts.Patient").value(1))
                .andExpect(jsonPath("$.resourceTypeCounts.Observation").value(14));
    }

    @Test
    void shouldReturn404WhenJobDoesNotExist() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(qualityCheckService.getJobStatus(unknownId))
                .thenThrow(new ResourceNotFoundException("Quality check job '" + unknownId + "' was not found."));

        mockMvc.perform(get("/api/v1/quality-checks/" + unknownId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("Quality check job '" + unknownId + "' was not found."));
    }
}
