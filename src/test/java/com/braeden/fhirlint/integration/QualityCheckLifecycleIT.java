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
class QualityCheckLifecycleIT {

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
    void shouldCompleteFullLifecycleForValidBundle() throws Exception {
        String cleanBundleJson = Files.readString(Path.of("sample-data/clean/clean-bundle.json"));

        MvcResult result = mockMvc.perform(post("/api/v1/quality-checks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cleanBundleJson))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andReturn();

        JsonNode postBody = objectMapper.readTree(result.getResponse().getContentAsString());
        String jobIdStr = postBody.get("id").asText();
        UUID jobId = UUID.fromString(jobIdStr);

        assertThat(postBody.get("status").asText()).isEqualTo("QUEUED");

        // Wait for async processing to complete (up to 10 seconds)
        long deadline = System.currentTimeMillis() + 10000;
        QualityCheckJob completedJob = null;
        while (System.currentTimeMillis() < deadline) {
            Optional<QualityCheckJob> opt = jobRepository.findById(jobId);
            if (opt.isPresent() && opt.get().getStatus() == JobStatus.COMPLETED) {
                completedJob = opt.get();
                break;
            }
            Thread.sleep(100);
        }

        assertThat(completedJob).isNotNull();
        assertThat(completedJob.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(completedJob.getResourcesAnalyzed()).isGreaterThan(0);
        assertThat(completedJob.getResourceTypeCounts()).isNotNull();
        assertThat(completedJob.getStartedAt()).isNotNull();
        assertThat(completedJob.getCompletedAt()).isNotNull();

        // Verify GET endpoint returns completed job details
        mockMvc.perform(get("/api/v1/quality-checks/" + jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.resourcesAnalyzed").value(completedJob.getResourcesAnalyzed()))
                .andExpect(jsonPath("$.resourceTypeCounts").isMap());
    }
}
