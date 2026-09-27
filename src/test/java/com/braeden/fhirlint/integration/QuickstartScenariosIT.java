package com.braeden.fhirlint.integration;

import com.braeden.fhirlint.model.JobStatus;
import com.braeden.fhirlint.model.QualityCheckJob;
import com.braeden.fhirlint.repository.QualityCheckJobRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class QuickstartScenariosIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QualityCheckJobRepository jobRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void executeScenario1CleanBundleIngestion() throws Exception {
        String cleanBundle = Files.readString(Path.of("sample-data/clean/clean-bundle.json"));

        MvcResult result = mockMvc.perform(post("/api/v1/quality-checks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cleanBundle))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID jobId = UUID.fromString(body.get("id").asText());

        // Poll until completion
        long deadline = System.currentTimeMillis() + 10000;
        QualityCheckJob job = null;
        while (System.currentTimeMillis() < deadline) {
            Optional<QualityCheckJob> opt = jobRepository.findById(jobId);
            if (opt.isPresent() && opt.get().getStatus() == JobStatus.COMPLETED) {
                job = opt.get();
                break;
            }
            Thread.sleep(100);
        }

        assertThat(job).isNotNull();
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.getResourcesAnalyzed()).isGreaterThan(0);
    }

    @Test
    void executeScenario2MalformedJsonRejection() throws Exception {
        mockMvc.perform(post("/api/v1/quality-checks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invalidJson\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    void executeScenario3NonFhirJsonRejection() throws Exception {
        mockMvc.perform(post("/api/v1/quality-checks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"John Doe\", \"active\": true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Payload must contain a valid FHIR 'resourceType' declaration."));
    }

    @Test
    void executeScenario4NonExistentJobIdReturns404() throws Exception {
        UUID unknownId = UUID.fromString("00000000-0000-0000-0000-000000000000");

        mockMvc.perform(get("/api/v1/quality-checks/" + unknownId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"));
    }

    @Test
    void executeScenario5MessyBundleIngestion() throws Exception {
        String messyBundle = Files.readString(Path.of("sample-data/messy/messy-bundle.json"));

        MvcResult result = mockMvc.perform(post("/api/v1/quality-checks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(messyBundle))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID jobId = UUID.fromString(body.get("id").asText());

        // Poll until completion
        long deadline = System.currentTimeMillis() + 10000;
        QualityCheckJob job = null;
        while (System.currentTimeMillis() < deadline) {
            Optional<QualityCheckJob> opt = jobRepository.findById(jobId);
            if (opt.isPresent() && opt.get().getStatus() == JobStatus.COMPLETED) {
                job = opt.get();
                break;
            }
            Thread.sleep(100);
        }

        assertThat(job).isNotNull();
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.getResourcesAnalyzed()).isGreaterThan(0);
        assertThat(job.getResourceTypeCounts()).isNotEmpty();
    }
}
