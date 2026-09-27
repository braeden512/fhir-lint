package com.braeden.fhirlint.integration;

import com.braeden.fhirlint.dto.JobCreatedResponse;
import com.braeden.fhirlint.model.JobStatus;
import com.braeden.fhirlint.model.QualityCheckJob;
import com.braeden.fhirlint.repository.QualityCheckJobRepository;
import com.braeden.fhirlint.service.QualityCheckService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class ZeroRetentionPrivacyIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private QualityCheckService qualityCheckService;

    @Autowired
    private QualityCheckJobRepository jobRepository;

    @Test
    void shouldVerifyDatabaseSchemaHasNoClinicalPayloadColumns() {
        List<String> columnNames = jdbcTemplate.query(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'quality_check_jobs'",
                (rs, rowNum) -> rs.getString("column_name").toLowerCase()
        );

        assertThat(columnNames).isNotEmpty();

        Set<String> allowedColumns = Set.of(
                "id",
                "status",
                "target_profile",
                "created_at",
                "started_at",
                "completed_at",
                "failure_reason",
                "resources_analyzed",
                "resource_type_counts"
        );

        assertThat(columnNames).allSatisfy(col ->
                assertThat(allowedColumns).contains(col)
        );

        // Explicitly assert forbidden clinical data column names
        List<String> forbiddenColumnNames = List.of("payload", "bundle", "raw", "data", "patient", "clinical_data", "resource");
        for (String forbidden : forbiddenColumnNames) {
            assertThat(columnNames).doesNotContain(forbidden);
        }
    }

    @Test
    void shouldEnsureSubmittedPatientPhiIsNotRetainedInDatabaseRow() throws Exception {
        String sensitivePatientName = "SecretPatientSensitiveHealthRecordXYZ987";
        String payload = """
                {
                  "resourceType": "Patient",
                  "id": "pat-sensitive-1",
                  "name": [{"family": "%s", "given": ["Confidential"]}]
                }
                """.formatted(sensitivePatientName);

        JobCreatedResponse created = qualityCheckService.submitQualityCheck(payload, "BASE_R4");
        UUID jobId = created.id();

        // Wait for job completion
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            Optional<QualityCheckJob> opt = jobRepository.findById(jobId);
            if (opt.isPresent() && opt.get().getStatus() == JobStatus.COMPLETED) {
                break;
            }
            Thread.sleep(100);
        }

        // Query raw database row as string representation
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM quality_check_jobs WHERE id = ?",
                jobId
        );

        String rowString = row.toString();
        assertThat(rowString).doesNotContain(sensitivePatientName);
        assertThat(rowString).doesNotContain("Confidential");
        assertThat(rowString).doesNotContain("pat-sensitive-1");
    }
}
